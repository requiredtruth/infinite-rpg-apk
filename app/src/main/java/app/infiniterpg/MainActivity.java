package app.infiniterpg;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import app.infiniterpg.ai.ModelBank;
import app.infiniterpg.ai.ModelCatalog;
import app.infiniterpg.ai.WorldGeneratorService;
import app.infiniterpg.ai.RuntimeSignals;
import app.infiniterpg.audio.ProceduralMusic;
import app.infiniterpg.audio.AmbientSoundscape;
import app.infiniterpg.data.ContentItem;
import app.infiniterpg.data.ContentRepository;
import app.infiniterpg.data.WorldSaveManager;
import app.infiniterpg.game.GameView;
import app.infiniterpg.ui.CraftingView;
import app.infiniterpg.ui.ChestView;
import app.infiniterpg.ui.CreationNoticeView;
import app.infiniterpg.ui.SettingsView;
import app.infiniterpg.ui.Ui;

public final class MainActivity extends Activity {
    private static final int PICK_GGUF=91;
    private FrameLayout root;private GameView game;private ContentRepository repo;private ProceduralMusic music;private AmbientSoundscape ambient;private WorldSaveManager worldSaves;private BroadcastReceiver receiver;private boolean receiverRegistered,inGame;private long creationSeq,catalogRevision;private final Handler handler=new Handler();
    private final Runnable periodicSave=new Runnable(){@Override public void run(){if(inGame)saveAndExport();handler.postDelayed(this,120000);}};
    @Override protected void onCreate(Bundle state){super.onCreate(state);repo=InfiniteRpgApp.get().repo();applySavedOrientation();creationSeq=parse(repo.getState("creation_seq","0"));catalogRevision=parse(repo.getState("catalog_revision","0"));music=new ProceduralMusic(repo);ambient=new AmbientSoundscape(repo);worldSaves=new WorldSaveManager(this,repo);root=new FrameLayout(this);setContentView(root);startForegroundService(new Intent(this,WorldGeneratorService.class));showGame();immersive();handler.postDelayed(periodicSave,120000);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},70);receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(!i.getBooleanExtra("creation_changed",false))return;long now=parse(repo.getState("creation_seq","0"));if(now>creationSeq){creationSeq=now;catalogRevision=parse(repo.getState("catalog_revision","0"));if(game!=null)game.reloadCatalog();showCreationNotice();}}};}
    private void showGame(){root.removeAllViews();if(game==null)game=new GameView(this,repo,this::showChest);else{long revision=parse(repo.getState("catalog_revision","0"));if(revision!=catalogRevision){catalogRevision=revision;game.reloadCatalog();}game.refreshWorld();}inGame=true;signalGameplay(true);root.addView(game,new FrameLayout.LayoutParams(-1,-1));LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.END);Button bag=Ui.button(this,"🎒");Button craft=Ui.button(this,"⚒");Button settings=Ui.button(this,"⚙");bag.setContentDescription("Inventory");craft.setContentDescription("Crafting and building");settings.setContentDescription("Menu");bag.setOnClickListener(v->showCraft(0));craft.setOnClickListener(v->showCraft(1));settings.setOnClickListener(v->showSettings());actions.addView(bag,new LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,48)));actions.addView(craft,new LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,48)));actions.addView(settings,new LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,48)));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Ui.dp(this,182),Ui.dp(this,54),Gravity.TOP|Gravity.RIGHT);lp.setMargins(0,Ui.dp(this,12),Ui.dp(this,12),0);root.addView(actions,lp);addRotateButton();}
    private void showSettings(){saveAndExport();inGame=false;signalGameplay(false);root.removeAllViews();root.addView(new SettingsView(this,repo,this::showGame,this::reloadWorld,this::pickGguf),new FrameLayout.LayoutParams(-1,-1));addRotateButton();}
    private void showCraft(int tab){saveAndExport();inGame=false;signalGameplay(false);root.removeAllViews();root.addView(new CraftingView(this,repo,this::showGame,this::beginPlacement,tab),new FrameLayout.LayoutParams(-1,-1));addRotateButton();}
    private void beginPlacement(String itemKey,String type,ContentItem blueprint){showGame();game.beginPlacement(itemKey,type,blueprint);}
    private void reloadWorld(){game=null;catalogRevision=parse(repo.getState("catalog_revision","0"));showGame();}
    private void saveAndExport(){if(game!=null)game.save();final String name=repo.getState("world_name","Infinite RPG World");new Thread(()->worldSaves.save(name),"infinite-rpg-autosave").start();}
    private void showChest(long structureId){if(game!=null)game.save();inGame=false;signalGameplay(false);root.removeAllViews();root.addView(new ChestView(this,repo,structureId,this::showGame),new FrameLayout.LayoutParams(-1,-1));addRotateButton();}
    private void addRotateButton(){Button rotate=Ui.button(this,"↻");rotate.setContentDescription("Rotate screen orientation");rotate.setOnClickListener(v->toggleOrientation());FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,48),Gravity.BOTTOM|Gravity.RIGHT);lp.setMargins(0,0,Ui.dp(this,12),Ui.dp(this,12));root.addView(rotate,lp);}
    private void toggleOrientation(){boolean portrait=getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT;int target=portrait?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;repo.putState("orientation_mode",portrait?"landscape":"portrait");setRequestedOrientation(target);}
    private void applySavedOrientation(){String mode=repo.getState("orientation_mode","landscape");setRequestedOrientation("portrait".equals(mode)?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);}
    private void pickGguf(){Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(pick,PICK_GGUF);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK_GGUF||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}String name="imported.gguf";long size=-1;try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()){name=c.getString(0);size=c.getLong(1);}}catch(Exception ignored){}final String chosen=name;final long total=size;repo.putState("model_status","importing "+chosen+" into Documents model bank");new Thread(()->{try{ModelBank.ModelInfo info=new ModelBank(this).importUri(uri,chosen,total,(done,all)->{repo.putState("download_bytes",Long.toString(done));repo.putState("download_total",Long.toString(all));repo.putState("model_status","importing GGUF to Documents bank");});ModelCatalog.selectBank(repo,info.name,info.bytes);startForegroundService(new Intent(this,WorldGeneratorService.class).setAction(WorldGeneratorService.ACTION_RELOAD_MODEL));runOnUiThread(()->{Toast.makeText(this,"Imported and selected "+info.name,Toast.LENGTH_LONG).show();showSettings();});}catch(Exception e){repo.putState("model_status","import failed: "+e.getMessage());runOnUiThread(()->Toast.makeText(this,"GGUF import failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}},"gguf-import").start();}
    private void showCreationNotice(){String type=repo.getState("last_creation_type","");String key=repo.getState("last_creation_key","");app.infiniterpg.data.ContentItem item=repo.byIdentity(type,key);if(item==null)return;CreationNoticeView notice=new CreationNoticeView(this,item);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Ui.dp(this,430),Ui.dp(this,112),Gravity.TOP|Gravity.LEFT);lp.setMargins(Ui.dp(this,18),Ui.dp(this,96),0,0);root.addView(notice,lp);notice.setAlpha(0);notice.setTranslationY(-40);notice.animate().alpha(1).translationY(0).setDuration(350).start();handler.postDelayed(()->{if(notice.getParent()!=null)notice.animate().alpha(0).translationY(-30).setDuration(300).withEndAction(()->root.removeView(notice)).start();},9000);}
    private long parse(String value){try{return Long.parseLong(value);}catch(Exception e){return 0;}}
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    private void signalGameplay(boolean active){RuntimeSignals.setGameplayActive(active);repo.putState("gameplay_active",Boolean.toString(active));startForegroundService(new Intent(this,WorldGeneratorService.class).setAction(active?WorldGeneratorService.ACTION_GAME_ACTIVE:WorldGeneratorService.ACTION_GAME_IDLE));}
    @Override protected void onResume(){super.onResume();immersive();signalGameplay(inGame);music.start();ambient.start();if(receiver!=null&&!receiverRegistered){IntentFilter f=new IntentFilter(WorldGeneratorService.ACTION_REFRESH);if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);receiverRegistered=true;}}
    @Override protected void onPause(){signalGameplay(false);saveAndExport();music.stop();ambient.stop();if(receiverRegistered){unregisterReceiver(receiver);receiverRegistered=false;}super.onPause();}
    @Override public void onBackPressed(){if(!inGame)showGame();else super.onBackPressed();}
    @Override protected void onDestroy(){handler.removeCallbacks(periodicSave);super.onDestroy();}
}
