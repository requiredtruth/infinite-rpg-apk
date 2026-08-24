#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <mutex>
#include <algorithm>
#include <random>
#include <string>
#include <vector>
#include "llama.h"

#define TAG "InfiniteRpgLlama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG,__VA_ARGS__)

static std::mutex g_mutex;
static llama_model *g_model=nullptr;
static llama_context *g_context=nullptr;
static float g_tps=0;
static std::atomic<bool> g_cancel{false};
static std::atomic<bool> g_loaded{false};
static std::string g_error;

// Generic strict JSON grammar. Content-specific requirements remain enforced
// by the Java validator and judge, while this prevents truncated prose,
// markdown fences, and malformed commas/quotes from reaching JSONObject.
static const char * JSON_GRAMMAR=R"GBNF(
root   ::= object
value  ::= object | array | string | number | ("true" | "false" | "null") ws
object ::= "{" ws (string ":" ws value ("," ws string ":" ws value)*)? "}" ws
array  ::= "[" ws (value ("," ws value)*)? "]" ws
string ::= "\"" ([^"\\\x7F\x00-\x1F] | "\\" (["\\bfnrt] | "u" [0-9a-fA-F]{4}))* "\"" ws
number ::= ("-"? ([0-9] | [1-9] [0-9]{0,15})) ("." [0-9]+)? ([eE] [-+]? [0-9]+)? ws
ws ::= | " " | "\n" [ \t]{0,20}
)GBNF";

static void release_all(){g_loaded.store(false);if(g_context){llama_free(g_context);g_context=nullptr;}if(g_model){llama_model_free(g_model);g_model=nullptr;}}
static std::string jstring_utf(JNIEnv *env,jstring s){if(!s)return {};const char *p=env->GetStringUTFChars(s,nullptr);std::string out=p?p:"";if(p)env->ReleaseStringUTFChars(s,p);return out;}

// llama token pieces are UTF-8 byte fragments. NewStringUTF expects modified UTF-8 and
// aborts Android when handed a split or malformed sequence, so decode defensively to UTF-16.
static jstring java_string(JNIEnv *env,const std::string &value){
    std::vector<jchar> out;out.reserve(value.size());size_t i=0;
    while(i<value.size()){
        const unsigned char a=(unsigned char)value[i];uint32_t cp=0;size_t n=1;
        if(a<0x80){cp=a;}
        else if((a&0xE0)==0xC0&&i+1<value.size()){cp=((a&0x1F)<<6)|((unsigned char)value[i+1]&0x3F);n=2;if(cp<0x80)cp=0xFFFD;}
        else if((a&0xF0)==0xE0&&i+2<value.size()){cp=((a&0x0F)<<12)|(((unsigned char)value[i+1]&0x3F)<<6)|((unsigned char)value[i+2]&0x3F);n=3;if(cp<0x800||(cp>=0xD800&&cp<=0xDFFF))cp=0xFFFD;}
        else if((a&0xF8)==0xF0&&i+3<value.size()){cp=((a&7)<<18)|(((unsigned char)value[i+1]&0x3F)<<12)|(((unsigned char)value[i+2]&0x3F)<<6)|((unsigned char)value[i+3]&0x3F);n=4;if(cp<0x10000||cp>0x10FFFF)cp=0xFFFD;}
        else cp=0xFFFD;
        bool continuation=true;for(size_t k=1;k<n;k++)if((((unsigned char)value[i+k])&0xC0)!=0x80)continuation=false;if(!continuation){cp=0xFFFD;n=1;}
        if(cp<=0xFFFF)out.push_back((jchar)cp);else{cp-=0x10000;out.push_back((jchar)(0xD800+(cp>>10)));out.push_back((jchar)(0xDC00+(cp&0x3FF)));}i+=n;
    }
    return env->NewString(out.empty()?nullptr:out.data(),(jsize)out.size());
}

extern "C" JNIEXPORT jboolean JNICALL Java_app_infiniterpg_ai_NativeLlama_load(JNIEnv *env,jclass,jstring path,jint n_ctx,jint threads){
    std::lock_guard<std::mutex> guard(g_mutex);release_all();g_error.clear();
    llama_backend_init();llama_model_params mp=llama_model_default_params();
    std::string file=jstring_utf(env,path);g_model=llama_model_load_from_file(file.c_str(),mp);if(!g_model){g_error="llama.cpp could not load the GGUF";return JNI_FALSE;}
    llama_context_params cp=llama_context_default_params();cp.n_ctx=std::max(768,std::min(1536,(int)n_ctx));cp.n_batch=64;cp.n_ubatch=64;cp.n_threads=std::max(1,std::min(2,(int)threads));cp.n_threads_batch=cp.n_threads;cp.no_perf=false;
    g_context=llama_init_from_model(g_model,cp);if(!g_context){g_error="llama.cpp could not allocate context";release_all();return JNI_FALSE;}g_loaded.store(true);return JNI_TRUE;
}

