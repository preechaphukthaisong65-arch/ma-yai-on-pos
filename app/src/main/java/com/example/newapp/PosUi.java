package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayDeque;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.zxing.integration.android.IntentIntegrator;
import java.util.Locale;
import java.util.WeakHashMap;

final class PosUi {
    private static final WeakHashMap<LinearLayout,FrameLayout> PAGE_CONTAINERS = new WeakHashMap<>();
    private static final WeakHashMap<LinearLayout,Boolean> BACK_BUTTON_ADDED = new WeakHashMap<>();
    private static final WeakHashMap<Activity,ArrayDeque<View>> NAV_STACK = new WeakHashMap<>();
    private PosUi() {}
    static int dp(Activity a, int n) { return (int)(n*a.getResources().getDisplayMetrics().density+0.5f); }
    static LinearLayout page(Activity a, String title) {
        a.getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN, android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
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
        FrameLayout outer=new FrameLayout(a); outer.setBackgroundColor(Color.TRANSPARENT);
        outer.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        showPage(a,outer); PAGE_CONTAINERS.put(root,outer); back(a,root); return root;
    }
    static TextView text(Activity a,String value,int size,int color) {
        TextView t=new TextView(a); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0,dp(a,5),0,dp(a,5)); return t;
    }
    static Button button(Activity a, LinearLayout root, String label, View.OnClickListener listener) {
        Button b=new Button(a); b.setText(label); b.setTextSize(16); b.setMinHeight(dp(a,56)); b.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8)); b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(a,6); root.addView(b,lp); return b;
    }
    static EditText input(Activity a, LinearLayout root,String hint) {
        EditText e=new EditText(a); e.setSingleLine(true); e.setHint(hint); e.setTextSize(14); e.setMinHeight(dp(a,60)); e.setPadding(dp(a,14),dp(a,10),dp(a,14),dp(a,10));
        GradientDrawable field=new GradientDrawable(); field.setColor(Color.WHITE); field.setCornerRadius(dp(a,10)); field.setStroke(dp(a,1),Color.rgb(150,170,155)); e.setBackground(field); e.setHintTextColor(Color.rgb(105,115,108));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(a,10); root.addView(e,lp); return e;
    }
    static void line(Activity a,LinearLayout root,String value) {
        TextView t=text(a,value,12,Color.rgb(20,20,20));
        t.setBackgroundColor(Color.WHITE);
        t.setPadding(dp(a,10),dp(a,10),dp(a,10),dp(a,10));
        t.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(a,4);
        root.addView(t,lp);
    }
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
    private static TextToSpeech sharedTts;
    private static boolean ttsReady=false;
    private static boolean ttsInitializing=false;
    private static final ArrayDeque<SpeechRequest> SPEECH_QUEUE=new ArrayDeque<>();
    private static final java.util.HashMap<String,SpeechRequest> SPEECH_CALLBACKS=new java.util.HashMap<>();
    private static final class SpeechRequest {
        final java.lang.ref.WeakReference<Activity> activity;
        final String message;
        final Runnable after;
        final String id;
        SpeechRequest(Activity a,String m,Runnable r) {
            activity=new java.lang.ref.WeakReference<>(a);
            message=m;
            after=r;
            id="pos-"+System.nanoTime();
        }
    }
    static void speak(Activity a,String message) { speakThen(a,message,()->{}); }
    static void speakThen(Activity a,String message,Runnable after) {
        if(a==null||a.isFinishing())return;
        SpeechRequest request=new SpeechRequest(a,message,after==null?()->{}:after);
        synchronized(PosUi.class) {
            SPEECH_QUEUE.addLast(request);
            if(!ttsInitializing&&!ttsReady) {
                ttsInitializing=true;
                sharedTts=new TextToSpeech(a.getApplicationContext(),status->{
                    synchronized(PosUi.class) {
                        ttsInitializing=false;
                        if(status!=TextToSpeech.SUCCESS) {
                            ttsReady=false;
                            sharedTts=null;
                            while(!SPEECH_QUEUE.isEmpty())runAfter(SPEECH_QUEUE.removeFirst());
                            return;
                        }
                        ttsReady=true;
                        ttsLangSafe(a,sharedTts);
                        sharedTts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
                            @Override public void onStart(String id) {}
                            @Override public void onDone(String id) { completeSpeech(id); }
                            @Override public void onError(String id) { completeSpeech(id); }
                        });
                        speakNext();
                    }
                });
            } else if(ttsReady) {
                speakNext();
            }
        }
    }
    private static void speakNext() {
        if(!ttsReady||sharedTts==null||SPEECH_QUEUE.isEmpty())return;
        SpeechRequest request=SPEECH_QUEUE.removeFirst();
        Activity activity=request.activity.get();
        if(activity==null||activity.isFinishing()) {
            speakNext();
            return;
        }
        SPEECH_CALLBACKS.put(request.id,request);
        int result=sharedTts.speak(request.message,TextToSpeech.QUEUE_ADD,null,request.id);
        if(result==TextToSpeech.ERROR)completeSpeech(request.id);
    }
    private static void completeSpeech(String id) {
        SpeechRequest request;
        synchronized(PosUi.class) {
            request=SPEECH_CALLBACKS.remove(id);
            speakNext();
        }
        if(request!=null)runAfter(request);
    }
    private static void runAfter(SpeechRequest request) {
        Activity activity=request.activity.get();
        if(activity!=null&&!activity.isFinishing())activity.runOnUiThread(request.after);
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
    static void showRoot(Activity a,View view) {
        NAV_STACK.remove(a);
        a.setContentView(view);
    }
    private static void showPage(Activity a,View view) {
        ViewGroup content=a.findViewById(android.R.id.content);
        if(content!=null&&content.getChildCount()>0) {
            View previous=content.getChildAt(0);
            ArrayDeque<View> stack=NAV_STACK.get(a);
            if(stack==null){stack=new ArrayDeque<>();NAV_STACK.put(a,stack);}
            if(previous!=view)stack.push(previous);
        }
        a.setContentView(view);
    }
    private static void navigateBack(Activity a) {
        ArrayDeque<View> stack=NAV_STACK.get(a);
        if(stack!=null&&!stack.isEmpty()) {
            View previous=stack.pop();
            a.setContentView(previous);
            if(stack.isEmpty())NAV_STACK.remove(a);
        } else {
            a.finish();
        }
    }
    static void back(Activity a,LinearLayout root) {
        if(root==null||BACK_BUTTON_ADDED.containsKey(root))return;
        BACK_BUTTON_ADDED.put(root,Boolean.TRUE);
        ImageView previous=new ImageView(a);
        int imageId=a.getResources().getIdentifier("back_button","drawable",a.getPackageName());
        if(imageId!=0)previous.setImageResource(imageId);
        previous.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previous.setAdjustViewBounds(true);
        previous.setContentDescription("ย้อนกลับ");
        previous.setClickable(true);
        previous.setFocusable(true);
        previous.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams buttonParams=new FrameLayout.LayoutParams(-2,dp(a,50),Gravity.BOTTOM|Gravity.RIGHT);
        buttonParams.setMargins(0,0,dp(a,8),Math.max(dp(a,4),Math.round(a.getResources().getDisplayMetrics().heightPixels*0.03f)));
        FrameLayout container=PAGE_CONTAINERS.get(root);
        if(container!=null) {
            container.addView(previous,buttonParams);
        } else {
            root.addView(previous,new LinearLayout.LayoutParams(-2,dp(a,50)));
        }
        previous.setOnClickListener(v->navigateBack(a));
    }
    static void result(Activity a,boolean success,String message,Runnable after) {
        String title=success?"บันทึกสำเร็จ":"บันทึกไม่สำเร็จ";
        new AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("ตกลง",(d,w)->{if(after!=null)after.run();}).show();
        speak(a,(success?"บันทึกสำเร็จ ":"บันทึกไม่สำเร็จ ")+message);
    }
}