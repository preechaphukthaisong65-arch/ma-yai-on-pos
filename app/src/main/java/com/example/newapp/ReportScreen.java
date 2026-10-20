package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Calendar;

public class ReportScreen extends Activity {
    private DataHub hub;
    private String storeId;
    private LinearLayout root;
    private EditText dayInput, monthInput;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        hub = DataHub.getInstance(getApplicationContext());
        storeId = StoreIdentity.get(this);
        render();
    }

    private void render() {
        root = POSUi.page(this, "รายงานยอดขาย");
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String month = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
        dayInput = POSUi.input(this, root, "วันที่ YYYY-MM-DD");
        dayInput.setText(day);
        POSUi.button(this, root, "รายงานยอดขายรายวัน", () -> showDaily(dayInput.getText().toString().trim()));
        monthInput = POSUi.input(this, root, "เดือน YYYY-MM");
        monthInput.setText(month);
        POSUi.button(this, root, "รายงานยอดขายรายเดือน", () -> showMonthly(monthInput.getText().toString().trim()));
        POSUi.button(this, root, "กลับหน้าหลัก", this::finish);
        root.addView(POSUi.text(this, "คำนวณจากบิลขายที่บันทึกอยู่ในฐานข้อมูลกลาง ไม่รวมข้อมูลที่ไม่มีวันที่ตรงกับช่วงรายงาน", 13));
    }

    private void showDaily(String day) {
        if (!day.matches("\\d{4}-\\d{2}-\\d{2}")) {
            root.addView(POSUi.text(this, "รูปแบบวันที่ไม่ถูกต้อง ใช้ YYYY-MM-DD", 14));
            return;
        }
        long start, end;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            f.setLenient(false);
            Date d = f.parse(day);
            Calendar c = Calendar.getInstance(); c.setTime(d);
            c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
            start = c.getTimeInMillis(); c.add(Calendar.DAY_OF_MONTH,1); end = c.getTimeInMillis();
        } catch (Exception e) { root.addView(POSUi.text(this, "วันที่ไม่ถูกต้อง", 14)); return; }
        showResult("รายวัน " + day, start, end);
    }

    private void showMonthly(String month) {
        if (!month.matches("\\d{4}-\\d{2}")) {
            root.addView(POSUi.text(this, "รูปแบบเดือนไม่ถูกต้อง ใช้ YYYY-MM", 14));
            return;
        }
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM", Locale.US);
            f.setLenient(false);
            Date d = f.parse(month);
            Calendar c = Calendar.getInstance(); c.setTime(d);
            c.set(Calendar.DAY_OF_MONTH,1); c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0);
            c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
            long start = c.getTimeInMillis(); c.add(Calendar.MONTH,1); long end = c.getTimeInMillis();
            showResult("รายเดือน " + month, start, end);
        } catch (Exception e) { root.addView(POSUi.text(this, "เดือนไม่ถูกต้อง", 14)); }
    }

    private void showResult(String title, long start, long end) {
        double totalSales = 0d, cashSales = 0d, creditCollections = 0d, outstanding = 0d;
        int count = 0;
        for (JSONObject bill : hub.getRecordsByType(storeId, "sale_bill")) {
            JSONObject p = bill.optJSONObject("payload");
            if (p == null) continue;
            long created = p.optLong("createdAt", 0L);
            if (created < start || created >= end) continue;
            double amount = Math.max(0d, p.optDouble("total", 0d));
            totalSales += amount;
            count++;
            if ("cash".equals(p.optString("paymentType"))) {
                cashSales += amount;
            } else if ("credit".equals(p.optString("paymentType"))
                    && "ค้างชำระ".equals(p.optString("paymentStatus"))) {
                outstanding += Math.max(0d, p.optDouble("remainingBalance", amount));
            }
        }
        // Credit collections are counted by actual payment date, not by the original sale date.
        for (JSONObject paymentRecord : hub.getRecordsByType(storeId, "credit_payment")) {
            JSONObject p = paymentRecord.optJSONObject("payload");
            if (p == null) continue;
            long paidAt = p.optLong("paidAt", 0L);
            if (paidAt >= start && paidAt < end) {
                creditCollections += Math.max(0d, p.optDouble("amount", 0d));
            }
        }
        double collections = cashSales + creditCollections;
        root.addView(POSUi.text(this, title + "\nจำนวนบิลขาย: " + count
                + "\nยอดขายตามวันที่ขาย: " + money(totalSales) + " บาท"
                + "\nรับเงินสดจากการขาย: " + money(cashSales) + " บาท"
                + "\nรับชำระหนี้ตามวันที่รับเงินจริง: " + money(creditCollections) + " บาท"
                + "\nรวมรับเงินในช่วงนี้: " + money(collections) + " บาท"
                + "\nยอดหนี้คงเหลือของบิลในช่วงขายนี้: " + money(outstanding) + " บาท", 17));
    }

    private String money(double value) { return String.format(Locale.US, "%.2f", value); }
}