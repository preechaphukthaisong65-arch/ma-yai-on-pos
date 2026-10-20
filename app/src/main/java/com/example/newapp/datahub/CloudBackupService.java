
package com.example.newapp.datahub;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
