package app.infiniterpg.game;

import android.graphics.Bitmap;
import android.graphics.Color;
import org.json.JSONArray;
import app.infiniterpg.data.ContentItem;

public final class PixelTexture {
    public static final class Map {
        public final int[] colors; public final byte[] pixels;
        Map(int[] c,byte[] p){colors=c;pixels=p;}
        public int sample(int x,int y){return colors[pixels[Math.floorMod(y,16)*16+Math.floorMod(x,16)]&0xff];}
    }
    private PixelTexture(){}
    public static Map parse(ContentItem item){if(item==null)return null;JSONArray palette=item.json.optJSONArray("palette"),rows=item.json.optJSONArray("pixels");if(palette==null||palette.length()<2||rows==null||rows.length()!=16)return null;int[] colors=new int[Math.min(10,palette.length())];for(int i=0;i<colors.length;i++)try{colors[i]=Color.parseColor(palette.optString(i));}catch(Exception e){colors[i]=Color.MAGENTA;}byte[] pixels=new byte[256];for(int y=0;y<16;y++){String row=rows.optString(y,"");if(row.length()!=16)return null;for(int x=0;x<16;x++){int v=Character.digit(row.charAt(x),16);pixels[y*16+x]=(byte)Math.max(0,Math.min(colors.length-1,v));}}return new Map(colors,pixels);}
    public static Bitmap bitmap(ContentItem item,int size){Map map=parse(item);if(map==null)return null;int[] raw=new int[256];for(int y=0;y<16;y++)for(int x=0;x<16;x++)raw[y*16+x]=map.sample(x,y);Bitmap small=Bitmap.createBitmap(raw,16,16,Bitmap.Config.ARGB_8888);return Bitmap.createScaledBitmap(small,size,size,false);}
}
