package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Single transaction boundary for collecting payment against one or more unpaid credit bills.
 * The UI may preview full-customer or selected-bill payments, but all eligibility, fee-waiver,
 * total calculation, state changes, payment history and ledger writes are owned here.
 */
final class CreditPaymentService {
    static final class BillQuote {
        final long billId;
        final String billCode;
        final String saleType;
        final long subtotalSatang;
        final long feeSatang;
        final long originalTotalSatang;
        final long payableSatang;
        final boolean feeWaived;

        BillQuote(long billId, String billCode, String saleType, long subtotalSatang,
                  long feeSatang, long originalTotalSatang, long payableSatang, boolean feeWaived) {
            this.billId = billId;
            this.billCode = billCode;
            this.saleType = saleType;
            this.subtotalSatang = subtotalSatang;
            this.feeSatang = feeSatang;
            this.originalTotalSatang = originalTotalSatang;
            this.payableSatang = payableSatang;
            this.feeWaived = feeWaived;
        }
    }

    static final class Quote {
        final long customerId;
        final long timestamp;
        final long totalSatang;
        final List<BillQuote> bills;

        Quote(long customerId, long timestamp, long totalSatang, List<BillQuote> bills) {
            this.customerId = customerId;
            this.timestamp = timestamp;
            this.totalSatang = totalSatang;
            this.bills = bills;
        }
    }

    static final class Result {
        final long totalSatang;
        final List<BillQuote> bills;

        Result(long totalSatang, List<BillQuote> bills) {
            this.totalSatang = totalSatang;
            this.bills = bills;
        }
    }

    private CreditPaymentService() {}

    static Quote quote(PosDatabase helper, long customerId, List<Long> billIds, long timestamp) {
        if (helper == null || customerId <= 0) throw new IllegalArgumentException("ข้อมูลลูกค้าไม่ถูกต้อง");
        Set<Long> uniqueIds = validateIds(billIds);
        SQLiteDatabase db = helper.getReadableDatabase();
        ArrayList<BillQuote> quotes = new ArrayList<>();
        long total = 0;
        for (long id : uniqueIds) {
            Cursor c = db.query("bills",
                    new String[]{"bill_code", "sale_type", "subtotal_satang", "fee_satang", "total_satang", "created_at", "status"},
                    "id=? AND customer_id=?", new String[]{String.valueOf(id), String.valueOf(customerId)},
                    null, null, null, "1");
            try {
                if (!c.moveToFirst()) throw new IllegalStateException("ไม่พบบิลของลูกค้าที่เลือก");
                if (!"UNPAID".equals(c.getString(6))) throw new IllegalStateException("บิลนี้ไม่ได้อยู่ในสถานะค้างชำระแล้ว");
                BillQuote q = quoteBill(id, c.getString(0), c.getString(1), c.getLong(2),
                        c.getLong(3), c.getLong(4), c.getLong(5), timestamp);
                total = Math.addExact(total, q.payableSatang);
                quotes.add(q);
            } finally {
                c.close();
            }
        }
        return new Quote(customerId, timestamp, total, quotes);
    }

