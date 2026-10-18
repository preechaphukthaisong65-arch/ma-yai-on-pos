
package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;

public class CreditAccountScreen extends Activity {

    private LocalDatabase database;
    private LinearLayout listLayout;
    private TextView summaryText;
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

    private TextView text(String value, float size, boolean bold) {
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

        root.addView(text("บัญชีลูกหนี้", 24, true));

        summaryText = text("กำลังโหลด...", 16, true);
        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(8);
        summaryParams.bottomMargin = dp(8);
        root.addView(summaryText, summaryParams);

        Button refresh = button("รีเฟรชรายการ");
        root.addView(refresh);
        refresh.setOnClickListener(v -> loadBills());

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams listParams =
                new LinearLayout.LayoutParams(-1, -2);
        listParams.topMargin = dp(8);
        root.addView(listLayout, listParams);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void loadBills() {
        listLayout.removeAllViews();

        SQLiteDatabase db = database.getReadableDatabase();
        int count = 0;
        double debtTotal = 0;

        try (Cursor cursor = db.rawQuery(
                "SELECT s.id, s.bill_number, s.customer_id, " +
                "s.total, s.created_at, c.first_name, c.last_name " +
                "FROM sales s " +
                "LEFT JOIN customers c ON c.id = s.customer_id " +
                "WHERE s.payment_type = 'CREDIT' AND s.status = 'PENDING' " +
                "ORDER BY s.created_at ASC, s.id ASC",
                null)) {

            while (cursor.moveToNext()) {
                long saleId = cursor.getLong(0);
                String billNo = cursor.getString(1);
                double total = cursor.getDouble(3);
                String createdAt = cursor.getString(4);
                String first = cursor.getString(5);
                String last = cursor.getString(6);

                String customer = ((first == null ? "" : first) + " "
                        + (last == null ? "" : last)).trim();

                if (customer.isEmpty()) {
                    customer = "ไม่พบชื่อลูกค้า";
                }

                count++;
                debtTotal += total;

                addBillCard(
                        saleId,
                        billNo,
                        customer,
                        total,
                        createdAt
                );
            }
        } catch (Exception e) {
            summaryText.setText("อ่านรายการหนี้ไม่สำเร็จ");
            Toast.makeText(
                    this,
                    "ตรวจสอบโครงสร้างตาราง sales และ customers",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        summaryText.setText(
                "บิลค้างชำระ " + count + " บิล\nยอดหนี้รวม "
                        + money.format(debtTotal) + " บาท"
        );

        if (count == 0) {
            listLayout.addView(text("ไม่มีบิลค้างชำระ", 16, false));
        }
    }

    private void addBillCard(
            long saleId,
            String billNo,
            String customer,
            double total,
            String createdAt
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(6);
        cardParams.bottomMargin = dp(6);

        card.addView(text("บิล: " + billNo, 17, true));
        card.addView(text("ลูกค้า: " + customer, 15, false));
        card.addView(text("วันที่: " + safe(createdAt), 13, false));
        card.addView(text("ยอดค้าง: " + money.format(total) + " บาท", 16, true));

        Button detailButton = button("ดูรายละเอียด / ชำระเต็มบิล");
        card.addView(detailButton);
        detailButton.setOnClickListener(v ->
                showBillDetails(saleId, billNo, customer, total, createdAt));

        listLayout.addView(card, cardParams);
    }

    private void showBillDetails(
            long saleId,
            String billNo,
            String customer,
            double total,
            String createdAt
    ) {
        StringBuilder details = new StringBuilder();

        details.append("เลขบิล: ").append(billNo).append("\n");
        details.append("ลูกค้า: ").append(customer).append("\n");
        details.append("วันที่: ").append(safe(createdAt)).append("\n\n");

        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT product_name, quantity, unit, unit_price, line_total " +
                "FROM sale_items WHERE sale_id = ? ORDER BY rowid ASC",
                new String[]{String.valueOf(saleId)})) {

            while (cursor.moveToNext()) {
                details.append(cursor.getString(0))
                        .append("  ")
                        .append(cursor.getDouble(1))
                        .append(" ")
                        .append(safe(cursor.getString(2)))
                        .append("\n  ")
                        .append(money.format(cursor.getDouble(3)))
                        .append(" x ")
                        .append(money.format(cursor.getDouble(1)))
                        .append(" = ")
                        .append(money.format(cursor.getDouble(4)))
                        .append(" บาท\n");
            }
        } catch (Exception e) {
            details.append("\nอ่านรายการสินค้าไม่ได้\n");
        }

        details.append("\nยอดต้องชำระเต็มบิล: ")
                .append(money.format(total)).append(" บาท");

        new AlertDialog.Builder(this)
                .setTitle("รายละเอียดบิลเครดิต")
                .setMessage(details.toString())
                .setNegativeButton("ปิด", null)
                .setPositiveButton("รับชำระเต็มบิล", (dialog, which) ->
                        confirmPayment(saleId, billNo, total))
                .show();
    }

    private void confirmPayment(long saleId, String billNo, double total) {
        new AlertDialog.Builder(this)
                .setTitle("ยืนยันรับชำระ")
                .setMessage(
                        "รับเงินสดเต็มจำนวน " + money.format(total)
                                + " บาท สำหรับบิล " + billNo + " ใช่หรือไม่?"
                )
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("ยืนยันรับเงิน", (dialog, which) -> {
                    try {
                        CreditPaymentRepository repository =
                                new CreditPaymentRepository(database);

                        CreditPaymentRepository.PaymentResult result =
                                repository.payFullBill(saleId);

                        if (result.success) {
                            new AlertDialog.Builder(this)
                                    .setTitle("รับชำระสำเร็จ")
                                    .setMessage(
                                            "เลขที่ใบเสร็จรับชำระ: "
                                                    + result.receiptNumber
                                                    + "\nยอดรับชำระ: "
                                                    + money.format(result.amount)
                                                    + " บาท"
                                    )
                                    .setPositiveButton("ตกลง", (d, w) ->
                                            loadBills())
                                    .show();
                        } else {
                            showMessage(result.message);
                        }
                    } catch (Exception e) {
                        showMessage("รับชำระไม่สำเร็จ: " + e.getMessage());
                    }
                })
                .show();
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    private void showMessage(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("ตกลง", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (listLayout != null) {
            loadBills();
        }
    }
}
