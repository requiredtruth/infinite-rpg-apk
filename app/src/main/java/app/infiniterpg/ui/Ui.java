package app.infiniterpg.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class Ui {
    public static final int BG=Color.rgb(6,20,38),PANEL=Color.rgb(12,38,58),GOLD=Color.rgb(255,200,87),CYAN=Color.rgb(72,202,228),TEXT=Color.rgb(232,242,244),MUTED=Color.rgb(155,180,190),GREEN=Color.rgb(90,200,125);
    private Ui(){}
    public static int dp(Context c,int v){return (int)(v*c.getResources().getDisplayMetrics().density+.5f);}
    public static TextView text(Context c,String value,int sp,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setPadding(dp(c,10),dp(c,6),dp(c,10),dp(c,6));return t;}
    public static Button button(Context c,String label){Button b=new Button(c);b.setText(label);b.setTextColor(TEXT);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(round(PANEL,14,GOLD,1));b.setPadding(dp(c,14),0,dp(c,14),0);return b;}
    public static GradientDrawable round(int color,int radius,int strokeColor,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);if(stroke>0)d.setStroke(stroke,strokeColor);return d;}
}
