
package com.example.newapp;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DecimalFormat;

public class PriceHistoryScreen extends Activity {

    private LocalDatabase database;
    private EditText barcodeInput;
    private LinearLayout results;
    private final DecimalFormat money = new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = LocalDatabase.getInstance(this);
        buildScreen();
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

        root.addView(text("ประวัติการแก้ไขราคาขาย", 22, true));

        barcodeInput = new EditText(this);
        barcodeInput.setSingleLine(true);
        barcodeInput.setHint("กรอกหรือสแกนบาร์โค้ด");
        root.addView(barcodeInput);

        Button search = new Button(this);
        search.setText("ค้นหาประวัติ");
        search.setAllCaps(false);
        root.addView(search);
        search.setOnClickListener(v -> searchHistory());

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams resultParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        resultParams.topMargin = dp(10);
        root.addView(results, resultParams);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void searchHistory() {
        String barcode = barcodeInput.getText().toString().trim();
        results.removeAllViews();

        if (barcode.isEmpty()) {
            results.addView(text("กรุณากรอกบาร์โค้ด", 15, false));
            return;
        }

        SQLiteDatabase db = database.getReadableDatabase();
        int count = 0;

        try (Cursor cursor = db.rawQuery(
                "SELECT ph.old_price, ph.new_price, ph.changed_at " +
                "FROM price_history ph " +
                "JOIN products p ON p.id = ph.product_id " +
                "WHERE p.barcode = ? " +
                "ORDER BY ph.changed_at DESC",
                new String[]{barcode})) {

            while (cursor.moveToNext()) {
                count++;

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(12), dp(10), dp(12), dp(10));
                card.setBackgroundColor(0xFFFFFFFF);

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(-1, -2);
                params.topMargin = dp(5);
                params.bottomMargin = dp(5);

                card.addView(text(
                        "ราคาเดิม: " + money.format(cursor.getDouble(0)) + " บาท",
                        15,
                        false
                ));

                card.addView(text(
                        "ราคาใหม่: " + money.format(cursor.getDouble(1)) + " บาท",
                        16,
                        true
                ));

                card.addView(text(
                        "เวลาแก้ไข: " + safe(cursor.getString(2)),
                        13,
                        false
                ));

                results.addView(card, params);
            }

        } catch (Exception e) {
            results.addView(text(
                    "อ่านประวัติไม่ได้ กรุณาตรวจสอบตาราง price_history",
                    15,
                    false
            ));
            return;
        }

        if (count == 0) {
            results.addView(text("ไม่พบประวัติการแก้ไขราคา", 16, true));
        }
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }
}
