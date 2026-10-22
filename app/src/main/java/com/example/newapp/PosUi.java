package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

final class PosUi {
    private PosUi() {}
    static int dp(Activity a, int n) { return (int)(n*a.getResources().getDisplayMetrics().density+0.5f); }

    static LinearLayout page(Activity a, String title) {
        a.getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        FrameLayout frame = new FrameLayout(a);
        ScrollView scroll = new ScrollView(a);
        int background = a.getResources().getIdentifier("app_background", "drawable", a.getPackageName());
        if (background != 0) scroll.setBackgroundResource(background);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,82));
        root.setBackgroundColor(Color.TRANSPARENT);
        if (background == 0) scroll.setBackgroundColor(Color.rgb(248,250,247));
        TextView heading = text(a,title,24,Color.rgb(35,80,48));
        heading.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        View gap = new View(a);
        root.addView(gap,new LinearLayout.LayoutParams(1,dp(a,10)));
        scroll.addView(root);
        frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout navigation = new LinearLayout(a);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setPadding(dp(a,8),dp(a,3),dp(a,8),dp(a,3));
        navigation.setBackgroundColor(Color.WHITE);
        Button home = navButton(a,"⌂ หน้าหลัก");
        home.setOnClickListener(v -> {
            Intent intent = new Intent(a,MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            a.startActivity(intent);
            a.finish();
        });
        Button back = navButton(a,"← ย้อนกลับ");
        back.setOnClickListener(v -> a.finish());
        navigation.addView(home,new LinearLayout.LayoutParams(0,dp(a,38),1f));
        View spacer = new View(a);
        navigation.addView(spacer,new LinearLayout.LayoutParams(dp(a,8),1));
        navigation.addView(back,new LinearLayout.LayoutParams(0,dp(a,38),1f));
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(-1,dp(a,46),Gravity.BOTTOM);
        frame.addView(navigation,navParams);
        a.setContentView(frame);
        return root;
    }

    private static Button navButton(Activity a,String label) {
        Button b=new Button(a);
        b.setText(label);
        b.setTextSize(13);
        b.setSingleLine(true);
        b.setMaxLines(1);
        b.setPadding(dp(a,3),0,dp(a,3),0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    static TextView text(Activity a,String value,int size,int color) {
        TextView t=new TextView(a);
        t.setText(value);
        t.setTextSize(size*1.5f);
        t.setTextColor(color);
        t.setPadding(dp(a,8),dp(a,5),dp(a,8),dp(a,5));
        t.setBackgroundColor(Color.WHITE);
        return t;
    }

    static Button button(Activity a, LinearLayout root, String label, View.OnClickListener listener) {
        Button b=new Button(a);
        b.setText(label);
        b.setTextSize(21f);
        b.setMinHeight(dp(a,52));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(a,8);
        root.addView(b,lp);
        return b;
    }

    static EditText input(Activity a, LinearLayout root,String hint) {
        EditText e=new EditText(a);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setTextSize(24f);
        e.setTextColor(Color.rgb(30,30,30));
        e.setHintTextColor(Color.rgb(110,110,110));
        GradientDrawable border=new GradientDrawable();
        border.setColor(Color.WHITE);
        border.setCornerRadius(dp(a,9));
        border.setStroke(dp(a,1),Color.rgb(125,155,132));
        e.setBackground(border);
        e.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));
        e.setMinimumHeight(dp(a,56));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(a,8);
        root.addView(e,lp);
        return e;
    }

    static void line(Activity a,LinearLayout root,String value) { root.addView(text(a,value,16,Color.DKGRAY)); }
    static void scan(Activity a) { BarcodeScanner.launch(a); }
    static void scan(Activity a,String prompt) { BarcodeScanner.launch(a,prompt); }
    static void speak(Activity a,String message) { speakThen(a,message,()->{}); }

    static void speakThen(Activity a,String message,Runnable after) {
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(a,status -> {
            if(status!=TextToSpeech.SUCCESS){if(holder[0]!=null)holder[0].shutdown();a.runOnUiThread(after);return;}
            ttsLangSafe(a,holder[0]);
            holder[0].setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { a.runOnUiThread(()->{holder[0].shutdown();after.run();}); }
                @Override public void onError(String id) { a.runOnUiThread(()->{holder[0].shutdown();after.run();}); }
            });
            holder[0].speak(message,TextToSpeech.QUEUE_FLUSH,null,"pos-"+System.nanoTime());
        });
    }

    static void showResult(Activity a,boolean success,String title,String message,Runnable after) {
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(title).setMessage(message)
                .setPositiveButton("ตกลง",(d,w)->{if(after!=null)after.run();}).create();
        dialog.setOnShowListener(d -> {
            TextView messageView=dialog.findViewById(android.R.id.message);
            if(messageView!=null)messageView.setTextSize(24f);
            int titleId=a.getResources().getIdentifier("alertTitle","id","android");
            TextView titleView=dialog.findViewById(titleId);
            if(titleView!=null)titleView.setTextSize(25f);
            speak(a,success?"บันทึกสำเร็จ":"บันทึกไม่สำเร็จ");
        });
        dialog.show();
    }

    private static void ttsLangSafe(Activity a,TextToSpeech tts) {
        Locale th=new Locale("th","TH");
        if(tts.isLanguageAvailable(th)>=TextToSpeech.LANG_AVAILABLE){
            tts.setLanguage(th);
            tts.setSpeechRate(0.95f);
            tts.setPitch(1.15f);
            if(android.os.Build.VERSION.SDK_INT>=21){
                try{
                    java.util.Set<android.speech.tts.Voice> voices=tts.getVoices();
                    if(voices!=null)for(android.speech.tts.Voice voice:voices){
                        String name=voice.getName()==null?"":voice.getName().toLowerCase(Locale.ROOT);
                        if("th".equals(voice.getLocale().getLanguage())&&(name.contains("female")||name.contains("woman")||name.contains("fem"))){
                            tts.setVoice(voice);break;
                        }
                    }
                }catch(Exception ignored){}
            }
        }
    }

    static String str(EditText e) { return e.getText().toString().trim(); }
    static long satang(EditText e) { return PosDatabase.toSatang(str(e)); }

    // Navigation is fixed to the bottom edge by page(); do not add duplicate in-content controls.
    static void back(Activity a,LinearLayout root) {}
}
