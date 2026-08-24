package app.infiniterpg.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.infiniterpg.data.ContentItem;

public final class CreationNoticeView extends LinearLayout {
    public CreationNoticeView(Context context,ContentItem item){super(context);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(Ui.dp(context,10),Ui.dp(context,9),Ui.dp(context,14),Ui.dp(context,9));setBackground(Ui.round(Ui.PANEL,20,Ui.GOLD,2));setElevation(Ui.dp(context,12));ItemIconView icon=new ItemIconView(context,item);addView(icon,new LayoutParams(Ui.dp(context,82),Ui.dp(context,82)));TextView text=Ui.text(context,"NEW "+item.type+" CREATED\n"+item.name+"\n"+item.text("description","A new creation entered the world."),13,Ui.TEXT);text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);LayoutParams tp=new LayoutParams(0,Ui.dp(context,90),1);tp.setMargins(Ui.dp(context,12),0,0,0);addView(text,tp);}
}
