
package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.UUID;

public class CreditPaymentRepository {

    private final LocalDatabase database;

    public CreditPaymentRepository(LocalDatabase database) {
        this.database = database;
    }

    public static class PaymentResult {
        public final boolean success;
        public final String message;
        public final String receiptNumber;
        public final double amount;

        public PaymentResult(
                boolean success,
                String message,
                String receiptNumber,
                double amount
        ) {
            this.success = success;
            this.message = message;
            this.receiptNumber = receiptNumber;
            this.amount = amount;
        }
    }

    public PaymentResult payFullBill(long saleId) {
        SQLiteDatabase db = database.getWritableDatabase();

        String receiptNumber = "CP-" +
                UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 12)
                        .toUpperCase();

        double total;
        long customerId;
        String originalBillNumber;

        db.beginTransaction();

        try {
            try (Cursor cursor = db.rawQuery(
                    "SELECT bill_number, customer_id, total, payment_type, status " +
                    "FROM sales WHERE id = ? LIMIT 1",
                    new String[]{String.valueOf(saleId)})) {

                if (!cursor.moveToFirst()) {
                    return failure("ไม่พบบิลที่ต้องการชำระ");
                }

                originalBillNumber = cursor.getString(0);
                customerId = cursor.isNull(1) ? -1 : cursor.getLong(1);
                total = cursor.getDouble(2);

                String paymentType = cursor.getString(3);
                String status = cursor.getString(4);

                if (!"CREDIT".equalsIgnoreCase(paymentType)) {
                    return failure("บิลนี้ไม่ใช่บิลเครดิต");
                }

                if (!"PENDING".equalsIgnoreCase(status)) {
                    return failure("บิลนี้ไม่ได้อยู่ในสถานะค้างชำระ");
                }
            }

            if (total <= 0) {
                return failure("ยอดบิลไม่ถูกต้อง");
            }

            String now = LocalDatabase.now();

            ContentValues payment = new ContentValues();
            if (customerId >= 0) {
                payment.put("customer_id", customerId);
            }
            payment.put("sale_id", saleId);
            payment.put("original_bill_number", originalBillNumber);
            payment.put("payment_receipt_number", receiptNumber);
            payment.put("amount", total);
            payment.put("created_at", now);

            db.insertOrThrow("credit_payments", null, payment);

            ContentValues update = new ContentValues();
            update.put("status", "PAID");
            update.put("paid_at", now);

            int updated = db.update(
                    "sales",
                    update,
                    "id = ? AND payment_type = 'CREDIT' AND status = 'PENDING'",
                    new String[]{String.valueOf(saleId)}
            );

            if (updated != 1) {
                throw new IllegalStateException(
                        "สถานะบิลเปลี่ยนไปแล้ว กรุณาตรวจสอบก่อนรับชำระอีกครั้ง"
                );
            }

            db.setTransactionSuccessful();

            return new PaymentResult(
                    true,
                    "รับชำระเต็มบิลสำเร็จ",
                    receiptNumber,
                    total
            );

        } catch (Exception e) {
            return failure(
                    e.getMessage() == null
                            ? "เกิดข้อผิดพลาดระหว่างรับชำระ"
                            : e.getMessage()
            );

        } finally {
            db.endTransaction();
        }
    }

    public Cursor getPaymentHistory(long saleId) {
        SQLiteDatabase db = database.getReadableDatabase();

        return db.rawQuery(
                "SELECT payment_receipt_number, original_bill_number, amount, created_at " +
                "FROM credit_payments WHERE sale_id = ? ORDER BY created_at DESC",
                new String[]{String.valueOf(saleId)}
        );
    }

    public Cursor getAllPaymentHistory() {
        SQLiteDatabase db = database.getReadableDatabase();

        return db.rawQuery(
                "SELECT cp.payment_receipt_number, cp.original_bill_number, " +
                "cp.amount, cp.created_at, c.first_name, c.last_name " +
                "FROM credit_payments cp " +
                "LEFT JOIN customers c ON c.id = cp.customer_id " +
                "ORDER BY cp.created_at DESC",
                null
        );
    }

    private PaymentResult failure(String message) {
        return new PaymentResult(false, message, null, 0);
    }
}
