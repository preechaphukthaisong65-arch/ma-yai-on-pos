
package com.example.newapp;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportScreen extends Activity {

    private ReportRepository reportRepository;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        reportRepository = new ReportRepository(
                LocalDatabase.getInstance(this)
        );

        buildScreen();
        showMenu();
    }

    private void buildScreen() {
        ScrollView scrollView = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("รายงานยอดขาย");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(45, 90, 55));
        title.setGravity(Gravity.CENTER);

        root.addView(title, matchWrap());

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 16, 0, 0);

        root.addView(content, matchWrap());

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void showMenu() {
        content.removeAllViews();

        addButton("รายงานวันนี้",
                () -> showSummary("DAY", "ยอดขายวันนี้"));

        addButton("รายงาน 7 วันล่าสุด",
                () -> showSummary("WEEK", "ยอดขาย 7 วันล่าสุด"));

        addButton("รายงานเดือนนี้",
                () -> showSummary("MONTH", "ยอดขายเดือนนี้"));

        addButton("รายงานยอดขายทั้งหมด",
                () -> showSummary("ALL", "ยอดขายทั้งหมด"));

        addButton("รายงานตามช่วงวันที่",
                this::showDateRangeForm);

        addButton("สินค้า 20 อันดับขายดี",
                () -> showProductRanking(true));

        addButton("สินค้า 20 อันดับขายช้า",
                () -> showProductRanking(false));
    }

    private void showSummary(String period, String heading) {
        content.removeAllViews();
        content.addView(makeText(heading), matchWrap());

        Cursor cursor = null;

        try {
            cursor = reportRepository.getSalesSummary(period);

            if (cursor == null || !cursor.moveToFirst()) {
                content.addView(
                        makeText("ไม่มีข้อมูลรายงาน"),
                        matchWrap()
                );
            } else {
                int billCount = readInt(cursor, "bill_count");
                double subtotal = readDouble(cursor, "subtotal");
                double serviceFee = readDouble(cursor, "service_fee");
                double total = readDouble(cursor, "total_sales");

                content.addView(
                        makeText("จำนวนบิลที่ชำระแล้ว: " + billCount + " บิล"),
                        matchWrap()
                );

                content.addView(
                        makeText("ยอดรวมสินค้า: " + money(subtotal) + " บาท"),
                        matchWrap()
                );

                content.addView(
                        makeText("ค่าบริการขายเชื่อ: " + money(serviceFee) + " บาท"),
                        matchWrap()
                );

                content.addView(
                        makeText("ยอดรวมสุทธิ: " + money(total) + " บาท"),
                        matchWrap()
                );
            }
        } catch (Exception e) {
            showError("อ่านรายงานไม่สำเร็จ", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        addButton("กลับเมนูรายงาน", this::showMenu);
    }

    private void showDateRangeForm() {
        content.removeAllViews();

        content.addView(
                makeText("กำหนดช่วงวันที่"),
                matchWrap()
        );

        EditText startDate = makeInput("วันที่เริ่มต้น yyyy-MM-dd");
        EditText endDate = makeInput("วันที่สิ้นสุด yyyy-MM-dd");

        addButton("ดูรายงาน", () -> {
            String start = startDate.getText().toString().trim();
            String end = endDate.getText().toString().trim();

            if (!isValidDate(start) || !isValidDate(end)) {
                toast("กรุณากรอกวันที่ให้ถูกต้อง รูปแบบ yyyy-MM-dd");
                return;
            }

            if (start.compareTo(end) > 0) {
                toast("วันที่เริ่มต้นต้องไม่เกินวันที่สิ้นสุด");
                return;
            }

            showRangeSummary(start, end);
        });

        addButton("กลับเมนูรายงาน", this::showMenu);
    }

    private void showRangeSummary(String start, String end) {
        content.removeAllViews();

        content.addView(
                makeText("รายงานวันที่ " + start + " ถึง " + end),
                matchWrap()
        );

        Cursor cursor = null;

        try {
            cursor = reportRepository.getSalesSummaryByDateRange(
                    start,
                    end
            );

            if (cursor == null || !cursor.moveToFirst()) {
                content.addView(
                        makeText("ไม่มีข้อมูลในช่วงวันที่นี้"),
                        matchWrap()
                );
            } else {
                content.addView(
                        makeText("จำนวนบิล: "
                                + readInt(cursor, "bill_count")),
                        matchWrap()
                );

                content.addView(
                        makeText("ยอดสินค้า: "
                                + money(readDouble(cursor, "subtotal"))
                                + " บาท"),
                        matchWrap()
                );

                content.addView(
                        makeText("ค่าบริการ: "
                                + money(readDouble(cursor, "service_fee"))
                                + " บาท"),
                        matchWrap()
                );

                content.addView(
                        makeText("ยอดรวมสุทธิ: "
                                + money(readDouble(cursor, "total_sales"))
                                + " บาท"),
                        matchWrap()
                );
            }
        } catch (Exception e) {
            showError("อ่านรายงานช่วงวันที่ไม่สำเร็จ", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        addButton("กลับเมนูรายงาน", this::showMenu);
    }

    private void showProductRanking(boolean topSelling) {
        content.removeAllViews();

        String heading = topSelling
                ? "สินค้า 20 อันดับขายดี"
                : "สินค้า 20 อันดับขายช้า";

        content.addView(makeText(heading), matchWrap());

        content.addView(
                makeText("ช่วงข้อมูล: 30 วันล่าสุด"),
                matchWrap()
        );

        Cursor cursor = null;

        try {
            cursor = topSelling
                    ? reportRepository.getTopSellingProducts()
                    : reportRepository.getSlowSellingProducts();

            if (cursor == null || !cursor.moveToFirst()) {
                content.addView(
                        makeText("ไม่พบข้อมูลการขายในช่วงนี้"),
                        matchWrap()
                );
            } else {
                int rank = 1;

                do {
                    String name = readString(cursor, "product_name");
                    String barcode = readString(cursor, "barcode");
                    String unit = readString(cursor, "unit");
                    int quantity = readInt(cursor, "quantity_sold");
                    double salesValue = readDouble(cursor, "sales_value");

                    String detail = rank + ". " + name
                            + "\nบาร์โค้ด: " + barcode
                            + "\nจำนวนขาย: " + quantity + " " + unit
                            + "\nมูลค่าขาย: " + money(salesValue) + " บาท";

                    content.addView(makeText(detail), matchWrap());

                    rank++;

                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            showError("อ่านอันดับสินค้าไม่สำเร็จ", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        addButton("กลับเมนูรายงาน", this::showMenu);
    }

    private EditText makeInput(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        content.addView(input, matchWrap());
        return input;
    }

    private TextView makeText(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(16);
        text.setTextColor(Color.DKGRAY);
        text.setPadding(8, 12, 8, 12);
        return text;
    }

    private void addButton(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        content.addView(button, matchWrap());
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private boolean isValidDate(String value) {
        if (value == null
                || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return false;
        }

        try {
            SimpleDateFormat format = new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
            );

            format.setLenient(false);

            Date parsed = format.parse(value);

            return parsed != null && format.format(parsed).equals(value);

        } catch (Exception e) {
            return false;
        }
    }

    private int readInt(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index)
                ? 0 : cursor.getInt(index);
    }

    private double readDouble(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index)
                ? 0.0 : cursor.getDouble(index);
    }

    private String readString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index)
                ? "-" : cursor.getString(index);
    }

    private String money(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    private void toast(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private void showError(String message, Exception e) {
        android.util.Log.e("ReportScreen", message, e);
        toast(message);
    }
}
