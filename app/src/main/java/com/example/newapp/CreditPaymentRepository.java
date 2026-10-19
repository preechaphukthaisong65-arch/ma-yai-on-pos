package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Locale;
import java.util.UUID;

public class CreditPaymentRepository {

    private final LocalDatabase database;

    public CreditPaymentRepository(LocalDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "ฐานข้อมูลห้ามเป็น null"
            );
        }
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

    /**
     * รับชำระบิลเครดิตเต็มจำนวน
     *
     * หน้าจอปัจจุบันเรียก payFullBill(saleId)
     * จึงรับชำระด้วยเงินสดเท่ากับยอดบิล
     *
     * บันทึกประวัติรับชำระและอัปเดตบิล
     * ภายใน Transaction เดียวกัน
     */
    public PaymentResult payFullBill(long saleId) {
        if (saleId <= 0) {
            return failure("รหัสบิลไม่ถูกต้อง");
        }

        SQLiteDatabase db = database.getWritableDatabase();

        String receiptNumber = "CP-" +
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 12)
                        .toUpperCase(Locale.US);

        db.beginTransaction();

        try {
            String originalBillNumber;
            double total;
            String paymentType;
            String status;

            try (Cursor cursor = db.rawQuery(
                    "SELECT bill_number, total, payment_type, status " +
                    "FROM sales WHERE id = ? LIMIT 1",
                    new String[]{String.valueOf(saleId)}
            )) {
                if (!cursor.moveToFirst()) {
                    return failure("ไม่พบบิลที่ต้องการชำระ");
                }

                originalBillNumber = cursor.getString(0);
                total = cursor.getDouble(1);
                paymentType = cursor.getString(2);
                status = cursor.getString(3);
            }

            if (!"CREDIT".equalsIgnoreCase(paymentType)) {
                return failure("บิลนี้ไม่ใช่บิลเครดิต");
            }

            if (!"PENDING".equalsIgnoreCase(status)) {
                return failure("บิลนี้ไม่ได้อยู่ในสถานะค้างชำระ");
            }

            if (Double.isNaN(total)
                    || Double.isInfinite(total)
                    || total <= 0) {
                return failure("ยอดบิลไม่ถูกต้อง");
            }

            // หน้าจอเดิมรับชำระเต็มจำนวนโดยไม่รับยอดเงินจากผู้ใช้
            double cashReceived = total;
            double change = 0.0d;
            String now = LocalDatabase.now();

            String receiptText =
                    "ใบเสร็จรับชำระหนี้\n" +
                    "เลขที่ใบเสร็จ: " + receiptNumber + "\n" +
                    "บิลต้นฉบับ: " + originalBillNumber + "\n" +
                    "วันที่รับชำระ: " + now + "\n" +
                    String.format(
                            Locale.US,
                            "ยอดรับชำระ: %.2f บาท\n",
                            total
                    ) +
                    "วิธีชำระ: เงินสด\n" +
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

            // บันทึกประวัติรับชำระตาม Schema ใน LocalDatabase
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

            // อัปเดตสถานะและยอดรับเงินจริงให้สอดคล้องกับประวัติชำระ
            ContentValues update = new ContentValues();
            update.put("status", "PAID");
            update.put("cash_received", cashReceived);
            update.put("change_due", change);
            update.put("paid_at", now);

            int updated = db.update(
                    "sales",
                    update,
                    "id = ? AND payment_type = 'CREDIT' " +
                    "AND status = 'PENDING'",
                    new String[]{String.valueOf(saleId)}
            );

            if (updated != 1) {
                throw new IllegalStateException(
                        "อัปเดตสถานะบิลไม่สำเร็จ " +
                        "กรุณาตรวจสอบสถานะบิลอีกครั้ง"
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
                            : "รับชำระไม่สำเร็จ: " + e.getMessage()
            );

        } finally {
            db.endTransaction();
        }
    }

    /**
     * ประวัติรับชำระของบิลหนึ่งใบ
     */
    public Cursor getPaymentHistory(long saleId) {
        SQLiteDatabase db = database.getReadableDatabase();

        return db.rawQuery(
                "SELECT " +
                "cp.payment_number AS payment_receipt_number, " +
                "s.bill_number AS original_bill_number, " +
                "cp.amount AS amount, " +
                "cp.paid_at AS created_at " +
                "FROM credit_payments cp " +
                "INNER JOIN sales s ON s.id = cp.sale_id " +
                "WHERE cp.sale_id = ? " +
                "ORDER BY cp.paid_at DESC, cp.id DESC",
                new String[]{String.valueOf(saleId)}
        );
    }

    /**
     * ประวัติรับชำระทั้งหมด
     */
    public Cursor getAllPaymentHistory() {
        SQLiteDatabase db = database.getReadableDatabase();

        return db.rawQuery(
                "SELECT " +
                "cp.payment_number AS payment_receipt_number, " +
                "s.bill_number AS original_bill_number, " +
                "cp.amount AS amount, " +
                "cp.paid_at AS created_at, " +
                "c.first_name AS first_name, " +
                "c.last_name AS last_name " +
                "FROM credit_payments cp " +
                "INNER JOIN sales s ON s.id = cp.sale_id " +
                "LEFT JOIN customers c ON c.id = s.customer_id " +
                "ORDER BY cp.paid_at DESC, cp.id DESC",
                null
        );
    }

    private PaymentResult failure(String message) {
        return new PaymentResult(
                false,
                message,
                null,
                0
        );
    }
    }