    static Result commit(PosDatabase helper, long customerId, List<Long> billIds,
                         long expectedTotalSatang, long timestamp) {
        if (helper == null || customerId <= 0) throw new IllegalArgumentException("ข้อมูลลูกค้าไม่ถูกต้อง");
        Set<Long> uniqueIds = validateIds(billIds);
        SQLiteDatabase db = helper.getWritableDatabase();
        ArrayList<BillQuote> committedBills = new ArrayList<>();
        long actualTotal = 0;
        db.beginTransaction();
        try {
            for (long id : uniqueIds) {
                Cursor c = db.query("bills",
                        new String[]{"bill_code", "sale_type", "subtotal_satang", "fee_satang", "total_satang", "created_at", "status"},
                        "id=? AND customer_id=?", new String[]{String.valueOf(id), String.valueOf(customerId)},
                        null, null, null, "1");
                BillQuote q;
                try {
                    if (!c.moveToFirst()) throw new IllegalStateException("ไม่พบบิลของลูกค้าที่เลือก");
                    if (!"UNPAID".equals(c.getString(6))) throw new IllegalStateException("บิลนี้ถูกชำระไปแล้วหรือไม่อยู่ในสถานะค้างชำระ");
                    q = quoteBill(id, c.getString(0), c.getString(1), c.getLong(2),
                            c.getLong(3), c.getLong(4), c.getLong(5), timestamp);
                } finally {
                    c.close();
                }

                ContentValues paid = new ContentValues();
                paid.put("status", "PAID");
                paid.put("paid_at", timestamp);
                if (db.update("bills", paid, "id=? AND customer_id=? AND status='UNPAID'",
                        new String[]{String.valueOf(id), String.valueOf(customerId)}) != 1) {
                    throw new IllegalStateException("สถานะบิลเปลี่ยนแล้ว กรุณาตรวจสอบใหม่");
                }

                ContentValues payment = new ContentValues();
                payment.put("bill_id", id);
                payment.put("amount_satang", q.payableSatang);
                payment.put("created_at", timestamp);
                db.insertOrThrow("payments", null, payment);

                ContentValues history = new ContentValues();
                history.put("bill_id", id);
                history.put("bill_code", q.billCode);
                history.put("amount_satang", q.payableSatang);
                history.put("created_at", timestamp);
                db.insertOrThrow("payment_history", null, history);

                if (q.feeWaived && q.feeSatang > 0) {
                    ContentValues adjustment = new ContentValues();
                    adjustment.put("bill_id", id);
                    adjustment.put("bill_code", q.billCode);
                    adjustment.put("sale_type", q.saleType);
                    adjustment.put("event_type", "CREDIT_FEE_WAIVED");
                    adjustment.put("subtotal_satang", 0);
                    adjustment.put("fee_satang", -q.feeSatang);
                    adjustment.put("total_satang", -q.feeSatang);
                    adjustment.put("created_at", timestamp);
                    db.insertOrThrow("sales_ledger", null, adjustment);
                }
                AuditService.record(db,"BILL",q.billCode,"CREDIT_PAYMENT",
                        "amount="+q.payableSatang+";feeWaived="+q.feeWaived,timestamp);
                actualTotal = Math.addExact(actualTotal, q.payableSatang);
                committedBills.add(q);
            }
            if (actualTotal != expectedTotalSatang) {
                throw new IllegalStateException("ยอดชำระเปลี่ยนระหว่างยืนยัน กรุณาตรวจสอบยอดใหม่ก่อนรับเงิน");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return new Result(actualTotal, committedBills);
    }

    private static BillQuote quoteBill(long id, String code, String type, long subtotal,
                                       long fee, long originalTotal, long createdAt, long now) {
        if (subtotal < 0 || fee < 0 || originalTotal < 0 || createdAt < 0) {
            throw new IllegalStateException("ยอดเงินหรือวันเวลาของบิลไม่ถูกต้อง");
        }
        if (!"CREDIT".equals(type)) {
            throw new IllegalStateException("บิลค้างชำระต้องเป็นบิลขายเชื่อ");
        }
        long expectedTotal;
        try { expectedTotal = Math.addExact(subtotal, fee); }
        catch (ArithmeticException e) { throw new IllegalStateException("ยอดบิลเกินขอบเขตที่รองรับ"); }
        if (originalTotal != expectedTotal) {
            throw new IllegalStateException("ยอดรวมบิลไม่ตรงกับยอดสินค้าและค่าบริการ");
        }
        boolean waive = fee > 0 && now >= createdAt &&
                now - createdAt <= ReportingService.FEE_WAIVER_WINDOW_MS;
        long payable = ReportingService.payableNow(type, subtotal, fee, originalTotal, createdAt, now);
        return new BillQuote(id, code, type, subtotal, fee, originalTotal, payable, waive);
    }

    private static Set<Long> validateIds(List<Long> billIds) {
        if (billIds == null || billIds.isEmpty()) throw new IllegalArgumentException("เลือกอย่างน้อยหนึ่งบิล");
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : billIds) {
            if (id == null || id <= 0 || !unique.add(id)) {
                throw new IllegalArgumentException("รายการบิลไม่ถูกต้องหรือมีบิลซ้ำ");
            }
        }
        return unique;
    }
}
