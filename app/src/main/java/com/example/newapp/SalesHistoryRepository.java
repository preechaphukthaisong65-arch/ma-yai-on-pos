
package com.example.newapp;

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
     * ประวัติบิลที่ชำระแล้วในช่วง 30 วันล่าสุด
     * ใช้ paid_at เป็นวันเริ่มนับสำหรับบิลเชื่อที่ชำระภายหลัง
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
                        "datetime('now', '-30 days') " +
                        "ORDER BY COALESCE(paid_at, created_at) DESC",
                null
        );
    }

    /**
     * บิลค้างชำระทั้งหมด ไม่จำกัดอายุบิล
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
                "created_at ASC"
        );
    }

    /**
     * รายละเอียดรายการสินค้าในบิล
     */
    public Cursor getSaleItems(long saleId) {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.query(
                "sale_items",
                new String[]{
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
                "_id ASC"
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
                        "change_amount",
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
     * ใช้ PRINTED หรือ FAILED ตามผลที่เครื่องพิมพ์รายงาน
     */
    public boolean updatePrintStatus(long saleId, String status) {
        if (saleId <= 0
                || !("PRINTED".equals(status)
                || "FAILED".equals(status)
                || "NOT_PRINTED".equals(status))) {
            return false;
        }

        SQLiteDatabase db = helper.getWritableDatabase();

        android.content.ContentValues values =
                new android.content.ContentValues();
        values.put("print_status", status);

        return db.update(
                "sales",
                values,
                "id = ?",
                new String[]{String.valueOf(saleId)}
        ) == 1;
    }
}
