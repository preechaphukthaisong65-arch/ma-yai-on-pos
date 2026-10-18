
package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class NotificationScreen extends Activity {

    private LocalDatabase database;
    private NotificationRepository repository;
    private LinearLayout list;
    private TextView summary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        database = LocalDatabase.getInstance(this);
        repository = new NotificationRepository(database);

        buildScreen();
        loadMessages();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xFF202020);

        if (bold) {
            view.setTypeface(null, android.graphics.Typeface.BOLD);
        }

        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(16));
        root.setBackgroundColor(0xFFF7F7F7);

        root.addView(text("ข้อความแจ้งเตือน", 23, true));

        summary = text("", 15, true);
        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(8);
        root.addView(summary, summaryParams);

        Button refresh = button("รีเฟรช");
        root.addView(refresh);
        refresh.setOnClickListener(v -> loadMessages());

        Button markAll = button("ทำเครื่องหมายว่าอ่านแล้วทั้งหมด");
        root.addView(markAll);
        markAll.setOnClickListener(v -> {
            int changed = repository.markAllRead();
            Toast.makeText(
                    this,
                    "ทำเครื่องหมายแล้ว " + changed + " รายการ",
                    Toast.LENGTH_SHORT
            ).show();
            loadMessages();
        });

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams listParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        listParams.topMargin = dp(8);
        root.addView(list, listParams);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void loadMessages() {
        list.removeAllViews();

        summary.setText(
                "ยังไม่ได้อ่าน " + repository.getUnreadCount() + " รายการ"
        );

        int count = 0;

        try (Cursor cursor = repository.getLatestMessages(200)) {
            while (cursor.moveToNext()) {
                count++;

                long id = cursor.getLong(0);
                String eventKey = cursor.getString(1);
                String message = cursor.getString(2);
                String createdAt = cursor.getString(3);
                boolean isRead = cursor.getInt(4) != 0;

                addMessage(id, eventKey, message, createdAt, isRead);
            }
        } catch (Exception e) {
            list.addView(text(
                    "อ่านประวัติแจ้งเตือนไม่สำเร็จ กรุณาตรวจสอบฐานข้อมูล",
                    15,
                    false
            ));
            return;
        }

        if (count == 0) {
            list.addView(text("ยังไม่มีข้อความแจ้งเตือน", 16, false));
        }
    }

    private void addMessage(
            long id,
            String eventKey,
            String message,
            String createdAt,
            boolean isRead
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundColor(isRead ? 0xFFFFFFFF : 0xFFEAF3FF);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.topMargin = dp(5);
        params.bottomMargin = dp(5);

        card.addView(text(
                isRead ? "อ่านแล้ว" : "ยังไม่ได้อ่าน",
                13,
                true
        ));

        card.addView(text(
                message == null ? "" : message,
                16,
                !isRead
        ));

        card.addView(text(
                "เวลา: " + safe(createdAt),
                12,
                false
        ));

        Button detail = button("รายละเอียด");
        card.addView(detail);

        detail.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("รายละเอียดแจ้งเตือน")
                    .setMessage(
                            safe(message) + "\n\n"
                                    + "เวลา: " + safe(createdAt) + "\n"
                                    + "รหัสเหตุการณ์: " + safe(eventKey)
                    )
                    .setPositiveButton("อ่านแล้ว", (dialog, which) -> {
                        repository.markRead(id);
                        loadMessages();
                    })
                    .setNegativeButton("ปิด", null)
                    .show();
        });

        list.addView(card, params);
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) loadMessages();
    }
}
