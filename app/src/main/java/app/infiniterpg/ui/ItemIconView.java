package app.infiniterpg.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import app.infiniterpg.R;
import app.infiniterpg.data.ContentItem;
import app.infiniterpg.game.PixelTexture;

public final class ItemIconView extends View {
    private static Bitmap itemAtlas,buildAtlas,structureAtlas;private final ContentItem item;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private Bitmap pixels;
    public ItemIconView(Context c,ContentItem item){super(c);this.item=item;if(itemAtlas==null)itemAtlas=BitmapFactory.decodeResource(c.getResources(),R.drawable.item_atlas_v2);if(buildAtlas==null)buildAtlas=BitmapFactory.decodeResource(c.getResources(),R.drawable.buildables_atlas_v3);if(structureAtlas==null)structureAtlas=BitmapFactory.decodeResource(c.getResources(),R.drawable.structures_caves_atlas_v5);pixels=PixelTexture.bitmap(item,128);setBackground(Ui.round(Ui.PANEL,18,Ui.GOLD,1));}
    @Override protected void onDraw(Canvas c){super.onDraw(c);int pad=Ui.dp(getContext(),6);Rect dst=new Rect(pad,pad,getWidth()-pad,getHeight()-pad);int cell=item==null?-1:item.number("atlas_cell",-1);if(cell>=0){String set=item.text("atlas_set","");Bitmap atlas="structures_v5".equals(set)?structureAtlas:"buildables".equals(set)?buildAtlas:itemAtlas;int cols="structures_v5".equals(set)?2:4,cellSize=atlas.getWidth()/cols;Rect src=new Rect((cell%cols)*cellSize,(cell/cols)*cellSize,(cell%cols+1)*cellSize,(cell/cols+1)*cellSize);c.drawBitmap(atlas,src,dst,p);}else if(pixels!=null){p.setFilterBitmap(false);c.drawBitmap(pixels,null,dst,p);}else if(item!=null){int color=Ui.CYAN;try{color=android.graphics.Color.parseColor(item.text("color",item.text("base_color","#48CAE4")));}catch(Exception ignored){}p.setColor(color);c.drawCircle(getWidth()/2f,getHeight()/2f,Math.min(getWidth(),getHeight())*.28f,p);p.setColor(android.graphics.Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.min(getWidth(),getHeight())*.28f);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);c.drawText(item.name.substring(0,1),getWidth()/2f,getHeight()*.60f,p);}}
}
