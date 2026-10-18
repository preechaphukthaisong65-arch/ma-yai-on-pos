
package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class CreditAccountRepository {

    private final LocalDatabase helper;

    public CreditAccountRepository(LocalDatabase helper) {
        if (helper == null) {
            throw new IllegalArgumentException("ฐานข้อมูลห้ามเป็น null");
        }
        this.helper = helper;
    }

    /**
     * รายชื่อลูกค้าที่มีบิลค้างชำระ
     * เรียงจากยอดหนี้รวมมากไปน้อย
     */
    public Cursor getCustomersWithDebt() {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT c.id AS customer_id, " +
                        "c.first_name, c.last_name, " +
                        "SUM(s.total) AS total_debt, " +
                        "COUNT(s.id) AS unpaid_bill_count " +
                        "FROM customers c " +
                        "INNER JOIN sales s ON s.customer_id = c.id " +
                        "WHERE s.payment_type = 'CREDIT' " +
                        "AND s.status = 'PENDING' " +
                        "GROUP BY c.id, c.first_name, c.last_name " +
                        "ORDER BY total_debt DESC",
                null
        );
    }

    /**
     * บิลค้างชำระของลูกค้าหนึ่งคน
     */
    public Cursor getUnpaidBills(long customerId) {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.query(
                "sales",
                new String[]{
                        "id",
                        "bill_number",
                        "created_at",
                        "subtotal",
                        "service_fee",
                        "total",
                        "status"
                },
                "customer_id = ? AND payment_type = 'CREDIT' " +
                        "AND status = 'PENDING'",
                new String[]{String.valueOf(customerId)},
                null,
                null,
                "created_at ASC"
        );
    }

    /**
     * รับชำระเต็มจำนวนของบิลเชื่อหนึ่งใบ
     * ไม่รองรับการชำระบางส่วน
     */
    public PaymentResult payFullBill(
            long saleId,
            double cashReceived,
            String paymentReceiptNumber
    ) {
        if (saleId <= 0
                || !validMoney(cashReceived)
                || paymentReceiptNumber == null
                || paymentReceiptNumber.trim().isEmpty()) {
            return PaymentResult.failure("ข้อมูลรับชำระไม่ถูกต้อง");
        }

        SQLiteDatabase db = helper.getWritableDatabase();
        boolean started = false;

        try {
            db.beginTransaction();
            started = true;

            Cursor cursor = db.query(
                    "sales",
                    new String[]{
                            "id",
                            "bill_number",
                            "customer_id",
                            "total",
                            "status",
                            "payment_type"
                    },
                    "id = ?",
                    new String[]{String.valueOf(saleId)},
                    null,
                    null,
                    null,
                    "1"
            );

            long customerId;
            String originalBill;
            double total;
            String status;
            String paymentType;

            try {
                if (!cursor.moveToFirst()) {
                    return PaymentResult.failure("ไม่พบบิลต้นฉบับ");
                }

                originalBill = cursor.getString(
                        cursor.getColumnIndexOrThrow("bill_number")
                );
                total = cursor.getDouble(
                        cursor.getColumnIndexOrThrow("total")
                );
                status = cursor.getString(
                        cursor.getColumnIndexOrThrow("status")
                );
                paymentType = cursor.getString(
                        cursor.getColumnIndexOrThrow("payment_type")
                );

                int customerIndex =
                        cursor.getColumnIndexOrThrow("customer_id");

                if (cursor.isNull(customerIndex)) {
                    return PaymentResult.failure(
                            "บิลนี้ไม่มีข้อมูลลูกค้า"
                    );
                }

                customerId = cursor.getLong(customerIndex);
            } finally {
                cursor.close();
            }

            if (!"CREDIT".equals(paymentType)
                    || !"PENDING".equals(status)) {
                return PaymentResult.failure(
                        "บิลนี้ไม่ใช่บิลเชื่อที่ค้างชำระ"
                );
            }

            if (cashReceived < total) {
                return PaymentResult.failure(
                        "ต้องรับชำระเต็มจำนวน " + total + " บาท"
                );
            }

            String now = LocalDatabase.now();

            ContentValues payment = new ContentValues();
            payment.put("customer_id", customerId);
            payment.put("sale_id", saleId);
            payment.put("original_bill_number", originalBill);
            payment.put(
                    "payment_receipt_number",
                    paymentReceiptNumber.trim()
            );
            payment.put("amount", total);
            payment.put("created_at", now);

            db.insertOrThrow(
                    "credit_payments",
                    null,
                    payment
            );

            ContentValues update = new ContentValues();
            update.put("status", "PAID");
            update.put("cash_received", total);
            update.put("change_amount", roundMoney(cashReceived - total));
            update.put("paid_at", now);

            int updated = db.update(
                    "sales",
                    update,
                    "id = ? AND status = 'PENDING' " +
                            "AND payment_type = 'CREDIT'",
                    new String[]{String.valueOf(saleId)}
            );

            if (updated != 1) {
                return PaymentResult.failure(
                        "เปลี่ยนสถานะบิลไม่สำเร็จ"
                );
            }

            db.setTransactionSuccessful();

            return PaymentResult.success(
                    saleId,
                    originalBill,
                    total,
                    roundMoney(cashReceived - total)
            );

        } catch (Exception e) {
            return PaymentResult.failure(
                    "รับชำระไม่สำเร็จ: " +
                            (e.getMessage() == null
                                    ? "เกิดข้อผิดพลาด"
                                    : e.getMessage())
            );
        } finally {
            if (started) {
                db.endTransaction();
            }
        }
    }

    public static class PaymentResult {
        public final boolean success;
        public final String message;
        public final long saleId;
        public final String originalBillNumber;
        public final double paidAmount;
        public final double changeAmount;

        private PaymentResult(
                boolean success,
                String message,
                long saleId,
                String originalBillNumber,
                double paidAmount,
                double changeAmount
        ) {
            this.success = success;
            this.message = message;
            this.saleId = saleId;
            this.originalBillNumber = originalBillNumber;
            this.paidAmount = paidAmount;
            this.changeAmount = changeAmount;
        }

        static PaymentResult failure(String message) {
            return new PaymentResult(
                    false, message, -1, null, 0, 0
            );
        }

        static PaymentResult success(
                long saleId,
                String billNumber,
                double amount,
                double change
        ) {
            return new PaymentResult(
                    true,
                    "รับชำระเต็มบิลสำเร็จ",
                    saleId,
                    billNumber,
                    amount,
                    change
            );
        }
    }

    private boolean validMoney(double amount) {
        return !Double.isNaN(amount)
                && !Double.isInfinite(amount)
                && amount >= 0;
    }

    private double roundMoney(double amount) {
        return Math.round(amount * 100.0d) / 100.0d;
    }
}
