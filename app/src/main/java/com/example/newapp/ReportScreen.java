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
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportScreen extends BaseScreen {

    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int DARK = Color.rgb(31, 41, 55);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int GRAY = Color.rgb(107, 114, 128);
    private static final int WHITE = Color.WHITE;

    private LocalDatabase localDatabase;
    private SQLiteDatabase database;

    private LinearLayout reportContainer;
    private TextView latestSalesText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createBackground();

        localDatabase = new LocalDatabase(this);
        database = localDatabase.getReadableDatabase();

        buildScreen();
        loadReport();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(14), dp(16), dp(24));

        scroll.addView(page);

        ViewGroup content = findViewById(android.R.id.content);
        View rootView = content.getChildCount() > 0
                ? content.getChildAt(0)
                : null;

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

        page.addView(text(
                "รายงานยอดขาย",
                25,
                DARK,
                true
        ));

        page.addView(text(
                "สรุปจากรายการขายที่บันทึกในเครื่อง",
                14,
                GRAY,
                false
        ));

        addSpace(page, 18);

        reportContainer = new LinearLayout(this);
        reportContainer.setOrientation(LinearLayout.VERTICAL);

        page.addView(
                reportContainer,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        addSpace(page, 20);

        page.addView(text(
                "รายการขายล่าสุด",
                20,
                DARK,
                true
        ));

        addSpace(page, 8);

        latestSalesText = text(
                "กำลังโหลดข้อมูล...",
                14,
                GRAY,
                false
        );

        latestSalesText.setGravity(Gravity.TOP);
        page.addView(latestSalesText);
    }

    private void loadReport() {
        if (database == null || !database.isOpen()) {
            return;
        }

        reportContainer.removeAllViews();

        try {
            addReportCard("วันนี้", getStartOfToday(), getStartOfTomorrow());
            addReportCard("สัปดาห์นี้", getStartOfWeek(), getStartOfNextWeek());
            addReportCard("เดือนนี้", getStartOfMonth(), getStartOfNextMonth());
            addReportCard("ปีนี้", getStartOfYear(), getStartOfNextYear());

            loadLatestSales();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "อ่านรายงานยอดขายไม่สำเร็จ",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void addReportCard(
            String title,
            String start,
            String end
    ) {
        double total = 0;
        int count = 0;

        Cursor cursor = null;

        try {
            cursor = database.rawQuery(
                    "SELECT COUNT(*), COALESCE(SUM(total), 0) " +
                    "FROM sales " +
                    "WHERE sold_at >= ? AND sold_at < ? " +
                    "AND payment_status != 'cancelled'",
                    new String[]{start, end}
            );

            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
                total = cursor.getDouble(1);
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundColor(WHITE);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(10);
        reportContainer.addView(card, params);

        card.addView(text(
                title,
                16,
                GRAY,
                true
        ));

        card.addView(text(
                money(total) + " บาท",
                25,
                BLUE,
                true
        ));

        card.addView(text(
                count + " รายการขาย",
                14,
                DARK,
                false
        ));
    }

    private void loadLatestSales() {
        StringBuilder result = new StringBuilder();
        Cursor cursor = null;

        try {
            cursor = database.rawQuery(
                    "SELECT receipt_number, sold_at, total, " +
                    "payment_method, payment_status " +
                    "FROM sales " +
                    "ORDER BY sold_at DESC, rowid DESC " +
                    "LIMIT 10",
                    null
            );

            if (cursor.getCount() == 0) {
                latestSalesText.setText("ยังไม่มีรายการขาย");
                return;
            }

            while (cursor.moveToNext()) {
                String receipt = cursor.getString(0);
                String soldAt = cursor.getString(1);
                double total = cursor.getDouble(2);
                String method = cursor.getString(3);
                String status = cursor.getString(4);

                result.append(receipt == null ? "-" : receipt)
                        .append("\n");

                result.append(formatDate(soldAt))
                        .append("\n");

                result.append("ยอด ")
                        .append(money(total))
                        .append(" บาท")
                        .append("  |  ")
                        .append(paymentMethodLabel(method))
                        .append("  |  ")
                        .append(paymentStatusLabel(status))
                        .append("\n\n");
            }

            latestSalesText.setText(result.toString());

        } catch (Exception e) {
            latestSalesText.setText(
                    "ไม่สามารถอ่านรายการขายล่าสุดได้"
            );

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String getStartOfToday() {
        return dateAt(0, 0, 0, 0);
    }

    private String getStartOfTomorrow() {
        return dateAt(1, 0, 0, 0);
    }

    private String getStartOfWeek() {
        java.util.Calendar c = calendarNow();
        int day = c.get(java.util.Calendar.DAY_OF_WEEK);

        int daysFromMonday =
                (day + 5) % 7;

        c.add(java.util.Calendar.DAY_OF_MONTH, -daysFromMonday);
        resetTime(c);

        return formatDatabaseDate(c.getTime());
    }

    private String getStartOfNextWeek() {
        java.util.Calendar c = calendarNow();
        int day = c.get(java.util.Calendar.DAY_OF_WEEK);

        int daysFromMonday =
                (day + 5) % 7;

        c.add(java.util.Calendar.DAY_OF_MONTH, -daysFromMonday);
        c.add(java.util.Calendar.DAY_OF_MONTH, 7);
        resetTime(c);

        return formatDatabaseDate(c.getTime());
    }

    private String getStartOfMonth() {
        java.util.Calendar c = calendarNow();
        c.set(java.util.Calendar.DAY_OF_MONTH, 1);
        resetTime(c);

        return formatDatabaseDate(c.getTime());
    }

    private String getStartOfNextMonth() {
        java.util.Calendar c = calendarNow();
        c.set(java.util.Calendar.DAY_OF_MONTH, 1);
        c.add(java.util.Calendar.MONTH, 1);
        resetTime(c);

        return formatDatabaseDate(c.getTime());
    }

    private String getStartOfYear() {
        java.util.Calendar c = calendarNow();
        c.set(java.util.Calendar.DAY_OF_YEAR, 1);
        resetTime(c);

        return formatDatabaseDate(c.getTime());
    }

    private String getStartOfNextYear() {
        java.util.Calendar c = calendarNow();
        c.set(java.util.Calendar.DAY_OF_YEAR, 1);
        c.add(java.util.Calendar.YEAR, 1);
        resetTime(c);

        return formatDatabaseDate(c.getTime());
    }

    private String dateAt(
            int dayOffset,
            int hour,
            int minute,
            int second
    ) {
        java.util.Calendar c = calendarNow();
        c.add(java.util.Calendar.DAY_OF_MONTH, dayOffset);
        c.set(java.util.Calendar.HOUR_OF_DAY, hour);
        c.set(java.util.Calendar.MINUTE, minute);
        c.set(java.util.Calendar.SECOND, second);
        c.set(java.util.Calendar.MILLISECOND, 0);

        return formatDatabaseDate(c.getTime());
    }

    private java.util.Calendar calendarNow() {
        return java.util.Calendar.getInstance();
    }

    private void resetTime(java.util.Calendar c) {
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
    }

    private String formatDatabaseDate(Date date) {
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
        ).format(date);
    }

    private String formatDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }

        try {
            String normalized = value.replace('T', ' ');

            SimpleDateFormat input = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.US
            );

            Date date = input.parse(normalized);

            if (date != null) {
                return new SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        Locale.getDefault()
                ).format(date);
            }

        } catch (Exception ignored) {
        }

        return value;
    }

    private String paymentMethodLabel(String method) {
        if (method == null) {
            return "ไม่ระบุวิธีชำระ";
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
            return "ไม่ระบุสถานะ";
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
