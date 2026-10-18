
package com.example.newapp;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class ReportRepository {

    private final LocalDatabase helper;

    public ReportRepository(LocalDatabase helper) {
        if (helper == null) {
            throw new IllegalArgumentException("ฐานข้อมูลห้ามเป็น null");
        }
        this.helper = helper;
    }

    /**
     * รายงานยอดขายตามช่วงเวลา
     *
     * period: DAY, WEEK, MONTH, ALL
     * รายงานนี้นับเฉพาะบิลที่ชำระแล้ว
     */
    public Cursor getSalesSummary(String period) {
        String condition;

        if ("DAY".equals(period)) {
            condition = "date(COALESCE(paid_at, created_at)) = date('now')";
        } else if ("WEEK".equals(period)) {
            condition = "date(COALESCE(paid_at, created_at)) >= " +
                    "date('now', '-6 days')";
        } else if ("MONTH".equals(period)) {
            condition = "date(COALESCE(paid_at, created_at)) >= " +
                    "date('now', 'start of month')";
        } else if ("ALL".equals(period)) {
            condition = "1 = 1";
        } else {
            throw new IllegalArgumentException(
                    "ช่วงเวลารายงานไม่ถูกต้อง"
            );
        }

        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT COUNT(*) AS bill_count, " +
                        "COALESCE(SUM(subtotal), 0) AS subtotal, " +
                        "COALESCE(SUM(service_fee), 0) AS service_fee, " +
                        "COALESCE(SUM(total), 0) AS total_sales " +
                        "FROM sales " +
                        "WHERE status = 'PAID' AND " + condition,
                null
        );
    }

    /**
     * รายงานยอดขายระหว่างวันที่
     * รูปแบบวันที่ yyyy-MM-dd
     */
    public Cursor getSalesSummaryByDateRange(
            String startDate,
            String endDate
    ) {
        if (startDate == null || endDate == null
                || startDate.trim().isEmpty()
                || endDate.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "กรุณาระบุวันที่เริ่มต้นและสิ้นสุด"
            );
        }

        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT COUNT(*) AS bill_count, " +
                        "COALESCE(SUM(subtotal), 0) AS subtotal, " +
                        "COALESCE(SUM(service_fee), 0) AS service_fee, " +
                        "COALESCE(SUM(total), 0) AS total_sales " +
                        "FROM sales " +
                        "WHERE status = 'PAID' " +
                        "AND date(COALESCE(paid_at, created_at)) " +
                        "BETWEEN date(?) AND date(?)",
                new String[]{startDate.trim(), endDate.trim()}
        );
    }

    /**
     * สินค้าขายดี 20 อันดับในช่วง 30 วันล่าสุด
     */
    public Cursor getTopSellingProducts() {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT si.product_id, si.barcode, " +
                        "si.product_name, si.unit, " +
                        "SUM(si.quantity) AS quantity_sold, " +
                        "SUM(si.line_total) AS sales_value " +
                        "FROM sale_items si " +
                        "INNER JOIN sales s ON s.id = si.sale_id " +
                        "WHERE s.status = 'PAID' " +
                        "AND date(COALESCE(s.paid_at, s.created_at)) " +
                        ">= date('now', '-29 days') " +
                        "GROUP BY si.product_id, si.barcode, " +
                        "si.product_name, si.unit " +
                        "ORDER BY quantity_sold DESC, sales_value DESC " +
                        "LIMIT 20",
                null
        );
    }

    /**
     * สินค้าที่ขายได้น้อยในกลุ่มที่มีการขายอย่างน้อยหนึ่งครั้ง
     * ไม่รวมสินค้าที่ไม่เคยขาย เพื่อไม่ให้ตีความว่าเป็นสินค้าขายช้า
     */
    public Cursor getSlowSellingProducts() {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT si.product_id, si.barcode, " +
                        "si.product_name, si.unit, " +
                        "SUM(si.quantity) AS quantity_sold, " +
                        "SUM(si.line_total) AS sales_value " +
                        "FROM sale_items si " +
                        "INNER JOIN sales s ON s.id = si.sale_id " +
                        "WHERE s.status = 'PAID' " +
                        "AND date(COALESCE(s.paid_at, s.created_at)) " +
                        ">= date('now', '-29 days') " +
                        "GROUP BY si.product_id, si.barcode, " +
                        "si.product_name, si.unit " +
                        "HAVING SUM(si.quantity) > 0 " +
                        "ORDER BY quantity_sold ASC, sales_value ASC " +
                        "LIMIT 20",
                null
        );
    }

    /**
     * ยอดขายรายวันสำหรับกราฟ
     */
    public Cursor getDailySalesForChart(int days) {
        if (days < 1 || days > 366) {
            throw new IllegalArgumentException(
                    "จำนวนวันต้องอยู่ระหว่าง 1 ถึง 366"
            );
        }

        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT date(COALESCE(paid_at, created_at)) AS sales_date, " +
                        "COALESCE(SUM(total), 0) AS total_sales " +
                        "FROM sales " +
                        "WHERE status = 'PAID' " +
                        "AND date(COALESCE(paid_at, created_at)) " +
                        ">= date('now', ?) " +
                        "GROUP BY date(COALESCE(paid_at, created_at)) " +
                        "ORDER BY sales_date ASC",
                new String[]{"-" + (days - 1) + " days"}
        );
    }
}
