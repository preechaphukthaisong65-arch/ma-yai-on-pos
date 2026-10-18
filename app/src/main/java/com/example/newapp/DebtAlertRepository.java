
package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DebtAlertRepository {

    private final LocalDatabase database;

    public DebtAlertRepository(LocalDatabase database) {
        this.database = database;
    }

    public int countOverdueBills() {
        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM sales " +
                "WHERE payment_type = 'CREDIT' " +
                "AND status = 'PENDING' " +
                "AND datetime(created_at) <= datetime('now', '-1 month')",
                null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public double getOverdueAmount() {
        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(total), 0) FROM sales " +
                "WHERE payment_type = 'CREDIT' " +
                "AND status = 'PENDING' " +
                "AND datetime(created_at) <= datetime('now', '-1 month')",
                null)) {
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0;
        }
    }

    /**
     * บันทึกข้อความเตือนเมื่อมีหนี้เกินหนึ่งเดือน
     * event_key ต้องมี UNIQUE constraint ใน notification_messages
     * เพื่อให้ INSERT OR IGNORE ป้องกันข้อความซ้ำได้
     */
    public boolean recordOverdueAlertIfNeeded() {
        SQLiteDatabase db = database.getWritableDatabase();

        int count = countOverdueBills();
        if (count <= 0) {
            return false;
        }

        String today = LocalDatabase.now().substring(0, 10);
        String eventKey = "OVERDUE_DEBT_" + today;

        ContentValues values = new ContentValues();
        values.put("event_key", eventKey);
        values.put(
                "message",
                "มีบิลเครดิตค้างเกินหนึ่งเดือน " + count + " บิล"
        );
        values.put("created_at", LocalDatabase.now());
        values.put("is_read", 0);

        long result = db.insertWithOnConflict(
                "notification_messages",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        return result != -1;
    }

    public Cursor getOverdueBills() {
        SQLiteDatabase db = database.getReadableDatabase();

        return db.rawQuery(
                "SELECT s.id, s.bill_number, s.total, s.created_at, " +
                "c.first_name, c.last_name " +
                "FROM sales s " +
                "LEFT JOIN customers c ON c.id = s.customer_id " +
                "WHERE s.payment_type = 'CREDIT' " +
                "AND s.status = 'PENDING' " +
                "AND datetime(s.created_at) <= datetime('now', '-1 month') " +
                "ORDER BY s.created_at ASC",
                null
        );
    }
}
