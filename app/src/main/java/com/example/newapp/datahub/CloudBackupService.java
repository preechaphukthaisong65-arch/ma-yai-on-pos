
package com.example.newapp.datahub;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CloudBackupService {

    private static final String COLLECTION_BACKUPS = "dataHubBackups";
    private static final String COLLECTION_RECORDS = "records";

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    public interface BackupCallback {
        void onSuccess();

        void onFailure(String message);
    }

    public interface RecordsCallback {
        void onSuccess(List<JSONObject> records);

        void onFailure(String message);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onFailure(String message);
    }

    public CloudBackupService() {
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    /**
     * Upload one record to Firestore.
     */
    public void upload(JSONObject record, BackupCallback callback) {
        if (record == null) {
            failBackup(callback, "ไม่มีข้อมูลสำหรับสำรอง");
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            failBackup(callback, "กรุณาเข้าสู่ระบบก่อนสำรองข้อมูล");
            return;
        }

        String storeId = record.optString("storeId", "").trim();
        String recordId = record.optString("recordId", "").trim();

        if (storeId.isEmpty() || recordId.isEmpty()) {
            failBackup(callback, "ข้อมูลร้านค้าหรือรหัสรายการไม่ถูกต้อง");
            return;
        }

        JSONObject payload = getPayload(record);
        if (payload == null) {
            failBackup(callback, "รูปแบบข้อมูลรายการไม่ถูกต้อง");
            return;
        }

        try {
            Map<String, Object> cloudRecord = new HashMap<>();
            cloudRecord.put("recordId", recordId);
            cloudRecord.put("storeId", storeId);
            cloudRecord.put(
                    "dataType",
                    record.optString("dataType", "")
            );
            cloudRecord.put(
                    "schemaVersion",
                    record.optInt("schemaVersion", 1)
            );
            cloudRecord.put(
                    "updatedAt",
                    record.optLong(
                            "updatedAt",
                            System.currentTimeMillis()
                    )
            );
            cloudRecord.put(
                    "revision",
                    record.optLong("revision", 1)
            );
            cloudRecord.put("payload", jsonToMap(payload));
            cloudRecord.put("uploadedBy", user.getUid());
            cloudRecord.put("deviceId", record.optString("deviceId", ""));

            com.google.firebase.firestore.DocumentReference document =
                    firestore.collection(COLLECTION_BACKUPS)
                            .document(storeId)
                            .collection(COLLECTION_RECORDS)
                            .document(recordId);
            long incomingRevision = record.optLong("revision", 1L);

            // Serialize competing uploads for the same record. A delayed
            // older upload must never replace a newer cloud revision.
            firestore.runTransaction(transaction -> {
                DocumentSnapshot existing = transaction.get(document);
                long cloudRevision = existing.exists()
                        ? existing.getLong("revision") == null
                            ? 0L : existing.getLong("revision")
                        : 0L;
                if (cloudRevision > incomingRevision) {
                    throw new IllegalStateException("Cloud record has a newer revision; refusing to mark stale data as synced");
                }
                if (cloudRevision == incomingRevision) {
                    Long cloudUpdatedAt = existing.exists() ? existing.getLong("updatedAt") : null;
                    long incomingUpdatedAt = record.optLong("updatedAt", 0L);
                    Object cloudPayload = existing.get("payload");
                    Object incomingPayload = cloudRecord.get("payload");
                    if (cloudUpdatedAt == null || cloudUpdatedAt.longValue() != incomingUpdatedAt
                            || !record.optString("dataType", "").equals(existing.getString("dataType"))
                            || !(cloudPayload instanceof Map)
                            || !deepEquals(cloudPayload, incomingPayload)) {
                        throw new IllegalStateException("Conflicting content at the same cloud revision; refusing overwrite");
                    }
                    return null;
                }
                transaction.set(document, cloudRecord, SetOptions.merge());
                return null;
            }).addOnSuccessListener(unused -> {
                if (callback != null) {
                    callback.onSuccess();
                }
            }).addOnFailureListener(error -> {
                failBackup(
                        callback,
                        "สำรองข้อมูลไม่สำเร็จ: " + safeMessage(error)
                );
            });

        } catch (Exception error) {
            failBackup(
                    callback,
                    "เตรียมข้อมูลสำรองไม่สำเร็จ: "
                            + safeMessage(error)
            );
        }
    }

    public interface CreditPaymentCallback {
        void onSuccess(List<JSONObject> committedRecords);
        void onFailure(String message);
    }

    /**
     * Atomically verifies current cloud balances and commits bill updates plus all
     * payment artifacts. No local-only payment is accepted by this path.
     */
    public void commitCreditPayment(String storeId, List<JSONObject> selectedBills,
            double receivedAmount, String deviceId, CreditPaymentCallback callback) {
        String store = storeId == null ? "" : storeId.trim();
        FirebaseUser user = auth.getCurrentUser();
        long receivedCents = Math.round(receivedAmount * 100d);
        if (user == null || !user.isEmailVerified() || store.isEmpty()
                || selectedBills == null || selectedBills.isEmpty()
                || receivedCents <= 0L || deviceId == null || deviceId.trim().isEmpty()) {
            callback.onFailure("ข้อมูลรับชำระไม่ครบหรือบัญชีไม่ได้เข้าสู่ระบบ");
            return;
        }

        String paymentId = "credit_payment:" + java.util.UUID.randomUUID();
        String historyId = "credit_payment_history:" + java.util.UUID.randomUUID();
        long now = System.currentTimeMillis();
        DocumentReference sessionRef = firestore.collection("storeDeviceSessions").document(store);
        List<DocumentReference> billRefs = new ArrayList<>();
        for (JSONObject bill : selectedBills) {
            String id = bill == null ? "" : bill.optString("recordId", "").trim();
            if (id.isEmpty()) {
                callback.onFailure("พบบิลที่ไม่มีรหัสรายการ");
                return;
            }
            billRefs.add(firestore.collection(COLLECTION_BACKUPS).document(store)
                    .collection(COLLECTION_RECORDS).document(id));
        }

        firestore.runTransaction(transaction -> {
            try {
            DocumentSnapshot session = transaction.get(sessionRef);
            if (!session.exists() || !user.getUid().equals(session.getString("ownerUid"))
                    || !deviceId.equals(session.getString("activeDeviceId"))) {
                throw new IllegalStateException("เครื่องนี้ไม่ได้เป็นเครื่องที่ใช้งานอยู่ กรุณาเข้าสู่ระบบใหม่");
            }

            List<DocumentSnapshot> cloudBills = new ArrayList<>();
            long dueCents = 0L;
            for (int i = 0; i < billRefs.size(); i++) {
                DocumentSnapshot cloud = transaction.get(billRefs.get(i));
                JSONObject local = selectedBills.get(i);
                if (!cloud.exists()
                        || !store.equals(cloud.getString("storeId"))
                        || !local.optString("recordId").equals(cloud.getString("recordId"))
                        || !"sale_bill".equals(cloud.getString("dataType"))) {
                    throw new IllegalStateException("บิลไม่มีข้อมูลสำรองบนเซิร์ฟเวอร์ กรุณาซิงก์และตรวจสอบก่อนรับชำระ");
                }
                Long cloudRevision = cloud.getLong("revision");
                if (cloudRevision == null || cloudRevision.longValue() != local.optLong("revision", -1L)) {
                    throw new IllegalStateException("ยอดบิลเปลี่ยนจากข้อมูลที่เปิดไว้ กรุณาค้นหาบิลใหม่ก่อนรับชำระ");
                }
                Object payloadValue = cloud.get("payload");
                if (!(payloadValue instanceof Map)) throw new IllegalStateException("ข้อมูลยอดหนี้บนเซิร์ฟเวอร์ไม่ถูกต้อง");
                Map<?, ?> p = (Map<?, ?>) payloadValue;
                Object status = p.get("paymentStatus");
                Object type = p.get("paymentType");
                Object rawBalance = p.containsKey("remainingBalance") ? p.get("remainingBalance") : p.get("total");
                if (!"credit".equals(String.valueOf(type)) || !"ค้างชำระ".equals(String.valueOf(status))
                        || !(rawBalance instanceof Number)) {
                    throw new IllegalStateException("บิลนี้ไม่ได้ค้างชำระแล้ว");
                }
                long cents = Math.round(((Number) rawBalance).doubleValue() * 100d);
                if (cents <= 0L || Long.MAX_VALUE - dueCents < cents) {
                    throw new IllegalStateException("ยอดคงเหลือของบิลไม่ถูกต้อง");
                }
                dueCents += cents;
                cloudBills.add(cloud);
            }
            if (receivedCents > dueCents) throw new IllegalStateException("เงินรับเกินยอดหนี้ล่าสุด");

            List<JSONObject> committed = new ArrayList<>();
            List<Map<String, Object>> changedBillMaps = new ArrayList<>();
            List<DocumentReference> changedBillRefs = new ArrayList<>();
            org.json.JSONArray billIds = new org.json.JSONArray();
            org.json.JSONArray allocations = new org.json.JSONArray();
            long remainingPayment = receivedCents;
            for (int i = 0; i < cloudBills.size(); i++) {
                DocumentSnapshot cloud = cloudBills.get(i);
                Map<String, Object> full = new HashMap<>(cloud.getData());
                Map<String, Object> oldPayload = new HashMap<>((Map<String, Object>) cloud.get("payload"));
                long balance = Math.round((((Number) (oldPayload.containsKey("remainingBalance")
                        ? oldPayload.get("remainingBalance") : oldPayload.get("total"))).doubleValue()) * 100d);
                long allocated = Math.min(balance, remainingPayment);
                if (allocated <= 0L) continue;
                long newBalance = balance - allocated;
                oldPayload.put("remainingBalance", newBalance / 100d);
                oldPayload.put("paymentStatus", newBalance == 0L ? "จ่ายแล้ว" : "ค้างชำระ");
                oldPayload.put("lastPaymentId", paymentId);
                oldPayload.put("lastPaymentAt", now);
                if (newBalance == 0L) oldPayload.put("paidAt", now); else oldPayload.remove("paidAt");
                long newRevision = cloud.getLong("revision") + 1L;
                full.put("payload", oldPayload);
                full.put("revision", newRevision);
                full.put("updatedAt", now);
                full.put("deviceId", deviceId);
                full.put("uploadedBy", user.getUid());
                changedBillMaps.add(full);
                changedBillRefs.add(billRefs.get(i));

                JSONObject localRecord = new JSONObject();
                localRecord.put("recordId", cloud.getString("recordId"));
                localRecord.put("storeId", store);
                localRecord.put("dataType", "sale_bill");
                localRecord.put("schemaVersion", cloud.getLong("schemaVersion") == null ? 1 : cloud.getLong("schemaVersion"));
                localRecord.put("revision", newRevision);
                localRecord.put("expectedRevision", newRevision - 1L);
                localRecord.put("updatedAt", now);
                localRecord.put("payload", new JSONObject(oldPayload));
                committed.add(localRecord);

                billIds.put(cloud.getString("recordId"));
                JSONObject allocation = new JSONObject();
                allocation.put("billId", cloud.getString("recordId"));
                allocation.put("amount", allocated / 100d);
                allocation.put("remainingBalance", newBalance / 100d);
                allocation.put("paymentStatus", newBalance == 0L ? "จ่ายแล้ว" : "ค้างชำระ");
                allocations.put(allocation);
                remainingPayment -= allocated;
            }
            if (remainingPayment != 0L) throw new IllegalStateException("คำนวณการจัดสรรยอดชำระไม่สำเร็จ");

            JSONObject payment = new JSONObject();
            payment.put("paymentId", paymentId);
            payment.put("paidAt", now);
            payment.put("amount", receivedCents / 100d);
            payment.put("paymentMethod", "cash");
            payment.put("billIds", billIds);
            payment.put("allocations", allocations);
            payment.put("isPartialPayment", receivedCents < dueCents);
            JSONObject receipt = new JSONObject();
            receipt.put("receiptId", "receipt:" + paymentId);
            receipt.put("paymentId", paymentId);
            receipt.put("receiptType", "ใบเสร็จรับชำระหนี้");
            receipt.put("createdAt", now);
            receipt.put("total", receivedCents / 100d);
            receipt.put("billIds", billIds);
            receipt.put("allocations", allocations);
            receipt.put("paymentStatus", receivedCents < dueCents ? "ชำระบางส่วน" : "รับชำระแล้ว");
            receipt.put("printStatus", "pending");
            JSONObject history = new JSONObject();
            history.put("paymentId", paymentId);
            history.put("paidAt", now);
            history.put("amount", receivedCents / 100d);
            history.put("billIds", billIds);
            history.put("allocations", allocations);
            history.put("eventType", "credit_payment");

            String[] ids = new String[]{paymentId, "receipt:" + paymentId, historyId};
            String[] types = new String[]{"credit_payment", "receipt", "credit_payment_history"};
            JSONObject[] payloads = new JSONObject[]{payment, receipt, history};
            List<Map<String, Object>> artifactMaps = new ArrayList<>();
            for (int i = 0; i < ids.length; i++) {
                JSONObject localRecord = new JSONObject();
                localRecord.put("recordId", ids[i]);
                localRecord.put("storeId", store);
                localRecord.put("dataType", types[i]);
                localRecord.put("schemaVersion", 1);
                localRecord.put("updatedAt", now);
                localRecord.put("revision", 1);
                localRecord.put("payload", payloads[i]);
                committed.add(localRecord);
                Map<String, Object> cloudRecord = new HashMap<>();
                cloudRecord.put("recordId", ids[i]);
                cloudRecord.put("storeId", store);
                cloudRecord.put("dataType", types[i]);
                cloudRecord.put("schemaVersion", 1);
                cloudRecord.put("updatedAt", now);
                cloudRecord.put("revision", 1);
                cloudRecord.put("payload", jsonToMap(payloads[i]));
                cloudRecord.put("uploadedBy", user.getUid());
                cloudRecord.put("deviceId", deviceId);
                artifactMaps.add(cloudRecord);
            }

            for (int i = 0; i < changedBillRefs.size(); i++) transaction.set(changedBillRefs.get(i), changedBillMaps.get(i));
            for (int i = 0; i < artifactMaps.size(); i++) {
                transaction.set(firestore.collection(COLLECTION_BACKUPS).document(store)
                        .collection(COLLECTION_RECORDS).document(ids[i]), artifactMaps.get(i));
            }
            return committed;
            } catch (Exception error) {
                throw new IllegalStateException("เตรียมธุรกรรมรับชำระไม่สำเร็จ", error);
            }
        }).addOnSuccessListener(committed -> callback.onSuccess(committed))
          .addOnFailureListener(error -> callback.onFailure("รับชำระไม่สำเร็จ: " + safeMessage(error)));
    }

    /** Reserve a barcode atomically so two online installations cannot claim the same value. */
    public void reserveBarcode(String storeId, String barcode, String deviceId, BackupCallback callback) {
        String store = storeId == null ? "" : storeId.trim();
        String code = barcode == null ? "" : barcode.trim();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || store.isEmpty() || code.isEmpty()) {
            failBackup(callback, "ข้อมูลร้านค้าหรือบาร์โค้ดไม่ถูกต้อง");
            return;
        }
        if (deviceId == null || deviceId.trim().isEmpty()) { failBackup(callback, "ไม่พบรหัสเครื่อง"); return; }
        DocumentReference reservation = firestore.collection("storeBarcodeReservations")
                .document(store).collection("barcodes").document(code);
        Map<String, Object> data = new HashMap<>();
        data.put("storeId", store);
        data.put("barcode", code);
        data.put("ownerUid", user.getUid());
        data.put("deviceId", deviceId);
        data.put("createdAt", System.currentTimeMillis());
        firestore.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(reservation);
            if (existing.exists()) throw new IllegalStateException("บาร์โค้ดนี้ถูกจองแล้ว กรุณาสร้างเลขใหม่");
            transaction.set(reservation, data);
            return null;
        }).addOnSuccessListener(unused -> { if (callback != null) callback.onSuccess(); })
          .addOnFailureListener(error -> failBackup(callback, "จองบาร์โค้ดไม่สำเร็จ: " + safeMessage(error)));
    }

    /**
     * Download all backup records belonging to one store.
     */
    public void fetchStoreRecords(
            String storeId,
            RecordsCallback callback
    ) {
        String checkedStoreId =
                storeId == null ? "" : storeId.trim();

        if (checkedStoreId.isEmpty()) {
            failRecords(callback, "ไม่พบรหัสร้านค้า");
            return;
        }

        if (auth.getCurrentUser() == null) {
            failRecords(
                    callback,
                    "กรุณาเข้าสู่ระบบก่อนกู้คืนข้อมูล"
            );
            return;
        }

        firestore.collection(COLLECTION_BACKUPS)
                .document(checkedStoreId)
                .collection(COLLECTION_RECORDS)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<JSONObject> records = new ArrayList<>();

                    try {
                        for (DocumentSnapshot document
                                : snapshot.getDocuments()) {
                            Map<String, Object> data =
                                    document.getData();

                            if (data == null) {
                                continue;
                            }

                            JSONObject record = new JSONObject(data);

                            String documentRecordId = document.getId();
                            String embeddedRecordId = record.optString("recordId", "").trim();
                            if (!embeddedRecordId.isEmpty()
                                    && !documentRecordId.equals(embeddedRecordId)) {
                                throw new JSONException(
                                        "Backup record ID does not match its Firestore document path"
                                );
                            }
                            // Older records may omit identity fields; when absent,
                            // use the trusted Firestore path rather than payload data.
                            record.put("recordId", documentRecordId);

                            String embeddedStoreId = record.optString("storeId", "").trim();
                            if (!embeddedStoreId.isEmpty()
                                    && !checkedStoreId.equals(embeddedStoreId)) {
                                throw new JSONException(
                                        "Backup store ID does not match the requested store path"
                                );
                            }
                            record.put("storeId", checkedStoreId);

                            records.add(record);
                        }

                        if (callback != null) {
                            callback.onSuccess(records);
                        }

                    } catch (Exception error) {
                        failRecords(
                                callback,
                                "อ่านข้อมูลสำรองไม่สำเร็จ: "
                                        + safeMessage(error)
                        );
                    }
                })
                .addOnFailureListener(error -> {
                    failRecords(
                            callback,
                            "ดาวน์โหลดข้อมูลสำรองไม่สำเร็จ: "
                                    + safeMessage(error)
                    );
                });
    }

    /** Delete a cloud record only if its identity, type and revision still match. */
    public void deleteRecord(String storeId, String recordId, String dataType,
            long expectedRevision, DeleteCallback callback) {
        String checkedStore = storeId == null ? "" : storeId.trim();
        String checkedId = recordId == null ? "" : recordId.trim();
        String checkedType = dataType == null ? "" : dataType.trim();
        if (checkedStore.isEmpty() || checkedId.isEmpty() || checkedType.isEmpty() || expectedRevision < 1L) {
            failDelete(callback, "ข้อมูลสำหรับลบไม่ถูกต้อง");
            return;
        }
        if (auth.getCurrentUser() == null) {
            failDelete(callback, "กรุณาเข้าสู่ระบบก่อนจัดการข้อมูลสำรอง");
            return;
        }
        com.google.firebase.firestore.DocumentReference document = firestore
                .collection(COLLECTION_BACKUPS).document(checkedStore)
                .collection(COLLECTION_RECORDS).document(checkedId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(document);
            if (!existing.exists()) return null;
            Long cloudRevision = existing.getLong("revision");
            if (!checkedStore.equals(existing.getString("storeId"))
                    || !checkedId.equals(existing.getString("recordId"))
                    || !checkedType.equals(existing.getString("dataType"))
                    || cloudRevision == null || cloudRevision.longValue() != expectedRevision) {
                throw new IllegalStateException("ข้อมูลบนคลาวด์เปลี่ยนไปหรือไม่ตรงกับรายการที่ตรวจสอบ");
            }
            transaction.delete(document);
            return null;
        }).addOnSuccessListener(unused -> {
            if (callback != null) callback.onSuccess();
        }).addOnFailureListener(error -> failDelete(callback,
                "ลบข้อมูลสำรองไม่สำเร็จ: " + safeMessage(error)));
    }

    private void failDelete(DeleteCallback callback, String message) {
        if (callback != null) callback.onFailure(message);
    }

    private boolean deepEquals(Object left, Object right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        if (left instanceof Number && right instanceof Number) {
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue()) == 0;
        }
        if (left instanceof Map && right instanceof Map) {
            Map<?, ?> a = (Map<?, ?>) left;
            Map<?, ?> b = (Map<?, ?>) right;
            if (!a.keySet().equals(b.keySet())) return false;
            for (Object key : a.keySet()) {
                if (!deepEquals(a.get(key), b.get(key))) return false;
            }
            return true;
        }
        if (left instanceof List && right instanceof List) {
            List<?> a = (List<?>) left;
            List<?> b = (List<?>) right;
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) {
                if (!deepEquals(a.get(i), b.get(i))) return false;
            }
            return true;
        }
        return left.equals(right);
    }

    private JSONObject getPayload(JSONObject record) {
        Object value = record.opt("payload");

        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }

        if (value instanceof String) {
            try {
                return new JSONObject((String) value);
            } catch (JSONException ignored) {
                return null;
            }
        }

        if (value instanceof Map) {
            return new JSONObject((Map<?, ?>) value);
        }

        return null;
    }

    private Map<String, Object> jsonToMap(JSONObject object)
            throws JSONException {
        Map<String, Object> result = new HashMap<>();
        JSONArray names = object.names();

        if (names == null) {
            return result;
        }

        for (int i = 0; i < names.length(); i++) {
            String key = names.getString(i);
            result.put(key, jsonToJava(object.get(key)));
        }

        return result;
    }

    private Object jsonToJava(Object value) throws JSONException {
        if (value == JSONObject.NULL) {
            return null;
        }

        if (value instanceof JSONObject) {
            return jsonToMap((JSONObject) value);
        }

        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> result = new ArrayList<>();

            for (int i = 0; i < array.length(); i++) {
                result.add(jsonToJava(array.get(i)));
            }

            return result;
        }

        return value;
    }

    private void failBackup(
            BackupCallback callback,
            String message
    ) {
        if (callback != null) {
            callback.onFailure(message);
        }
    }

    private void failRecords(
            RecordsCallback callback,
            String message
    ) {
        if (callback != null) {
            callback.onFailure(message);
        }
    }

    private String safeMessage(Exception error) {
        if (error == null
                || error.getMessage() == null
                || error.getMessage().trim().isEmpty()) {
            return "เกิดข้อผิดพลาดที่ไม่ทราบสาเหตุ";
        }

        return error.getMessage();
    }
}
