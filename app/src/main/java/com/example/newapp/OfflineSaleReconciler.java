package com.example.newapp;

import android.content.Context;

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.ServerSaleService;
import com.example.newapp.datahub.StoreIdentity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays offline sale bills as idempotent server transactions in creation order.
 * Generic record-by-record backup is deliberately paused while these bills exist.
 */
public final class OfflineSaleReconciler {
    public interface Callback {
        void onComplete(boolean success, String message);
    }

    private final Context appContext;
    private final DataHub hub;

    public OfflineSaleReconciler(Context context) {
        appContext = context.getApplicationContext();
        hub = DataHub.getInstance(appContext);
    }

    public void reconcile(String storeId, Callback callback) {
        if (storeId == null || storeId.trim().isEmpty()) {
            callback.onComplete(false, "ไม่พบรหัสร้านสำหรับซิงก์รายการออฟไลน์");
            return;
        }
        List<JSONObject> pending = new ArrayList<>();
        for (JSONObject record : hub.getRecordsByType(storeId.trim(), "sale_bill")) {
            JSONObject payload = record.optJSONObject("payload");
            if (payload != null && payload.optBoolean("offlinePending", false)) {
                pending.add(record);
            }
        }
        pending.sort(Comparator.comparingLong(r -> {
            JSONObject p = r.optJSONObject("payload");
            return p == null ? 0L : p.optLong("createdAt", 0L);
        }));
        if (pending.isEmpty()) {
            callback.onComplete(true, "ไม่มีรายการขายออฟไลน์ค้างอยู่");
            return;
        }
        LinkedHashMap<String, JSONObject> committed = new LinkedHashMap<>();
        commitAt(storeId.trim(), pending, 0, committed, callback);
    }

    private void commitAt(String storeId, List<JSONObject> pending, int index,
            LinkedHashMap<String, JSONObject> committed, Callback callback) {
        if (index >= pending.size()) {
            // Product snapshots from the last response are authoritative. Their
            // expectedRevision is a local SQLite compare-and-set guard, not a
            // server revision supplied by the caller.
            for (JSONObject record : committed.values()) {
                if (!"product".equals(record.optString("dataType", ""))) continue;
                JSONObject local = hub.getRecord(storeId, record.optString("recordId", ""));
                if (local == null) {
                    callback.onComplete(false, "ไม่พบสินค้าในเครื่องหลังซิงก์ จึงไม่เขียนทับข้อมูล");
                    return;
                }
                try {
                    record.put("expectedRevision", local.optLong("revision", 0L));
                } catch (Exception error) {
                    callback.onComplete(false, "เตรียมข้อมูลสต๊อกหลังซิงก์ไม่สำเร็จ");
                    return;
                }
            }
            boolean applied = new StockSalesRepository(appContext)
                    .applyServerCommittedSaleRecords(new ArrayList<>(committed.values()));
            callback.onComplete(applied, applied
                    ? "ซิงก์รายการขายออฟไลน์และตรวจสอบสต๊อกจากเซิร์ฟเวอร์แล้ว"
                    : "เซิร์ฟเวอร์ยืนยันรายการแล้ว แต่ข้อมูลในเครื่องมี revision ขัดแย้ง ข้อมูลเดิมถูกเก็บไว้");
            return;
        }

        JSONObject record = pending.get(index);
        JSONObject bill = record.optJSONObject("payload");
        if (bill == null) {
            callback.onComplete(false, "พบบิลออฟไลน์ที่ไม่มีรายละเอียด จึงหยุดซิงก์เพื่อรักษาข้อมูล");
            return;
        }
        String billId = record.optString("recordId", "").trim();
        String paymentType = bill.optString("paymentType", "");
        JSONArray rawItems = bill.optJSONArray("items");
        if (billId.isEmpty() || rawItems == null || rawItems.length() == 0
                || !("cash".equals(paymentType) || "credit".equals(paymentType))) {
            callback.onComplete(false, "ข้อมูลบิลออฟไลน์ไม่ครบถ้วน จึงหยุดซิงก์เพื่อรักษาข้อมูล");
            return;
        }
        List<JSONObject> items = new ArrayList<>();
        for (int i = 0; i < rawItems.length(); i++) {
            JSONObject line = rawItems.optJSONObject(i);
            if (line == null || line.optString("productRecordId", "").trim().isEmpty()
                    || !line.has("localRevision") || line.optLong("localRevision", -1L) < 0L
                    || !Double.isFinite(line.optDouble("quantity", Double.NaN))
                    || line.optDouble("quantity", 0d) <= 0d) {
                callback.onComplete(false, "ข้อมูลสินค้าในบิลออฟไลน์ไม่ครบ จึงหยุดซิงก์");
                return;
            }
            items.add(line);
        }

        String customerId = bill.optString("customerRecordId", "");
        JSONObject customer = customerId.isEmpty() ? null : hub.getRecord(storeId, customerId);
        if ("credit".equals(paymentType) && customer == null) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("firstName", bill.optString("customerFirstName", ""));
                payload.put("lastName", bill.optString("customerLastName", ""));
                payload.put("createdAt", bill.optLong("createdAt", System.currentTimeMillis()));
                customer = new JSONObject();
                customer.put("recordId", customerId);
                customer.put("dataType", "credit_customer");
                customer.put("payload", payload);
            } catch (Exception error) {
                callback.onComplete(false, "กู้คืนข้อมูลลูกหนี้จากบิลออฟไลน์ไม่สำเร็จ");
                return;
            }
        }

        new ServerSaleService(appContext).commit(storeId, billId, paymentType,
                bill.optDouble("cashReceived", 0d), customerId, customer, items,
                new ServerSaleService.Callback() {
                    @Override public void onSuccess(List<JSONObject> records, boolean alreadyCommitted) {
                        if (records == null || records.isEmpty()) {
                            callback.onComplete(false, "เซิร์ฟเวอร์ไม่ส่งข้อมูลยืนยันบิล " + billId);
                            return;
                        }
                        for (JSONObject result : records) {
                            String id = result.optString("recordId", "").trim();
                            if (id.isEmpty() || !storeId.equals(result.optString("storeId", ""))) {
                                callback.onComplete(false, "ข้อมูลตอบกลับไม่ตรงกับร้าน จึงหยุดซิงก์");
                                return;
                            }
                            // Replacing the same product key keeps the newest server
                            // stock snapshot when several offline bills affect one SKU.
                            committed.put(id, result);
                        }
                        commitAt(storeId, pending, index + 1, committed, callback);
                    }

                    @Override public void onFailure(String message) {
                        callback.onComplete(false, "ซิงก์บิลออฟไลน์ " + billId + " ไม่สำเร็จ: " + message);
                    }
                });
    }
}
