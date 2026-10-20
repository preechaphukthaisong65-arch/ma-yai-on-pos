
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
                if (cloudRevision <= incomingRevision) {
                    transaction.set(document, cloudRecord, SetOptions.merge());
                }
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

                            if (!record.has("recordId")
                                    || record.optString(
                                            "recordId", ""
                                    ).trim().isEmpty()) {
                                record.put(
                                        "recordId",
                                        document.getId()
                                );
                            }

                            if (!record.has("storeId")
                                    || record.optString(
                                            "storeId", ""
                                    ).trim().isEmpty()) {
                                record.put(
                                        "storeId",
                                        checkedStoreId
                                );
                            }

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
