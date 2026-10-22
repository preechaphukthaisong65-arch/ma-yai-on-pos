package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.WeakHashMap;

final class PosUi {
    private PosUi() {}
    private static final WeakHashMap<Activity, ArrayDeque<View>> PAGE_HISTORY = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Boolean> SKIP_NEXT_HISTORY = new WeakHashMap<>();
    static int dp(Activity a, int n) { return (int)(n*a.getResources().getDisplayMetrics().density+0.5f); }

    static void resetNavigation(Activity a) {
        synchronized (PAGE_HISTORY) { PAGE_HISTORY.remove(a); SKIP_NEXT_HISTORY.put(a,Boolean.TRUE); }
    }

    static void clearNavigation(Activity a) {
        synchronized (PAGE_HISTORY) { PAGE_HISTORY.remove(a); SKIP_NEXT_HISTORY.remove(a); }
    }

    static LinearLayout page(Activity a, String title) {
        a.getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        ViewGroup content = a.findViewById(android.R.id.content);
        boolean skipHistory;
        synchronized (PAGE_HISTORY) {
            skipHistory = Boolean.TRUE.equals(SKIP_NEXT_HISTORY.remove(a));
        }
        if (!skipHistory && content != null && content.getChildCount() > 0) {
            View previous = content.getChildAt(0);
            synchronized (PAGE_HISTORY) {
                ArrayDeque<View> history = PAGE_HISTORY.get(a);
                if (history == null) { history = new ArrayDeque<>(); PAGE_HISTORY.put(a, history); }
                if (history.peek() != previous) history.push(previous);
                while (history.size() > 20) history.removeLast();
            }
        }

        LinearLayout container = new LinearLayout(a);
        container.setOrientation(LinearLayout.VERTICAL);
        int background = a.getResources().getIdentifier("app_background", "drawable", a.getPackageName());
        if (background != 0) container.setBackgroundResource(background);
        else container.setBackgroundColor(Color.rgb(248,250,247));

        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8));
        root.setBackgroundColor(Color.TRANSPARENT);

        TextView heading = text(a,title,36,Color.rgb(35,80,48));
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(a,8),dp(a,4),dp(a,8),dp(a,4));
        root.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        View gap = new View(a);
        root.addView(gap,new LinearLayout.LayoutParams(1,dp(a,6)));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        container.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));

        LinearLayout nav = new LinearLayout(a);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(a,8),dp(a,2),dp(a,8),dp(a,2));
        nav.setBackgroundColor(Color.argb(238,255,255,255));
        Button home = navButton(a,"← หน้าหลัก");
        Button back = navButton(a,"ย้อนกลับ →");
        nav.addView(home,new LinearLayout.LayoutParams(0,dp(a,44),1f));
        View spacer = new View(a);
        nav.addView(spacer,new LinearLayout.LayoutParams(dp(a,8),1));
        nav.addView(back,new LinearLayout.LayoutParams(0,dp(a,44),1f));
        home.setOnClickListener(v -> {
            resetNavigation(a);
            Intent intent = new Intent(a, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            a.startActivity(intent);
            a.finish();
        });
        back.setOnClickListener(v -> goBack(a));
        container.addView(nav,new LinearLayout.LayoutParams(-1,dp(a,48)));
        a.setContentView(container);
        return root;
    }

    private static Button navButton(Activity a,String label) {
        Button b = new Button(a);
        b.setText(label);
        b.setTextSize(21);
        b.setSingleLine(true);
        b.setMinHeight(dp(a,40));
        b.setPadding(dp(a,4),0,dp(a,4),0);
        b.setAllCaps(false);
        return b;
    }

    private static void goBack(Activity a) {
        View previous = null;
        synchronized (PAGE_HISTORY) {
            ArrayDeque<View> history = PAGE_HISTORY.get(a);
            if (history != null) {
                previous = history.poll();
                if (history.isEmpty()) PAGE_HISTORY.remove(a);
            }
        }
        if (previous != null) a.setContentView(previous);
        else a.finish();
    }

    static TextView text(Activity a,String value,int size,int color) {
        TextView t=new TextView(a); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(dp(a,8),dp(a,5),dp(a,8),dp(a,5));
        t.setBackgroundColor(Color.WHITE);
        return t;
    }

    static Button button(Activity a, LinearLayout root, String label, View.OnClickListener listener) {
        Button b=new Button(a); b.setText(label); b.setTextSize(21); b.setAllCaps(false);
        b.setMinHeight(dp(a,52)); b.setPadding(dp(a,8),dp(a,6),dp(a,8),dp(a,6));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(a,6); root.addView(b,lp); return b;
    }

    static EditText input(Activity a, LinearLayout root,String hint) {
        EditText e=new EditText(a); e.setSingleLine(true); e.setHint(hint);
        e.setTextSize(24);
        e.setTextColor(Color.rgb(30,30,30));
        e.setHintTextColor(Color.rgb(110,110,110));
        GradientDrawable field = new GradientDrawable();
        field.setColor(Color.WHITE);
        field.setCornerRadius(dp(a,8));
        field.setStroke(dp(a,1),Color.rgb(115,145,120));
        e.setBackground(field);
        e.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));
        e.setMinHeight(dp(a,58));
        e.setSelectAllOnFocus(false);
        root.addView(e,new LinearLayout.LayoutParams(-1,-2));
        View gap = new View(a);
        root.addView(gap,new LinearLayout.LayoutParams(1,dp(a,6)));
        return e;
    }

    static void styleInput(Activity a,TextView e) {
        e.setTextSize(24);
        e.setMinHeight(dp(a,58));
        e.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));
        GradientDrawable field = new GradientDrawable();
        field.setColor(Color.WHITE);
        field.setCornerRadius(dp(a,8));
        field.setStroke(dp(a,1),Color.rgb(115,145,120));
        e.setBackground(field);
    }

    static void line(Activity a,LinearLayout root,String value) {
        TextView t=text(a,value,24,Color.DKGRAY);
        root.addView(t,new LinearLayout.LayoutParams(-1,-2));
        View gap = new View(a);
        root.addView(gap,new LinearLayout.LayoutParams(1,dp(a,3)));
    }

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

    static void result(Activity a,boolean success,String title,String message,Runnable after) {
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("ตกลง",(d,w)->{if(after!=null)after.run();})
                .create();
        dialog.setOnShowListener(d -> {
            TextView body = dialog.findViewById(android.R.id.message);
            if(body!=null)body.setTextSize(24);
            Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if(positive!=null)positive.setTextSize(21);
            int titleId=a.getResources().getIdentifier("alertTitle","id","android");
            TextView heading=dialog.findViewById(titleId);
            if(heading!=null)heading.setTextSize(26);
        });
        dialog.show();
        String spoken = success ? "บันทึกสำเร็จ" : "บันทึกไม่สำเร็จ";
        speak(a,spoken);
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

    // The bottom navigation is part of page(); this method remains for old screen call sites.
    static void back(Activity a,LinearLayout root) { }
}
