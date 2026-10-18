package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class NotificationRepository {

    private final LocalDatabase database;

    public NotificationRepository(LocalDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "ฐานข้อมูลห้ามเป็น null"
            );
        }
        this.database = database;
    }

    public boolean addMessage(String eventKey, String message) {
        if (eventKey == null || eventKey.trim().isEmpty()
                || message == null || message.trim().isEmpty()) {
            return false;
        }

        SQLiteDatabase db = database.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("event_type", eventKey.trim());
        values.put("message", message.trim());
        values.put("created_at", LocalDatabase.now());
        values.put("is_read", 0);

        long id = db.insert("notification_messages", null, values);
        return id != -1;
    }

    public Cursor getLatestMessages(int limit) {
        SQLiteDatabase db = database.getReadableDatabase();
        int safeLimit = Math.max(1, Math.min(limit, 500));

        return db.rawQuery(
                "SELECT id, event_type, message, created_at, is_read " +
                "FROM notification_messages " +
                "ORDER BY id DESC LIMIT " + safeLimit,
                null
        );
    }

    public int getUnreadCount() {
        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM notification_messages " +
                "WHERE is_read = 0",
                null
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public boolean markRead(long notificationId) {
        SQLiteDatabase db = database.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("is_read", 1);

        return db.update(
                "notification_messages",
                values,
                "id = ?",
                new String[]{String.valueOf(notificationId)}
        ) > 0;
    }

    public int markAllRead() {
        SQLiteDatabase db = database.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("is_read", 1);

        return db.update(
                "notification_messages",
                values,
                "is_read = 0",
                null
        );
    }
}
