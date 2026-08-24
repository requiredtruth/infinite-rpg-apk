package app.infiniterpg.ai;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import app.infiniterpg.InfiniteRpgApp;
import app.infiniterpg.data.ContentRepository;

/** Resumable selected-model staging backed by Documents/Infinite RPG/Models. */
public final class ModelDownloader {
    private final ContentRepository repo;private final File directory,activeFile,partFile;private final ModelBank bank;
    private final AtomicBoolean cancelled=new AtomicBoolean(false),active=new AtomicBoolean(false);private static final AtomicBoolean migrationStarted=new AtomicBoolean(false);
    public interface Listener{void onProgress(long downloaded,long total,String status);void onDone(File model);void onError(String error);}

    public ModelDownloader(Context context){Context app=context.getApplicationContext();repo=InfiniteRpgApp.get().repo();directory=new File(app.getFilesDir(),"models");directory.mkdirs();activeFile=new File(directory,"active.gguf");partFile=new File(directory,"active.gguf.part");bank=new ModelBank(app);migratePreviousQwen();}
    public ModelCatalog.Profile profile(){return ModelCatalog.selected(repo);}
    public File modelFile(){return activeFile;}
    public boolean isReady(){ModelCatalog.Profile p=profile();return p.key.equals(repo.getState("active_model_key",""))&&ModelBank.valid(activeFile,p.minBytes);}
    public boolean hasPartial(){return partFile.isFile()&&partFile.length()>0;}
    public boolean isDownloading(){return active.get();}
    public void cancel(){cancelled.set(true);}
    public void invalidateActive(){cancel();NativeLlama.cancel();NativeLlama.unload();if(activeFile.exists())activeFile.delete();if(partFile.exists())partFile.delete();repo.putState("active_model_key","");}
    public void delete(){ModelCatalog.Profile p=profile();invalidateActive();bank.delete(p.fileName);File old=new File(directory,p.fileName);if(old.exists())old.delete();}

    public void download(Listener listener){if(!active.compareAndSet(false,true))return;cancelled.set(false);new Thread(()->{ModelCatalog.Profile p=profile();try{
            if(isReady()){listener.onDone(activeFile);return;}
            invalidateForDifferentSelection(p);
            ModelBank.ModelInfo saved=bank.find(p.fileName);
            if(saved!=null){listener.onProgress(0,saved.bytes,"Staging "+p.label+" from Documents bank");if(!bank.stage(p.fileName,activeFile,(got,total)->listener.onProgress(got,total,"Staging from Documents bank")))throw new IllegalStateException("Could not stage banked GGUF");repo.putState("active_model_key",p.key);listener.onDone(activeFile);return;}
            if(p.isBankOnly())throw new IllegalStateException("Imported model is missing from Documents/Infinite RPG/Models");
            long existing=partFile.exists()?partFile.length():0;HttpURLConnection conn=(HttpURLConnection)new URL(p.url).openConnection();conn.setConnectTimeout(20000);conn.setReadTimeout(30000);conn.setRequestProperty("User-Agent","InfiniteRPG/0.5 Android");if(existing>0)conn.setRequestProperty("Range","bytes="+existing+"-");conn.connect();int code=conn.getResponseCode();if(code!=200&&code!=206)throw new IllegalStateException("Download HTTP "+code);if(code==200&&existing>0){partFile.delete();existing=0;}long segment=conn.getContentLengthLong(),total=segment>0?existing+segment:-1;
            try(InputStream in=conn.getInputStream();FileOutputStream out=new FileOutputStream(partFile,existing>0)){byte[] buffer=new byte[1024*1024];int n;long got=existing,last=0;while((n=in.read(buffer))>=0){if(cancelled.get())throw new InterruptedException("Download paused");out.write(buffer,0,n);got+=n;long now=System.currentTimeMillis();if(now-last>250){listener.onProgress(got,total,"Downloading "+p.label);last=now;}}out.getFD().sync();}finally{conn.disconnect();}
            if(!ModelBank.valid(partFile,p.minBytes))throw new IllegalStateException("Downloaded file failed GGUF validation");if(activeFile.exists())activeFile.delete();if(!partFile.renameTo(activeFile))throw new IllegalStateException("Could not finalize selected model");repo.putState("active_model_key",p.key);
            listener.onProgress(activeFile.length(),activeFile.length(),"Saving reusable copy to Documents model bank");try{bank.store(activeFile,p.fileName,(got,totalBytes)->listener.onProgress(got,totalBytes,"Banking model in Documents"));}catch(Exception bankError){repo.log("ERROR","Model works, but Documents bank copy failed: "+bankError.getMessage());}listener.onDone(activeFile);
        }catch(InterruptedException e){listener.onError("Download paused; it will resume from the same byte");}catch(Exception e){listener.onError(e.getMessage()==null?e.toString():e.getMessage());}finally{active.set(false);}},"gguf-model-bank").start();}

    private void invalidateForDifferentSelection(ModelCatalog.Profile p){if(!p.key.equals(repo.getState("active_model_key",""))){NativeLlama.cancel();NativeLlama.unload();if(activeFile.exists())activeFile.delete();if(partFile.exists())partFile.delete();repo.putState("active_model_key","");}}
    private void migratePreviousQwen(){File old=new File(directory,"Qwen3-0.6B-Q8_0.gguf");if(!ModelBank.valid(old,580L*1024L*1024L)||bank.find(old.getName())!=null||!migrationStarted.compareAndSet(false,true))return;repo.putState("model_status","banking previously downloaded Qwen3 model");new Thread(()->{try{bank.store(old,old.getName(),null);repo.log("INFO","Copied previous Qwen3 model into the Documents model bank");}catch(Exception e){repo.log("ERROR","Could not bank previous Qwen3 model: "+e.getMessage());}},"legacy-model-bank").start();}
}
