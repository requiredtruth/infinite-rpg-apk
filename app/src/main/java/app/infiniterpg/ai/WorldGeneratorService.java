package app.infiniterpg.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import app.infiniterpg.InfiniteRpgApp;
import app.infiniterpg.MainActivity;
import app.infiniterpg.R;
import app.infiniterpg.data.ContentRepository;

public final class WorldGeneratorService extends Service {
    public static final String ACTION_REFRESH="app.infiniterpg.GENERATOR_REFRESH";
    public static final String ACTION_GENERATE_NOW="app.infiniterpg.GENERATE_NOW";
    public static final String ACTION_START_GENERATION="app.infiniterpg.START_GENERATION";
    public static final String ACTION_STOP_GENERATION="app.infiniterpg.STOP_GENERATION";
    public static final String ACTION_DOWNLOAD="app.infiniterpg.DOWNLOAD_MODEL";
    public static final String ACTION_PAUSE_DOWNLOAD="app.infiniterpg.PAUSE_DOWNLOAD";
    public static final String ACTION_DELETE_MODEL="app.infiniterpg.DELETE_MODEL";
    public static final String ACTION_RELOAD_MODEL="app.infiniterpg.RELOAD_MODEL";
    public static final String ACTION_GAME_ACTIVE="app.infiniterpg.GAME_ACTIVE";
    public static final String ACTION_GAME_IDLE="app.infiniterpg.GAME_IDLE";
    private static final String CHANNEL="infinite_rpg_generator";
    private final AtomicBoolean running=new AtomicBoolean(false);
    private final AtomicBoolean generationRequested=new AtomicBoolean(false);
    private ContentRepository repo; private GenerationEngine engine; private PowerManager power;

