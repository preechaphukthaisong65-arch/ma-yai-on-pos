
package com.example.newapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

public class ProductRepository {

    private final LocalDatabase helper;

    public ProductRepository(Context context) {
        helper = LocalDatabase.getInstance(context);
    }

    public static class Product {
        public long id;
        public String barcode;
        public String name;
        public double price;
        public double cost;
        public String unit;
        public int stockQty;
        public int minStock;
        public String createdAt;
        public String updatedAt;
    }

    /**
     * ค้นหาสินค้าด้วยบาร์โค้ดตรงตัวเท่านั้น
     */
    public Product findByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return null;
        }

        try (Cursor cursor = helper.findProductByBarcode(
                barcode.trim())) {

            if (!cursor.moveToFirst()) {
                return null;
            }

            return readProduct(cursor);
        }
    }

    /**
     * อ่านสินค้าจาก Cursor ซึ่งต้องมีคอลัมน์ครบตามตาราง products
     */
    private Product readProduct(Cursor cursor) {
        Product p = new Product();

        p.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        p.barcode = cursor.getString(
                cursor.getColumnIndexOrThrow("barcode"));
        p.name = cursor.getString(
                cursor.getColumnIndexOrThrow("name"));
        p.price = cursor.getDouble(
                cursor.getColumnIndexOrThrow("price"));
        p.cost = cursor.getDouble(
                cursor.getColumnIndexOrThrow("cost"));
        p.unit = cursor.getString(
                cursor.getColumnIndexOrThrow("unit"));
        p.stockQty = cursor.getInt(
                cursor.getColumnIndexOrThrow("stock_qty"));
        p.minStock = cursor.getInt(
                cursor.getColumnIndexOrThrow("min_stock"));
        p.createdAt = cursor.getString(
                cursor.getColumnIndexOrThrow("created_at"));
        p.updatedAt = cursor.getString(
                cursor.getColumnIndexOrThrow("updated_at"));

        return p;
    }

    /**
     * แสดงสินค้าทั้งหมด เรียงจากจำนวนคงเหลือน้อยไปมาก
     * ผู้เรียกต้องปิด Cursor หลังใช้งาน
     */
    public Cursor getAllProductsByStockAscending() {
        return helper.getReadableDatabase().query(
                "products",
                new String[]{
                        "id",
                        "barcode",
                        "name",
                        "price",
                        "cost",
                        "unit",
                        "stock_qty",
                        "min_stock",
                        "created_at",
                        "updated_at"
                },
                null,
                null,
                null,
                null,
                "stock_qty ASC, name COLLATE NOCASE ASC"
        );
    }

    /**
     * สร้างสินค้าใหม่โดยยังไม่รับสินค้าเข้าคลัง
     * ใช้สำหรับกรณีที่ระบบอื่นจัดการการรับสต๊อกแยกต่างหาก
     *
     * หากต้องการเพิ่มสินค้าใหม่พร้อมรับสต๊อก
     * ให้ใช้เมธอดรับสินค้าใน InventoryRepository
     */
    public long createProduct(
            String barcode,
            String name,
            double price,
            double cost,
            String unit,
            int minStock
    ) {
        validateProduct(
                barcode, name, price, cost, unit, minStock);

        SQLiteDatabase db = helper.getWritableDatabase();
        String timestamp = LocalDatabase.now();

        ContentValues values = new ContentValues();
        values.put("barcode", barcode.trim());
        values.put("name", name.trim());
        values.put("price", price);
        values.put("cost", cost);
        values.put("unit", unit.trim());
        values.put("stock_qty", 0);
        values.put("min_stock", minStock);
        values.put("created_at", timestamp);
        values.put("updated_at", timestamp);

        try {
            return db.insertOrThrow("products", null, values);
        } catch (SQLiteConstraintException e) {
            throw new IllegalArgumentException(
                    "บาร์โค้ดซ้ำ หรือข้อมูลสินค้าไม่ผ่านเงื่อนไข", e);
        }
    }

    /**
     * เปลี่ยนเฉพาะราคาขาย พร้อมบันทึกประวัติ
     * การแก้ราคาและการเขียนประวัติอยู่ใน transaction เดียวกัน
     */
    public void changeSalePrice(
            String barcode,
            double newPrice
    ) {
        if (barcode == null || barcode.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "กรุณาระบุบาร์โค้ดสินค้า");
        }

        if (!Double.isFinite(newPrice) || newPrice < 0) {
            throw new IllegalArgumentException(
                    "ราคาขายไม่ถูกต้อง");
        }

        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            Product product = findByBarcodeInDatabase(
                    db, barcode.trim());

            if (product == null) {
                throw new IllegalArgumentException(
                        "ไม่พบสินค้าตามบาร์โค้ด");
            }

            if (Double.compare(product.price, newPrice) == 0) {
                db.setTransactionSuccessful();
                return;
            }

            String timestamp = LocalDatabase.now();

            ContentValues update = new ContentValues();
            update.put("price", newPrice);
            update.put("updated_at", timestamp);

            int rows = db.update(
                    "products",
                    update,
                    "id = ?",
                    new String[]{String.valueOf(product.id)}
            );

            if (rows != 1) {
                throw new IllegalStateException(
                        "บันทึกราคาสินค้าไม่สำเร็จ");
            }

            ContentValues history = new ContentValues();
            history.put("product_id", product.id);
            history.put("barcode", product.barcode);
            history.put("product_name", product.name);
            history.put("old_price", product.price);
            history.put("new_price", newPrice);
            history.put("changed_at", timestamp);

            db.insertOrThrow(
                    "price_history", null, history);

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }
    }

    private Product findByBarcodeInDatabase(
            SQLiteDatabase db,
            String barcode
    ) {
        try (Cursor cursor = db.query(
                "products",
                null,
                "barcode = ?",
                new String[]{barcode},
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            return readProduct(cursor);
        }
    }

    /**
     * ค้นประวัติการแก้ราคาของบาร์โค้ดตรงตัว
     * ผู้เรียกต้องปิด Cursor หลังใช้งาน
     */
    public Cursor getPriceHistory(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "กรุณาระบุบาร์โค้ดสินค้า");
        }

        return helper.getReadableDatabase().query(
                "price_history",
                new String[]{
                        "id",
                        "product_id",
                        "barcode",
                        "product_name",
                        "old_price",
                        "new_price",
                        "changed_at"
                },
                "barcode = ?",
                new String[]{barcode.trim()},
                null,
                null,
                "changed_at DESC, id DESC"
        );
    }

    private void validateProduct(
            String barcode,
            String name,
            double price,
            double cost,
            String unit,
            int minStock
    ) {
        if (barcode == null || barcode.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "กรุณาระบุบาร์โค้ด");
        }

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "กรุณาระบุชื่อสินค้า");
        }

        if (unit == null || unit.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "กรุณาระบุหน่วยสินค้า");
        }

        if (!Double.isFinite(price) || price < 0) {
            throw new IllegalArgumentException(
                    "ราคาขายไม่ถูกต้อง");
        }

        if (!Double.isFinite(cost) || cost < 0) {
            throw new IllegalArgumentException(
                    "ต้นทุนไม่ถูกต้อง");
        }

        if (minStock < 0) {
            throw new IllegalArgumentException(
                    "ลิมิตสต๊อกต้องไม่ติดลบ");
        }
    }
}