static std::string complete_internal(JNIEnv *env,jstring prompt,jint max_tokens,jfloat temperature,jlong seed,jobject listener){
    std::lock_guard<std::mutex> guard(g_mutex);g_error.clear();g_tps=0;if(!g_model||!g_context){g_error="Model is not loaded";return {};}
    g_cancel.store(false);jmethodID callback=nullptr;if(listener){jclass cls=env->GetObjectClass(listener);callback=env->GetMethodID(cls,"onToken","(Ljava/lang/String;F)V");}
    std::string input=jstring_utf(env,prompt);llama_memory_clear(llama_get_memory(g_context),true);const llama_vocab *vocab=llama_model_get_vocab(g_model);
    int count=-llama_tokenize(vocab,input.c_str(),(int)input.size(),nullptr,0,true,true);if(count<=0){g_error="Prompt tokenization failed";return {};}
    std::vector<llama_token> tokens(count);if(llama_tokenize(vocab,input.c_str(),(int)input.size(),tokens.data(),count,true,true)<0){g_error="Prompt tokenization failed";return {};}
    // n_batch is deliberately only 64 to keep memory use low. Passing the
    // entire several-hundred-token prompt to llama_decode in one batch can hit
    // a llama.cpp assertion and abort this Android process. Decode in bounded
    // chunks instead; the context appends each chunk in order.
    const int decode_chunk=64;int decoded=0;
    while(decoded<count&&!g_cancel.load()){
        int n=std::min(decode_chunk,count-decoded);llama_batch batch=llama_batch_get_one(tokens.data()+decoded,n);
        int rc=llama_decode(g_context,batch);if(rc!=0){g_error="Prompt chunk decode failed at token "+std::to_string(decoded)+" (code "+std::to_string(rc)+")";return {};}
        decoded+=n;
    }
    if(g_cancel.load()){g_error="Generation cancelled while decoding prompt";return {};}
    int available=std::max(0,(int)llama_n_ctx(g_context)-count-8);int token_limit=std::min({460,(int)max_tokens,available});if(token_limit<32){g_error="Prompt leaves too little context for JSON output";return {};}
    llama_sampler_chain_params sp=llama_sampler_chain_default_params();llama_sampler *sampler=llama_sampler_chain_init(sp);llama_sampler *grammar=llama_sampler_init_grammar(vocab,JSON_GRAMMAR,"root");if(!grammar){llama_sampler_free(sampler);g_error="Could not initialize strict JSON grammar";return {};}llama_sampler_chain_add(sampler,grammar);llama_sampler_chain_add(sampler,llama_sampler_init_top_k(20));llama_sampler_chain_add(sampler,llama_sampler_init_top_p(.90f,1));llama_sampler_chain_add(sampler,llama_sampler_init_temp(std::max(.05f,(float)temperature)));llama_sampler_chain_add(sampler,llama_sampler_init_dist((uint32_t)seed));
    std::string output;output.reserve(max_tokens*5);auto started=std::chrono::steady_clock::now();int generated=0;
    for(int i=0;i<token_limit&&!g_cancel.load();i++){llama_token tok=llama_sampler_sample(sampler,g_context,-1);if(llama_vocab_is_eog(vocab,tok))break;char piece[256];std::string chunk;int n=llama_token_to_piece(vocab,tok,piece,sizeof(piece),0,true);if(n<0){std::vector<char> big(-n);n=llama_token_to_piece(vocab,tok,big.data(),(int)big.size(),0,true);if(n>0)chunk.assign(big.data(),n);}else if(n>0)chunk.assign(piece,n);output+=chunk;llama_batch one=llama_batch_get_one(&tok,1);if(llama_decode(g_context,one)!=0){g_error="Token decode stopped at the context limit";break;}generated++;double seconds=std::chrono::duration<double>(std::chrono::steady_clock::now()-started).count();g_tps=seconds>0?generated/seconds:0;if(listener&&callback&&!chunk.empty()){jstring jchunk=java_string(env,chunk);env->CallVoidMethod(listener,callback,jchunk,g_tps);env->DeleteLocalRef(jchunk);if(env->ExceptionCheck()){env->ExceptionClear();listener=nullptr;callback=nullptr;}}}
    llama_sampler_free(sampler);return output;
}

extern "C" JNIEXPORT jstring JNICALL Java_app_infiniterpg_ai_NativeLlama_complete(JNIEnv *env,jclass,jstring prompt,jint max_tokens,jfloat temperature,jlong seed){std::string out=complete_internal(env,prompt,max_tokens,temperature,seed,nullptr);return java_string(env,out);}
extern "C" JNIEXPORT jstring JNICALL Java_app_infiniterpg_ai_NativeLlama_completeStreaming(JNIEnv *env,jclass,jstring prompt,jint max_tokens,jfloat temperature,jlong seed,jobject listener){std::string out=complete_internal(env,prompt,max_tokens,temperature,seed,listener);return java_string(env,out);}
extern "C" JNIEXPORT void JNICALL Java_app_infiniterpg_ai_NativeLlama_cancel(JNIEnv*,jclass){g_cancel.store(true);}

extern "C" JNIEXPORT void JNICALL Java_app_infiniterpg_ai_NativeLlama_unload(JNIEnv*,jclass){std::lock_guard<std::mutex> guard(g_mutex);release_all();}
extern "C" JNIEXPORT jboolean JNICALL Java_app_infiniterpg_ai_NativeLlama_isLoaded(JNIEnv*,jclass){return g_loaded.load()?JNI_TRUE:JNI_FALSE;}
extern "C" JNIEXPORT jfloat JNICALL Java_app_infiniterpg_ai_NativeLlama_lastTokensPerSecond(JNIEnv*,jclass){return g_tps;}
extern "C" JNIEXPORT jstring JNICALL Java_app_infiniterpg_ai_NativeLlama_lastError(JNIEnv *env,jclass){return java_string(env,g_error);}
