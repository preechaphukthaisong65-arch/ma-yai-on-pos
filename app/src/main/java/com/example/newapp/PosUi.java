package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
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
    private static final float TEXT_SCALE = 1.5f;
    private PosUi() {}

    static int dp(Activity a, int n) {
        return (int)(n * a.getResources().getDisplayMetrics().density + 0.5f);
    }

    static LinearLayout page(Activity a, String title) {
        a.getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        int background = a.getResources().getIdentifier("app_background", "drawable", a.getPackageName());
        LinearLayout frame = new LinearLayout(a);
        frame.setOrientation(LinearLayout.VERTICAL);
        if (background == 0) frame.setBackgroundColor(Color.rgb(248,250,247));
        else frame.setBackgroundResource(background);

        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,16), dp(a,12), dp(a,16), dp(a,18));
        root.setBackgroundColor(Color.TRANSPARENT);
        TextView heading = text(a, title, 24, Color.rgb(35,80,48));
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(dp(a,10), dp(a,10), dp(a,10), dp(a,10));
        root.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        View gap = new View(a);
        root.addView(gap, new LinearLayout.LayoutParams(1, dp(a,10)));
        scroll.addView(root);
        frame.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        frame.addView(navigation(a), new LinearLayout.LayoutParams(-1, dp(a,52)));
        a.setContentView(frame);

        // Scale every text widget once, including widgets created directly by older screens.
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> scaleTree(root));
        return root;
    }

    private static void scaleTree(View view) {
        Object marker = view.getTag();
        if (!(marker instanceof String) || !"pos-ui-scaled".equals(marker)) {
            if (view instanceof TextView) {
                TextView t = (TextView)view;
                t.setTextSize(TypedValue.COMPLEX_UNIT_PX, t.getTextSize() * TEXT_SCALE);
                if (view instanceof EditText) styleInput((EditText)view, null);
                if (view instanceof Button) {
                    view.setMinimumHeight(Math.max(view.getMinimumHeight(), 52));
                    t.setIncludeFontPadding(true);
                }
            }
            view.setTag("pos-ui-scaled");
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) scaleTree(group.getChildAt(i));
        }
    }

    private static LinearLayout navigation(Activity a) {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(a,8), dp(a,2), dp(a,8), dp(a,2));
        bar.setBackgroundColor(Color.argb(245,255,255,255));

        Button home = navButton(a, "← หน้าหลัก");
        home.setContentDescription("กลับหน้าหลัก");
        home.setOnClickListener(v -> {
            Intent intent = new Intent(a, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            a.startActivity(intent);
            a.finish();
        });
        Button back = navButton(a, "ย้อนกลับ →");
        back.setContentDescription("ย้อนกลับหน้าก่อน");
        back.setOnClickListener(v -> a.finish());

        bar.addView(home, new LinearLayout.LayoutParams(0, -1, 1f));
        View spacer = new View(a);
        bar.addView(spacer, new LinearLayout.LayoutParams(dp(a,8), 1));
        bar.addView(back, new LinearLayout.LayoutParams(0, -1, 1f));
        return bar;
    }

    private static Button navButton(Activity a, String label) {
        Button b = new Button(a);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setMinHeight(dp(a,42));
        b.setPadding(dp(a,4), 0, dp(a,4), 0);
        b.setBackground(tintedRounded(Color.rgb(232,242,233), Color.rgb(45,95,56), dp(a,10)));
        b.setTextColor(Color.rgb(35,80,48));
        return b;
    }

    private static GradientDrawable tintedRounded(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        d.setStroke(1, stroke);
        return d;
    }

    static TextView text(Activity a, String value, int size, int color) {
        TextView t = new TextView(a);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(a,10), dp(a,8), dp(a,10), dp(a,8));
        t.setBackground(tintedRounded(Color.WHITE, Color.rgb(225,231,225), dp(a,8)));
        return t;
    }

    static Button button(Activity a, LinearLayout root, String label, View.OnClickListener listener) {
        Button b = new Button(a);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setMinHeight(dp(a,54));
        b.setPadding(dp(a,12), dp(a,8), dp(a,12), dp(a,8));
        b.setBackground(tintedRounded(Color.rgb(244,249,244), Color.rgb(100,145,105), dp(a,10)));
        b.setTextColor(Color.rgb(30,70,38));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(a,8);
        root.addView(b, lp);
        return b;
    }

    static EditText input(Activity a, LinearLayout root, String hint) {
        EditText e = new EditText(a);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setTextSize(16);
        e.setTextColor(Color.rgb(30,30,30));
        e.setHintTextColor(Color.rgb(100,100,100));
        styleInput(e, a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(a,10);
        root.addView(e, lp);
        return e;
    }

    private static void styleInput(EditText e, Activity a) {
        int pad = a == null ? 18 : dp(a,12);
        e.setPadding(pad, pad, pad, pad);
        if (a != null) e.setMinHeight(dp(a,56));
        e.setBackground(tintedRounded(Color.WHITE, Color.rgb(105,145,110), a == null ? 16 : dp(a,10)));
        e.setSelectAllOnFocus(false);
    }

    static void line(Activity a, LinearLayout root, String value) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(a,5);
        root.addView(text(a, value, 16, Color.DKGRAY), lp);
    }

    static void scan(Activity a) { BarcodeScanner.launch(a); }
    static void scan(Activity a, String prompt) { BarcodeScanner.launch(a, prompt); }

    static void speak(Activity a, String message) { speakThen(a, message, () -> {}); }

    static void speakThen(Activity a, String message, Runnable after) {
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(a, status -> {
            if (status != TextToSpeech.SUCCESS) {
                if (holder[0] != null) holder[0].shutdown();
                a.runOnUiThread(after);
                return;
            }
            ttsLangSafe(a, holder[0]);
            holder[0].setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) {
                    a.runOnUiThread(() -> { holder[0].shutdown(); after.run(); });
                }
                @Override public void onError(String id) {
                    a.runOnUiThread(() -> { holder[0].shutdown(); after.run(); });
                }
            });
            holder[0].speak(message, TextToSpeech.QUEUE_FLUSH, null, "pos-" + System.nanoTime());
        });
    }

    static void transactionFeedback(Activity a, boolean success, String detail) { transactionFeedback(a, success, detail, null); }\n\n    static void transactionFeedback(Activity a, boolean success, String detail, Runnable after) {
        String title = success ? "บันทึกสำเร็จ" : "บันทึกไม่สำเร็จ";
        String spoken = success ? "บันทึกรายการสำเร็จค่ะ" : "บันทึกรายการไม่สำเร็จค่ะ";
        if (detail != null && !detail.trim().isEmpty()) spoken += " " + detail;
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(detail == null || detail.trim().isEmpty() ? title : detail)
                .setPositiveButton("ตกลง", null)
                .create();
        dialog.setOnShowListener(d -> speak(a, spoken));
        dialog.show();
    }

    private static void ttsLangSafe(Activity a, TextToSpeech tts) {
        Locale th = new Locale("th","TH");
        if (tts.isLanguageAvailable(th) >= TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(th);
            tts.setSpeechRate(0.95f);
            tts.setPitch(1.15f);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                try {
                    java.util.Set<android.speech.tts.Voice> voices = tts.getVoices();
                    if (voices != null) for (android.speech.tts.Voice voice : voices) {
                        String name = voice.getName() == null ? "" : voice.getName().toLowerCase(Locale.ROOT);
                        if ("th".equals(voice.getLocale().getLanguage()) &&
                                (name.contains("female") || name.contains("woman") || name.contains("fem"))) {
                            tts.setVoice(voice);
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    static String str(EditText e) { return e.getText().toString().trim(); }
    static long satang(EditText e) { return PosDatabase.toSatang(str(e)); }

    // Navigation is supplied by page() so every submenu has the same two bottom controls.
    static void back(Activity a, LinearLayout root) {}
}
