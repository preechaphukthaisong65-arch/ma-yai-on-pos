package com.example.newapp;

import android.app.AlertDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryScreen extends BaseScreen {

    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int DARK = Color.rgb(31, 41, 55);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int RED = Color.rgb(220, 38, 38);
    private static final int GRAY = Color.rgb(107, 114, 128);
    private static final int WHITE = Color.WHITE;
    private static final int BORDER = Color.rgb(229, 231, 235);

    private LocalDatabase localDatabase;
    private SQLiteDatabase database;

    private LinearLayout listContainer;
    private EditText searchInput;
    private TextView summaryText;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createBackground();

        localDatabase = new LocalDatabase(this);
        database = localDatabase.getReadableDatabase();

        buildScreen();
        loadSales();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(14), dp(16), dp(24));

        scroll.addView(page);

        ViewGroup content = findViewById(android.R.id.content);
        View backgroundRoot = content.getChildCount() > 0
                ? content.getChildAt(0)
                : null;

        if (backgroundRoot instanceof ViewGroup) {
            ((ViewGroup) backgroundRoot).addView(
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

        TextView title = text(
                "ประวัติการขาย",
                25,
                DARK,
                true
        );
        page.addView(title);

        page.addView(text(
                "ตรวจสอบรายการขายที่บันทึกไว้",
                14,
                GRAY,
                false
        ));

        addSpace(page, 14);

        summaryText = text(
                "กำลังโหลดข้อมูล...",
                15,
                BLUE,
                true
        );
        page.addView(summaryText);

        addSpace(page, 12);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(16);
        searchInput.setHint("ค้นหาเลขที่ใบเสร็จหรือชื่อลูกค้า");
        searchInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        searchInput.setBackgroundColor(WHITE);

        page.addView(
                searchInput,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(50)
                )
        );

        addSpace(page, 12);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        page.addView(
                listContainer,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        emptyText = text(
                "ไม่พบรายการขาย",
                15,
                GRAY,
                false
        );
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(0, dp(24), 0, dp(24));
        page.addView(emptyText);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {}

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                loadSales();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadSales() {
        if (database == null || !database.isOpen()) {
            return;
        }

        listContainer.removeAllViews();

        String keyword = searchInput == null
                ? ""
                : searchInput.getText().toString().trim();

        Cursor cursor = null;

        int count = 0;
        double totalSales = 0;

        try {
            String sql =
                    "SELECT id, receipt_number, sold_at, total, " +
                    "payment_method, payment_status, customer_name " +
                    "FROM sales ";

            String[] args = null;

            if (!keyword.isEmpty()) {
                sql +=
                        "WHERE receipt_number LIKE ? " +
                        "OR customer_name LIKE ? ";
                args = new String[]{
                        "%" + keyword + "%",
                        "%" + keyword + "%"
                };
            }

            sql += "ORDER BY sold_at DESC, rowid DESC";

            cursor = database.rawQuery(sql, args);

            while (cursor.moveToNext()) {
                String saleId = cursor.getString(0);
                String receiptNumber = cursor.getString(1);
                String soldAt = cursor.getString(2);
                double total = cursor.getDouble(3);
                String paymentMethod = cursor.getString(4);
                String paymentStatus = cursor.getString(5);
                String customerName = cursor.isNull(6)
                        ? ""
                        : cursor.getString(6);

                addSaleCard(
                        saleId,
                        receiptNumber,
                        soldAt,
                        total,
                        paymentMethod,
                        paymentStatus,
                        customerName
                );

                count++;
                totalSales += total;
            }

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "อ่านประวัติการขายไม่สำเร็จ",
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        summaryText.setText(
                "พบ " + count + " รายการ"
                        + "  |  ยอดรวม " + money(totalSales) + " บาท"
        );

        emptyText.setVisibility(
                count == 0 ? View.VISIBLE : View.GONE
        );
    }

    private void addSaleCard(
            String saleId,
            String receiptNumber,
            String soldAt,
            double total,
            String paymentMethod,
            String paymentStatus,
            String customerName
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(WHITE);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin = dp(10);

        listContainer.addView(card, cardParams);

        TextView receipt = text(
                receiptNumber == null || receiptNumber.isEmpty()
                        ? "รายการขาย " + saleId
                        : receiptNumber,
                16,
                BLUE,
                true
        );
        card.addView(receipt);

        card.addView(text(
                "วันที่: " + formatDate(soldAt),
                13,
                GRAY,
                false
        ));

        if (customerName != null && !customerName.trim().isEmpty()) {
            card.addView(text(
                    "ลูกค้า: " + customerName,
                    14,
                    DARK,
                    false
            ));
        }

        addSpace(card, 4);

        card.addView(text(
                "ยอดขาย: " + money(total) + " บาท",
                19,
                DARK,
                true
        ));

        card.addView(text(
                "ชำระ: " + paymentMethodLabel(paymentMethod),
                14,
                DARK,
                false
        ));

        TextView status = text(
                "สถานะ: " + paymentStatusLabel(paymentStatus),
                14,
                paymentStatusColor(paymentStatus),
                true
        );
        card.addView(status);

        addSpace(card, 6);

        TextView detailButton = text(
                "แตะเพื่อดูรายละเอียด  ›",
                14,
                BLUE,
                true
        );
        detailButton.setGravity(Gravity.END);
        card.addView(detailButton);

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);

        card.addView(
                divider,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1)
                )
        );

        card.setOnClickListener(v -> showSaleDetails(
                saleId,
                receiptNumber,
                soldAt,
                total,
                paymentMethod,
                paymentStatus,
                customerName
        ));
    }

    private void showSaleDetails(
            String saleId,
            String receiptNumber,
            String soldAt,
            double total,
            String paymentMethod,
            String paymentStatus,
            String customerName
    ) {
        StringBuilder details = new StringBuilder();

        details.append("เลขที่ใบเสร็จ: ")
                .append(receiptNumber == null ? "-" : receiptNumber)
                .append("\n");

        details.append("วันที่: ")
                .append(formatDate(soldAt))
                .append("\n");

        if (customerName != null && !customerName.trim().isEmpty()) {
            details.append("ลูกค้า: ")
                    .append(customerName)
                    .append("\n");
        }

        details.append("วิธีชำระ: ")
                .append(paymentMethodLabel(paymentMethod))
                .append("\n");

        details.append("สถานะ: ")
                .append(paymentStatusLabel(paymentStatus))
                .append("\n\n");

        Cursor cursor = null;

        try {
            cursor = database.rawQuery(
                    "SELECT product_name, barcode, unit_price, " +
                    "quantity, line_total " +
                    "FROM sale_items " +
                    "WHERE sale_id = ? " +
                    "ORDER BY id ASC",
                    new String[]{saleId}
            );

            if (!cursor.moveToFirst()) {
                details.append("ไม่พบรายละเอียดสินค้า\n");
            } else {
                do {
                    String productName = cursor.getString(0);
                    String barcode = cursor.getString(1);
                    double unitPrice = cursor.getDouble(2);
                    int quantity = cursor.getInt(3);
                    double lineTotal = cursor.getDouble(4);

                    details.append(productName)
                            .append("\n");

                    details.append("บาร์โค้ด: ")
                            .append(barcode == null ? "-" : barcode)
                            .append("\n");

                    details.append(money(unitPrice))
                            .append(" บาท × ")
                            .append(quantity)
                            .append(" = ")
                            .append(money(lineTotal))
                            .append(" บาท\n\n");

                } while (cursor.moveToNext());
            }

        } catch (Exception e) {
            details.append("อ่านรายละเอียดสินค้าไม่สำเร็จ\n");

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        details.append("----------------------\n");
        details.append("ยอดรวม: ")
                .append(money(total))
                .append(" บาท");

        new AlertDialog.Builder(this)
                .setTitle("รายละเอียดการขาย")
                .setMessage(details.toString())
                .setPositiveButton("ปิด", null)
                .show();
    }

    private String paymentMethodLabel(String method) {
        if (method == null) {
            return "ไม่ระบุ";
        }

        switch (method.toLowerCase(Locale.ROOT)) {
            case "cash":
                return "เงินสด";
            case "credit":
                return "เครดิต";
            case "transfer":
                return "โอนเงิน";
            case "qr":
                return "QR";
            default:
                return method;
        }
    }

    private String paymentStatusLabel(String status) {
        if (status == null) {
            return "ไม่ระบุ";
        }

        switch (status.toLowerCase(Locale.ROOT)) {
            case "paid":
                return "ชำระแล้ว";
            case "unpaid":
                return "ค้างชำระ";
            case "partial":
                return "ชำระบางส่วน";
            case "cancelled":
                return "ยกเลิก";
            default:
                return status;
        }
    }

    private int paymentStatusColor(String status) {
        if (status == null) {
            return GRAY;
        }

        switch (status.toLowerCase(Locale.ROOT)) {
            case "paid":
                return GREEN;
            case "unpaid":
                return RED;
            default:
                return GRAY;
        }
    }

    private String formatDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }

        try {
            SimpleDateFormat inputFormat =
                    new SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss",
                            Locale.US
                    );

            Date date = inputFormat.parse(value);

            if (date != null) {
                return new SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        Locale.getDefault()
                ).format(date);
            }

        } catch (Exception ignored) {
            // ถ้าเป็นรูปแบบอื่น ให้แสดงค่าที่บันทึกไว้แทน
        }

        return value;
    }

    private TextView text(
            String value,
            int size,
            int color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, dp(3), 0, dp(3));

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        return view;
    }

    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);

        parent.addView(
                space,
                new LinearLayout.LayoutParams(
                        1,
                        dp(height)
                )
        );
    }

    private String money(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    @Override
    protected void onDestroy() {
        if (localDatabase != null) {
            localDatabase.close();
        }

        super.onDestroy();
    }
    }
