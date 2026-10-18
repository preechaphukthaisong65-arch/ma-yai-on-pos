package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class CloudSyncStatusScreen extends Activity {

    private LocalDatabase database;
    private TextView statusText;
    private TextView pendingText;
    private TextView failedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = LocalDatabase.getInstance(this);
        buildScreen();
        refreshStatus();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xFF202020);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.setBackgroundColor(0xFFF7F7F7);

        root.addView(text("สถานะสำรองข้อมูล", 23, true));

        statusText = text("", 16, true);
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(12);
        root.addView(statusText, statusParams);

        pendingText = text("", 16, false);
        LinearLayout.LayoutParams pendingParams =
                new LinearLayout.LayoutParams(-1, -2);
        pendingParams.topMargin = dp(12);
        root.addView(pendingText, pendingParams);

        failedText = text("", 16, false);
        LinearLayout.LayoutParams failedParams =
                new LinearLayout.LayoutParams(-1, -2);
        failedParams.topMargin = dp(6);
        root.addView(failedText, failedParams);

        Button refresh = new Button(this);
        refresh.setText("รีเฟรชสถานะ");
        refresh.setAllCaps(false);
        
        // เพิ่มระยะห่างด้านบนให้ปุ่มรีเฟรชดูสวยงามขึ้น
        LinearLayout.LayoutParams refreshParams =
                new LinearLayout.LayoutParams(-2, -2);
        refreshParams.topMargin = dp(16);
        root.addView(refresh, refreshParams);
        
        refresh.setOnClickListener(v -> refreshStatus());

        TextView note = text(
                "ข้อมูลยังอยู่ในฐานข้อมูลภายในโทรศัพท์ " +
                "การมีรายการในคิวไม่ได้หมายความว่ามีการสำรองข้อมูลขึ้นคลาวด์แล้ว",
                14,
                false
        );

        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        noteParams.topMargin = dp(16);
        root.addView(note, noteParams);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void refreshStatus() {
        CloudSyncManager manager = new CloudSyncManager(database);

        statusText.setText(manager.getSyncStatusMessage());
        pendingText.setText(
                "รายการรอซิงก์: " + manager.countPending() + " รายการ"
        );
        failedText.setText(
                "รายการล้มเหลว: " + manager.countFailed() + " รายการ"
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusText != null) refreshStatus();
    }
}
