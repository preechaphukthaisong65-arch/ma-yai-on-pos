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

final class PosUi {
    private PosUi() {}
    static int dp(Activity a, int n) { return (int)(n*a.getResources().getDisplayMetrics().density+0.5f); }
    static LinearLayout page(Activity a, String title) {
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
        scroll.addView(root); a.setContentView(scroll); return root;
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
        Button home=new Button(a); home.setText("⌂ หน้าหลัก"); home.setTextSize(15); home.setSingleLine(true); home.setMinHeight(dp(a,42)); home.setPadding(dp(a,4),0,dp(a,4),0);
        Button previous=new Button(a); previous.setText("← ย้อนกลับ"); previous.setTextSize(15); previous.setSingleLine(true); previous.setMinHeight(dp(a,42)); previous.setPadding(dp(a,4),0,dp(a,4),0);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,dp(a,44),1f); hp.rightMargin=dp(a,8);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(a,44),1f);
        footer.addView(home,hp); footer.addView(previous,bp); root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        home.setOnClickListener(v->{Intent i=new Intent(a,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);a.startActivity(i);a.finish();});
        previous.setOnClickListener(v->a.finish());
    }
    static void result(Activity a,boolean success,String message,Runnable after) {
        String title=success?"บันทึกสำเร็จ":"บันทึกไม่สำเร็จ";
        new AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("ตกลง",(d,w)->{if(after!=null)after.run();}).show();
        speak(a,(success?"บันทึกสำเร็จ ":"บันทึกไม่สำเร็จ ")+message);
    }
}