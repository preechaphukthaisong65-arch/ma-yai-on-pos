
package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class SalesHistoryRepository {

    private final LocalDatabase helper;

    public SalesHistoryRepository(LocalDatabase helper) {
        if (helper == null) {
            throw new IllegalArgumentException("ฐานข้อมูลห้ามเป็น null");
        }
        this.helper = helper;
    }

    /**
     * ประวัติการขาย 30 วันล่าสุด
     * ยึดวันที่ขาย created_at และแสดงทุกสถานะ
     */
    public Cursor getRecentBills() {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT s.id, s.bill_number, s.payment_type, s.status, " +
                        "s.customer_id, " +
                        "COALESCE(c.first_name, '') AS first_name, " +
                        "COALESCE(c.last_name, '') AS last_name, " +
                        "s.subtotal, s.service_fee, s.total, " +
                        "s.cash_received, s.change_due, " +
                        "s.created_at, s.paid_at, s.print_status " +
                        "FROM sales s " +
                        "LEFT JOIN customers c ON c.id = s.customer_id " +
                        "WHERE date(s.created_at) >= " +
                        "date('now', '+7 hours', '-29 days') " +
                        "AND date(s.created_at) <= date('now', '+7 hours') " +
                        "ORDER BY s.created_at DESC, s.id DESC",
                null
        );
    }

    /**
     * ประวัติบิลที่ชำระแล้วใน 30 วันล่าสุด
     * ยังคง API เดิมไว้เพื่อไม่กระทบหน้าที่เรียกใช้
     */
    public Cursor getRecentPaidBills() {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT id, bill_number, payment_type, status, " +
                        "customer_id, subtotal, service_fee, total, " +
                        "paid_at, created_at, print_status " +
                        "FROM sales " +
                        "WHERE status = 'PAID' " +
                        "AND COALESCE(paid_at, created_at) >= " +
                        "datetime('now', '+7 hours', '-30 days') " +
                        "ORDER BY COALESCE(paid_at, created_at) DESC, id DESC",
                null
        );
    }

    /**
     * บิลเครดิตที่ยังค้างชำระทั้งหมด
     */
    public Cursor getAllPendingCreditBills() {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.query(
                "sales",
                new String[]{
                        "id",
                        "bill_number",
                        "customer_id",
                        "subtotal",
                        "service_fee",
                        "total",
                        "created_at",
                        "status"
                },
                "payment_type = 'CREDIT' AND status = 'PENDING'",
                null,
                null,
                null,
                "created_at ASC, id ASC"
        );
    }

    /**
     * รายละเอียดสินค้าในบิล
     * ใช้คอลัมน์ id ซึ่งมีอยู่จริงในตาราง sale_items
     */
    public Cursor getSaleItems(long saleId) {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.query(
                "sale_items",
                new String[]{
                        "id",
                        "barcode",
                        "product_name",
                        "unit",
                        "quantity",
                        "unit_price",
                        "line_total"
                },
                "sale_id = ?",
                new String[]{String.valueOf(saleId)},
                null,
                null,
                "id ASC"
        );
    }

    /**
     * ค้นหาบิลด้วยเลขบิลแบบตรงตัว
     */
    public Cursor findBill(String billNumber) {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.query(
                "sales",
                new String[]{
                        "id",
                        "bill_number",
                        "payment_type",
                        "status",
                        "customer_id",
                        "subtotal",
                        "service_fee",
                        "total",
                        "cash_received",
                        "change_due",
                        "created_at",
                        "paid_at",
                        "receipt_text",
                        "print_status"
                },
                "bill_number = ?",
                new String[]{
                        billNumber == null ? "" : billNumber.trim()
                },
                null,
                null,
                null,
                "1"
        );
    }

    /**
     * บันทึกสถานะการพิมพ์โดยไม่เปลี่ยนยอดขาย
     */
    public boolean updatePrintStatus(long saleId, String status) {
        if (saleId <= 0
                || !("PRINTED".equals(status)
                || "FAILED".equals(status)
                || "NOT_PRINTED".equals(status))) {
            return false;
        }

        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("print_status", status);

        return db.update(
                "sales",
                values,
                "id = ?",
                new String[]{String.valueOf(saleId)}
        ) == 1;
    }
}
