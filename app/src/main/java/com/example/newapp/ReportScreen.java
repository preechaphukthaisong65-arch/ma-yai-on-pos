package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class ReportScreen extends Activity {
    private static final TimeZone THAILAND = TimeZone.getTimeZone("Asia/Bangkok");
    private DataHub hub;
    private String storeId;
    private LinearLayout root;
    private EditText dayInput, monthInput, startInput, endInput;

    private static final class ProductSales {
        String key;
        String name;
        String barcode;
        String unit;
        double quantity;

        ProductSales(String key, String name, String barcode, String unit) {
            this.key = key;
            this.name = name;
            this.barcode = barcode;
            this.unit = unit;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        hub = DataHub.getInstance(getApplicationContext());
        storeId = StoreIdentity.get(this);
        render();
    }

    private void render() {
        root = POSUi.page(this, "รายงานยอดขาย");
        String day = formatDate(System.currentTimeMillis());
        String month = new SimpleDateFormat("yyyy-MM", Locale.US) {{
            setTimeZone(THAILAND);
        }}.format(new Date());
        dayInput = POSUi.input(this, root, "วันที่ YYYY-MM-DD");
        dayInput.setText(day);
        POSUi.button(this, root, "รายงานยอดขายรายวัน", () -> showDaily(dayInput.getText().toString().trim()));
        POSUi.button(this, root, "รายงานยอดขายรายสัปดาห์ (จันทร์–อาทิตย์)", this::showWeekly);
        monthInput = POSUi.input(this, root, "เดือน YYYY-MM");
        monthInput.setText(month);
        POSUi.button(this, root, "รายงานยอดขายรายเดือน", () -> showMonthly(monthInput.getText().toString().trim()));
        startInput = POSUi.input(this, root, "วันที่เริ่มต้น YYYY-MM-DD");
        startInput.setText(day);
        endInput = POSUi.input(this, root, "วันที่สิ้นสุด YYYY-MM-DD");
        endInput.setText(day);
        POSUi.button(this, root, "รายงานช่วงวันที่กำหนด", this::showCustom);
        POSUi.button(this, root, "สินค้าขายดี 20 อันดับ (30 วัน)", () -> showTopBottom(true));
        POSUi.button(this, root, "สินค้าขายน้อย 20 อันดับ (30 วัน)", () -> showTopBottom(false));
        POSUi.button(this, root, "กลับหน้าหลัก", this::finish);
        root.addView(POSUi.text(this,
                "รายงานแยกยอดขายตามวันที่ขาย ยอดค้างชำระตามวันที่ขาย และเงินรับชำระหนี้ตามวันที่รับเงินจริง "
                        + "ใช้เวลาไทย (UTC+7) และเป็นการอ่านข้อมูลเท่านั้น", 13));
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(THAILAND);
        return f.format(new Date(timestamp));
    }

    private Date parseExact(String value, String pattern) {
        try {
            SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
            f.setLenient(false);
            f.setTimeZone(THAILAND);
            ParsePosition position = new ParsePosition(0);
            Date parsed = f.parse(value, position);
            if (parsed == null || position.getIndex() != value.length()) return null;
            return parsed;
        } catch (Exception ignored) {
            return null;
        }
    }

    private long startOfDay(Date date) {
        Calendar c = Calendar.getInstance(THAILAND, Locale.US);
        c.setTime(date);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void showDaily(String day) {
        Date parsed = parseExact(day, "yyyy-MM-dd");
        if (parsed == null) {
            root.addView(POSUi.text(this, "วันที่ไม่ถูกต้อง ใช้รูปแบบ YYYY-MM-DD", 14));
            return;
        }
        long start = startOfDay(parsed);
        Calendar c = Calendar.getInstance(THAILAND, Locale.US);
        c.setTimeInMillis(start);
        c.add(Calendar.DAY_OF_MONTH, 1);
        showResult("รายวัน " + day, start, c.getTimeInMillis());
    }

    private void showWeekly() {
        Calendar c = Calendar.getInstance(THAILAND, Locale.US);
        c.setTimeInMillis(System.currentTimeMillis());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int daysSinceMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        c.add(Calendar.DAY_OF_MONTH, -daysSinceMonday);
        long start = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_MONTH, 7);
        long end = c.getTimeInMillis();
        showResult("รายสัปดาห์ " + formatDate(start) + " ถึง " + formatDate(end - 1L), start, end);
    }

    private void showMonthly(String month) {
        Date parsed = parseExact(month, "yyyy-MM");
        if (parsed == null) {
            root.addView(POSUi.text(this, "เดือนไม่ถูกต้อง ใช้รูปแบบ YYYY-MM", 14));
            return;
        }
        Calendar c = Calendar.getInstance(THAILAND, Locale.US);
        c.setTime(parsed);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        c.add(Calendar.MONTH, 1);
        showResult("รายเดือน " + month, start, c.getTimeInMillis());
    }

    private void showCustom() {
        String startText = POSUi.value(startInput).trim();
        String endText = POSUi.value(endInput).trim();
        Date startDate = parseExact(startText, "yyyy-MM-dd");
        Date endDate = parseExact(endText, "yyyy-MM-dd");
        if (startDate == null || endDate == null) {
            root.addView(POSUi.text(this, "กรุณาระบุวันที่เริ่มต้นและสิ้นสุดเป็น YYYY-MM-DD", 14));
            return;
        }
        long start = startOfDay(startDate);
        long endStart = startOfDay(endDate);
        if (endStart < start) {
            root.addView(POSUi.text(this, "วันที่สิ้นสุดต้องไม่ก่อนวันที่เริ่มต้น", 14));
            return;
        }
        Calendar c = Calendar.getInstance(THAILAND, Locale.US);
        c.setTimeInMillis(endStart);
        c.add(Calendar.DAY_OF_MONTH, 1);
        showResult("รายงาน " + startText + " ถึง " + endText, start, c.getTimeInMillis());
    }

    private void showResult(String title, long start, long end) {
        Map<String, JSONObject> billsById = new LinkedHashMap<>();
        for (JSONObject bill : hub.getRecordsByType(storeId, "sale_bill")) {
            String id = bill.optString("recordId", "").trim();
            JSONObject p = bill.optJSONObject("payload");
            if (!id.isEmpty() && p != null) billsById.put(id, p);
        }

        // Use retained snapshots only for bills no longer present as active bill records.
        for (JSONObject history : hub.getRecordsByType(storeId, "sale_history")) {
            JSONObject hp = history.optJSONObject("payload");
            if (hp == null) continue;
            String billId = hp.optString("billId", "").trim();
            if (billId.isEmpty()) {
                String recordId = history.optString("recordId", "");
                if (recordId.startsWith("sale_history:")) billId = recordId.substring("sale_history:".length());
            }
            if (billId.isEmpty() || billsById.containsKey(billId)) continue;
            JSONObject snapshot = hp.optJSONObject("saleSnapshot");
            try {
                JSONObject p = snapshot == null ? new JSONObject() : new JSONObject(snapshot.toString());
                if (!p.has("createdAt")) p.put("createdAt", hp.optLong("createdAt", 0L));
                if (!p.has("total")) p.put("total", hp.optDouble("total", 0d));
                if (!p.has("paymentType")) p.put("paymentType", hp.optString("paymentType", ""));
                if (!p.has("paymentStatus")) p.put("paymentStatus", hp.optString("paymentStatus", ""));
                billsById.put(billId, p);
            } catch (Exception ignored) {
                // Skip malformed snapshots rather than inventing financial values.
            }
        }

        // Payments are the authoritative evidence that a historical credit bill was settled.
        java.util.Set<String> paidBillIds = new java.util.HashSet<>();
        for (JSONObject paymentRecord : hub.getRecordsByType(storeId, "credit_payment")) {
            JSONObject p = paymentRecord.optJSONObject("payload");
            if (p == null) continue;
            JSONArray allocations = p.optJSONArray("allocations");
            if (allocations != null) {
                for (int i = 0; i < allocations.length(); i++) {
                    JSONObject allocation = allocations.optJSONObject(i);
                    if (allocation == null) continue;
                    String id = allocation.optString("billId", "").trim();
                    double remaining = allocation.optDouble("remainingBalance", Double.NaN);
                    if (!id.isEmpty() && ("จ่ายแล้ว".equals(allocation.optString("paymentStatus", ""))
                            || (Double.isFinite(remaining) && remaining <= 0.009d))) {
                        paidBillIds.add(id);
                    }
                }
            } else if (!p.optBoolean("isPartialPayment", false)) {
                JSONArray ids = p.optJSONArray("billIds");
                if (ids == null) continue;
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.optString(i, "").trim();
                    if (!id.isEmpty()) paidBillIds.add(id);
                }
            }
        }

        double totalSales = 0d, cashSales = 0d, creditSales = 0d;
        double paidSales = 0d, pendingSales = 0d, outstanding = 0d, creditCollections = 0d;
        int billCount = 0, paidCount = 0, pendingCount = 0;
        for (Map.Entry<String, JSONObject> entry : billsById.entrySet()) {
            JSONObject p = entry.getValue();
            long created = p.optLong("createdAt", 0L);
            if (created < start || created >= end) continue;
            double amount = Math.max(0d, p.optDouble("total", 0d));
            String type = p.optString("paymentType", "");
            String status = p.optString("paymentStatus", "");
            if ("credit".equals(type) && paidBillIds.contains(entry.getKey())) status = "จ่ายแล้ว";
            billCount++;
            totalSales += amount;
            if ("cash".equals(type)) cashSales += amount;
            if ("credit".equals(type)) creditSales += amount;
            if ("จ่ายแล้ว".equals(status) || "cash".equals(type)) {
                paidCount++;
                paidSales += amount;
            } else if ("ค้างชำระ".equals(status)) {
                pendingCount++;
                pendingSales += amount;
                if ("credit".equals(type)) {
                    outstanding += Math.max(0d, p.optDouble("remainingBalance", amount));
                }
            }
        }

        // Debt collections use actual payment date, not the original sale date.
        for (JSONObject paymentRecord : hub.getRecordsByType(storeId, "credit_payment")) {
            JSONObject p = paymentRecord.optJSONObject("payload");
            if (p == null) continue;
            long paidAt = p.optLong("paidAt", 0L);
            if (paidAt >= start && paidAt < end) creditCollections += Math.max(0d, p.optDouble("amount", 0d));
        }

        root.addView(POSUi.text(this, title
                + "\nจำนวนบิลขาย: " + billCount
                + "\nยอดขายรวมตามวันที่ขาย: " + money(totalSales) + " บาท"
                + "\nยอดขายเงินสด: " + money(cashSales) + " บาท"
                + "\nยอดขายเชื่อ: " + money(creditSales) + " บาท"
                + "\nบิลชำระแล้ว: " + paidCount + " บิล (" + money(paidSales) + " บาท)"
                + "\nบิลค้างชำระ: " + pendingCount + " บิล (" + money(pendingSales) + " บาท)"
                + "\nยอดหนี้คงเหลือของบิลที่ขายในช่วงนี้: " + money(outstanding) + " บาท"
                + "\nรับชำระหนี้ตามวันที่รับเงินจริง: " + money(creditCollections) + " บาท"
                + "\nรวมเงินสดจากการขายและรับชำระหนี้: " + money(cashSales + creditCollections) + " บาท", 16));
    }

    private void showTopBottom(boolean top) {
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance(THAILAND, Locale.US);
        c.setTimeInMillis(now);
        c.add(Calendar.DAY_OF_MONTH, -30);
        long cutoff = c.getTimeInMillis();
        Map<String, String> currentUnits = new HashMap<>();
        for (JSONObject product : hub.getRecordsByType(storeId, "product")) {
            JSONObject payload = product.optJSONObject("payload");
            if (payload == null) continue;
            String id = product.optString("recordId", "").trim();
            if (!id.isEmpty()) currentUnits.put(id, payload.optString("unit", "หน่วย"));
        }
        Map<String, ProductSales> totals = new LinkedHashMap<>();
        for (JSONObject record : hub.getRecordsByType(storeId, "stock_history")) {
            JSONObject p = record.optJSONObject("payload");
            if (p == null || !"ขายสินค้า".equals(p.optString("transactionType", ""))) continue;
            long changedAt = p.optLong("changedAt", 0L);
            if (changedAt < cutoff || changedAt > now) continue;
            double sold = Math.max(0d, -p.optDouble("delta", 0d));
            if (sold <= 0d) continue;
            String productId = p.optString("productRecordId", "").trim();
            String barcode = p.optString("barcode", "").trim();
            String name = p.optString("productName", "ไม่ระบุชื่อสินค้า").trim();
            String key = !productId.isEmpty() ? productId : (!barcode.isEmpty() ? barcode : name);
            ProductSales entry = totals.get(key);
            if (entry == null) {
                entry = new ProductSales(key, name, barcode, currentUnits.getOrDefault(productId, "หน่วย"));
                totals.put(key, entry);
            }
            entry.quantity += sold;
        }
        List<ProductSales> ranking = new ArrayList<>(totals.values());
        ranking.sort((a, b) -> {
            int compare = Double.compare(a.quantity, b.quantity);
            if (top) compare = -compare;
            if (compare != 0) return compare;
            return a.name.compareToIgnoreCase(b.name);
        });
        root.addView(POSUi.text(this, (top ? "สินค้าขายดี 20 อันดับ" : "สินค้าขายน้อย 20 อันดับ")
                + "\nช่วง 30 วันย้อนหลัง (นับตามจำนวนหน่วยที่ขายจริง)", 18));
        if (ranking.isEmpty()) {
            root.addView(POSUi.text(this, "ไม่พบประวัติการขายสินค้าในช่วง 30 วัน", 15));
            return;
        }
        int count = Math.min(20, ranking.size());
        for (int i = 0; i < count; i++) {
            ProductSales item = ranking.get(i);
            root.addView(POSUi.text(this, (i + 1) + ". " + item.name
                    + "\nขายได้ " + formatQuantity(item.quantity) + " หน่วย"
                    + (item.barcode.isEmpty() ? "" : "\nบาร์โค้ด: " + item.barcode), 15));
        }
        root.addView(POSUi.text(this,
                "หมายเหตุ: จัดอันดับเฉพาะสินค้าที่มียอดขายอย่างน้อย 1 หน่วย เพราะยังไม่มีข้อกำหนดให้รวมสินค้าที่ไม่เคยขาย", 12));
    }

    private String formatQuantity(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001d) return Long.toString(Math.round(value));
        return String.format(Locale.US, "%.3f", value);
    }

    private String money(double value) { return String.format(Locale.US, "%.2f", value); }
}