package com.example.newapp.datahub;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * ตรวจสอบข้อมูลก่อนกู้คืน
 *
 * เวอร์ชันนี้ยังไม่เขียนข้อมูลลง LocalStore
 */
public final class RestoreManager {

    private final BackupManager backupManager;

    public RestoreManager() {
        backupManager = new BackupManager();
    }

    /**
     * ตรวจสอบข้อมูลสำรองและร้านเจ้าของข้อมูล
     */
    public boolean canRestore(
            JSONObject record,
            String expectedStoreId
    ) {
        if (record == null
                || expectedStoreId == null
                || expectedStoreId.trim().isEmpty()) {
            return false;
        }

        if (!backupManager.isValidRecord(record)) {
            return false;
        }

        String recordStoreId =
                record.optString("storeId", "");

        return expectedStoreId.trim().equals(recordStoreId);
    }

    /**
     * สร้างสำเนาข้อมูลที่ผ่านการตรวจสอบแล้ว
     * ไม่แก้ไข record ต้นฉบับ
     */
    public JSONObject prepareRestore(
            JSONObject record,
            String expectedStoreId
    ) throws JSONException {

        if (!canRestore(record, expectedStoreId)) {
            throw new IllegalArgumentException(
                    "ข้อมูลไม่ผ่านการตรวจสอบสำหรับกู้คืน"
            );
        }

        JSONObject restored = new JSONObject(
                record.toString()
        );

        // ข้อมูลที่กู้คืนต้องตรวจสอบสถานะการซิงก์ใหม่
        restored.put(
                "syncStatus",
                DataHub.SYNC_PENDING
        );

        return restored;
    }
}
