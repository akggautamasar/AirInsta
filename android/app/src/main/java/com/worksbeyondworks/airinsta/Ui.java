package com.worksbeyondworks.airinsta;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static void systemBars(Activity activity,boolean light,int color){
        activity.getWindow().setStatusBarColor(color);activity.getWindow().setNavigationBarColor(color);
        if(android.os.Build.VERSION.SDK_INT>=30){
            android.view.WindowInsetsController c=activity.getWindow().getInsetsController();
            if(c!=null){int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;c.setSystemBarsAppearance(light?mask:0,mask);}
        }else activity.getWindow().getDecorView().setSystemUiVisibility(light?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
    }
    final Activity activity;
    final int bg,panel,text,muted,field,secondary,accent;
    final boolean light;
    Ui(Activity activity,boolean light) {
        this.activity=activity; this.light=light;
        bg=Color.parseColor(light?"#FCF6FB":"#110D19");
        panel=Color.parseColor(light?"#FFFFFF":"#241B2C");
        text=Color.parseColor(light?"#291B30":"#FBF0F8");
        muted=Color.parseColor(light?"#756377":"#BCABBE");
        field=Color.parseColor(light?"#F4EAF2":"#18121F");
        secondary=Color.parseColor(light?"#F4DFED":"#453045");
        accent=Color.parseColor(light?"#A63069":"#FA8BB9");
    }
    int dp(float n) { return Math.round(n*activity.getResources().getDisplayMetrics().density); }
    GradientDrawable shape(int color,int radius) {
        GradientDrawable out=new GradientDrawable();out.setColor(color);out.setCornerRadius(dp(radius));return out;
    }
    TextView text(String value,int size,int color,boolean bold) {
        TextView out=new TextView(activity);out.setText(value);out.setTextSize(size);out.setTextColor(color);
        out.setLineSpacing(dp(2),1f);
        if(bold)out.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return out;
    }
    Button button(String label,boolean primary) {
        Button out=new Button(activity);out.setText(label);out.setAllCaps(false);out.setTextSize(13);
        out.setTypeface(Typeface.DEFAULT,Typeface.BOLD);out.setTextColor(primary?Color.rgb(33,16,30):text);
        out.setBackground(shape(primary?Color.rgb(250,139,185):secondary,12));
        out.setMinHeight(dp(46));out.setPadding(dp(10),dp(3),dp(10),dp(3));return out;
    }
    LinearLayout column() { LinearLayout out=new LinearLayout(activity);out.setOrientation(LinearLayout.VERTICAL);return out; }
    LinearLayout row() { LinearLayout out=new LinearLayout(activity);out.setOrientation(LinearLayout.HORIZONTAL);out.setGravity(Gravity.CENTER_VERTICAL);return out; }
    void add(LinearLayout parent,View child,int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p); }
    void weight(LinearLayout row,View child,int left) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.leftMargin=dp(left);row.addView(child,p); }
    LinearLayout card() { LinearLayout out=column();out.setPadding(dp(16),dp(16),dp(16),dp(16));out.setBackground(shape(panel,20));return out; }
    static String time(int millis) { int sec=Math.max(0,millis/1000);return String.format(java.util.Locale.ROOT,"%d:%02d",sec/60,sec%60); }
    static String bytes(long n) { if(n<0)return "—"; if(n<1024)return n+" B";if(n<1024*1024)return String.format(java.util.Locale.ROOT,"%.1f KB",n/1024d);return String.format(java.util.Locale.ROOT,"%.1f MB",n/(1024d*1024)); }
}
