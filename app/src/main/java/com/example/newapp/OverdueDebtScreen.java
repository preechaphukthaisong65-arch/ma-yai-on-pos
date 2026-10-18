package com.example.newapp;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DecimalFormat;

public class OverdueDebtScreen extends Activity {

    private LocalDatabase database;
    private LinearLayout list;
    private TextView summary;
    private final DecimalFormat money = new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = LocalDatabase.getInstance(this);
        buildScreen();
        loadBills();
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
        root.setPadding(dp(14), dp(12), dp(14), dp(16));
        root.setBackgroundColor(0xFFF7F7F7);

        root.addView(text("บิลเครดิตค้างเกินหนึ่งเดือน", 22, true));

        summary = text("", 16, true);
        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(8);
        root.addView(summary, summaryParams);

        Button refresh = new Button(this);
        refresh.setText("ตรวจสอบอีกครั้ง");
        refresh.setAllCaps(false);
        root.addView(refresh);
        refresh.setOnClickListener(v -> loadBills());

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

    private void loadBills() {
        list.removeAllViews();

        DebtAlertRepository repository =
                new DebtAlertRepository(database);

        int count = repository.countOverdueBills();
        double total = repository.getOverdueAmount();

        summary.setText(
                "จำนวน " + count + " บิล\nยอดค้างรวม "
                        + money.format(total) + " บาท"
        );

        int shown = 0;

        try (Cursor cursor = repository.getOverdueBills()) {
            while (cursor.moveToNext()) {
                shown++;

                String billNo = cursor.getString(1);
                double amount = cursor.getDouble(2);
                String createdAt = cursor.getString(3);
                String first = cursor.getString(4);
                String last = cursor.getString(5);

                String customer = ((first == null ? "" : first) + " "
                        + (last == null ? "" : last)).trim();

                if (customer.isEmpty()) {
                    customer = "ไม่ระบุลูกค้า";
                }

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(12), dp(10), dp(12), dp(10));
                card.setBackgroundColor(0xFFFFFFFF);

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(-1, -2);
                params.topMargin = dp(5);
                params.bottomMargin = dp(5);

                card.addView(text("บิล: " + safe(billNo), 16, true));
                card.addView(text("ลูกค้า: " + customer, 14, false));
                card.addView(text("วันที่ขาย: " + safe(createdAt), 13, false));
                card.addView(text(
                        "ยอดค้าง: " + money.format(amount) + " บาท",
                        16,
                        true
                ));

                list.addView(card, params);
            }
        } catch (Exception e) {
            list.addView(text(
                    "โหลดรายการไม่สำเร็จ กรุณาตรวจสอบฐานข้อมูล",
                    15,
                    false
            ));
            return;
        }

        if (shown == 0) {
            list.addView(text(
                    "ไม่มีบิลเครดิตค้างเกินหนึ่งเดือน",
                    16,
                    false
            ));
        }
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) loadBills();
    }
}
