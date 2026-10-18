
package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

public class CloudSyncManager {

    private final LocalDatabase database;

    public CloudSyncManager(LocalDatabase database) {
        this.database = database;
    }

    public static class QueueResult {
        public final boolean success;
        public final long queueId;
        public final String message;

        public QueueResult(boolean success, long queueId, String message) {
            this.success = success;
            this.queueId = queueId;
            this.message = message;
        }
    }

    /**
     * เพิ่มงานลงคิวภายในเครื่อง ยังไม่ได้ส่งข้อมูลออกอินเทอร์เน็ต
     */
    public QueueResult enqueue(
            String entityType,
            String entityId,
            JSONObject payload
    ) {
        if (entityType == null || entityType.trim().isEmpty()) {
            return new QueueResult(false, -1, "ไม่ระบุประเภทข้อมูล");
        }

        if (entityId == null || entityId.trim().isEmpty()) {
            return new QueueResult(false, -1, "ไม่ระบุรหัสข้อมูล");
        }

        SQLiteDatabase db = database.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("entity_type", entityType);
        values.put("entity_id", entityId);
        values.put("payload_json", payload == null ? "{}" : payload.toString());
        values.put("sync_status", "PENDING");
        values.put("created_at", LocalDatabase.now());
        values.put("last_error", "");

        try {
            long id = db.insertOrThrow("cloud_sync_queue", null, values);

            return new QueueResult(
                    true,
                    id,
                    "เพิ่มรายการลงคิวสำรองข้อมูลในเครื่องแล้ว"
            );

        } catch (Exception e) {
            return new QueueResult(
                    false,
                    -1,
                    "เพิ่มคิวไม่สำเร็จ: " + safe(e.getMessage())
            );
        }
    }

    public int countPending() {
        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM cloud_sync_queue " +
                "WHERE sync_status = 'PENDING'",
                null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public int countFailed() {
        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM cloud_sync_queue " +
                "WHERE sync_status = 'FAILED'",
                null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /**
     * ยังไม่มีปลายทาง API จึงห้ามเปลี่ยนรายการเป็น SYNCED
     */
    public String getSyncStatusMessage() {
        return "ยังไม่ได้ตั้งค่าเซิร์ฟเวอร์คลาวด์ " +
                "ข้อมูลในเครื่องยังไม่ถูกส่งออกไป";
    }

    public Cursor getQueueHistory() {
        SQLiteDatabase db = database.getReadableDatabase();

        return db.rawQuery(
                "SELECT _id, entity_type, entity_id, sync_status, " +
                "created_at, last_error " +
                "FROM cloud_sync_queue ORDER BY _id DESC",
                null
        );
    }

    private String safe(String value) {
        return value == null ? "ไม่ทราบสาเหตุ" : value;
    }
}
