package app.infiniterpg.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes portable deterministic world snapshots in Documents/Infinite RPG. */
public final class WorldSaveManager {
    public static final class SaveInfo {public final Uri uri;public final String name;public final long modified;SaveInfo(Uri uri,String name,long modified){this.uri=uri;this.name=name;this.modified=modified;}}
    private static final String FOLDER=Environment.DIRECTORY_DOCUMENTS+"/Infinite RPG/";
    private final Context context;private final ContentRepository repo;
    public WorldSaveManager(Context context,ContentRepository repo){this.context=context.getApplicationContext();this.repo=repo;}
    public synchronized String save(String worldName){if(Build.VERSION.SDK_INT<29)return "World export requires Android 10 or newer";String clean=worldName==null?"Infinite RPG World":worldName.replaceAll("[^A-Za-z0-9 _-]","").trim();if(clean.isEmpty())clean="Infinite RPG World";String file=clean+".infinite-rpg.json";ContentResolver cr=context.getContentResolver();Uri collection=MediaStore.Files.getContentUri("external");Uri target=find(file);try{if(target==null){ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,file);v.put(MediaStore.MediaColumns.MIME_TYPE,"application/json");v.put(MediaStore.MediaColumns.RELATIVE_PATH,FOLDER);v.put(MediaStore.MediaColumns.IS_PENDING,1);target=cr.insert(collection,v);}if(target==null)return "Could not create world file";try(OutputStream out=cr.openOutputStream(target,"wt")){if(out==null)return "Could not open world file";out.write(repo.exportWorld(clean).toString(2).getBytes(StandardCharsets.UTF_8));}ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);cr.update(target,ready,null,null);repo.putState("world_name",clean);repo.log("WORLD","Saved "+clean+" to Documents/Infinite RPG");return "Saved to Documents/Infinite RPG/"+file;}catch(Exception e){return "Save failed: "+e.getMessage();}}
    public synchronized boolean load(SaveInfo info){try(InputStream in=context.getContentResolver().openInputStream(info.uri)){if(in==null)return false;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;while((n=in.read(b))>0)out.write(b,0,n);return repo.importWorld(new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)));}catch(Exception e){repo.log("ERROR","World file load: "+e.getMessage());return false;}}
    public List<SaveInfo> list(){ArrayList<SaveInfo> out=new ArrayList<>();if(Build.VERSION.SDK_INT<29)return out;Uri collection=MediaStore.Files.getContentUri("external");String[] projection={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.DATE_MODIFIED};try(Cursor c=context.getContentResolver().query(collection,projection,MediaStore.MediaColumns.RELATIVE_PATH+"=? AND "+MediaStore.MediaColumns.DISPLAY_NAME+" LIKE ?",new String[]{FOLDER,"%.infinite-rpg.json"},MediaStore.MediaColumns.DATE_MODIFIED+" DESC")){if(c!=null)while(c.moveToNext()){Uri uri=Uri.withAppendedPath(collection,Long.toString(c.getLong(0)));String file=c.getString(1),name=file.endsWith(".infinite-rpg.json")?file.substring(0,file.length()-15):file;out.add(new SaveInfo(uri,name,c.getLong(2)*1000L));}}return out;}
    private Uri find(String file){Uri collection=MediaStore.Files.getContentUri("external");try(Cursor c=context.getContentResolver().query(collection,new String[]{MediaStore.MediaColumns._ID},MediaStore.MediaColumns.RELATIVE_PATH+"=? AND "+MediaStore.MediaColumns.DISPLAY_NAME+"=?",new String[]{FOLDER,file},null)){if(c!=null&&c.moveToFirst())return Uri.withAppendedPath(collection,Long.toString(c.getLong(0)));}return null;}
}
