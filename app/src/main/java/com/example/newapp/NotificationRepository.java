
package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class NotificationRepository {

    private final LocalDatabase helper;

    public NotificationRepository(LocalDatabase helper) {
        if (helper == null) {
            throw new IllegalArgumentException("ฐานข้อมูลห้ามเป็น null");
        }
        this.helper = helper;
    }

    /**
     * เพิ่มข้อความแจ้งเตือนลงฐานข้อมูล
     * eventKey ใช้ระบุเหตุการณ์เพื่อป้องกันรายการซ้ำ
     */
    public boolean addMessage(
            String eventKey,
            String message,
            String category
    ) {
        if (isBlank(eventKey)
                || isBlank(message)
                || isBlank(category)) {
            return false;
        }

        SQLiteDatabase db = helper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("message", message.trim());
        values.put("category", category.trim());
        values.put("created_at", LocalDatabase.now());

        // ต้องมีคอลัมน์ event_key แบบ UNIQUE ในตาราง
        values.put("event_key", eventKey.trim());

        long id = db.insertWithOnConflict(
                "notification_messages",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        return id != -1;
    }

    /**
     * ข้อความแจ้งเตือนล่าสุดสำหรับแถบข้อความ
     */
    public Cursor getRecentMessages(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "จำนวนข้อความต้องอยู่ระหว่าง 1 ถึง 100"
            );
        }

        SQLiteDatabase db = helper.getReadableDatabase();

        return db.query(
                "notification_messages",
                new String[]{
                        "id",
                        "message",
                        "category",
                        "created_at"
                },
                null,
                null,
                null,
                null,
                "id DESC",
                String.valueOf(limit)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
