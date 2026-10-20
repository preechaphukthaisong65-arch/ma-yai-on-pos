package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryScreen extends Activity {
    private DataHub hub;
    private String storeId;
    private LinearLayout root, content;
    private String viewMode = "bills";
    private String sortMode = "debt";
    private String selectedCustomerId = "";
    private String selectedCustomerName = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        hub = DataHub.getInstance(getApplicationContext());
        storeId = StoreIdentity.get(this);
        showHome();
    }

    private List<JSONObject> bills() {
        List<JSONObject> result = hub.getRecordsByType(storeId, "sale_bill");
        result.sort((a,b) -> Long.compare(dateOf(b), dateOf(a)));
        return result;
    }

    private long dateOf(JSONObject record) {
        JSONObject p = record == null ? null : record.optJSONObject("payload");
        if (p == null) return 0L;
        long date = p.optLong("paidAt", 0L);
        if (date <= 0) date = p.optLong("createdAt", 0L);
        return date;
    }

    private String dateText(long time) {
        return time <= 0 ? "ไม่ระบุวันที่" : new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(time));
    }

    private void showHome() {
        viewMode = "bills";
        root = POSUi.page(this, "ประวัติการขาย");
        POSUi.button(this, root, "บิลทั้งหมด", () -> showBills("all"));
        POSUi.button(this, root, "จ่ายแล้ว", () -> showBills("paid"));
        POSUi.button(this, root, "ค้างชำระ", () -> showBills("unpaid"));
        POSUi.button(this, root, "บัญชีลูกค้าเชื่อ ทั้งหมด", this::showCustomers);
        POSUi.button(this, root, "กลับหน้าหลัก", this::finish);
        root.addView(POSUi.text(this, "บิลที่จ่ายแล้วแสดงตามวันที่ขาย/ชำระที่บันทึกไว้ ส่วนบิลค้างชำระจะยังแสดงจนกว่าจะชำระ", 13));
    }

    private void showBills(String filter) {
        root = POSUi.page(this, filter.equals("paid") ? "ประวัติ — จ่ายแล้ว" :
                filter.equals("unpaid") ? "ประวัติ — ค้างชำระ" : "ประวัติ — บิลทั้งหมด");
        POSUi.button(this, root, "กลับเมนูประวัติ", this::showHome);
        int count = 0;
        for (JSONObject bill : bills()) {
            JSONObject p = bill.optJSONObject("payload");
            if (p == null) continue;
            String status = p.optString("paymentStatus", "ไม่ระบุสถานะ");
            if (filter.equals("paid") && !"จ่ายแล้ว".equals(status)) continue;
            if (filter.equals("unpaid") && !"ค้างชำระ".equals(status)) continue;
            count++;
            String name = p.optString("customerName", "");
            String label = "จ่ายแล้ว".equals(status) ? "จ่ายแล้ว" : "ค้างชำระ".equals(status) ? "ค้างชำระ" : status;
            root.addView(POSUi.text(this, "บิล: " + bill.optString("recordId") + "\nวันที่: "
                    + dateText(dateOf(bill)) + "\nสถานะ: " + label + "\nลูกค้า: "
                    + (name.isEmpty() ? "ไม่ระบุ" : name) + "\nยอด: "
                    + money(p.optDouble("total", 0d)) + " บาท", 15));
            POSUi.button(this, root, "ดูรายละเอียดบิล", () -> showBillDetail(bill));
        }
        if (count == 0) root.addView(POSUi.text(this, "ไม่พบข้อมูลบิลในหมวดนี้", 15));
    }

    private void showBillDetail(JSONObject bill) {
        JSONObject p = bill.optJSONObject("payload");
        if (p == null) return;
        root = POSUi.page(this, "รายละเอียดบิล");
        root.addView(POSUi.text(this, "เลขบิล: " + bill.optString("recordId"), 16));
        root.addView(POSUi.text(this, "วันที่: " + dateText(dateOf(bill)), 15));
        root.addView(POSUi.text(this, "สถานะ: " + p.optString("paymentStatus", "ไม่ระบุสถานะ"), 15));
        root.addView(POSUi.text(this, "ประเภท: " + p.optString("paymentType", "ไม่ระบุ"), 15));
        root.addView(POSUi.text(this, "ยอดรวม: " + money(p.optDouble("total", 0d)) + " บาท", 18));
        root.addView(POSUi.text(this, "วันที่ชำระ: " + dateText(p.optLong("paidAt", 0L)), 15));
        org.json.JSONArray items = p.optJSONArray("items");
        if (items != null) for (int i=0;i<items.length();i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) root.addView(POSUi.text(this, item.optString("name", "สินค้า")
                    + " × " + item.optDouble("quantity", 0d) + " = "
                    + money(item.optDouble("lineTotal", 0d)) + " บาท", 14));
        }
        POSUi.button(this, root, "กลับรายการบิล", () -> showBills("all"));
    }

    private void showCustomers() {
        viewMode = "customers";
        root = POSUi.page(this, "บัญชีลูกค้าเชื่อ ทั้งหมด");
        POSUi.button(this, root, "เรียงยอดหนี้มากไปน้อย", () -> { sortMode = "debt"; showCustomers(); });
        POSUi.button(this, root, "เรียงบิลค้างเก่าที่สุดก่อน", () -> { sortMode = "oldest"; showCustomers(); });
        POSUi.button(this, root, "กลับเมนูประวัติ", this::showHome);
        class CustomerDebt {
            String id, name;
            double amount;
            long oldest;
            CustomerDebt(String i, String n, double a, long o) { id=i; name=n; amount=a; oldest=o; }
        }
        java.util.Map<String, CustomerDebt> map = new java.util.LinkedHashMap<>();
        for (JSONObject bill : bills()) {
            JSONObject p = bill.optJSONObject("payload");
            if (p == null || !"credit".equals(p.optString("paymentType"))
                    || !"ค้างชำระ".equals(p.optString("paymentStatus"))) continue;
            String id = p.optString("customerRecordId", "");
            String name = p.optString("customerName", "ไม่ระบุชื่อลูกค้า").trim();
            if (id.isEmpty()) id = "name:" + name;
            CustomerDebt d = map.get(id);
            double amount = p.optDouble("remainingBalance", p.optDouble("total", 0d));
            long created = p.optLong("createdAt", 0L);
            if (d == null) { d = new CustomerDebt(id, name, 0d, created); map.put(id,d); }
            d.amount += Math.max(0d, amount);
            if (created > 0 && (d.oldest == 0 || created < d.oldest)) d.oldest = created;
        }
        List<CustomerDebt> list = new ArrayList<>(map.values());
        if ("oldest".equals(sortMode)) list.sort((a,b) -> compareOldest(a.oldest,b.oldest));
        else list.sort((a,b) -> Double.compare(b.amount,a.amount));
        if (list.isEmpty()) root.addView(POSUi.text(this, "ไม่มีลูกค้าที่มียอดค้างชำระ", 15));
        for (CustomerDebt d : list) {
            // List view deliberately contains only customer name and total debt.
            POSUi.button(this, root, d.name + " — ค้าง " + money(d.amount) + " บาท",
                    () -> showCustomerBills(d.id, d.name));
        }
    }

    private int compareOldest(long a, long b) {
        if (a <= 0 && b <= 0) return 0;
        if (a <= 0) return 1;
        if (b <= 0) return -1;
        return Long.compare(a,b);
    }

    private void showCustomerBills(String customerId, String customerName) {
        selectedCustomerId = customerId;
        selectedCustomerName = customerName;
        root = POSUi.page(this, "บิลค้าง — " + customerName);
        POSUi.button(this, root, "กลับรายชื่อลูกค้า", this::showCustomers);
        int count = 0;
        for (JSONObject bill : bills()) {
            JSONObject p = bill.optJSONObject("payload");
            if (p == null || !"credit".equals(p.optString("paymentType"))
                    || !"ค้างชำระ".equals(p.optString("paymentStatus"))) continue;
            String id = p.optString("customerRecordId", "");
            String name = p.optString("customerName", "ไม่ระบุชื่อลูกค้า").trim();
            if (!(customerId.equals(id) || (customerId.startsWith("name:") && customerId.equals("name:" + name)))) continue;
            count++;
            root.addView(POSUi.text(this, "บิล: " + bill.optString("recordId") + "\nวันที่: "
                    + dateText(p.optLong("createdAt", 0L)) + "\nยอดค้าง: "
                    + money(p.optDouble("remainingBalance", p.optDouble("total", 0d))) + " บาท", 15));
        }
        if (count == 0) root.addView(POSUi.text(this, "ไม่พบรายการค้างชำระ", 15));
        // This detail screen is intentionally read-only; payment remains in the sales flow.
    }

    private String money(double amount) { return String.format(Locale.US, "%.2f", amount); }
}