    @Override public void onCreate(){
        super.onCreate();repo=InfiniteRpgApp.get().repo();engine=new GenerationEngine(this,repo);power=(PowerManager)getSystemService(POWER_SERVICE);createChannel();startForeground(41,notification("Preparing local world generator"));
        if(Boolean.parseBoolean(repo.getState("generation_inflight","false"))){
            String stage=repo.getState("native_stage","unknown native stage");int recovered=repo.recoverRunningJobs();int crashes=integer(repo.getState("native_crash_count","0"),0)+1;
            repo.putState("native_crash_count",Integer.toString(crashes));repo.putState("generation_inflight","false");repo.putState("generator_enabled","false");repo.putState("generator_phase","recovered at "+stage+" — press Start or Generate Now");repo.putState("last_result","Native pass stopped at "+stage+". Recovered "+recovered+" job(s); controls are ready to retry.");repo.putState("model_runtime","recovered after interrupted native pass #"+crashes);repo.log("RECOVER","Native process stopped at "+stage+"; "+recovered+" job(s) requeued");
        }
        running.set(true);new Thread(this::loop,"world-generator").start();
        if(!engine.downloader().isReady()&&engine.downloader().hasPartial())downloadModel();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_GENERATE_NOW.equals(intent.getAction())){repo.putState("generator_enabled","true");repo.putState("generator_phase","Generate Now accepted — preparing Qwen");repo.putState("last_result","Generate Now accepted; checking model and device state");generationRequested.set(true);}
        if(intent!=null&&ACTION_START_GENERATION.equals(intent.getAction())){repo.putState("generator_enabled","true");repo.putState("generator_phase","Start accepted — preparing Qwen");repo.putState("last_result","Generation enabled; queued work will start now");generationRequested.set(true);}
        if(intent!=null&&ACTION_STOP_GENERATION.equals(intent.getAction())){repo.putState("generator_enabled","false");NativeLlama.cancel();repo.putState("generator_phase","stopped by user");}
        if(intent!=null&&ACTION_GAME_ACTIVE.equals(intent.getAction())){repo.putState("gameplay_active","true");NativeLlama.cancel();repo.putState("generator_phase","yielding to gameplay");}
        if(intent!=null&&ACTION_GAME_IDLE.equals(intent.getAction()))repo.putState("gameplay_active","false");
        if(intent!=null&&ACTION_DOWNLOAD.equals(intent.getAction()))downloadModel();
        if(intent!=null&&ACTION_PAUSE_DOWNLOAD.equals(intent.getAction())){engine.downloader().cancel();repo.putState("model_status","download paused — auto-resume enabled");}
        if(intent!=null&&ACTION_DELETE_MODEL.equals(intent.getAction())){engine.downloader().delete();repo.putState("model_status","not downloaded");repo.putState("download_bytes","0");repo.putState("download_total","-1");repo.log("INFO","Local model removed");}
        if(intent!=null&&ACTION_RELOAD_MODEL.equals(intent.getAction())){engine.downloader().invalidateActive();repo.putState("generator_enabled","false");repo.putState("model_status","switching to "+ModelCatalog.selected(repo).label);repo.putState("model_runtime","unloaded for model/settings change");repo.log("MODEL","Selected "+ModelCatalog.settingsSummary(repo));downloadModel();}
        broadcast();return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){running.set(false);engine.downloader().cancel();NativeLlama.cancel();NativeLlama.unload();super.onDestroy();}

    private void downloadModel(){
        repo.putState("model_status","starting download");
        engine.downloader().download(new ModelDownloader.Listener(){
            @Override public void onProgress(long got,long total,String status){repo.putState("model_status",status);repo.putState("download_bytes",Long.toString(got));repo.putState("download_total",Long.toString(total));updateNotification(status+" "+percent(got,total));broadcast();}
            @Override public void onDone(File model){ModelCatalog.Profile p=ModelCatalog.selected(repo);repo.putState("model_status",p.label+" ready");repo.putState("model_runtime","selected model staged; press Start generations");repo.log("INFO",p.label+" ready ("+(model.length()/1048576)+" MiB); reusable bank copy is in Documents/Infinite RPG/Models");updateNotification(p.label+" ready");generationRequested.set(true);broadcast();}
            @Override public void onError(String error){boolean paused=error.startsWith("Download paused");repo.putState("model_status",paused?"download paused — auto-resume enabled":"connection lost — resuming automatically");repo.log(paused?"INFO":"ERROR","Model download: "+error);updateNotification(paused?"Model download paused":"Download reconnecting automatically");broadcast();if(!paused)new Thread(()->{try{Thread.sleep(10000);if(running.get()&&!engine.downloader().isReady())downloadModel();}catch(InterruptedException ignored){}},"model-auto-resume").start();}
        });
    }

    private void loop(){
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        while(running.get()){
            try{
                boolean enabled=Boolean.parseBoolean(repo.getState("generator_enabled","true"));
                int interval=Integer.parseInt(repo.getState("generation_interval_seconds","20"));
                long last=Long.parseLong(repo.getState("last_generation_ms","0"));
                boolean due=System.currentTimeMillis()-last>=interval*1000L;
                boolean requested=generationRequested.get();
                if(enabled&&engine.downloader().isReady()&&(requested||due)&&safeToRun()){
                    generationRequested.set(false);
                    PowerManager.WakeLock wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"infinite-rpg:generator");wake.acquire(10*60*1000L);
                    repo.putState("generation_inflight","true");
                    try{repo.putState("generator_phase","safe draft + judge");updateNotification("Forging in protected process");GenerationEngine.Result result=engine.generateOne();repo.putState("last_result",result.message);repo.putState("last_generation_ms",Long.toString(System.currentTimeMillis()));repo.putState("native_crash_count","0");if(result.accepted)broadcast(true);}
                    finally{repo.putState("generation_inflight","false");if(wake.isHeld())wake.release();repo.putState("generator_phase","idle");updateNotification("World generator idle");broadcast();}
                }
                Thread.sleep(1000);
            }catch(InterruptedException e){Thread.currentThread().interrupt();break;}catch(Exception e){String message=e.getMessage()==null?e.toString():e.getMessage();repo.putState("generator_phase","error — retry remains enabled");repo.putState("last_result",message);repo.log("ERROR","Generator loop: "+message);try{Thread.sleep(5000);}catch(InterruptedException ignored){break;}}
        }
    }

    private boolean safeToRun(){
        if(Boolean.parseBoolean(repo.getState("gameplay_active","false"))){repo.putState("generator_phase","yielding to gameplay");return false;}
        BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE);int battery=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        boolean charging=bm.isCharging();if(battery>0&&battery<20&&!charging){repo.putState("generator_phase","paused: low battery");return false;}
        if(Build.VERSION.SDK_INT>=29&&power.getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_MODERATE){repo.putState("generator_phase","paused: device warm");return false;}
        if(!NativeLlama.isLoaded()){ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);if(memory.lowMemory){repo.putState("generator_phase","paused: Android reports critical memory pressure");return false;}}
        return true;
    }

    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"World Generator",NotificationManager.IMPORTANCE_LOW);c.setDescription("Local Qwen content generation progress");getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification notification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,CHANNEL).setContentTitle("Infinite RPG").setContentText(text).setSmallIcon(R.drawable.ic_notification).setContentIntent(pi).setOngoing(true).build();}
    private void updateNotification(String text){getSystemService(NotificationManager.class).notify(41,notification(text));}
    private void broadcast(){broadcast(false);}
    private void broadcast(boolean creationChanged){sendBroadcast(new Intent(ACTION_REFRESH).setPackage(getPackageName()).putExtra("creation_changed",creationChanged));}
    private String percent(long got,long total){return total>0?(100*got/total)+"%":(got/1048576)+" MiB";}
    private int integer(String value,int fallback){try{return Integer.parseInt(value);}catch(Exception ignored){return fallback;}}
}
