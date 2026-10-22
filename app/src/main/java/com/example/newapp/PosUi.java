package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
        int background = a.getResources().getIdentifier("app_background", "drawable", a.getPackageName());
        if (background != 0) frame.setBackgroundResource(background);
        else frame.setBackgroundColor(Color.rgb(248,250,247));

        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0,0,0,dp(a,54));
        if (background != 0) scroll.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,76));
        root.setBackgroundColor(Color.TRANSPARENT);
        TextView heading = text(a,title,24,Color.rgb(35,80,48));
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        View gap = new View(a);
        root.addView(gap,new LinearLayout.LayoutParams(1,dp(a,10)));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        a.setContentView(frame);
        return root;
    }

    static TextView text(Activity a,String value,int size,int color) {
        TextView t=new TextView(a);
        t.setText(value);
        t.setTextSize(Math.round(size*1.5f));
        t.setTextColor(color);
        t.setPadding(dp(a,8),dp(a,7),dp(a,8),dp(a,7));
        t.setBackgroundColor(Color.WHITE);
        t.setLineSpacing(dp(a,2),1.0f);
        return t;
    }

    static Button button(Activity a, LinearLayout root, String label, View.OnClickListener listener) {
        Button b=new Button(a);
        b.setText(label);
        b.setTextSize(21);
        b.setAllCaps(false);
        b.setMinHeight(dp(a,52));
        b.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8));
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
        styleInput(a,e);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(a,10);
        root.addView(e,lp);
        return e;
    }

    static void styleInput(Activity a, EditText e) {
        e.setTextSize(27);
        e.setTextColor(Color.rgb(30,30,30));
        e.setHintTextColor(Color.rgb(100,100,100));
        GradientDrawable field = new GradientDrawable();
        field.setColor(Color.WHITE);
        field.setCornerRadius(dp(a,10));
        field.setStroke(dp(a,2),Color.rgb(105,145,112));
        e.setBackground(field);
        e.setPadding(dp(a,14),dp(a,12),dp(a,14),dp(a,12));
        e.setMinHeight(dp(a,60));
    }

    static void line(Activity a,LinearLayout root,String value) {
        TextView t=text(a,value,16,Color.DKGRAY);
        t.setBackgroundColor(Color.argb(238,255,255,255));
        root.addView(t,new LinearLayout.LayoutParams(-1,-2));
        View gap=new View(a);
        root.addView(gap,new LinearLayout.LayoutParams(1,dp(a,5)));
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

    static AlertDialog.Builder dialog(Activity a) { return new StyledDialogBuilder(a); }

    static void result(Activity a, boolean success, String title, String message, Runnable after) {
        String spoken=(success?"ทำรายการสำเร็จ ":"ทำรายการไม่สำเร็จ ")+message;
        AlertDialog d=dialog(a).setTitle(title).setMessage(message)
                .setPositiveButton("ตกลง",(dialog,which)->{if(after!=null)after.run();})
                .create();
        d.setOnShowListener(shown->speak(a,spoken));
        d.show();
        styleDialog(d);
    }

    private static void styleDialog(AlertDialog d) {
        android.content.Context context=d.getContext();
        TextView message=d.findViewById(android.R.id.message);
        if(message!=null){message.setTextSize(24);message.setPadding(dpContext(context,20),dpContext(context,12),dpContext(context,20),dpContext(context,12));}
        int titleId=d.getContext().getResources().getIdentifier("alertTitle","id","android");
        TextView title=titleId==0?null:d.findViewById(titleId);
        if(title!=null)title.setTextSize(30);
        Button positive=d.getButton(DialogInterface.BUTTON_POSITIVE);
        Button negative=d.getButton(DialogInterface.BUTTON_NEGATIVE);
        Button neutral=d.getButton(DialogInterface.BUTTON_NEUTRAL);
        if(positive!=null)positive.setTextSize(21);
        if(negative!=null)negative.setTextSize(21);
        if(neutral!=null)neutral.setTextSize(21);
    }

    private static int dpContext(android.content.Context context,int n) {
        return (int)(n*context.getResources().getDisplayMetrics().density+0.5f);
    }

    private static final class StyledDialogBuilder extends AlertDialog.Builder {
        StyledDialogBuilder(Activity a) { super(a); }
        @Override public AlertDialog show() {
            AlertDialog d=super.show();
            styleDialog(d);
            return d;
        }
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
        ViewParentLookup.addNavigation(a,root);
    }

    static void home(Activity a,LinearLayout root) {
        ViewParentLookup.addNavigation(a,root);
    }

    private static final class ViewParentLookup {
        static void addNavigation(Activity a,LinearLayout root) {
            if(root.getTag()!=null)return;
            android.view.ViewParent p=root.getParent();
            if(!(p instanceof ScrollView))return;
            android.view.ViewParent outer=p.getParent();
            if(!(outer instanceof FrameLayout))return;
            FrameLayout frame=(FrameLayout)outer;
            LinearLayout nav=new LinearLayout(a);
            nav.setOrientation(LinearLayout.HORIZONTAL);
            nav.setGravity(Gravity.CENTER_VERTICAL);
            nav.setPadding(dp(a,8),dp(a,3),dp(a,8),dp(a,3));
            nav.setBackgroundColor(Color.argb(246,255,255,255));

            Button home=new Button(a);
            home.setText("← หน้าหลัก");
            home.setTextSize(18);
            home.setAllCaps(false);
            home.setMinHeight(dp(a,40));
            home.setPadding(dp(a,6),0,dp(a,6),0);
            home.setSingleLine(true);
            home.setOnClickListener(v->{
                Intent intent=new Intent(a,MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                a.startActivity(intent);
                a.finish();
            });
            nav.addView(home,new LinearLayout.LayoutParams(-2,dp(a,42)));

            View spacer=new View(a);
            nav.addView(spacer,new LinearLayout.LayoutParams(0,1,1f));

            Button back=new Button(a);
            back.setText("ย้อนกลับ →");
            back.setTextSize(18);
            back.setAllCaps(false);
            back.setMinHeight(dp(a,40));
            back.setPadding(dp(a,6),0,dp(a,6),0);
            back.setSingleLine(true);
            back.setOnClickListener(v->a.finish());
            nav.addView(back,new LinearLayout.LayoutParams(-2,dp(a,42)));

            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(a,48),Gravity.BOTTOM);
            frame.addView(nav,lp);
            root.setTag(Boolean.TRUE);
        }
    }
}
