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

    static void saleItem(SQLiteDatabase db, long billId, String billCode, String barcode,
                         String productName, int quantity, long unitPriceSatang,
                         long lineTotalSatang, long now) {
        if (quantity <= 0 || unitPriceSatang < 0L || lineTotalSatang < 0L)
            throw new IllegalArgumentException("รายการขายไม่ถูกต้อง");
        PosRecords.salesLedgerItem(db, billId, billCode, "SALE", barcode, productName,
                quantity, unitPriceSatang, lineTotalSatang, now);
    }

    static void reversalItem(SQLiteDatabase db, long billId, String billCode, String eventType,
                             String barcode, String productName, int quantity,
                             long unitPriceSatang, long lineTotalSatang, long now) {
        if (!"RETURN".equals(eventType) && !"CANCEL".equals(eventType))
            throw new IllegalArgumentException("ชนิดการย้อนรายการไม่ถูกต้อง");
        if (quantity >= 0 || lineTotalSatang > 0L || unitPriceSatang < 0L)
            throw new IllegalArgumentException("จำนวนรายการย้อนบิลไม่ถูกต้อง");
        PosRecords.salesLedgerItem(db, billId, billCode, eventType, barcode, productName,
                quantity, unitPriceSatang, lineTotalSatang, now);
    }

    static void payment(SQLiteDatabase db, long billId, String billCode,
                        long amountSatang, long now) {
        PosRecords.paymentHistory(db, billId, billCode, amountSatang, now);
        // This audit entry is per bill; batch cash tender and change are logged once
        // by CreditAccountScreen so they are not duplicated across every bill.
        PosDatabase.audit(db, "PAYMENT_RECEIVED", "BILL", billCode,
                "amount_satang=" + amountSatang);
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

    static void stockChange(SQLiteDatabase db, long productId, String barcode, String productName,
                            String changeType, int beforeQty, int afterQty, String reference, long now) {
        PosRecords.stockMovement(db, productId, barcode, productName, changeType,
                beforeQty, afterQty, reference, now);
        PosDatabase.audit(db, "STOCK_" + changeType, "PRODUCT", String.valueOf(productId),
                "barcode=" + barcode + "; before_qty=" + beforeQty + "; after_qty=" + afterQty
                        + "; reference=" + (reference == null ? "" : reference));
    }

    static void stockLimitChanged(SQLiteDatabase db, long productId, int oldLimit, int newLimit) {
        if (oldLimit < 0 || newLimit < 0) throw new IllegalArgumentException("ลิมิตสต็อกไม่ถูกต้อง");
        PosDatabase.audit(db, "STOCK_LIMIT_CHANGED", "PRODUCT", String.valueOf(productId),
                "old_limit=" + oldLimit + "; new_limit=" + newLimit);
    }

    static void productCreated(SQLiteDatabase db, long productId, String barcode, String name,
                               long priceSatang, int initialQty, int stockLimit, long now) {
        PosRecords.stockMovement(db, productId, barcode, name, "INITIAL", 0, initialQty, null, now);
        PosDatabase.audit(db, "PRODUCT_CREATED", "PRODUCT", String.valueOf(productId),
                "barcode=" + barcode + "; name=" + name + "; price_satang=" + priceSatang
                        + "; stock=" + initialQty + "; stock_limit=" + stockLimit);
    }

    static void customerProfileUpdated(SQLiteDatabase db, long customerId,
                                       String oldName, String newName) {
        PosDatabase.audit(db, "CUSTOMER_PROFILE_UPDATED", "CUSTOMER", String.valueOf(customerId),
                "name_before=" + oldName + "; name_after=" + newName);
    }

    static void barcodeRegistered(SQLiteDatabase db, String barcode, String name,
                                  long priceSatang, String source) {
        PosDatabase.audit(db, "BARCODE_REGISTERED", "BARCODE", barcode,
                "name=" + name + "; price_satang=" + priceSatang + "; source=" + source);
    }

    static void priceChange(SQLiteDatabase db, long productId, String barcode, String productName,
                            long oldPriceSatang, long newPriceSatang, long now) {
        PosRecords.priceChange(db, productId, barcode, productName, oldPriceSatang, newPriceSatang, now);
        PosDatabase.audit(db, "PRICE_CHANGED", "PRODUCT", String.valueOf(productId),
                "barcode=" + barcode + "; old_price_satang=" + oldPriceSatang
                        + "; new_price_satang=" + newPriceSatang);
    }
}
