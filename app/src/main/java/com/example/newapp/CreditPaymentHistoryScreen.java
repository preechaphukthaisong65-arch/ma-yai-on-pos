
package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;

public class CreditPaymentHistoryScreen extends Activity {

    private LocalDatabase database;
    private LinearLayout list;
    private final DecimalFormat money = new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = LocalDatabase.getInstance(this);
        buildScreen();
        loadHistory();
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

        root.addView(text("ประวัติรับชำระหนี้", 23, true));

        Button refresh = new Button(this);
        refresh.setText("รีเฟรช");
        refresh.setAllCaps(false);
        root.addView(refresh);
        refresh.setOnClickListener(v -> loadHistory());

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

    private void loadHistory() {
        list.removeAllViews();

        SQLiteDatabase db = database.getReadableDatabase();
        int count = 0;

        String sql =
                "SELECT " +
                "cp.payment_number AS payment_receipt_number, " +
                "s.bill_number AS original_bill_number, " +
                "cp.amount AS amount, " +
                "cp.paid_at AS created_at, " +
                "c.first_name AS first_name, " +
                "c.last_name AS last_name " +
                "FROM credit_payments cp " +
                "INNER JOIN sales s ON s.id = cp.sale_id " +
                "LEFT JOIN customers c ON c.id = s.customer_id " +
                "ORDER BY cp.paid_at DESC, cp.id DESC";

        try (Cursor cursor = db.rawQuery(sql, null)) {

            while (cursor.moveToNext()) {
                count++;

                String receiptNo = cursor.getString(0);
                String originalBill = cursor.getString(1);
                double amount = cursor.getDouble(2);
                String date = cursor.getString(3);
                String first = cursor.getString(4);
                String last = cursor.getString(5);

                String customer = ((first == null ? "" : first) + " "
                        + (last == null ? "" : last)).trim();

                if (customer.isEmpty()) customer = "ไม่ระบุลูกค้า";

                addItem(receiptNo, originalBill, amount, date, customer);
            }
        } catch (Exception e) {
            list.addView(text(
                    "โหลดประวัติไม่ได้ กรุณาตรวจสอบตาราง credit_payments",
                    15,
                    false
            ));
            return;
        }

        if (count == 0) {
            list.addView(text("ยังไม่มีประวัติรับชำระ", 16, false));
        }
    }

    private void addItem(
            String receiptNo,
            String originalBill,
            double amount,
            String date,
            String customer
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(5);
        params.bottomMargin = dp(5);

        card.addView(text("ใบเสร็จรับชำระ: " + safe(receiptNo), 16, true));
        card.addView(text("บิลต้นฉบับ: " + safe(originalBill), 14, false));
        card.addView(text("ลูกค้า: " + customer, 14, false));
        card.addView(text("วันที่: " + safe(date), 13, false));
        card.addView(text("ยอดรับชำระ: " + money.format(amount) + " บาท", 16, true));

        Button reprint = new Button(this);
        reprint.setText("พิมพ์ใบเสร็จนี้ซ้ำ");
        reprint.setAllCaps(false);
        card.addView(reprint);

        reprint.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("พิมพ์ใบเสร็จซ้ำ")
                    .setMessage(
                            "จะพิมพ์ใบเสร็จรับชำระเลขที่ " + receiptNo
                                    + "\nจะไม่มีการรับเงินหรือสร้างรายการใหม่"
                    )
                    .setNegativeButton("ยกเลิก", null)
                    .setPositiveButton("พิมพ์", (dialog, which) -> {
                        ReceiptPrinter printer = new ReceiptPrinter(this);
                        ReceiptPrinter.PrintResult result =
                                printer.printCreditPayment(receiptNo);

                        Toast.makeText(
                                this,
                                result.message,
                                Toast.LENGTH_LONG
                        ).show();
                    })
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
        if (list != null) loadHistory();
    }
}
