
package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DebtAlertRepository {

    private final LocalDatabase database;

    public DebtAlertRepository(LocalDatabase database) {
        this.database = database;
    }

    /**
     * คำนวณวันตัดยอดโดยใช้เวลาประเทศไทยจาก LocalDatabase
     * ใช้เกณฑ์เดียวกันทั้งการนับบิล ยอดหนี้ และรายการบิล
     */
    private String getOverdueCutoff() {
        return LocalDatabase.oneMonthAgo();
    }

    public int countOverdueBills() {
        SQLiteDatabase db = database.getReadableDatabase();
        String cutoff = getOverdueCutoff();

        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM sales " +
                "WHERE payment_type = 'CREDIT' " +
                "AND status = 'PENDING' " +
                "AND datetime(created_at) <= datetime(?)",
                new String[]{cutoff})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public double getOverdueAmount() {
        SQLiteDatabase db = database.getReadableDatabase();
        String cutoff = getOverdueCutoff();

        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(total), 0) FROM sales " +
                "WHERE payment_type = 'CREDIT' " +
                "AND status = 'PENDING' " +
                "AND datetime(created_at) <= datetime(?)",
                new String[]{cutoff})) {
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0;
        }
    }

    /**
     * บันทึกข้อความเตือนเมื่อมีหนี้เกินหนึ่งเดือน
     * event_key ต้องมี UNIQUE index ใน notification_messages
     * เพื่อป้องกันข้อความเตือนซ้ำในวันเดียวกัน
     */
    public boolean recordOverdueAlertIfNeeded() {
        SQLiteDatabase db = database.getWritableDatabase();

        int count = countOverdueBills();
        if (count <= 0) {
            return false;
        }

        String now = LocalDatabase.now();
        String today = now.substring(0, 10);
        String eventKey = "OVERDUE_DEBT_" + today;

        ContentValues values = new ContentValues();
        values.put("event_key", eventKey);
        values.put(
                "message",
                "มีบิลเครดิตค้างเกินหนึ่งเดือน " + count + " บิล"
        );
        values.put("created_at", now);
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
        String cutoff = getOverdueCutoff();

        return db.rawQuery(
                "SELECT s.id, s.bill_number, s.total, s.created_at, " +
                "c.first_name, c.last_name " +
                "FROM sales s " +
                "LEFT JOIN customers c ON c.id = s.customer_id " +
                "WHERE s.payment_type = 'CREDIT' " +
                "AND s.status = 'PENDING' " +
                "AND datetime(s.created_at) <= datetime(?) " +
                "ORDER BY s.created_at ASC",
                new String[]{cutoff}
        );
    }
}
