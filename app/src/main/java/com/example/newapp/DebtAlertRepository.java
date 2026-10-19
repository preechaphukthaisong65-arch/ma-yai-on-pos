
package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DebtAlertRepository {

    private final LocalDatabase database;

    public DebtAlertRepository(LocalDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "ฐานข้อมูลห้ามเป็น null"
            );
        }
        this.database = database;
    }

    /**
     * ใช้เวลาประเทศไทย UTC+7 จาก LocalDatabase
     */
    private String getOverdueCutoff() {
        return LocalDatabase.oneMonthAgo();
    }

    /**
     * นับเฉพาะบิลเครดิตที่ยังค้างชำระและขายมาแล้วอย่างน้อยหนึ่งเดือน
     */
    public int countOverdueBills() {
        SQLiteDatabase db = database.getReadableDatabase();
        String cutoff = getOverdueCutoff();

        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM sales " +
                "WHERE payment_type = 'CREDIT' " +
                "AND status = 'PENDING' " +
                "AND datetime(created_at) <= datetime(?)",
                new String[]{cutoff}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /**
     * รวมยอดบิลเครดิตที่ยังค้างชำระเกินหนึ่งเดือน
     */
    public double getOverdueAmount() {
        SQLiteDatabase db = database.getReadableDatabase();
        String cutoff = getOverdueCutoff();

        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(total), 0) FROM sales " +
                "WHERE payment_type = 'CREDIT' " +
                "AND status = 'PENDING' " +
                "AND datetime(created_at) <= datetime(?)",
                new String[]{cutoff}
        )) {
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0;
        }
    }

    /**
     * บันทึกการแจ้งเตือนหนี้เกินหนึ่งเดือนวันละหนึ่งรายการ
     * event_key มี UNIQUE index ในฐานข้อมูล
     * event_type ต้องถูกบันทึกด้วยเพราะเป็น NOT NULL
     */
    public boolean recordOverdueAlertIfNeeded() {
        int count = countOverdueBills();

        if (count <= 0) {
            return false;
        }

        String now = LocalDatabase.now();
        String today = now.substring(0, 10);
        String eventKey = "OVERDUE_DEBT_" + today;

        ContentValues values = new ContentValues();
        values.put("event_type", "OVERDUE_DEBT");
        values.put(
                "message",
                "มีบิลเครดิตค้างเกินหนึ่งเดือน " + count + " บิล"
        );
        values.put("created_at", now);
        values.put("is_read", 0);
        values.put("event_key", eventKey);

        SQLiteDatabase db = database.getWritableDatabase();

        long result = db.insertWithOnConflict(
                "notification_messages",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        // คืน true เมื่อบันทึกแถวใหม่สำเร็จ
        // หากมีการแจ้งเตือนของวันนี้แล้ว จะไม่เพิ่มรายการซ้ำ
        return result != -1;
    }

    /**
     * รายการบิลเครดิตค้างเกินหนึ่งเดือน เรียงจากเก่าที่สุดก่อน
     */
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
