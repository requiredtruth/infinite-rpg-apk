package app.infiniterpg.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.LruCache;
import app.infiniterpg.R;
import app.infiniterpg.data.ContentItem;
import app.infiniterpg.data.ContentRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fixed-resolution terrain cache designed for low-end phones. */
public final class WorldTextureCache {
    public static final int CHUNK_TILES=8;
    private static final int TEXELS_PER_TILE=16;
    private static final int CHUNK_TEXELS=CHUNK_TILES*TEXELS_PER_TILE;

    private static final class BiomeVisual {
        final int cell;final float blend;final List<PixelTexture.Map> overlays;
        BiomeVisual(ContentItem item,List<PixelTexture.Map> maps){cell=item.number("texture_cell",Math.floorMod(item.identity.hashCode(),16));blend=Math.max(.08f,Math.min(.30f,(float)item.decimal("texture_blend",.18)));overlays=maps;}
    }

    private final long seed;private final ContentRepository repo;
    private final int[] atlas=new int[1024*1024];
    private final LruCache<Long,Bitmap> cache=new LruCache<Long,Bitmap>(96){};
    private List<BiomeVisual> visuals=new ArrayList<>();

    public WorldTextureCache(Context context,ContentRepository repo,long seed){
        this.repo=repo;this.seed=seed;Bitmap source=BitmapFactory.decodeResource(context.getResources(),R.drawable.terrain_atlas_v2);
        source.getPixels(atlas,0,1024,0,0,1024,1024);source.recycle();reload();
    }

    public void reload(){
        Map<String,List<PixelTexture.Map>> overlayMap=new HashMap<>();
        for(ContentItem style:repo.list("TILE_STYLE",256)){PixelTexture.Map map=PixelTexture.parse(style);if(map!=null)overlayMap.computeIfAbsent(style.text("biome",""),k->new ArrayList<>()).add(map);}
        ArrayList<BiomeVisual> next=new ArrayList<>();for(ContentItem biome:repo.list("BIOME",128))next.add(new BiomeVisual(biome,overlayMap.get(biome.identity)));visuals=next;cache.evictAll();
    }

    public Bitmap chunk(int cx,int cy){
        long key=(((long)cx)<<32)^(cy&0xffffffffL);Bitmap hit=cache.get(key);if(hit!=null)return hit;
        final int block=4,grid=CHUNK_TEXELS/block;double[] noise=new double[grid*grid];
        for(int gy=0;gy<grid;gy++)for(int gx=0;gx<grid;gx++){double wx=cx*CHUNK_TILES+(double)(gx*block)/TEXELS_PER_TILE,wy=cy*CHUNK_TILES+(double)(gy*block)/TEXELS_PER_TILE;noise[gy*grid+gx]=WorldMath.fractal(seed,wx,wy);}
        int[] out=new int[CHUNK_TEXELS*CHUNK_TEXELS];List<BiomeVisual> local=visuals;
        if(local.isEmpty())java.util.Arrays.fill(out,Color.rgb(48,100,58));
        else for(int py=0;py<CHUNK_TEXELS;py++)for(int px=0;px<CHUNK_TEXELS;px++){
            double n=noise[(py/block)*grid+(px/block)];double pos=n*Math.max(1,local.size()-1);int ai=Math.min(local.size()-1,(int)Math.floor(pos)),bi=Math.min(local.size()-1,ai+1);float f=(float)(pos-ai);f=f*f*(3-2*f);
            BiomeVisual a=local.get(ai),b=local.get(bi);int globalX=cx*CHUNK_TEXELS+px,globalY=cy*CHUNK_TEXELS+py;int color=mix(atlasSample(a,globalX,globalY),atlasSample(b,globalX,globalY),f);BiomeVisual dominant=f<.5f?a:b;PixelTexture.Map overlay=overlayFor(dominant,globalX,globalY);if(overlay!=null)color=mix(color,overlay.sample(globalX>>1,globalY>>1),dominant.blend);out[py*CHUNK_TEXELS+px]=color;
        }
        Bitmap made=Bitmap.createBitmap(out,CHUNK_TEXELS,CHUNK_TEXELS,Bitmap.Config.RGB_565);cache.put(key,made);return made;
    }

    private int atlasSample(BiomeVisual biome,int x,int y){int ox=(biome.cell%4)*256,oy=(biome.cell/4)*256;return atlas[(oy+(y&255))*1024+ox+(x&255)];}
    private PixelTexture.Map overlayFor(BiomeVisual biome,int x,int y){if(biome.overlays==null||biome.overlays.isEmpty())return null;if(biome.overlays.size()==1)return biome.overlays.get(0);long h=WorldMath.hash(seed^0xA17E,x>>7,y>>7);return biome.overlays.get(Math.floorMod((int)h,biome.overlays.size()));}
    private int mix(int a,int b,float f){float q=1-f;return Color.rgb((int)(Color.red(a)*q+Color.red(b)*f),(int)(Color.green(a)*q+Color.green(b)*f),(int)(Color.blue(a)*q+Color.blue(b)*f));}
}
