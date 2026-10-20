package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Locale;

public class CreditAccountRepository {

    private final LocalDatabase helper;

    public CreditAccountRepository(LocalDatabase helper) {
        if (helper == null) {
            throw new IllegalArgumentException(
                    "ฐานข้อมูลห้ามเป็น null"
            );
        }
        this.helper = helper;
    }

    /**
     * รายชื่อลูกค้าที่มีบิลเครดิตค้างชำระ
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
     * บิลเครดิตที่ยังค้างชำระของลูกค้าหนึ่งคน
     * คงลำดับคอลัมน์เดิมไว้ และเพิ่ม due_date ต่อท้าย
     * ผู้เรียกต้องปิด Cursor หลังใช้งาน
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
                        "status",
                        "due_date"
                },
                "customer_id = ? " +
                "AND payment_type = 'CREDIT' " +
                "AND status = 'PENDING'",
                new String[]{String.valueOf(customerId)},
                null,
                null,
                "created_at ASC, id ASC"
        );
    }

    /**
     * รับชำระบิลเครดิตเต็มจำนวน
     *
     * บันทึกประวัติรับชำระและเปลี่ยนสถานะบิล
     * ภายใน Transaction เดียวกัน
     *
     * การขายและสต๊อกยังคงแยกจากกัน
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
            return PaymentResult.failure(
                    "ข้อมูลรับชำระไม่ถูกต้อง"
            );
        }

        String receiptNumber = paymentReceiptNumber.trim();

        SQLiteDatabase db = helper.getWritableDatabase();
        boolean transactionStarted = false;

        try {
            db.beginTransaction();
            transactionStarted = true;

            String originalBill;
            double total;
            String status;
            String paymentType;

            try (Cursor cursor = db.rawQuery(
                    "SELECT bill_number, total, status, payment_type " +
                    "FROM sales WHERE id = ? LIMIT 1",
                    new String[]{String.valueOf(saleId)}
            )) {
                if (!cursor.moveToFirst()) {
                    return PaymentResult.failure(
                            "ไม่พบบิลต้นฉบับ"
                    );
                }

                originalBill = cursor.getString(0);
                total = cursor.getDouble(1);
                status = cursor.getString(2);
                paymentType = cursor.getString(3);
            }

            if (!"CREDIT".equalsIgnoreCase(paymentType)
                    || !"PENDING".equalsIgnoreCase(status)) {
                return PaymentResult.failure(
                        "บิลนี้ไม่ใช่บิลเครดิตที่ค้างชำระ"
                );
            }

            if (!validMoney(total) || total <= 0) {
                return PaymentResult.failure(
                        "ยอดบิลไม่ถูกต้อง"
                );
            }

            if (cashReceived < total) {
                return PaymentResult.failure(
                        "ต้องรับชำระเต็มจำนวน " +
                        String.format(
                                Locale.US,
                                "%.2f",
                                total
                        ) +
                        " บาท"
                );
            }

            double change = roundMoney(cashReceived - total);
            String now = LocalDatabase.now();

            String receiptText =
                    "ใบเสร็จรับชำระหนี้\n" +
                    "เลขที่ใบเสร็จ: " + receiptNumber + "\n" +
                    "บิลต้นฉบับ: " + originalBill + "\n" +
                    "วันที่รับชำระ: " + now + "\n" +
                    String.format(
                            Locale.US,
                            "ยอดหนี้: %.2f บาท\n",
                            total
                    ) +
                    String.format(
                            Locale.US,
                            "รับเงินสด: %.2f บาท\n",
                            cashReceived
                    ) +
                    String.format(
                            Locale.US,
                            "เงินทอน: %.2f บาท",
                            change
                    );

            ContentValues payment = new ContentValues();
            payment.put("payment_number", receiptNumber);
            payment.put("sale_id", saleId);
            payment.put("amount", total);
            payment.put("cash_received", cashReceived);
            payment.put("change_due", change);
            payment.put("paid_at", now);
            payment.put("receipt_text", receiptText);
            payment.put("print_status", "NOT_PRINTED");

            db.insertOrThrow(
                    "credit_payments",
                    null,
                    payment
            );

            ContentValues update = new ContentValues();
            update.put("status", "PAID");
            update.put("cash_received", cashReceived);
            update.put("change_due", change);
            update.put("paid_at", now);

            int updated = db.update(
                    "sales",
                    update,
                    "id = ? " +
                    "AND payment_type = 'CREDIT' " +
                    "AND status = 'PENDING'",
                    new String[]{String.valueOf(saleId)}
            );

            if (updated != 1) {
                throw new IllegalStateException(
                        "เปลี่ยนสถานะบิลไม่สำเร็จ " +
                        "กรุณาตรวจสอบสถานะบิลอีกครั้ง"
                );
            }

            db.setTransactionSuccessful();

            return PaymentResult.success(
                    saleId,
                    originalBill,
                    total,
                    change
            );

        } catch (Exception e) {
            return PaymentResult.failure(
                    "รับชำระไม่สำเร็จ: " +
                    (e.getMessage() == null
                            ? "เกิดข้อผิดพลาด"
                            : e.getMessage())
            );

        } finally {
            if (transactionStarted) {
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
                    false,
                    message,
                    -1,
                    null,
                    0,
                    0
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
