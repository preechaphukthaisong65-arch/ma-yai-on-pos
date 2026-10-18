package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;

public class ReceiptReprintScreen extends Activity {

    private LocalDatabase database;
    private EditText billInput;
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

        root.addView(text("ค้นหาใบเสร็จเพื่อพิมพ์ซ้ำ", 22, true));

        billInput = new EditText(this);
        billInput.setSingleLine(true);
        billInput.setHint("กรอกเลขที่บิล");
        root.addView(billInput);

        Button search = new Button(this);
        search.setText("ค้นหาบิล");
        search.setAllCaps(false);
        root.addView(search);
        search.setOnClickListener(v -> searchBill());

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        params.topMargin = dp(10);
        root.addView(results, params);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void searchBill() {
        String billNumber = billInput.getText().toString().trim();
        results.removeAllViews();

        if (billNumber.isEmpty()) {
            results.addView(text("กรุณากรอกเลขที่บิล", 15, false));
            return;
        }

        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT id, bill_number, payment_type, status, total, " +
                "created_at, print_status FROM sales " +
                "WHERE bill_number = ? LIMIT 1",
                new String[]{billNumber})) {

            if (!cursor.moveToFirst()) {
                results.addView(text("ไม่พบบิลนี้", 16, true));
                return;
            }

            long saleId = cursor.getLong(0);
            String bill = cursor.getString(1);
            String type = cursor.getString(2);
            String status = cursor.getString(3);
            double total = cursor.getDouble(4);
            String createdAt = cursor.getString(5);
            String printStatus = cursor.getString(6);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackgroundColor(0xFFFFFFFF);

            card.addView(text("เลขที่บิล: " + safe(bill), 17, true));
            card.addView(text("วันที่: " + safe(createdAt), 14, false));
            card.addView(text(
                    "ประเภท: " + ("CREDIT".equalsIgnoreCase(type)
                            ? "ขายเชื่อ" : "ขายเงินสด"),
                    14,
                    false
            ));
            card.addView(text(
                    "สถานะบิล: " + safe(status),
                    14,
                    false
            ));
            card.addView(text(
                    "ยอดรวม: " + money.format(total) + " บาท",
                    16,
                    true
            ));
            card.addView(text(
                    "สถานะพิมพ์ล่าสุด: " + safe(printStatus),
                    13,
                    false
            ));

            Button reprint = new Button(this);
            reprint.setText("พิมพ์ใบเสร็จเดิมซ้ำ");
            reprint.setAllCaps(false);
            card.addView(reprint);

            reprint.setOnClickListener(v ->
                    confirmReprint(saleId, bill));

            results.addView(card);

        } catch (Exception e) {
            results.addView(text(
                    "ค้นหาบิลไม่สำเร็จ กรุณาตรวจสอบฐานข้อมูล",
                    15,
                    false
            ));
        }
    }

    private void confirmReprint(long saleId, String billNumber) {
        new AlertDialog.Builder(this)
                .setTitle("ยืนยันพิมพ์ใบเสร็จซ้ำ")
                .setMessage(
                        "บิล " + billNumber
                                + "\nจะพิมพ์จากข้อมูลเดิม"
                                + "\nไม่สร้างบิลใหม่และไม่หักสต๊อกซ้ำ"
                )
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("พิมพ์ซ้ำ", (dialog, which) -> {
                    ReceiptPrinter printer = new ReceiptPrinter(this);

                    new Thread(() -> {
                        ReceiptPrinter.PrintResult result =
                                printer.printSale(saleId);

                        runOnUiThread(() -> Toast.makeText(
                                this,
                                result.message,
                                Toast.LENGTH_LONG
                        ).show());
                    }).start();
                })
                .show();
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }
}
