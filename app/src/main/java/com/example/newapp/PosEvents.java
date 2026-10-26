package com.example.newapp;

import android.database.sqlite.SQLiteDatabase;

/**
 * Canonical event contract for financially significant POS operations.
 * Call only inside the same PosTransactions transaction as the business-state change.
 * Each event writes its report ledger and audit entry together, so neither can commit alone.
 */
final class PosEvents {
    private PosEvents() {}

    static void sale(SQLiteDatabase db, long billId, String billCode, String saleType,
                     long subtotalSatang, long feeSatang, long totalSatang, long now) {
        PosRecords.salesLedger(db, billId, billCode, saleType, "SALE",
                subtotalSatang, feeSatang, totalSatang, now);
        PosDatabase.audit(db, "SALE", "BILL", billCode,
                "type=" + saleType + "; subtotal_satang=" + subtotalSatang
                        + "; fee_satang=" + feeSatang + "; total_satang=" + totalSatang);
    }

    static void payment(SQLiteDatabase db, long billId, String billCode, long amountSatang,
                        long cashReceivedSatang, long changeSatang, long now) {
        PosRecords.paymentHistory(db, billId, billCode, amountSatang, now);
        PosDatabase.audit(db, "PAYMENT_RECEIVED", "BILL", billCode,
                "amount_satang=" + amountSatang + "; cash_received_satang="
                        + cashReceivedSatang + "; change_satang=" + changeSatang);
    }

    static void creditFeeWaived(SQLiteDatabase db, long billId, String billCode,
                                String saleType, long feeSatang, long now) {
        if (feeSatang < 0L) throw new IllegalArgumentException("ค่าบริการไม่ถูกต้อง");
        PosRecords.salesLedger(db, billId, billCode, saleType, "CREDIT_FEE_WAIVED",
                0L, Math.negateExact(feeSatang), Math.negateExact(feeSatang), now);
        PosDatabase.audit(db, "CREDIT_FEE_WAIVED", "BILL", billCode,
                "waived_fee_satang=" + feeSatang);
    }

    static void billReversal(SQLiteDatabase db, long billId, String billCode, String saleType,
                             String eventType, long reversalSubtotalSatang,
                             long reversalFeeSatang, long reversalTotalSatang, long now) {
        if (!"RETURN".equals(eventType) && !"CANCEL".equals(eventType))
            throw new IllegalArgumentException("ชนิดการย้อนบิลไม่ถูกต้อง");
        if (Math.addExact(reversalSubtotalSatang, reversalFeeSatang) != reversalTotalSatang)
            throw new IllegalArgumentException("ยอดย้อนบิลไม่ตรงกัน");
        PosRecords.salesLedger(db, billId, billCode, saleType, eventType,
                reversalSubtotalSatang, reversalFeeSatang, reversalTotalSatang, now);
        PosDatabase.audit(db, eventType, "BILL", billCode,
                "subtotal_satang=" + reversalSubtotalSatang + "; fee_satang="
                        + reversalFeeSatang + "; total_satang=" + reversalTotalSatang);
    }

    static void priceChange(SQLiteDatabase db, long productId, String barcode, String productName,
                            long oldPriceSatang, long newPriceSatang, long now) {
        PosRecords.priceChange(db, productId, barcode, productName, oldPriceSatang, newPriceSatang, now);
        PosDatabase.audit(db, "PRICE_CHANGED", "PRODUCT", String.valueOf(productId),
                "barcode=" + barcode + "; old_price_satang=" + oldPriceSatang
                        + "; new_price_satang=" + newPriceSatang);
    }
}
