package com.example.newapp.datahub;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public final class CloudBackupService {

    public interface BackupCallback {
        void onSuccess();

        void onFailure(String message);
    }

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    public CloudBackupService() {
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    /**
     * ส่งข้อมูลสำรองไป Firestore
     *
     * หมายเหตุ:
     * ต้องกำหนด Firestore Security Rules ให้ตรวจสอบสิทธิ์
     * และสิทธิ์เข้าถึงข้อมูลของแต่ละร้านก่อนใช้งานจริง
     */
    public void upload(
            JSONObject record,
            BackupCallback callback
    ) {
        if (callback == null) {
            return;
        }

        if (record == null) {
            callback.onFailure("ไม่พบข้อมูลสำรอง");
            return;
        }

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            callback.onFailure(
                    "กรุณาเข้าสู่ระบบก่อนสำรองข้อมูล"
            );
            return;
        }

        if (!new BackupManager().isValidRecord(record)) {
            callback.onFailure("รูปแบบข้อมูลสำรองไม่ถูกต้อง");
            return;
        }

        String recordId = record.optString("recordId", "");
        String storeId = record.optString("storeId", "");

        if (recordId.trim().isEmpty()
                || storeId.trim().isEmpty()) {
            callback.onFailure("รหัสข้อมูลหรือรหัสร้านว่าง");
            return;
        }

        Map<String, Object> cloudRecord =
                new HashMap<>();

        try {
            cloudRecord.put(
                    "recordId",
                    record.getString("recordId")
            );
            cloudRecord.put(
                    "storeId",
                    record.getString("storeId")
            );
            cloudRecord.put(
                    "dataType",
                    record.getString("dataType")
            );
            cloudRecord.put(
                    "schemaVersion",
                    record.getInt("schemaVersion")
            );
            cloudRecord.put(
                    "updatedAt",
                    record.get("updatedAt")
            );
            cloudRecord.put(
                    "revision",
                    record.getInt("revision")
            );
            cloudRecord.put(
                    "payload",
                    jsonObjectToMap(
                            record.getJSONObject("payload")
                    )
            );
            cloudRecord.put(
                    "uploadedBy",
                    user.getUid()
            );

        } catch (Exception e) {
            callback.onFailure(
                    "แปลงข้อมูลสำรองไม่สำเร็จ"
            );
            return;
        }

        DocumentReference document = firestore
                .collection("dataHubBackups")
                .document(storeId)
                .collection("records")
                .document(recordId);

        document.set(cloudRecord, SetOptions.merge())
                .addOnSuccessListener(unused ->
                        callback.onSuccess()
                )
                .addOnFailureListener(error ->
                        callback.onFailure(
                                error.getMessage() == null
                                        ? "สำรองข้อมูลไม่สำเร็จ"
                                        : error.getMessage()
                        )
                );
    }

    private Map<String, Object> jsonObjectToMap(
            JSONObject json
    ) {
        Map<String, Object> result =
                new HashMap<>();

        java.util.Iterator<String> keys =
                json.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.opt(key);

            if (value instanceof JSONObject) {
                result.put(
                        key,
                        jsonObjectToMap((JSONObject) value)
                );
            } else if (value != JSONObject.NULL) {
                result.put(key, value);
            } else {
                result.put(key, null);
            }
        }

        return result;
    }
}
