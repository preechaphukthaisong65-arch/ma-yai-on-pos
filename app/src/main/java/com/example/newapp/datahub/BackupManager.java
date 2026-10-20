package com.example.newapp.datahub;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * ตรวจสอบและเตรียมข้อมูลสำหรับการสำรอง
 *
 * ไฟล์นี้ยังไม่ส่งข้อมูลไป Firebase
 */
public final class BackupManager {

    public BackupManager() {
        // ไม่มีการเชื่อมต่อเครือข่ายในคลาสนี้
    }

    /**
     * ตรวจสอบโครงสร้างข้อมูลก่อนสำรอง
     */
    public boolean isValidRecord(JSONObject record) {
        if (record == null) {
            return false;
        }

        try {
            String recordId = record.optString("recordId", "");
            String storeId = record.optString("storeId", "");
            String dataType = record.optString("dataType", "");
            int schemaVersion = record.optInt("schemaVersion", -1);
            int revision = record.optInt("revision", -1);
            JSONObject payload = record.optJSONObject("payload");

            return !isBlank(recordId)
                    && !isBlank(storeId)
                    && !isBlank(dataType)
                    && schemaVersion > 0
                    && revision > 0
                    && payload != null;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * สร้างสำเนาข้อมูลที่ผ่านการตรวจสอบแล้ว
     * ข้อมูลต้นฉบับจะไม่ถูกแก้ไข
     */
    public JSONObject prepareBackup(JSONObject record)
            throws JSONException {

        if (!isValidRecord(record)) {
            throw new IllegalArgumentException(
                    "Invalid record: cannot prepare backup"
            );
        }

        JSONObject backup = new JSONObject();

        backup.put("recordId", record.getString("recordId"));
        backup.put("storeId", record.getString("storeId"));
        backup.put("dataType", record.getString("dataType"));
        backup.put("schemaVersion", record.getInt("schemaVersion"));
        backup.put("updatedAt", record.get("updatedAt"));
        backup.put("revision", record.getInt("revision"));
        backup.put("payload", new JSONObject(
                record.getJSONObject("payload").toString()
        ));

        // สำเนาที่เตรียมแล้ว แต่ยังไม่ได้ส่งขึ้นคลาวด์
        backup.put("syncStatus", DataHub.SYNC_PENDING);

        return backup;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
