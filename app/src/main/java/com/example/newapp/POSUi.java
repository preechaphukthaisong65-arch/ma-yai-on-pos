package com.example.newapp;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class POSUi {
    private POSUi() {}
    static int dp(Activity a, int n) { return (int)(n * a.getResources().getDisplayMetrics().density + .5f); }
    static LinearLayout page(Activity a, String title) {
        ScrollView scroll = new ScrollView(a);
        LinearLayout root = new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,20)); root.setBackgroundColor(Color.WHITE);
        TextView heading = text(a,title,22); heading.setGravity(Gravity.CENTER);
        root.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        scroll.addView(root); a.setContentView(scroll); return root;
    }
    static TextView text(Activity a,String value,int size) {
        TextView v=new TextView(a); v.setText(value); v.setTextSize(size); v.setTextColor(Color.rgb(35,45,55));
        v.setPadding(0,dp(a,7),0,dp(a,7)); return v;
    }
    static Button button(Activity a, LinearLayout root,String label,Runnable action) {
        Button b=new Button(a); b.setText(label); b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(a,5); lp.bottomMargin=dp(a,3);
        root.addView(b,lp); return b;
    }
    static EditText input(Activity a,LinearLayout root,String hint) {
        EditText e=new EditText(a); e.setSingleLine(true); e.setHint(hint);
        root.addView(e,new LinearLayout.LayoutParams(-1,-2)); return e;
    }
    static String value(EditText e) { return e.getText().toString().trim(); }
    static double number(EditText e) { try { return Double.parseDouble(value(e)); } catch(Exception ex) { return -1; } }
}
