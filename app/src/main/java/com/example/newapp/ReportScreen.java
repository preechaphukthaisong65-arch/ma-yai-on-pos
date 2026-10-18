package com.example.newapp;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class ReportScreen extends BaseScreen {

    private static final int DARK = Color.rgb(31, 41, 55);
    private static final int GRAY = Color.rgb(107, 114, 128);
    private static final int WHITE = Color.WHITE;

    private LinearLayout historyListLayout;
    private TextView summaryText;
    private LocalDatabase dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createBackground();
        dbHelper = new LocalDatabase(this);

        buildScreen();
        loadReportData();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(14), dp(16), dp(24));

        scroll.addView(page);

        ViewGroup content = findViewById(android.R.id.content);
        View rootView = content.getChildCount() > 0 ? content.getChildAt(0) : null;

        if (rootView instanceof ViewGroup) {
            ((ViewGroup) rootView).addView(
                    scroll,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        } else {
            content.addView(
                    scroll,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        }

        page.addView(text("รายงานยอดขายและประวัติ", 25, DARK, true));
        page.addView(text("สรุปภาพรวมและประวัติการขายทั้งหมด", 14, GRAY, false));

        addSpace(page, 16);

        // กล่องสรุปยอดรวม
        summaryText = text("ยอดขายรวมทั้งหมด: 0.00 บาท", 16, DARK, true);
        summaryText.setPadding(dp(14), dp(14), dp(14), dp(14));
        summaryText.setBackgroundColor(WHITE);
        page.addView(summaryText);

        addSpace(page, 16);

        page.addView(text("ประวัติใบเสร็จ", 16, DARK, true));
        addSpace(page, 6);

        historyListLayout = new LinearLayout(this);
        historyListLayout.setOrientation(LinearLayout.VERTICAL);
        page.addView(historyListLayout);
    }

    private void loadReportData() {
        historyListLayout.removeAllViews();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        double totalSalesSum = 0.0;
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("SELECT receipt_no, total_amount, payment_method, sold_at FROM sales ORDER BY id DESC", null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String receiptNo = cursor.getString(0);
                    double amount = cursor.getDouble(1);
                    String method = cursor.getString(2);
                    String soldAt = cursor.getString(3);

                    totalSalesSum += amount;

                    LinearLayout card = new LinearLayout(this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(dp(12), dp(10), dp(12), dp(10));
                    card.setBackgroundColor(WHITE);

                    card.addView(text("เลขที่: " + receiptNo, 15, DARK, true));
                    card.addView(text("ยอดเงิน: " + String.format(Locale.US, "%,.2f", amount) + " บาท (" + ("credit".equalsIgnoreCase(method) ? "เครดิต" : "เงินสด") + ")", 14, GRAY, false));
                    card.addView(text("เวลา: " + (soldAt != null ? soldAt : "-"), 12, GRAY, false));

                    historyListLayout.addView(card);
                    addSpace(historyListLayout, 8);

                } while (cursor.moveToNext());
            } else {
                historyListLayout.addView(text("ยังไม่มีประวัติการขาย", 14, GRAY, false));
            }
        } catch (Exception e) {
            historyListLayout.addView(text("เกิดข้อผิดพลาดในการโหลดข้อมูล", 14, Color.RED, false));
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }

        summaryText.setText("ยอดขายรวมทั้งหมด: " + String.format(Locale.US, "%,.2f", totalSalesSum) + " บาท");
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }
        return view;
    }

    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }

    @Override
    protected void onDestroy() {
        if (dbHelper != null) dbHelper.close();
        super.onDestroy();
    }
}
