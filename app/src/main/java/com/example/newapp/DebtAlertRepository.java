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
     * เวลาปัจจุบันตามเขตเวลาที่ LocalDatabase ใช้
     */
    private String getNow() {
        return LocalDatabase.now();
    }

    /**
     * เงื่อนไขบิลที่เลยกำหนดชำระ
     *
     * ถ้ามี due_date ให้ใช้วันครบกำหนดนั้น
     * ถ้าเป็นข้อมูลเก่าที่ยังไม่มี due_date
     * ให้ใช้วันที่ขายบวก 30 วันแทน
     */
    private String overdueCondition(String alias) {
        return " " + alias + ".payment_type = 'CREDIT' " +
                "AND " + alias + ".status = 'PENDING' " +
                "AND datetime(COALESCE(" +
                "NULLIF(" + alias + ".due_date, ''), " +
                "datetime(" + alias + ".created_at, '+30 days')" +
                ")) <= datetime(?) ";
    }

    /**
     * นับจำนวนบิลเครดิตที่เลยกำหนดชำระแล้ว
     */
    public int countOverdueBills() {
        SQLiteDatabase db = database.getReadableDatabase();

        String sql =
                "SELECT COUNT(*) FROM sales s WHERE " +
                overdueCondition("s");

        try (Cursor cursor = db.rawQuery(
                sql,
                new String[]{getNow()}
        )) {
            return cursor.moveToFirst()
                    ? cursor.getInt(0)
                    : 0;
        }
    }

    /**
     * รวมยอดค้างชำระของบิลที่เลยกำหนดแล้ว
     */
    public double getOverdueAmount() {
        SQLiteDatabase db = database.getReadableDatabase();

        String sql =
                "SELECT COALESCE(SUM(s.total), 0) " +
                "FROM sales s WHERE " +
                overdueCondition("s");

        try (Cursor cursor = db.rawQuery(
                sql,
                new String[]{getNow()}
        )) {
            return cursor.moveToFirst()
                    ? cursor.getDouble(0)
                    : 0.0d;
        }
    }

    /**
     * บันทึกการแจ้งเตือนหนี้เลยกำหนดวันละหนึ่งรายการ
     * ใช้ event_key ป้องกันการสร้างรายการแจ้งเตือนซ้ำ
     *
     * คืน true เมื่อเพิ่มรายการแจ้งเตือนใหม่สำเร็จ
     */
    public boolean recordOverdueAlertIfNeeded() {
        int count = countOverdueBills();

        if (count <= 0) {
            return false;
        }

        String now = getNow();
        String today = now.substring(0, 10);
        String eventKey = "OVERDUE_DEBT_" + today;

        ContentValues values = new ContentValues();
        values.put("event_type", "OVERDUE_DEBT");
        values.put(
                "message",
                "มีบิลเครดิตเลยกำหนดชำระ " + count + " บิล"
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

        return result != -1;
    }

    /**
     * รายการบิลเครดิตที่เลยกำหนดชำระ
     * เรียงจากบิลเก่าสุดก่อน
     *
     * คอลัมน์ที่คืน:
     * id, bill_number, total, created_at,
     * first_name, last_name, due_date
     */
    public Cursor getOverdueBills() {
        SQLiteDatabase db = database.getReadableDatabase();

        String sql =
                "SELECT s.id, s.bill_number, s.total, " +
                "s.created_at, c.first_name, c.last_name, " +
                "COALESCE(NULLIF(s.due_date, ''), " +
                "datetime(s.created_at, '+30 days')) AS due_date " +
                "FROM sales s " +
                "LEFT JOIN customers c ON c.id = s.customer_id " +
                "WHERE " + overdueCondition("s") +
                "ORDER BY due_date ASC, s.created_at ASC";

        return db.rawQuery(
                sql,
                new String[]{getNow()}
        );
    }

    /**
     * สรุปหนี้เลยกำหนดแยกตามลูกค้า
     *
     * คอลัมน์ที่คืน:
     * customer_id, first_name, last_name,
     * overdue_bill_count, overdue_amount, earliest_due_date
     *
     * บิลที่ไม่มี customer_id จะถูกรวมเป็นกลุ่ม
     * "ไม่ระบุลูกค้า" เพื่อไม่ให้ยอดหนี้หายไปจากสรุป
     */
    public Cursor getOverdueCustomers() {
        SQLiteDatabase db = database.getReadableDatabase();

        String sql =
                "SELECT " +
                "s.customer_id AS customer_id, " +
                "COALESCE(c.first_name, 'ไม่ระบุ') AS first_name, " +
                "COALESCE(c.last_name, 'ลูกค้า') AS last_name, " +
                "COUNT(s.id) AS overdue_bill_count, " +
                "COALESCE(SUM(s.total), 0) AS overdue_amount, " +
                "MIN(COALESCE(NULLIF(s.due_date, ''), " +
                "datetime(s.created_at, '+30 days'))) " +
                "AS earliest_due_date " +
                "FROM sales s " +
                "LEFT JOIN customers c ON c.id = s.customer_id " +
                "WHERE " + overdueCondition("s") +
                "GROUP BY s.customer_id, c.first_name, c.last_name " +
                "ORDER BY earliest_due_date ASC";

        return db.rawQuery(
                sql,
                new String[]{getNow()}
        );
    }

    /**
     * นับจำนวนบิลที่ยังค้างชำระของลูกค้ารายหนึ่ง
     * รวมเฉพาะบิลเครดิตที่สถานะ PENDING
     */
    public int countPendingBillsForCustomer(long customerId) {
        if (customerId <= 0) {
            return 0;
        }

        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM sales " +
                "WHERE customer_id = ? " +
                "AND payment_type = 'CREDIT' " +
                "AND status = 'PENDING'",
                new String[]{String.valueOf(customerId)}
        )) {
            return cursor.moveToFirst()
                    ? cursor.getInt(0)
                    : 0;
        }
    }

    /**
     * รวมยอดหนี้ที่ยังค้างชำระของลูกค้ารายหนึ่ง
     * ไม่รวมบิลที่ชำระแล้ว ยกเลิก หรือคืนเงิน
     */
    public double getPendingAmountForCustomer(long customerId) {
        if (customerId <= 0) {
            return 0.0d;
        }

        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(SUM(total), 0) FROM sales " +
                "WHERE customer_id = ? " +
                "AND payment_type = 'CREDIT' " +
                "AND status = 'PENDING'",
                new String[]{String.valueOf(customerId)}
        )) {
            return cursor.moveToFirst()
                    ? cursor.getDouble(0)
                    : 0.0d;
        }
    }

    /**
     * คืน Cursor สำหรับรายการบิลค้างชำระของลูกค้ารายหนึ่ง
     * ผู้เรียกต้องปิด Cursor เมื่อใช้งานเสร็จ
     */
    public Cursor getPendingBillsForCustomer(long customerId) {
        SQLiteDatabase db = database.getReadableDatabase();

        return db.rawQuery(
                "SELECT id, bill_number, total, created_at, " +
                "due_date, status " +
                "FROM sales " +
                "WHERE customer_id = ? " +
                "AND payment_type = 'CREDIT' " +
                "AND status = 'PENDING' " +
                "ORDER BY created_at ASC",
                new String[]{String.valueOf(customerId)}
        );
    }
    }
