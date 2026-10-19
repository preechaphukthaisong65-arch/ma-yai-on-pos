package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class SalesRepository {

    public static final String PAYMENT_CASH = "CASH";
    public static final String PAYMENT_CREDIT = "CREDIT";

    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    private static final double CREDIT_FEE_RATE = 0.05d;
    private static final int CREDIT_DUE_DAYS = 30;

    private static final TimeZone THAILAND_TIME_ZONE =
            TimeZone.getTimeZone("Asia/Bangkok");

    private final LocalDatabase helper;

    public SalesRepository(LocalDatabase helper) {
        if (helper == null) {
            throw new IllegalArgumentException(
                    "ฐานข้อมูลห้ามเป็น null"
            );
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
                    false,
                    message,
                    -1,
                    null,
                    0,
                    0,
                    0,
                    0
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
    }

    private static class PreparedLine {
        ProductSnapshot product;
        int quantity;
        double lineTotal;
    }

    /**
     * บันทึกการขายภายใน transaction เดียวกัน
     *
     * เงินสด:
     * - ไม่มีค่าบริการ
     * - ต้องรับเงินเพียงพอ
     *
     * ขายเชื่อ:
     * - คิดค่าบริการ 5%
     * - ต้องเลือกลูกค้า
     * - กำหนดวันครบกำหนดชำระ 30 วันหลังวันที่ขาย
     *
     * สำคัญ:
     * - ไม่ตัด products.stock_qty
     * - ไม่เพิ่มข้อมูล stock_movements
     * - ไม่ลบหรือแก้ไขข้อมูลสต๊อก
     */
    public SaleResult createSale(
            List<SaleLineInput> inputLines,
            String paymentType,
            Long customerId,
            double cashReceived
    ) {
        if (inputLines == null || inputLines.isEmpty()) {
            return SaleResult.failure(
                    "ไม่มีสินค้าในรายการขาย"
            );
        }

        if (!PAYMENT_CASH.equals(paymentType)
                && !PAYMENT_CREDIT.equals(paymentType)) {
            return SaleResult.failure(
                    "ประเภทการชำระเงินไม่ถูกต้อง"
            );
        }

        if (!validMoney(cashReceived)) {
            return SaleResult.failure(
                    "จำนวนเงินที่รับมาไม่ถูกต้อง"
            );
        }

        if (PAYMENT_CREDIT.equals(paymentType)
                && (customerId == null || customerId <= 0)) {
            return SaleResult.failure(
                    "การขายเชื่อต้องเลือกลูกค้า"
            );
        }

        SQLiteDatabase db = helper.getWritableDatabase();
        boolean transactionStarted = false;

        try {
            db.beginTransaction();
            transactionStarted = true;

            if (PAYMENT_CREDIT.equals(paymentType)
                    && !customerExists(db, customerId)) {
                return SaleResult.failure(
                        "ไม่พบข้อมูลลูกค้า"
                );
            }

            List<PreparedLine> lines =
                    prepareLines(db, inputLines);

            if (lines == null || lines.isEmpty()) {
                return SaleResult.failure(
                        "พบสินค้าไม่ถูกต้องหรือจำนวนสินค้าไม่ถูกต้อง"
                );
            }

            double subtotal = 0.0d;

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
                return SaleResult.failure(
                        "เงินที่รับมาไม่เพียงพอ"
                );
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
            sale.put("change_due", change);

            if (PAYMENT_CASH.equals(paymentType)) {
                sale.put("paid_at", timestamp);
                sale.putNull("due_date");
            } else {
                sale.putNull("paid_at");
                sale.put("due_date", calculateDueDate());
            }

            sale.put("receipt_text", "");
            sale.put("receipt_details_deleted", 0);
            sale.put("print_status", "NOT_PRINTED");
            sale.put("created_at", timestamp);

            long saleId = db.insertOrThrow(
                    "sales",
                    null,
                    sale
            );

            if (saleId <= 0) {
                return SaleResult.failure(
                        "สร้างบิลไม่สำเร็จ"
                );
            }

            // บันทึกเฉพาะรายการสินค้าที่ขาย
            // ห้ามตัดสต๊อกหรือสร้าง stock movement ที่นี่
            for (PreparedLine line : lines) {
                ProductSnapshot product = line.product;

                ContentValues item = new ContentValues();
                item.put("sale_id", saleId);
                item.put("product_id", product.id);
                item.put("barcode", product.barcode);
                item.put("product_name", product.name);
                item.put("unit", product.unit);
                item.put("quantity", line.quantity);
                item.put("unit_price", product.price);
                item.put("line_total", line.lineTotal);

                db.insertOrThrow(
                        "sale_items",
                        null,
                        item
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
            String message = e.getMessage();

            return SaleResult.failure(
                    "บันทึกการขายไม่สำเร็จ: "
                            + (message == null
                            ? "เกิดข้อผิดพลาด"
                            : message)
            );

        } finally {
            if (transactionStarted) {
                db.endTransaction();
            }
        }
    }

    /**
     * คำนวณวันครบกำหนดชำระสำหรับการขายเชื่อ
     * โดยใช้เขตเวลาไทยและรูปแบบวันที่ของฐานข้อมูล
     */
    private String calculateDueDate() {
        Calendar calendar = Calendar.getInstance(
                THAILAND_TIME_ZONE,
                Locale.US
        );

        calendar.add(Calendar.DAY_OF_MONTH, CREDIT_DUE_DAYS);

        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
        );

        format.setTimeZone(THAILAND_TIME_ZONE);

        return format.format(calendar.getTime());
    }

    private List<PreparedLine> prepareLines(
            SQLiteDatabase db,
            List<SaleLineInput> inputLines
    ) {
        LinkedHashMap<String, Integer> quantities =
                new LinkedHashMap<>();

        // รวมจำนวนของบาร์โค้ดเดียวกัน
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
            ProductSnapshot product =
                    findProduct(db, barcode);

            int quantity = quantities.get(barcode);

            if (product == null
                    || quantity <= 0
                    || !validMoney(product.price)) {
                return null;
            }

            double lineTotal = product.price * quantity;

            if (!validMoney(lineTotal)) {
                return null;
            }

            PreparedLine line = new PreparedLine();
            line.product = product;
            line.quantity = quantity;
            line.lineTotal = roundMoney(lineTotal);

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
                        "price"
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
        String timestamp = new SimpleDateFormat(
                "yyyyMMddHHmmss",
                Locale.US
        ).format(new Date());

        return "POS-" + timestamp + "-"
                + UUID.randomUUID()
                .toString()
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
```]]>
