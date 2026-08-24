package app.infiniterpg.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import app.infiniterpg.data.ContentItem;
import app.infiniterpg.data.ContentRepository;
import java.util.List;

public final class ChestView extends LinearLayout {
    private final ContentRepository repo;private final long chestId;private final Runnable onBack;private final LinearLayout bag,chest;
    public ChestView(Context context,ContentRepository repo,long chestId,Runnable onBack){super(context);this.repo=repo;this.chestId=chestId;this.onBack=onBack;setOrientation(VERTICAL);setBackgroundColor(Ui.BG);setPadding(Ui.dp(context,22),Ui.dp(context,10),Ui.dp(context,22),Ui.dp(context,10));LinearLayout head=new LinearLayout(context);head.setGravity(Gravity.CENTER_VERTICAL);TextView title=Ui.text(context,"STORAGE CHEST",24,Ui.GOLD);title.setTypeface(null,1);head.addView(title,new LayoutParams(0,Ui.dp(context,50),1));Button back=Ui.button(context,"← Close chest");back.setOnClickListener(v->onBack.run());head.addView(back,new LayoutParams(Ui.dp(context,170),Ui.dp(context,44)));addView(head);LinearLayout cols=new LinearLayout(context);bag=new LinearLayout(context);bag.setOrientation(VERTICAL);chest=new LinearLayout(context);chest.setOrientation(VERTICAL);ScrollView a=new ScrollView(context);a.addView(bag);ScrollView b=new ScrollView(context);b.addView(chest);cols.addView(a,new LayoutParams(0,0,1));cols.addView(b,new LayoutParams(0,0,1));addView(cols,new LayoutParams(-1,0,1));refresh();}
    private void refresh(){bag.removeAllViews();chest.removeAllViews();bag.addView(heading("YOUR BACKPACK — STORE ITEMS"));List<String[]> inv=repo.inventoryList();if(inv.isEmpty())bag.addView(Ui.text(getContext(),"Backpack empty",14,Ui.MUTED));for(String[] row:inv)addRow(bag,row[0],Integer.parseInt(row[1]),true);chest.addView(heading("CHEST CONTENTS — TAKE ITEMS"));List<String[]> stored=repo.chestItems(chestId);if(stored.isEmpty())chest.addView(Ui.text(getContext(),"Chest empty",14,Ui.MUTED));for(String[] row:stored)addRow(chest,row[0],Integer.parseInt(row[1]),false);}
    private void addRow(LinearLayout parent,String key,int quantity,boolean storing){ContentItem item=repo.byIdentity("ITEM",key);if(item==null)item=repo.byIdentity("MATERIAL",key);LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);row.addView(new ItemIconView(getContext(),item),new LayoutParams(Ui.dp(getContext(),54),Ui.dp(getContext(),54)));String name=item==null?key.replace('_',' '):item.name;TextView text=Ui.text(getContext(),quantity+" × "+name,14,Ui.TEXT);row.addView(text,new LayoutParams(0,Ui.dp(getContext(),58),1));Button one=Ui.button(getContext(),storing?"Store 1":"Take 1");one.setOnClickListener(v->{boolean ok=storing?repo.moveToChest(chestId,key,1):repo.moveFromChest(chestId,key,1);Toast.makeText(getContext(),ok?"Moved 1":"Could not move item",Toast.LENGTH_SHORT).show();refresh();});row.addView(one,new LayoutParams(Ui.dp(getContext(),100),Ui.dp(getContext(),42)));Button all=Ui.button(getContext(),storing?"Store all":"Take all");all.setOnClickListener(v->{boolean ok=storing?repo.moveToChest(chestId,key,quantity):repo.moveFromChest(chestId,key,quantity);Toast.makeText(getContext(),ok?"Moved "+quantity:"Could not move items",Toast.LENGTH_SHORT).show();refresh();});row.addView(all,new LayoutParams(Ui.dp(getContext(),105),Ui.dp(getContext(),42)));parent.addView(row,new LayoutParams(-1,Ui.dp(getContext(),64)));}
    private TextView heading(String value){TextView t=Ui.text(getContext(),value,16,Ui.CYAN);t.setTypeface(null,1);t.setPadding(0,Ui.dp(getContext(),8),0,Ui.dp(getContext(),8));return t;}
}
