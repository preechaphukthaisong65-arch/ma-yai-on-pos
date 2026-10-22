package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;

/**
 * Single entry point for committing a sale. The bill, its items, stock changes,
 * stock history and sales ledger are written in one SQLite transaction.
 */
final class SalesTransactionService {
    static final class Line {
        final long productId;
        final String barcode;
        final String name;
        final String unit;
        final int quantity;
        final int expectedStock;
        final long unitPriceSatang;

        Line(long productId, String barcode, String name, String unit,
             int quantity, int expectedStock, long unitPriceSatang) {
            this.productId = productId;
            this.barcode = barcode;
            this.name = name;
            this.unit = unit;
            this.quantity = quantity;
            this.expectedStock = expectedStock;
            this.unitPriceSatang = unitPriceSatang;
        }
    }

    static final class Result {
        final long billId;
        final String billCode;
        final long subtotalSatang;
        final long feeSatang;
        final long totalSatang;
        final long cashReceivedSatang;
        final long changeSatang;

        Result(long billId, String billCode, long subtotalSatang, long feeSatang,
               long totalSatang, long cashReceivedSatang, long changeSatang) {
            this.billId = billId;
            this.billCode = billCode;
            this.subtotalSatang = subtotalSatang;
            this.feeSatang = feeSatang;
            this.totalSatang = totalSatang;
            this.cashReceivedSatang = cashReceivedSatang;
            this.changeSatang = changeSatang;
        }
    }

    private SalesTransactionService() {}

    static Result commit(PosDatabase helper, List<Line> lines, boolean credit,
                         String customerName, long cashReceivedSatang, long timestamp) {
        if (helper == null) throw new IllegalArgumentException("ฐานข้อมูลไม่พร้อมใช้งาน");
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("ไม่มีสินค้าในบิล");
        if (credit && (customerName == null || customerName.trim().isEmpty())) {
            throw new IllegalArgumentException("กรอกชื่อเต็มลูกค้า");
        }

        long subtotal = 0;
        for (Line line : lines) {
            if (line == null || line.productId <= 0 || line.quantity <= 0 ||
                    line.expectedStock < 0 || line.unitPriceSatang < 0 ||
                    line.barcode == null || line.barcode.trim().isEmpty() ||
                    line.name == null || line.name.trim().isEmpty() ||
                    line.unit == null || line.unit.trim().isEmpty()) {
                throw new IllegalArgumentException("ข้อมูลสินค้าในบิลไม่ถูกต้อง");
            }
            if (line.quantity > line.expectedStock) {
                throw new IllegalStateException("สินค้า " + line.name + " มีสต๊อกไม่พอ");
            }
            subtotal = Math.addExact(subtotal, Math.multiplyExact(line.unitPriceSatang, (long) line.quantity));
        }
        long fee = credit ? PosDatabase.creditFee(subtotal) : 0;
        long total = Math.addExact(subtotal, fee);
        if (!credit && cashReceivedSatang < total) {
            throw new IllegalArgumentException("เงินรับไม่พอ");
        }
        long change = credit ? 0 : cashReceivedSatang - total;

        SQLiteDatabase db = helper.getWritableDatabase();
        long billId;
        String billCode;
        db.beginTransaction();
        try {
            billCode = nextBillCode(db, timestamp);
            Long customerId = null;
            if (credit) {
                Cursor c = db.query("customers", new String[]{"id"}, "full_name=?",
                        new String[]{customerName.trim()}, null, null, null, "1");
                try {
                    if (c.moveToFirst()) customerId = c.getLong(0);
                } finally {
                    c.close();
                }
                if (customerId == null) {
                    ContentValues customer = new ContentValues();
                    customer.put("full_name", customerName.trim());
                    customer.put("created_at", timestamp);
                    customerId = db.insertOrThrow("customers", null, customer);
                }
            }

            ContentValues bill = new ContentValues();
            bill.put("bill_code", billCode);
            if (customerId != null) bill.put("customer_id", customerId);
            bill.put("sale_type", credit ? "CREDIT" : "CASH");
            bill.put("status", credit ? "UNPAID" : "PAID");
            bill.put("subtotal_satang", subtotal);
            bill.put("fee_satang", fee);
            bill.put("total_satang", total);
            bill.put("cash_received_satang", credit ? 0 : cashReceivedSatang);
            bill.put("change_satang", change);
            bill.put("created_at", timestamp);
            if (!credit) bill.put("paid_at", timestamp);
            billId = db.insertOrThrow("bills", null, bill);

            for (Line line : lines) {
                InventoryService.changeStock(db, line.productId, line.barcode, line.name,
                        line.expectedStock, line.expectedStock - line.quantity, "SALE", billCode, timestamp);

                ContentValues item = new ContentValues();
                item.put("bill_id", billId);
                item.put("product_id", line.productId);
                item.put("barcode", line.barcode);
                item.put("product_name", line.name);
                item.put("unit", line.unit);
                item.put("quantity", line.quantity);
                item.put("unit_price_satang", line.unitPriceSatang);
                item.put("line_total_satang", Math.multiplyExact(line.unitPriceSatang, (long) line.quantity));
                db.insertOrThrow("bill_items", null, item);

                ContentValues ledgerItem = new ContentValues();
                ledgerItem.put("bill_id", billId);
                ledgerItem.put("bill_code", billCode);
                ledgerItem.put("event_type", "SALE");
                ledgerItem.put("barcode", line.barcode);
                ledgerItem.put("product_name", line.name);
                ledgerItem.put("quantity", line.quantity);
                ledgerItem.put("unit_price_satang", line.unitPriceSatang);
                ledgerItem.put("line_total_satang", Math.multiplyExact(line.unitPriceSatang, (long) line.quantity));
                ledgerItem.put("created_at", timestamp);
                db.insertOrThrow("sales_ledger_items", null, ledgerItem);
            }

            ContentValues ledger = new ContentValues();
            ledger.put("bill_id", billId);
            ledger.put("bill_code", billCode);
            ledger.put("sale_type", credit ? "CREDIT" : "CASH");
            ledger.put("event_type", "SALE");
            ledger.put("subtotal_satang", subtotal);
            ledger.put("fee_satang", fee);
            ledger.put("total_satang", total);
            ledger.put("created_at", timestamp);
            db.insertOrThrow("sales_ledger", null, ledger);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return new Result(billId, billCode, subtotal, fee, total,
                credit ? 0 : cashReceivedSatang, change);
    }

    private static String nextBillCode(SQLiteDatabase db, long timestamp) {
        String base = "B" + timestamp;
        String candidate = base;
        int suffix = 1;
        while (true) {
            Cursor c = db.query("bills", new String[]{"id"}, "bill_code=?",
                    new String[]{candidate}, null, null, null, "1");
            boolean exists;
            try {
                exists = c.moveToFirst();
            } finally {
                c.close();
            }
            if (!exists) return candidate;
            candidate = base + "-" + (suffix++);
        }
    }
}
