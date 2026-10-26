package com.example.newapp;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/** Shared writers for operational history and sales-ledger records. */
final class PosRecords {
    private PosRecords() {}

    static void stockMovement(SQLiteDatabase db, Long productId, String barcode,
                              String productName, String changeType, int beforeQty,
                              int afterQty, String reference, long createdAt) {
        ContentValues row = new ContentValues();
        if (productId != null) row.put("product_id", productId);
        row.put("barcode", barcode);
        row.put("product_name", productName);
        row.put("change_type", changeType);
        row.put("before_qty", beforeQty);
        row.put("after_qty", afterQty);
        row.put("difference", Math.subtractExact(afterQty, beforeQty));
        if (reference != null) row.put("reference", reference);
        row.put("created_at", createdAt);
        db.insertOrThrow("stock_history", null, row);
    }

    static void salesLedger(SQLiteDatabase db, long billId, String billCode,
                            String saleType, String eventType, long subtotal,
                            long fee, long total, long createdAt) {
        ContentValues row = new ContentValues();
        row.put("bill_id", billId);
        row.put("bill_code", billCode);
        row.put("sale_type", saleType);
        row.put("event_type", eventType);
        row.put("subtotal_satang", subtotal);
        row.put("fee_satang", fee);
        row.put("total_satang", total);
        row.put("created_at", createdAt);
        db.insertOrThrow("sales_ledger", null, row);
    }

    static void salesLedgerItem(SQLiteDatabase db, long billId, String billCode,
                                String eventType, String barcode, String productName,
                                int quantity, long unitPriceSatang, long lineTotalSatang,
                                long createdAt) {
        ContentValues row = new ContentValues();
        row.put("bill_id", billId);
        row.put("bill_code", billCode);
        row.put("event_type", eventType);
        row.put("barcode", barcode);
        row.put("product_name", productName);
        row.put("quantity", quantity);
        row.put("unit_price_satang", unitPriceSatang);
        row.put("line_total_satang", lineTotalSatang);
        row.put("created_at", createdAt);
        db.insertOrThrow("sales_ledger_items", null, row);
    }

    static void priceChange(SQLiteDatabase db, long productId, String barcode,
                            String productName, long oldPriceSatang,
                            long newPriceSatang, long createdAt) {
        ContentValues row = new ContentValues();
        row.put("product_id", productId);
        row.put("barcode", barcode);
        row.put("product_name", productName);
        row.put("old_price_satang", oldPriceSatang);
        row.put("new_price_satang", newPriceSatang);
        row.put("created_at", createdAt);
        db.insertOrThrow("price_history", null, row);
    }

    static void paymentHistory(SQLiteDatabase db, long billId, String billCode,
                               long amountSatang, long createdAt) {
        ContentValues row = new ContentValues();
        row.put("bill_id", billId);
        row.put("bill_code", billCode);
        row.put("amount_satang", amountSatang);
        row.put("created_at", createdAt);
        db.insertOrThrow("payment_history", null, row);
    }
}
