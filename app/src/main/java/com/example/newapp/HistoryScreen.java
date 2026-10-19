
package com.example.newapp;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class HistoryScreen extends Activity {

    private SalesHistoryRepository repository;
    private LinearLayout content;
    private EditText searchInput;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_PAID = 1;
    private static final int FILTER_PENDING = 2;

    private int currentFilter = FILTER_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = new SalesHistoryRepository(
                LocalDatabase.getInstance(this)
        );

        buildScreen();
        showHistory();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 20);
        root.setBackgroundColor(Color.WHITE);

        TextView title = makeText("ประวัติการขาย", 24, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, params());

        TextView subtitle = makeText(
                "บิลย้อนหลัง 30 วัน ตามวันที่ขาย",
                14,
                false
        );
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, params());

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("ค้นหาเลขที่บิล");
        searchInput.setTextSize(16);
        root.addView(searchInput, params());

        Button searchButton = makeButton(
                "ค้นหาเลขที่บิล",
                this::searchBill
        );
        root.addView(searchButton, params());

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);

        Button allButton = makeButton(
                "บิลทั้งหมด",
                () -> {
                    currentFilter = FILTER_ALL;
                    showHistory();
                }
        );

        Button paidButton = makeButton(
                "จ่ายแล้ว",
                () -> {
                    currentFilter = FILTER_PAID;
                    showHistory();
                }
        );

        Button pendingButton = makeButton(
                "ค้างชำระ",
                () -> {
                    currentFilter = FILTER_PENDING;
                    showHistory();
                }
        );

        filters.addView(allButton, weightedParams());
        filters.addView(paidButton, weightedParams());
        filters.addView(pendingButton, weightedParams());

        root.addView(filters, params());

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 8, 0, 8);
        root.addView(content, params());

        root.addView(
                makeButton("กลับหน้าหลัก", this::finish),
                params()
        );

        scroll.addView(root);
        setContentView(scroll);
    }

    private void showHistory() {
        content.removeAllViews();

        Cursor cursor = null;
        int shown = 0;

        try {
            cursor = repository.getRecentBills();

            if (cursor == null || !cursor.moveToFirst()) {
                content.addView(
                        makeText("ไม่พบบิลในช่วง 30 วันล่าสุด", 16, false),
                        params()
                );
                return;
            }

            do {
                String status = getString(cursor, "status");
                String paymentType = getString(cursor, "payment_type");

                if (currentFilter == FILTER_PAID
                        && !"PAID".equalsIgnoreCase(status)) {
                    continue;
                }

                if (currentFilter == FILTER_PENDING
                        && !("PENDING".equalsIgnoreCase(status)
                        && "CREDIT".equalsIgnoreCase(paymentType))) {
                    continue;
                }

                long saleId = getLong(cursor, "id");
                String billNumber = getString(cursor, "bill_number");
                String firstName = getString(cursor, "first_name");
                String lastName = getString(cursor, "last_name");
                String createdAt = getString(cursor, "created_at");
                double total = getDouble(cursor, "total");

                String customerName = (firstName + " " + lastName).trim();
                if (customerName.isEmpty()) {
                    customerName = "ลูกค้าทั่วไป";
                }

                String paymentLabel = "CREDIT".equalsIgnoreCase(paymentType)
                        ? "ขายเชื่อ"
                        : "เงินสด";

                String statusLabel = "PENDING".equalsIgnoreCase(status)
                        ? "ค้างชำระ"
                        : "ชำระแล้ว";

                String summary =
                        "เลขที่บิล: " + billNumber
                                + "\nวันที่ขาย: " + createdAt
                                + "\nลูกค้า: " + customerName
                                + "\nประเภท: " + paymentLabel
                                + "\nสถานะ: " + statusLabel
                                + "\nยอดรวม: " + money(total) + " บาท";

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(12, 8, 12, 12);
                card.setBackgroundColor(Color.rgb(245, 248, 245));

                TextView detail = makeText(summary, 15, false);
                card.addView(detail, params());

                card.addView(
                        makeButton(
                                "ดูรายละเอียดบิล",
                                () -> showBillDetails(saleId, billNumber)
                        ),
                        params()
                );

                content.addView(card, params());
                addDivider();

                shown++;

            } while (cursor.moveToNext());

            if (shown == 0) {
                String message = currentFilter == FILTER_PAID
                        ? "ไม่พบบิลที่ชำระแล้วในช่วงนี้"
                        : currentFilter == FILTER_PENDING
                        ? "ไม่พบบิลค้างชำระในช่วงนี้"
                        : "ไม่พบบิลในช่วง 30 วันล่าสุด";

                content.addView(makeText(message, 16, false), params());
            }

        } catch (Exception e) {
            showError("อ่านประวัติการขายไม่สำเร็จ", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void searchBill() {
        String billNumber = searchInput.getText().toString().trim();

        if (billNumber.isEmpty()) {
            toast("กรุณากรอกเลขที่บิล");
            return;
        }

        Cursor cursor = null;

        try {
            cursor = repository.findBill(billNumber);

            if (cursor == null || !cursor.moveToFirst()) {
                toast("ไม่พบเลขที่บิลนี้");
                return;
            }

            long saleId = getLong(cursor, "id");
            String foundBill = getString(cursor, "bill_number");

            showBillDetails(saleId, foundBill);

        } catch (Exception e) {
            showError("ค้นหาบิลไม่สำเร็จ", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void showBillDetails(long saleId, String billNumber) {
        content.removeAllViews();

        Cursor saleCursor = null;

        try {
            saleCursor = repository.findBill(billNumber);

            if (saleCursor == null || !saleCursor.moveToFirst()) {
                content.addView(
                        makeText("ไม่พบบิลนี้ในฐานข้อมูล", 16, false),
                        params()
                );
                addBackToHistoryButton();
                return;
            }

            String paymentType = getString(saleCursor, "payment_type");
            String status = getString(saleCursor, "status");
            String createdAt = getString(saleCursor, "created_at");
            String paidAt = getString(saleCursor, "paid_at");
            String printStatus = getString(saleCursor, "print_status");

            double subtotal = getDouble(saleCursor, "subtotal");
            double serviceFee = getDouble(saleCursor, "service_fee");
            double total = getDouble(saleCursor, "total");
            double cashReceived = getDouble(saleCursor, "cash_received");
            double changeDue = getDouble(saleCursor, "change_due");

            String paymentLabel = "CREDIT".equalsIgnoreCase(paymentType)
                    ? "ขายเชื่อ"
                    : "เงินสด";

            String statusLabel = "PENDING".equalsIgnoreCase(status)
                    ? "ค้างชำระ"
                    : "ชำระแล้ว";

            content.addView(
                    makeText("รายละเอียดบิล " + billNumber, 20, true),
                    params()
            );

            StringBuilder summary = new StringBuilder();
            summary.append("วันที่ขาย: ").append(createdAt)
                    .append("\nประเภทการขาย: ").append(paymentLabel)
                    .append("\nสถานะ: ").append(statusLabel)
                    .append("\nยอดรวมสินค้า: ").append(money(subtotal))
                    .append(" บาท");

            if (serviceFee != 0) {
                summary.append("\nค่าบริการ: ")
                        .append(money(serviceFee))
                        .append(" บาท");
            }

            summary.append("\nยอดสุทธิ: ")
                    .append(money(total))
                    .append(" บาท");

            if ("CASH".equalsIgnoreCase(paymentType)) {
                summary.append("\nรับเงิน: ")
                        .append(money(cashReceived))
                        .append(" บาท")
                        .append("\nเงินทอน: ")
                        .append(money(changeDue))
                        .append(" บาท");
            }

            if (paidAt != null && !paidAt.trim().isEmpty()) {
                summary.append("\nวันที่ชำระ: ").append(paidAt);
            }

            summary.append("\nสถานะการพิมพ์: ").append(printStatus);

            content.addView(
                    makeText(summary.toString(), 16, false),
                    params()
            );

            content.addView(
                    makeText("รายการสินค้าในบิล", 18, true),
                    params()
            );

            Cursor items = null;

            try {
                items = repository.getSaleItems(saleId);

                if (items == null || !items.moveToFirst()) {
                    content.addView(
                            makeText("ไม่พบรายการสินค้าในบิลนี้", 15, false),
                            params()
                    );
                } else {
                    do {
                        String productName = getString(items, "product_name");
                        String barcode = getString(items, "barcode");
                        String unit = getString(items, "unit");
                        int quantity = getInt(items, "quantity");
                        double unitPrice = getDouble(items, "unit_price");
                        double lineTotal = getDouble(items, "line_total");

                        String itemText =
                                productName
                                        + "\nบาร์โค้ด: " + barcode
                                        + "\nจำนวน: " + quantity + " " + unit
                                        + "\nราคาต่อหน่วย: "
                                        + money(unitPrice) + " บาท"
                                        + "\nรวม: " + money(lineTotal) + " บาท";

                        content.addView(
                                makeText(itemText, 15, false),
                                params()
                        );
                        addDivider();

                    } while (items.moveToNext());
                }

            } finally {
                if (items != null) {
                    items.close();
                }
            }

            content.addView(
                    makeButton(
                            "พิมพ์ใบเสร็จซ้ำ",
                            () -> printReceipt(saleId)
                    ),
                    params()
            );

            addBackToHistoryButton();

        } catch (Exception e) {
            showError("อ่านรายละเอียดบิลไม่สำเร็จ", e);
            addBackToHistoryButton();
        } finally {
            if (saleCursor != null) {
                saleCursor.close();
            }
        }
    }

    private void printReceipt(long saleId) {
        try {
            ReceiptPrinter printer = new ReceiptPrinter(this);
            ReceiptPrinter.PrintResult result = printer.printSale(saleId);

            if (result.success) {
                repository.updatePrintStatus(saleId, "PRINTED");
                toast(result.message);
            } else {
                repository.updatePrintStatus(saleId, "FAILED");
                toast(result.message);
            }

        } catch (Exception e) {
            showError("สั่งพิมพ์ใบเสร็จไม่สำเร็จ", e);
        }
    }

    private void addBackToHistoryButton() {
        content.addView(
                makeButton("กลับรายการประวัติ", this::showHistory),
                params()
        );
    }

    private void addDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.LTGRAY);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                );
        p.topMargin = 8;
        p.bottomMargin = 8;

        content.addView(divider, p);
    }

    private TextView makeText(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.rgb(40, 50, 40));
        text.setPadding(8, 8, 8, 8);

        if (bold) {
            text.setTypeface(null, android.graphics.Typeface.BOLD);
        }

        return text;
    }

    private Button makeButton(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightedParams() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
    }

    private String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) {
            return "";
        }

        String value = cursor.getString(index);
        return value == null ? "" : value;
    }

    private long getLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index)
                ? 0L : cursor.getLong(index);
    }

    private int getInt(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index)
                ? 0 : cursor.getInt(index);
    }

    private double getDouble(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index)
                ? 0.0 : cursor.getDouble(index);
    }

    private String money(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    private void toast(String message) {
        Toast.makeText(
                this,
                message == null ? "" : message,
                Toast.LENGTH_LONG
        ).show();
    }

    private void showError(String message, Exception e) {
        android.util.Log.e("HistoryScreen", message, e);
        toast(message);
    }
}
