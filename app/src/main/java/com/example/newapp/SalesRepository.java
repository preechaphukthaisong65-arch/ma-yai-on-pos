
package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class SalesRepository {

    public static final String PAYMENT_CASH = "CASH";
    public static final String PAYMENT_CREDIT = "CREDIT";

    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_PENDING = "PENDING";

    private static final double CREDIT_FEE_RATE = 0.05d;

    private final LocalDatabase helper;

    public SalesRepository(LocalDatabase helper) {
        if (helper == null) {
            throw new IllegalArgumentException("ฐานข้อมูลห้ามเป็น null");
        }
        this.helper = helper;
    }

    public static class SaleLineInput {
        public final String barcode;
        public final int quantity;

        public SaleLineInput(String barcode, int quantity) {
            this.barcode = barcode;
            this.quantity = quantity;
        }
    }

    public static class SaleResult {
        public final boolean success;
        public final String message;
        public final long saleId;
        public final String billNumber;
        public final double subtotal;
        public final double serviceFee;
        public final double total;
        public final double changeAmount;

        private SaleResult(
                boolean success,
                String message,
                long saleId,
                String billNumber,
                double subtotal,
                double serviceFee,
                double total,
                double changeAmount
        ) {
            this.success = success;
            this.message = message;
            this.saleId = saleId;
            this.billNumber = billNumber;
            this.subtotal = subtotal;
            this.serviceFee = serviceFee;
            this.total = total;
            this.changeAmount = changeAmount;
        }

        static SaleResult failure(String message) {
            return new SaleResult(
                    false, message, -1, null, 0, 0, 0, 0
            );
        }

        static SaleResult success(
                long id,
                String bill,
                double subtotal,
                double fee,
                double total,
                double change
        ) {
            return new SaleResult(
                    true,
                    "บันทึกการขายสำเร็จ",
                    id,
                    bill,
                    subtotal,
                    fee,
                    total,
                    change
            );
        }
    }

    private static class ProductSnapshot {
        long id;
        String barcode;
        String name;
        String unit;
        double price;
        int stockQty;
    }

    private static class PreparedLine {
        ProductSnapshot product;
        int quantity;
        double lineTotal;
    }

    /**
     * เงินสด: ไม่มีค่าบริการและต้องรับเงินเพียงพอ
     * ขายเชื่อ: คิดค่าบริการ 5% และต้องเลือกลูกค้า
     * บันทึกบิล รายการสินค้า และตัดสต๊อกใน transaction เดียวกัน
     */
    public SaleResult createSale(
            List<SaleLineInput> inputLines,
            String paymentType,
            Long customerId,
            double cashReceived
    ) {
        if (inputLines == null || inputLines.isEmpty()) {
            return SaleResult.failure("ไม่มีสินค้าในรายการขาย");
        }

        if (!PAYMENT_CASH.equals(paymentType)
                && !PAYMENT_CREDIT.equals(paymentType)) {
            return SaleResult.failure("ประเภทการชำระเงินไม่ถูกต้อง");
        }

        if (!validMoney(cashReceived)) {
            return SaleResult.failure("จำนวนเงินที่รับมาไม่ถูกต้อง");
        }

        if (PAYMENT_CREDIT.equals(paymentType)
                && (customerId == null || customerId <= 0)) {
            return SaleResult.failure("การขายเชื่อต้องเลือกลูกค้า");
        }

        SQLiteDatabase db = helper.getWritableDatabase();
        boolean transactionStarted = false;

        try {
            db.beginTransaction();
            transactionStarted = true;

            if (PAYMENT_CREDIT.equals(paymentType)
                    && !customerExists(db, customerId)) {
                return SaleResult.failure("ไม่พบข้อมูลลูกค้า");
            }

            List<PreparedLine> lines = prepareLines(db, inputLines);

            if (lines == null || lines.isEmpty()) {
                return SaleResult.failure(
                        "พบสินค้าไม่ถูกต้องหรือจำนวนสต๊อกไม่เพียงพอ"
                );
            }

            double subtotal = 0;
            for (PreparedLine line : lines) {
                subtotal += line.lineTotal;
            }

            subtotal = roundMoney(subtotal);

            double fee = PAYMENT_CREDIT.equals(paymentType)
                    ? roundMoney(subtotal * CREDIT_FEE_RATE)
                    : 0.0d;

            double total = roundMoney(subtotal + fee);

            if (PAYMENT_CASH.equals(paymentType)
                    && cashReceived < total) {
                return SaleResult.failure("เงินที่รับมาไม่เพียงพอ");
            }

            double actualCash = PAYMENT_CASH.equals(paymentType)
                    ? cashReceived
                    : 0.0d;

            double change = PAYMENT_CASH.equals(paymentType)
                    ? roundMoney(cashReceived - total)
                    : 0.0d;

            String billNumber = createUniqueBillNumber();
            String timestamp = LocalDatabase.now();

            ContentValues sale = new ContentValues();
            sale.put("bill_number", billNumber);
            sale.put("payment_type", paymentType);
            sale.put(
                    "status",
                    PAYMENT_CASH.equals(paymentType)
                            ? STATUS_PAID
                            : STATUS_PENDING
            );

            if (customerId == null) {
                sale.putNull("customer_id");
            } else {
                sale.put("customer_id", customerId);
            }

            sale.put("subtotal", subtotal);
            sale.put("service_fee", fee);
            sale.put("total", total);
            sale.put("cash_received", actualCash);

            // ชื่อต้องตรงกับ LocalDatabase.java
            sale.put("change_due", change);

            if (PAYMENT_CASH.equals(paymentType)) {
                sale.put("paid_at", timestamp);
            } else {
                sale.putNull("paid_at");
            }

            sale.put("receipt_text", "");
            sale.put("print_status", "NOT_PRINTED");
            sale.put("created_at", timestamp);

            long saleId = db.insertOrThrow("sales", null, sale);

            for (PreparedLine line : lines) {
                ProductSnapshot product = line.product;

                int currentStock;
                try (Cursor stockCursor = db.query(
                        "products",
                        new String[]{"stock_qty"},
                        "id = ?",
                        new String[]{String.valueOf(product.id)},
                        null,
                        null,
                        null,
                        "1"
                )) {
                    if (!stockCursor.moveToFirst()) {
                        return SaleResult.failure("ไม่พบสินค้าในคลัง");
                    }
                    currentStock = stockCursor.getInt(0);
                }

                if (currentStock < line.quantity) {
                    return SaleResult.failure(
                            "สต๊อกไม่เพียงพอสำหรับ " + product.name
                    );
                }

                ContentValues item = new ContentValues();
                item.put("sale_id", saleId);
                item.put("product_id", product.id);
                item.put("barcode", product.barcode);
                item.put("product_name", product.name);
                item.put("unit", product.unit);
                item.put("quantity", line.quantity);
                item.put("unit_price", product.price);
                item.put("line_total", line.lineTotal);

                db.insertOrThrow("sale_items", null, item);

                ContentValues stockUpdate = new ContentValues();
                stockUpdate.put(
                        "stock_qty",
                        currentStock - line.quantity
                );
                stockUpdate.put("updated_at", timestamp);

                int updated = db.update(
                        "products",
                        stockUpdate,
                        "id = ? AND stock_qty >= ?",
                        new String[]{
                                String.valueOf(product.id),
                                String.valueOf(line.quantity)
                        }
                );

                if (updated != 1) {
                    return SaleResult.failure(
                            "ตัดสต๊อกไม่สำเร็จ: " + product.name
                    );
                }

                ContentValues movement = new ContentValues();
                movement.put("product_id", product.id);
                movement.put("operation", "SALE");
                movement.put("qty_before", currentStock);
                movement.put(
                        "qty_after",
                        currentStock - line.quantity
                );
                movement.put("qty_diff", -line.quantity);
                movement.put("note", "ขายตามบิล " + billNumber);
                movement.put("created_at", timestamp);

                db.insertOrThrow(
                        "stock_movements",
                        null,
                        movement
                );
            }

            db.setTransactionSuccessful();

            return SaleResult.success(
                    saleId,
                    billNumber,
                    subtotal,
                    fee,
                    total,
                    change
            );

        } catch (Exception e) {
            return SaleResult.failure(
                    "บันทึกการขายไม่สำเร็จ: " +
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

    private List<PreparedLine> prepareLines(
            SQLiteDatabase db,
            List<SaleLineInput> inputLines
    ) {
        java.util.LinkedHashMap<String, Integer> quantities =
                new java.util.LinkedHashMap<>();

        for (SaleLineInput input : inputLines) {
            if (input == null
                    || input.barcode == null
                    || input.barcode.trim().isEmpty()
                    || input.quantity <= 0) {
                return null;
            }

            String barcode = input.barcode.trim();
            int oldQty = quantities.containsKey(barcode)
                    ? quantities.get(barcode)
                    : 0;

            long newQty = (long) oldQty + input.quantity;
            if (newQty > Integer.MAX_VALUE) {
                return null;
            }

            quantities.put(barcode, (int) newQty);
        }

        List<PreparedLine> result = new ArrayList<>();

        for (String barcode : quantities.keySet()) {
            ProductSnapshot product = findProduct(db, barcode);
            int quantity = quantities.get(barcode);

            if (product == null || product.stockQty < quantity) {
                return null;
            }

            PreparedLine line = new PreparedLine();
            line.product = product;
            line.quantity = quantity;
            line.lineTotal = roundMoney(product.price * quantity);

            result.add(line);
        }

        return result;
    }

    private ProductSnapshot findProduct(
            SQLiteDatabase db,
            String barcode
    ) {
        try (Cursor cursor = db.query(
                "products",
                new String[]{
                        "id",
                        "barcode",
                        "name",
                        "unit",
                        "price",
                        "stock_qty"
                },
                "barcode = ?",
                new String[]{barcode},
                null,
                null,
                null,
                "1"
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            ProductSnapshot product = new ProductSnapshot();
            product.id = cursor.getLong(
                    cursor.getColumnIndexOrThrow("id")
            );
            product.barcode = cursor.getString(
                    cursor.getColumnIndexOrThrow("barcode")
            );
            product.name = cursor.getString(
                    cursor.getColumnIndexOrThrow("name")
            );
            product.unit = cursor.getString(
                    cursor.getColumnIndexOrThrow("unit")
            );
            product.price = cursor.getDouble(
                    cursor.getColumnIndexOrThrow("price")
            );
            product.stockQty = cursor.getInt(
                    cursor.getColumnIndexOrThrow("stock_qty")
            );

            return product;
        }
    }

    private boolean customerExists(
            SQLiteDatabase db,
            Long customerId
    ) {
        try (Cursor cursor = db.query(
                "customers",
                new String[]{"id"},
                "id = ?",
                new String[]{String.valueOf(customerId)},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }

    private String createUniqueBillNumber() {
        String timestamp = new java.text.SimpleDateFormat(
                "yyyyMMddHHmmss",
                Locale.US
        ).format(new java.util.Date());

        return "POS-" + timestamp + "-"
                + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.US);
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
