package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.zxing.integration.android.IntentIntegrator;
import java.util.Locale;
import java.util.ArrayDeque;
import java.util.WeakHashMap;

final class PosUi {
    private static final WeakHashMap<LinearLayout,LinearLayout> PAGE_CONTAINERS = new WeakHashMap<>();
    private static final WeakHashMap<Activity,ArrayDeque<View>> PAGE_HISTORY = new WeakHashMap<>();
    private PosUi() {}
    static int dp(Activity a, int n) { return (int)(n*a.getResources().getDisplayMetrics().density+0.5f); }
    static void hideSystemBars(Activity a) {
        a.getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN, android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor=a.getWindow().getDecorView();
        final int immersiveFlags=View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decor.setSystemUiVisibility(immersiveFlags);
        decor.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN)==0
                    || (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)==0) {
                decor.setSystemUiVisibility(immersiveFlags);
            }
        });
    }
    static void clearBackStack(Activity a) {
        ArrayDeque<View> history=PAGE_HISTORY.get(a);
        if(history!=null)history.clear();
    }
    static void goBack(Activity a) {
        hideSystemBars(a);
        ArrayDeque<View> history=PAGE_HISTORY.get(a);
        if(history!=null&&!history.isEmpty()) {
            View previous=history.pop();
            a.setContentView(previous);
            hideSystemBars(a);
        } else {
            a.finish();
        }
    }
    static LinearLayout page(Activity a, String title) {
        hideSystemBars(a);
        ScrollView scroll = new ScrollView(a);
        int background = a.getResources().getIdentifier("app_background", "drawable", a.getPackageName());
        if (background != 0) scroll.setBackgroundResource(background);
        LinearLayout root = new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,24));
        root.setBackgroundColor(Color.TRANSPARENT);
        if (background == 0) scroll.setBackgroundColor(Color.rgb(248,250,247));
        TextView heading = text(a,title,24,Color.rgb(35,80,48)); heading.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        View gap = new View(a); root.addView(gap,new LinearLayout.LayoutParams(1,dp(a,10)));
        scroll.addView(root);
        LinearLayout outer=new LinearLayout(a); outer.setOrientation(LinearLayout.VERTICAL); outer.setBackgroundColor(Color.TRANSPARENT);
        outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        android.view.ViewGroup content=(android.view.ViewGroup)a.findViewById(android.R.id.content);
        if(content!=null&&content.getChildCount()>0) {
            View previous=content.getChildAt(0);
            ArrayDeque<View> history=PAGE_HISTORY.get(a);
            if(history==null){history=new ArrayDeque<>();PAGE_HISTORY.put(a,history);}
            history.push(previous);
        }
        a.setContentView(outer); PAGE_CONTAINERS.put(root,outer); hideSystemBars(a); return root;
    }
    static TextView text(Activity a,String value,int size,int color) {
        TextView t=new TextView(a); t.setText(value); t.setTextSize(size*1.5f); t.setTextColor(color);
        t.setPadding(0,dp(a,5),0,dp(a,5)); return t;
    }
    static Button button(Activity a, LinearLayout root, String label, View.OnClickListener listener) {
        Button b=new Button(a); b.setText(label); b.setTextSize(21); b.setMinHeight(dp(a,56)); b.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8)); b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(a,6); root.addView(b,lp); return b;
    }
    static EditText input(Activity a, LinearLayout root,String hint) {
        EditText e=new EditText(a); e.setSingleLine(true); e.setHint(hint); e.setTextSize(24); e.setMinHeight(dp(a,60)); e.setPadding(dp(a,14),dp(a,10),dp(a,14),dp(a,10));
        GradientDrawable field=new GradientDrawable(); field.setColor(Color.WHITE); field.setCornerRadius(dp(a,10)); field.setStroke(dp(a,1),Color.rgb(150,170,155)); e.setBackground(field); e.setHintTextColor(Color.rgb(105,115,108));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(a,10); root.addView(e,lp); return e;
    }
    static void line(Activity a,LinearLayout root,String value) { root.addView(text(a,value,16,Color.DKGRAY)); }
    static void scan(Activity a) { scan(a,"สแกนบาร์โค้ดสินค้า"); }
    static void scan(Activity a,String prompt) {
        IntentIntegrator scan=new IntentIntegrator(a);
        scan.setPrompt(prompt); scan.setBeepEnabled(false);
        scan.setOrientationLocked(true); scan.setDesiredBarcodeFormats(IntentIntegrator.ONE_D_CODE_TYPES);
        try {
            scan.initiateScan();
        } catch (RuntimeException e) {
            new AlertDialog.Builder(a)
                    .setTitle("เปิดกล้องสแกนไม่ได้")
                    .setMessage("ระบบเปิดหน้าสแกนไม่สำเร็จ กรุณาตรวจสอบสิทธิ์กล้องและติดตั้งแอปใหม่หากยังเกิดปัญหา")
                    .setPositiveButton("ตกลง", null)
                    .show();
        }
    }
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
    static void back(Activity a,LinearLayout root) {
        LinearLayout footer=new LinearLayout(a); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0,dp(a,8),0,0);
        Button previous=new Button(a); previous.setText("← ย้อนกลับ"); previous.setTextSize(18); previous.setSingleLine(true); previous.setMinHeight(dp(a,48)); previous.setPadding(dp(a,12),0,dp(a,12),0);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(a,48));
        footer.addView(previous,bp);
        LinearLayout container=PAGE_CONTAINERS.get(root);
        if(container!=null)container.addView(footer,new LinearLayout.LayoutParams(-1,dp(a,54)));
        else root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        previous.setOnClickListener(v->goBack(a));
    }
    static void result(Activity a,boolean success,String message,Runnable after) {
        String title=success?"บันทึกสำเร็จ":"บันทึกไม่สำเร็จ";
        new AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("ตกลง",(d,w)->{if(after!=null){clearBackStack(a);after.run();}}).show();
        speak(a,(success?"บันทึกสำเร็จ ":"บันทึกไม่สำเร็จ ")+message);
    }
}