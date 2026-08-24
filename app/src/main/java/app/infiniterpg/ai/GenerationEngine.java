package app.infiniterpg.ai;

import android.content.Context;
import android.app.ActivityManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import app.infiniterpg.data.ContentItem;
import app.infiniterpg.data.ContentRepository;
import app.infiniterpg.data.GenerationJob;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class GenerationEngine {
    public static final class Result { public final boolean accepted; public final String message; Result(boolean a,String m){accepted=a;message=m;} }
    private final ContentRepository repo;
    private final ModelDownloader downloader;
    private final Random random=new Random();
    private final boolean compactDevice;

    public GenerationEngine(Context context,ContentRepository repo){this.repo=repo;this.downloader=new ModelDownloader(context);ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);compactDevice=memory.totalMem<6000L*1024L*1024L;repo.putState("generation_profile",ModelCatalog.settingsSummary(repo));}
    public ModelDownloader downloader(){return downloader;}

    public boolean ensureLoaded() {
        if(!downloader.isReady()||!NativeLlama.isAvailable())return false;
        if(NativeLlama.isLoaded())return true;
        ModelCatalog.Profile profile=ModelCatalog.selected(repo);int requested=integer(repo.getState("model_context",Integer.toString(profile.context)),profile.context);int ctx=Math.max(768,Math.min(compactDevice?1536:1536,requested));int threads=Math.max(1,Math.min(2,integer(repo.getState("model_threads",Integer.toString(profile.threads)),profile.threads)));
        repo.putState("generation_profile",ModelCatalog.settingsSummary(repo));repo.putState("native_stage","loading "+profile.label+" at "+ctx+" context / "+threads+" thread(s)");
        boolean ok=NativeLlama.load(downloader.modelFile().getAbsolutePath(),ctx,threads);
        repo.putState("model_runtime",ok?"loaded":"error: "+NativeLlama.lastError());return ok;
    }

    public Result generateOne() {
        GenerationJob job=repo.claimNextJob(); if(job==null)return new Result(false,"No queued job");
        repo.putState("active_job",job.type+" — "+job.prompt);repo.log("RUN",job.type+" generation pass");
        try {
            if(!ensureLoaded()){
                repo.finishJob(job.id,false,"Model not downloaded or native runtime missing");
                return new Result(false,"Qwen is not ready");
            }
            GenerationJob work=job;
            if("ANY".equals(job.type))work=planNovelCreation(job);
            repo.putState("active_job",work.type+" — "+work.prompt);
            ModelCatalog.Profile profile=ModelCatalog.selected(repo);int configured=integer(repo.getState("model_max_tokens",Integer.toString(profile.maxTokens)),profile.maxTokens);int max=Math.max(160,Math.min(460,configured));float temperature=decimal(repo.getState("model_temperature",Float.toString(profile.temperature)),profile.temperature);
            String prompt=draftPrompt(work);String draft=runSafely("DRAFT",prompt,max,temperature,random.nextLong());
            repo.putState("last_tps",String.format(Locale.US,"%.1f",NativeLlama.lastTokensPerSecond()));
            JSONObject candidate;String why;try{candidate=extractObject(draft);why=localValidation(work.type,candidate);}catch(Exception invalid){candidate=null;why=invalid.getMessage();}
            if(why!=null){repo.log("REPAIR",work.type+": "+why);String repaired=runSafely("REPAIR",repairPrompt(work,draft,why),max,.12f,random.nextLong());try{candidate=extractObject(repaired);why=localValidation(work.type,candidate);}catch(Exception invalid){candidate=null;why=invalid.getMessage();}}
            if(why!=null||candidate==null){String failed="Draft and repair failed: "+(why==null?"invalid output":why);repo.finishJob(job.id,false,failed);repo.log("REJECT",failed);return new Result(false,failed);}
            String judgment=runSafely("JUDGE",judgePrompt(work.type,candidate),112,0.25f,random.nextLong());
            JSONObject judge=extractObject(judgment);boolean approved=judge.optBoolean("approved",false);double score=judge.optDouble("score",0);
            String reason=judge.optString("reason","No judge reason");
            if(!approved||score<0.72){repo.finishJob(job.id,false,"Judge rejected: "+reason);repo.log("REJECT",work.type+": "+reason);return new Result(false,reason);}
            boolean accepted=repo.acceptGenerated(work.type,candidate,score);
            repo.finishJob(job.id,accepted,accepted?"accepted":"duplicate or similarity threshold");
            return new Result(accepted,accepted?"Accepted live "+work.type+": "+candidate.optString("name"):"Rejected as duplicate/similar");
        } catch(Exception e){String message=e.getMessage()==null?e.toString():e.getMessage();repo.finishJob(job.id,false,message);repo.log("ERROR",job.type+": "+message);return new Result(false,message);}
        finally{repo.putState("active_job","idle");}
    }

    private GenerationJob planNovelCreation(GenerationJob job) {
        String[] allowed={"MATERIAL","ITEM","RECIPE","BIOME","CREATURE","QUEST","TILE_STYLE","MUSIC","STRUCTURE","WEATHER"};
        String fallback=allowed[Math.floorMod((int)(job.id*31+job.attempts),allowed.length)];
        try{
            String prompt="You are the Infinite RPG endless-world planner.\nChoose what the game should create next. It must connect usefully to the current live catalog and not merely rename something. Allowed type: MATERIAL, ITEM, RECIPE, BIOME, CREATURE, QUEST, TILE_STYLE, MUSIC, STRUCTURE, WEATHER.\nExisting materials: "+catalog("MATERIAL",10,350)+"\nExisting items: "+catalog("ITEM",10,350)+"\nExisting biomes: "+catalog("BIOME",8,280)+"\nExisting creatures: "+catalog("CREATURE",8,280)+"\nReturn raw JSON only: {\"type\":\"ONE_ALLOWED_TYPE\",\"concept\":\"one concrete connected idea\"}.";
            JSONObject plan=extractObject(runSafely("PLANNER",prompt,80,0.85f,random.nextLong()));String selected=plan.optString("type",fallback).trim().toUpperCase(Locale.US);boolean valid=false;for(String type:allowed)if(type.equals(selected)){valid=true;break;}if(!valid)selected=fallback;String concept=plan.optString("concept","Expand the live world with a useful original connected addition");repo.putState("forge_choice",selected+" — "+concept);repo.log("PLAN","Qwen chose "+selected+": "+concept);return new GenerationJob(job.id,selected,"Qwen selected this endless creation: "+concept,job.priority,job.attempts);
        }catch(Exception e){repo.putState("forge_choice",fallback+" — deterministic planner fallback");repo.log("PLAN","Planner JSON fallback selected "+fallback);return new GenerationJob(job.id,fallback,"Create an original addition that connects to the live catalog",job.priority,job.attempts);}
    }

    private String runSafely(String pass,String prompt,int max,float temperature,long seed){
        ModelCatalog.Profile profile=ModelCatalog.selected(repo);String effective=ModelCatalog.wrapPrompt(repo,prompt);repo.putState("stream_pass",pass+" • JSON-GUIDED • CRASH-GUARDED");repo.putState("current_prompt","MODEL  "+profile.label+"\nTEMPLATE  "+repo.getState("model_template",profile.template)+"\n\n"+prompt);repo.putState("stream_output","Waiting for the selected model's first token…");repo.putState("stream_status",pass+" — decoding prompt in safe 64-token chunks");repo.putState("stream_token_count","0");repo.putState("stream_started_ms",Long.toString(System.currentTimeMillis()));repo.putState("native_stage",pass.toLowerCase(Locale.US)+" prompt chunk decode");
        AtomicBoolean done=new AtomicBoolean(false);AtomicInteger tokenCount=new AtomicInteger(0);AtomicInteger liveTpsBits=new AtomicInteger(Float.floatToIntBits(0f));Object streamLock=new Object();StringBuilder live=new StringBuilder();
        Thread telemetry=new Thread(()->{while(!done.get()){String snapshot;synchronized(streamLock){snapshot=live.toString();}float liveTps=Float.intBitsToFloat(liveTpsBits.get());repo.putState("last_tps",String.format(Locale.US,"%.1f",liveTps));repo.putState("stream_token_count",Integer.toString(tokenCount.get()));repo.putState("stream_status",tokenCount.get()>0?pass+" — streaming live tokens":pass+" — decoding prompt");if(!snapshot.isEmpty())repo.putState("stream_output",snapshot);try{Thread.sleep(250);}catch(InterruptedException e){return;}}},"generation-stream-publisher");telemetry.setDaemon(true);telemetry.start();
        String result;try{result=NativeLlama.completeStreaming(effective,max,temperature,seed,(chunk,tps)->{liveTpsBits.set(Float.floatToIntBits(tps));if(chunk==null||chunk.isEmpty())return;synchronized(streamLock){live.append(chunk);}tokenCount.incrementAndGet();});repo.putState("native_stage",pass.toLowerCase(Locale.US)+" output returned");}finally{done.set(true);telemetry.interrupt();}
        repo.putState("stream_output",result==null||result.isEmpty()?"The model pass ended without usable output.":result);repo.putState("stream_token_count",Integer.toString(tokenCount.get()));repo.putState("stream_status",pass+" — pass complete");repo.putState("last_tps",String.format(Locale.US,"%.1f",NativeLlama.lastTokensPerSecond()));return result;
    }

    private String draftPrompt(GenerationJob job) {
        return "You are the Infinite RPG offline game-content author.\n"+
            "Create exactly ONE "+job.type+" for an infinite 2D exploration and crafting RPG. "+job.prompt+".\n"+
            "OUTPUT CONTRACT: "+ContentSchemas.forType(job.type)+"\n"+
            "Existing same-type catalog (do not repeat or lightly rename): "+catalog(job.type,12,500)+"\n"+
            referencesFor(job.type)+
            "Rules: Invent every value; never emit empty strings. identity_key must be the exact key name and must contain a new descriptive snake_case value. Colors are quoted #RRGGBB strings, never numbers or nested matrices. Use playable coherent values and ASCII only; no copyrighted names. Pixel types require 16 rows of 16 quoted palette digits with no shorthand. Recipes and drops use only catalog keys. Return one complete raw JSON object only; no markdown.";
    }

    private String repairPrompt(GenerationJob job,String bad,String reason){String clipped=bad==null?"":bad.substring(0,Math.min(900,bad.length()));return "Repair one invalid "+job.type+" object. Failure: "+reason+"\nOUTPUT CONTRACT: "+ContentSchemas.forType(job.type)+"\nInvalid output: "+clipped+"\nReplace empty or wrong values with original concrete values. Use the exact key identity_key, not key. Colors must be quoted #RRGGBB strings. Preserve useful ideas only when they satisfy the contract. Return one complete raw JSON object only.";}

    private String referencesFor(String type){
        if("MATERIAL".equals(type)||"TILE_STYLE".equals(type)||"WEATHER".equals(type))return "Existing biomes: "+catalog("BIOME",10,330)+"\n";
        if("BIOME".equals(type))return "Existing materials: "+catalog("MATERIAL",12,400)+"\n";
        if("RECIPE".equals(type)||"STRUCTURE".equals(type))return "Existing materials: "+catalog("MATERIAL",12,390)+"\nExisting items: "+catalog("ITEM",12,390)+"\n";
        if("CREATURE".equals(type))return "Existing biomes: "+catalog("BIOME",8,280)+"\nExisting foods and drops: "+catalog("MATERIAL",10,330)+catalog("ITEM",8,270)+"\n";
        if("QUEST".equals(type))return "Existing materials: "+catalog("MATERIAL",8,270)+"\nExisting items: "+catalog("ITEM",8,270)+"\nExisting creatures: "+catalog("CREATURE",6,220)+"\n";
        return "";
    }

    private String judgePrompt(String type,JSONObject candidate) {
        return "You are the strict second-pass Infinite RPG catalog judge.\nCandidate type: "+type+"\nCandidate: "+candidate+"\n"+
            "Existing catalog: "+catalog(type,16,650)+"\nRequired schema: "+ContentSchemas.forType(type)+"\n"+
            "Reject if it duplicates, lightly renames, contradicts, cannot be rendered/used, references impossible keys, is unbalanced, or misses fields. " +
            "Return raw JSON only: {\"approved\":true|false,\"score\":0.0..1.0,\"reason\":\"short concrete reason\",\"closest_existing\":\"key or none\"}.";
    }
    private String catalog(String type,int limit,int max){for(int n=limit;n>0;n--){String value=repo.compactCatalog(type,n);if(value.length()<=max)return value;}return "[]";}

    private String localValidation(String type,JSONObject o) {
        String id=ContentRepository.normalize(o.optString("identity_key",""));String name=o.optString("name","").trim();
        if(id.length()<3||name.length()<3)return "Missing valid identity_key or name";
        o.remove("identity_key");try{o.put("identity_key",id);}catch(JSONException ignored){}
        if(type.equals("MATERIAL")&&!validColor(o.optString("color")))return "Material color must be #RRGGBB";
        if(type.equals("ITEM")&&!validColor(o.optString("color")))return "Item color must be #RRGGBB";
        if(type.equals("BIOME")){JSONArray p=o.optJSONArray("palette");if(p==null||p.length()!=4)return "Biome requires four palette colors";for(int i=0;i<4;i++)if(!validColor(p.optString(i)))return "Biome palette entries must be four quoted #RRGGBB strings";if(o.optDouble("noise_max",0)<=o.optDouble("noise_min",0))return "Biome noise_max must be greater than noise_min";}
        if(type.equals("RECIPE")){JSONObject ingredients=o.optJSONObject("ingredients");if(ingredients==null)return "Recipe needs ingredients";String result=o.optString("result","");if(!catalogKeyExists(result))return "Recipe result must already exist in the item or material catalog";String bad=invalidReferences(ingredients);if(bad!=null)return "Recipe references missing or invalid ingredient: "+bad;}
        if(type.equals("ITEM")||type.equals("TILE_STYLE")||type.equals("CREATURE")||type.equals("STRUCTURE")){JSONArray palette=o.optJSONArray("palette"),rows=o.optJSONArray("pixels");if(palette==null||palette.length()!=6)return "Pixel texture requires exactly six palette colors";if(rows==null||rows.length()!=16)return "Pixel texture requires exactly sixteen rows";for(int y=0;y<16;y++)if(!rows.optString(y).matches("[0-5]{16}"))return "Every pixel row must be exactly 16 digits from 0 to 5";}
        if(type.equals("CREATURE")){JSONObject drops=o.optJSONObject("drops");String food=o.optString("food","");if(drops==null||food.isEmpty())return "Creature requires food and drops";if(!catalogKeyExists(food))return "Creature food must exist in the item or material catalog";String bad=invalidReferences(drops);if(bad!=null)return "Creature references missing or invalid drop: "+bad;}
        if(type.equals("STRUCTURE")){JSONObject required=o.optJSONObject("required_items");if(required==null)return "Structure requires build ingredients";String bad=invalidReferences(required);if(bad!=null)return "Structure references missing or invalid build item: "+bad;}
        if(type.equals("WEATHER")&&(!validColor(o.optString("color"))||o.optDouble("light",0)<.35||o.optDouble("light",2)>1.0))return "Weather requires a valid color and light from 0.35 to 1.0";
        if(type.equals("MUSIC")){JSONArray c=o.optJSONArray("chords"),m=o.optJSONArray("melody"),b=o.optJSONArray("bass"),r=o.optJSONArray("rhythm");if(c==null||c.length()!=8||m==null||m.length()!=32||b==null||b.length()!=16||r==null||r.length()!=16)return "Music needs 8 chords, 32 melody steps, 16 bass steps, and 16 rhythm steps";}
        return null;
    }
    private boolean catalogKeyExists(String key){return key!=null&&!key.isEmpty()&&(repo.byIdentity("ITEM",key)!=null||repo.byIdentity("MATERIAL",key)!=null);}
    private String invalidReferences(JSONObject values){JSONArray names=values.names();if(names==null||names.length()==0)return "empty list";for(int i=0;i<names.length();i++){String key=names.optString(i);if(values.optInt(key,0)<=0||!catalogKeyExists(key))return key;}return null;}
    private boolean validColor(String s){return s!=null&&s.matches("#[0-9a-fA-F]{6}");}
    private int integer(String value,int fallback){try{return Integer.parseInt(value);}catch(Exception e){return fallback;}}
    private float decimal(String value,float fallback){try{return Math.max(.05f,Math.min(1.2f,Float.parseFloat(value)));}catch(Exception e){return fallback;}}

    public static JSONObject extractObject(String text)throws JSONException {
        if(text==null)throw new JSONException("Empty model output");int start=text.indexOf('{');if(start<0)throw new JSONException("Model returned no JSON object");
        int depth=0;boolean quoted=false,escaped=false;
        for(int i=start;i<text.length();i++){char c=text.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='\"')quoted=false;}else{if(c=='\"')quoted=true;else if(c=='{')depth++;else if(c=='}'&&--depth==0)return new JSONObject(text.substring(start,i+1));}}
        throw new JSONException("Model JSON object was incomplete");
    }
}
