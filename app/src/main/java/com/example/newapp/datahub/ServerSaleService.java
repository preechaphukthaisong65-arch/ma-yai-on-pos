package com.example.newapp.datahub;

import android.content.Context;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.HttpsCallableResult;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Calls the trusted server transaction for online sale + inventory commits. */
public final class ServerSaleService {
    public interface Callback {
        void onSuccess(List<JSONObject> committedRecords, boolean alreadyCommitted);
        void onFailure(String message);
    }

    private final Context appContext;
    private final FirebaseFunctions functions;

    public ServerSaleService(Context context) {
        appContext = context.getApplicationContext();
        functions = FirebaseFunctions.getInstance();
    }

    public void commit(String storeId, String billId, String paymentType, double cashReceived,
            String customerRecordId, JSONObject customer, List<JSONObject> items, Callback callback) {
        if (storeId == null || storeId.trim().isEmpty() || billId == null || billId.trim().isEmpty()
                || items == null || items.isEmpty() || callback == null) {
            if (callback != null) callback.onFailure("ข้อมูลการขายไม่ครบถ้วน");
            return;
        }
        List<Map<String, Object>> normalizedItems = new ArrayList<>();
        for (JSONObject item : items) {
            if (item == null) {
                callback.onFailure("พบรายการสินค้าไม่ถูกต้อง");
                return;
            }
            String productId = item.optString("productRecordId", "").trim();
            double quantity = item.optDouble("quantity", Double.NaN);
            if (productId.isEmpty() || !Double.isFinite(quantity) || quantity <= 0) {
                callback.onFailure("รหัสสินค้าหรือจำนวนขายไม่ถูกต้อง");
                return;
            }
            Map<String, Object> normalized = new HashMap<>();
            normalized.put("productRecordId", productId);
            normalized.put("quantity", quantity);
            normalized.put("localRevision", item.optLong("localRevision", 0L));
            normalizedItems.add(normalized);
        }
        Map<String, Object> request = new HashMap<>();
        request.put("storeId", storeId.trim());
        request.put("deviceId", StoreIdentity.getOrCreateDeviceId(appContext));
        request.put("billId", billId.trim());
        request.put("idempotencyKey", billId.trim());
        request.put("paymentType", paymentType);
        request.put("cashReceived", cashReceived);
        request.put("customerRecordId", customerRecordId == null ? "" : customerRecordId.trim());
        if (customer != null) {
            JSONObject payload = customer.optJSONObject("payload");
            if (payload != null) {
                Map<String, Object> customerInput = new HashMap<>();
                customerInput.put("recordId", customer.optString("recordId", ""));
                customerInput.put("firstName", payload.optString("firstName", ""));
                customerInput.put("lastName", payload.optString("lastName", ""));
                customerInput.put("createdAt", payload.optLong("createdAt", System.currentTimeMillis()));
                request.put("customer", customerInput);
            }
        }
        request.put("items", normalizedItems);

        functions.getHttpsCallable("commitPosSale").call(request)
                .addOnSuccessListener(result -> {
                    try {
                        Object raw = result.getData();
                        if (!(raw instanceof Map)) {
                            callback.onFailure("เซิร์ฟเวอร์ส่งผลการขายกลับมาไม่ถูกต้อง");
                            return;
                        }
                        Map<?, ?> response = (Map<?, ?>) raw;
                        boolean alreadyCommitted = Boolean.TRUE.equals(response.get("alreadyCommitted"));
                        List<JSONObject> records = new ArrayList<>();
                        Object rawRecords = response.get("records");
                        if (rawRecords instanceof List) {
                            for (Object value : (List<?>) rawRecords) {
                                if (!(value instanceof Map)) {
                                    callback.onFailure("รูปแบบข้อมูลจากเซิร์ฟเวอร์ไม่ถูกต้อง");
                                    return;
                                }
                                JSONObject record = new JSONObject((Map<?, ?>) value);
                                if (!storeId.trim().equals(record.optString("storeId", ""))) {
                                    callback.onFailure("เซิร์ฟเวอร์ส่งข้อมูลข้ามร้านค้า จึงหยุดบันทึก");
                                    return;
                                }
                                records.add(record);
                            }
                        }
                        callback.onSuccess(records, alreadyCommitted);
                    } catch (Exception error) {
                        callback.onFailure("อ่านผลการขายจากเซิร์ฟเวอร์ไม่สำเร็จ");
                    }
                })
                .addOnFailureListener(error -> {
                    String message = error.getLocalizedMessage();
                    if (error instanceof FirebaseFunctionsException) {
                        FirebaseFunctionsException functionsError = (FirebaseFunctionsException) error;
                        if (functionsError.getCode() == FirebaseFunctionsException.Code.PERMISSION_DENIED) {
                            message = "บัญชีหรือเครื่องนี้ไม่มีสิทธิ์ขายในร้านปัจจุบัน";
                        } else if (functionsError.getCode() == FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
                            message = "เซิร์ฟเวอร์ปฏิเสธการขาย: " + (message == null ? "ตรวจสอบสต๊อกและยอดเงิน" : message);
                        }
                    }
                    callback.onFailure(message == null ? "ยืนยันการขายกับเซิร์ฟเวอร์ไม่สำเร็จ" : message);
                });
    }
}
