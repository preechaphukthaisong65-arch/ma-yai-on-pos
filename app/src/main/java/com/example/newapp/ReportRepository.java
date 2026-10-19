
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
     * รายงานยอดขายตามวันที่ขาย
     *
     * period: DAY, WEEK, MONTH, ALL
     *
     * นับทุกบิลทั้ง PAID และ PENDING
     * PAID คือยอดบิลที่ชำระแล้ว
     * PENDING คือยอดบิลที่ยังค้างชำระ
     *
     * ใช้วันที่ขาย created_at ไม่ใช้ paid_at
     * เพื่อให้บิลที่ชำระภายหลังยังอยู่ในช่วงวันที่ขายเดิม
     */
    public Cursor getSalesSummary(String period) {
        String condition = buildPeriodCondition(
                period,
                "created_at"
        );

        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT " +
                        "COUNT(*) AS bill_count, " +
                        "COALESCE(SUM(CASE " +
                        "WHEN status = 'PAID' THEN 1 ELSE 0 END), 0) " +
                        "AS paid_bill_count, " +
                        "COALESCE(SUM(CASE " +
                        "WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) " +
                        "AS pending_bill_count, " +
                        "COALESCE(SUM(subtotal), 0) AS subtotal, " +
                        "COALESCE(SUM(service_fee), 0) AS service_fee, " +
                        "COALESCE(SUM(total), 0) AS total_sales, " +
                        "COALESCE(SUM(CASE " +
                        "WHEN status = 'PAID' THEN total ELSE 0 END), 0) " +
                        "AS paid_sales, " +
                        "COALESCE(SUM(CASE " +
                        "WHEN status = 'PENDING' THEN total ELSE 0 END), 0) " +
                        "AS pending_sales " +
                        "FROM sales " +
                        "WHERE " + condition,
                null
        );
    }

    /**
     * รายงานยอดขายระหว่างวันที่ขาย
     * รูปแบบวันที่ yyyy-MM-dd
     *
     * รวมทุกสถานะและแยกยอด PAID/PENDING
     */
    public Cursor getSalesSummaryByDateRange(
            String startDate,
            String endDate
    ) {
        validateDateRange(startDate, endDate);

        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT " +
                        "COUNT(*) AS bill_count, " +
                        "COALESCE(SUM(CASE " +
                        "WHEN status = 'PAID' THEN 1 ELSE 0 END), 0) " +
                        "AS paid_bill_count, " +
                        "COALESCE(SUM(CASE " +
                        "WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) " +
                        "AS pending_bill_count, " +
                        "COALESCE(SUM(subtotal), 0) AS subtotal, " +
                        "COALESCE(SUM(service_fee), 0) AS service_fee, " +
                        "COALESCE(SUM(total), 0) AS total_sales, " +
                        "COALESCE(SUM(CASE " +
                        "WHEN status = 'PAID' THEN total ELSE 0 END), 0) " +
                        "AS paid_sales, " +
                        "COALESCE(SUM(CASE " +
                        "WHEN status = 'PENDING' THEN total ELSE 0 END), 0) " +
                        "AS pending_sales " +
                        "FROM sales " +
                        "WHERE date(created_at) BETWEEN date(?) AND date(?)",
                new String[]{
                        startDate.trim(),
                        endDate.trim()
                }
        );
    }

    /**
     * รายงานยอดค้างชำระตามช่วงเวลา
     *
     * period: DAY, MONTH, YEAR
     *
     * ยอดค้างชำระนับจากบิล PENDING
     * และอ้างอิงวันที่ขาย created_at
     */
    public Cursor getDebtSummary(String period) {
        String condition;

        if ("DAY".equals(period)) {
            condition =
                    "date(created_at) = date('now', '+7 hours')";
        } else if ("MONTH".equals(period)) {
            condition =
                    "date(created_at) >= " +
                    "date('now', '+7 hours', 'start of month') " +
                    "AND date(created_at) <= date('now', '+7 hours')";
        } else if ("YEAR".equals(period)) {
            condition =
                    "date(created_at) >= " +
                    "date('now', '+7 hours', 'start of year') " +
                    "AND date(created_at) <= date('now', '+7 hours')";
        } else {
            throw new IllegalArgumentException(
                    "ช่วงเวลารายงานหนี้ไม่ถูกต้อง"
            );
        }

        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT " +
                        "COUNT(*) AS pending_bill_count, " +
                        "COALESCE(SUM(total), 0) AS pending_debt " +
                        "FROM sales " +
                        "WHERE status = 'PENDING' " +
                        "AND payment_type = 'CREDIT' " +
                        "AND " + condition,
                null
        );
    }

    /**
     * รายงานยอดค้างชำระระหว่างวันที่ขาย
     * รูปแบบวันที่ yyyy-MM-dd
     */
    public Cursor getDebtSummaryByDateRange(
            String startDate,
            String endDate
    ) {
        validateDateRange(startDate, endDate);

        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT " +
                        "COUNT(*) AS pending_bill_count, " +
                        "COALESCE(SUM(total), 0) AS pending_debt " +
                        "FROM sales " +
                        "WHERE status = 'PENDING' " +
                        "AND payment_type = 'CREDIT' " +
                        "AND date(created_at) BETWEEN date(?) AND date(?)",
                new String[]{
                        startDate.trim(),
                        endDate.trim()
                }
        );
    }

    /**
     * สินค้าขายดี 20 อันดับในช่วง 30 วันล่าสุด
     * นับสินค้าที่ขายในบิลทั้ง PAID และ PENDING
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
                        "WHERE date(s.created_at) >= " +
                        "date('now', '+7 hours', '-29 days') " +
                        "AND date(s.created_at) <= date('now', '+7 hours') " +
                        "GROUP BY si.product_id, si.barcode, " +
                        "si.product_name, si.unit " +
                        "ORDER BY quantity_sold DESC, sales_value DESC " +
                        "LIMIT 20",
                null
        );
    }

    /**
     * สินค้าที่ขายได้น้อยในกลุ่มที่มีการขายอย่างน้อยหนึ่งครั้ง
     * ใช้ข้อมูล 30 วันล่าสุดและนับบิลทุกสถานะ
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
                        "WHERE date(s.created_at) >= " +
                        "date('now', '+7 hours', '-29 days') " +
                        "AND date(s.created_at) <= date('now', '+7 hours') " +
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
     * รวมบิลทุกสถานะตามวันที่ขาย
     */
    public Cursor getDailySalesForChart(int days) {
        if (days < 1 || days > 366) {
            throw new IllegalArgumentException(
                    "จำนวนวันต้องอยู่ระหว่าง 1 ถึง 366"
            );
        }

        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT date(created_at) AS sales_date, " +
                        "COALESCE(SUM(total), 0) AS total_sales, " +
                        "COALESCE(SUM(CASE " +
                        "WHEN status = 'PAID' THEN total ELSE 0 END), 0) " +
                        "AS paid_sales, " +
                        "COALESCE(SUM(CASE " +
                        "WHEN status = 'PENDING' THEN total ELSE 0 END), 0) " +
                        "AS pending_sales " +
                        "FROM sales " +
                        "WHERE date(created_at) >= " +
                        "date('now', '+7 hours', ?) " +
                        "AND date(created_at) <= date('now', '+7 hours') " +
                        "GROUP BY date(created_at) " +
                        "ORDER BY sales_date ASC",
                new String[]{
                        "-" + (days - 1) + " days"
                }
        );
    }

    /**
     * สร้างเงื่อนไขช่วงรายงาน
     * SQLite ใช้เวลาปัจจุบัน UTC แล้วบวก 7 ชั่วโมง
     * เพื่อหาวันปัจจุบันตามเวลาไทย
     */
    private String buildPeriodCondition(
            String period,
            String dateColumn
    ) {
        if ("DAY".equals(period)) {
            return "date(" + dateColumn + ") = " +
                    "date('now', '+7 hours')";
        }

        if ("WEEK".equals(period)) {
            return "date(" + dateColumn + ") >= " +
                    "date('now', '+7 hours', 'weekday 0', '-6 days') " +
                    "AND date(" + dateColumn + ") <= " +
                    "date('now', '+7 hours')";
        }

        if ("MONTH".equals(period)) {
            return "date(" + dateColumn + ") >= " +
                    "date('now', '+7 hours', 'start of month') " +
                    "AND date(" + dateColumn + ") <= " +
                    "date('now', '+7 hours')";
        }

        if ("ALL".equals(period)) {
            return "1 = 1";
        }

        throw new IllegalArgumentException(
                "ช่วงเวลารายงานไม่ถูกต้อง"
        );
    }

    private void validateDateRange(
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

        if (!startDate.trim().matches("\\d{4}-\\d{2}-\\d{2}")
                || !endDate.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException(
                    "รูปแบบวันที่ต้องเป็น yyyy-MM-dd"
            );
        }

        if (startDate.trim().compareTo(endDate.trim()) > 0) {
            throw new IllegalArgumentException(
                    "วันที่เริ่มต้นต้องไม่เกินวันที่สิ้นสุด"
            );
        }
    }
}
