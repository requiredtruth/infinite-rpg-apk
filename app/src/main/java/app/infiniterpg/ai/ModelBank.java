package app.infiniterpg.ai;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Persistent reusable GGUF bank in Documents/Infinite RPG/Models. */
public final class ModelBank {
    public interface Progress{void update(long done,long total);}
    public static final class ModelInfo{public final Uri uri;public final String name;public final long bytes;ModelInfo(Uri uri,String name,long bytes){this.uri=uri;this.name=name;this.bytes=bytes;}}
    public static final String FOLDER=Environment.DIRECTORY_DOCUMENTS+"/Infinite RPG/Models/";
    private final Context context;
    public ModelBank(Context context){this.context=context.getApplicationContext();}
    public List<ModelInfo> list(){ArrayList<ModelInfo> out=new ArrayList<>();if(Build.VERSION.SDK_INT<29)return out;Uri collection=MediaStore.Files.getContentUri("external");String[] cols={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.SIZE};try(Cursor c=context.getContentResolver().query(collection,cols,MediaStore.MediaColumns.RELATIVE_PATH+"=? AND "+MediaStore.MediaColumns.DISPLAY_NAME+" LIKE ?",new String[]{FOLDER,"%.gguf"},MediaStore.MediaColumns.DISPLAY_NAME+" ASC")){if(c!=null)while(c.moveToNext())out.add(new ModelInfo(Uri.withAppendedPath(collection,Long.toString(c.getLong(0))),c.getString(1),c.getLong(2)));}return out;}
    public ModelInfo find(String name){String clean=cleanName(name);for(ModelInfo i:list())if(i.name.equalsIgnoreCase(clean))return i;return null;}
    public boolean stage(String name,File destination,Progress progress)throws Exception{ModelInfo info=find(name);if(info==null)return false;try(InputStream in=context.getContentResolver().openInputStream(info.uri);FileOutputStream out=new FileOutputStream(destination,false)){if(in==null)return false;copy(in,out,info.bytes,progress);out.getFD().sync();}return valid(destination,Math.max(20L*1024L*1024L,info.bytes/2));}
    public ModelInfo store(File source,String name,Progress progress)throws Exception{try(FileInputStream in=new FileInputStream(source)){return write(in,cleanName(name),source.length(),progress);}}
    public ModelInfo importUri(Uri source,String displayName,long size,Progress progress)throws Exception{String clean=cleanName(displayName);ModelInfo existing=find(clean);if(existing!=null&&existing.uri.equals(source)){if(!validUri(source))throw new IllegalStateException("Selected file is not a valid GGUF");return existing;}try(InputStream in=context.getContentResolver().openInputStream(source)){if(in==null)throw new IllegalStateException("Could not open selected file");return write(in,clean,size,progress);}}
    private ModelInfo write(InputStream in,String name,long total,Progress progress)throws Exception{if(Build.VERSION.SDK_INT<29)throw new IllegalStateException("Documents model bank requires Android 10+");ContentResolver cr=context.getContentResolver();Uri collection=MediaStore.Files.getContentUri("external"),target=findUri(name);if(target==null){ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);v.put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream");v.put(MediaStore.MediaColumns.RELATIVE_PATH,FOLDER);v.put(MediaStore.MediaColumns.IS_PENDING,1);target=cr.insert(collection,v);}if(target==null)throw new IllegalStateException("Could not create Documents model");try(OutputStream out=cr.openOutputStream(target,"wt")){if(out==null)throw new IllegalStateException("Could not write Documents model");copy(in,out,total,progress);}ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);cr.update(target,ready,null,null);ModelInfo info=find(name);if(info==null||info.bytes<20L*1024L*1024L||!validUri(info.uri)){cr.delete(target,null,null);throw new IllegalStateException("Imported file is not a valid GGUF");}return info;}
    public boolean delete(String name){ModelInfo info=find(name);return info!=null&&context.getContentResolver().delete(info.uri,null,null)>0;}
    private Uri findUri(String name){ModelInfo i=find(name);return i==null?null:i.uri;}
    private boolean validUri(Uri uri){try(InputStream in=context.getContentResolver().openInputStream(uri)){byte[] h=new byte[4];return in!=null&&in.read(h)==4&&h[0]=='G'&&h[1]=='G'&&h[2]=='U'&&h[3]=='F';}catch(Exception e){return false;}}
    public static boolean valid(File f,long min){if(!f.isFile()||f.length()<min)return false;try(FileInputStream in=new FileInputStream(f)){byte[] h=new byte[4];return in.read(h)==4&&h[0]=='G'&&h[1]=='G'&&h[2]=='U'&&h[3]=='F';}catch(Exception e){return false;}}
    private static void copy(InputStream in,OutputStream out,long total,Progress progress)throws Exception{byte[] b=new byte[1024*1024];long done=0,last=0;int n;while((n=in.read(b))>0){out.write(b,0,n);done+=n;long now=System.currentTimeMillis();if(progress!=null&&now-last>250){progress.update(done,total);last=now;}}if(progress!=null)progress.update(done,total);}
    public static String cleanName(String input){String n=input==null?"imported.gguf":input.replaceAll("[^A-Za-z0-9._ -]","_").trim();if(!n.toLowerCase(java.util.Locale.US).endsWith(".gguf"))n+=".gguf";return n.isEmpty()?"imported.gguf":n;}
}
