package com.example.newapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ProductRepository {

    private final LocalDatabase helper;

    public ProductRepository(Context context) {
        helper = new LocalDatabase(context.getApplicationContext());
    }

    public static class Product {
        public long id;
        public String barcode;
        public String name;
        public double price;
        public double cost;
        public int stock;
        public int minStock;
        public String imageData;
        public String unit;
        public boolean archived;

        public Product(
                long id,
                String barcode,
                String name,
                double price,
                double cost,
                int stock,
                int minStock,
                String imageData,
                String unit,
                boolean archived) {

            this.id = id;
            this.barcode = barcode;
            this.name = name;
            this.price = price;
            this.cost = cost;
            this.stock = stock;
            this.minStock = minStock;
            this.imageData = imageData;
            this.unit = unit;
            this.archived = archived;
        }

        public boolean isLowStock() {
            return stock <= minStock;
        }
    }

    private String now() {
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
        ).format(new Date());
    }

    private Product readProduct(Cursor c) {
        return new Product(
                c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("barcode")),
                c.getString(c.getColumnIndexOrThrow("name")),
                c.getDouble(c.getColumnIndexOrThrow("price")),
                c.getDouble(c.getColumnIndexOrThrow("cost")),
                c.getInt(c.getColumnIndexOrThrow("stock")),
                c.getInt(c.getColumnIndexOrThrow("min_stock")),
                c.getString(c.getColumnIndexOrThrow("image_data")),
                c.getString(c.getColumnIndexOrThrow("unit")),
                c.getInt(c.getColumnIndexOrThrow("is_archived")) == 1
        );
    }

    private Product findByBarcode(
            SQLiteDatabase db,
            String barcode) {

        Cursor c = db.query(
                "products",
                null,
                "barcode = ?",
                new String[]{barcode},
                null,
                null,
                null,
                "1"
        );

        try {
            if (c.moveToFirst()) {
                return readProduct(c);
            }
            return null;
        } finally {
            c.close();
        }
    }

    public Product getByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return null;
        }

        SQLiteDatabase db = helper.getReadableDatabase();
        return findByBarcode(db, barcode.trim());
    }

    public Product getById(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();

        Cursor c = db.query(
                "products",
                null,
                "id = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null,
                "1"
        );

        try {
            if (c.moveToFirst()) {
                return readProduct(c);
            }
            return null;
        } finally {
            c.close();
        }
    }

    public ArrayList<Product> getActiveProducts() {
        ArrayList<Product> result = new ArrayList<>();

        SQLiteDatabase db = helper.getReadableDatabase();

        Cursor c = db.query(
                "products",
                null,
                "is_archived = 0",
                null,
                null,
                null,
                "name COLLATE NOCASE ASC"
        );

        try {
            while (c.moveToNext()) {
                result.add(readProduct(c));
            }
        } finally {
            c.close();
        }

        return result;
    }

    public ArrayList<Product> getLowStockProducts() {
        ArrayList<Product> result = new ArrayList<>();

        SQLiteDatabase db = helper.getReadableDatabase();

        Cursor c = db.rawQuery(
                "SELECT * FROM products " +
                "WHERE is_archived = 0 AND stock <= min_stock " +
                "ORDER BY stock ASC, name COLLATE NOCASE ASC",
                null
        );

        try {
            while (c.moveToNext()) {
                result.add(readProduct(c));
            }
        } finally {
            c.close();
        }

        return result;
    }

    /**
     * เพิ่มสินค้าและออกบาร์โค้ดภายในระบบโดยอัตโนมัติ
     * การบันทึกสินค้าและเลื่อนเลขบาร์โค้ดเกิดในธุรกรรมเดียวกัน
     */
    public Product addProduct(
            String name,
            double price,
            double cost,
            int initialStock,
            int minStock,
            String imageData,
            String unit) {

        validateProduct(
                name, price, cost, initialStock, minStock, unit
        );

        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            long nextBarcode = getNextBarcodeNumber(db);
            String barcode = String.valueOf(nextBarcode);

            // ตรวจทั้งค่าที่จะใช้และเลขที่บันทึกไว้
            if (findByBarcode(db, barcode) != null) {
                throw new IllegalStateException(
                        "บาร์โค้ดซ้ำ: " + barcode
                );
            }

            ContentValues values = new ContentValues();
            values.put("barcode", barcode);
            values.put("name", name.trim());
            values.put("price", price);
            values.put("cost", cost);
            values.put("stock", initialStock);
            values.put("min_stock", minStock);
            values.put("image_data", imageData);
            values.put(
                    "unit",
                    unit == null || unit.trim().isEmpty()
                            ? "ชิ้น"
                            : unit.trim()
            );
            values.put("is_archived", 0);
            values.put("created_at", now());
            values.put("updated_at", now());

            long id = db.insertOrThrow(
                    "products", null, values
            );

            // บันทึกประวัติสต็อกเริ่มต้นเมื่อมีสินค้าเข้าคลัง
            if (initialStock != 0) {
                insertStockMovement(
                        db,
                        id,
                        "INITIAL",
                        initialStock,
                        0,
                        initialStock,
                        null
                );
            }

            ContentValues setting = new ContentValues();
            setting.put(
                    "setting_value",
                    String.valueOf(nextBarcode + 1)
            );

            int updated = db.update(
                    "app_settings",
                    setting,
                    "setting_key = ?",
                    new String[]{"next_barcode"}
            );

            if (updated == 0) {
                setting.put("setting_key", "next_barcode");
                db.insertOrThrow(
                        "app_settings", null, setting
                );
            }

            db.setTransactionSuccessful();

            return new Product(
                    id,
                    barcode,
                    name.trim(),
                    price,
                    cost,
                    initialStock,
                    minStock,
                    imageData,
                    unit == null || unit.trim().isEmpty()
                            ? "ชิ้น"
                            : unit.trim(),
                    false
            );

        } finally {
            db.endTransaction();
        }
    }

    private long getNextBarcodeNumber(SQLiteDatabase db) {
        Cursor c = db.query(
                "app_settings",
                new String[]{"setting_value"},
                "setting_key = ?",
                new String[]{"next_barcode"},
                null,
                null,
                null,
                "1"
        );

        try {
            if (!c.moveToFirst()) {
                return 2000001L;
            }

            long value = Long.parseLong(c.getString(0));

            if (value < 2000001L) {
                throw new IllegalStateException(
                        "เลขบาร์โค้ดถัดไปต่ำกว่าค่าตั้งต้น"
                );
            }

            return value;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "ค่าเลขบาร์โค้ดในฐานข้อมูลไม่ถูกต้อง", e
            );
        } finally {
            c.close();
        }
    }

    private void validateProduct(
            String name,
            double price,
            double cost,
            int stock,
            int minStock,
            String unit) {

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "กรุณาระบุชื่อสินค้า"
            );
        }

        if (!Double.isFinite(price) || price < 0
                || !Double.isFinite(cost) || cost < 0) {
            throw new IllegalArgumentException(
                    "ราคาและต้นทุนต้องเป็นตัวเลขตั้งแต่ศูนย์ขึ้นไป"
            );
        }

        if (stock < 0 || minStock < 0) {
            throw new IllegalArgumentException(
                    "จำนวนสต็อกและขั้นต่ำต้องไม่ติดลบ"
            );
        }
    }

    public boolean updateProduct(
            long id,
            String name,
            double price,
            double cost,
            int minStock,
            String imageData,
            String unit) {

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "กรุณาระบุชื่อสินค้า"
            );
        }

        if (!Double.isFinite(price) || price < 0
                || !Double.isFinite(cost) || cost < 0
                || minStock < 0) {
            throw new IllegalArgumentException(
                    "ข้อมูลราคา ต้นทุน หรือสต็อกขั้นต่ำไม่ถูกต้อง"
            );
        }

        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            Product old = getById(id);

            if (old == null) {
                return false;
            }

            if (Double.compare(old.price, price) != 0) {
                ContentValues history = new ContentValues();
                history.put("product_id", id);
                history.put("product_name", old.name);
                history.put("old_price", old.price);
                history.put("new_price", price);
                history.put("changed_at", now());

                db.insertOrThrow(
                        "price_history", null, history
                );
            }

            ContentValues values = new ContentValues();
            values.put("name", name.trim());
            values.put("price", price);
            values.put("cost", cost);
            values.put("min_stock", minStock);
            values.put("image_data", imageData);
            values.put(
                    "unit",
                    unit == null || unit.trim().isEmpty()
                            ? "ชิ้น"
                            : unit.trim()
            );
            values.put("updated_at", now());

            int rows = db.update(
                    "products",
                    values,
                    "id = ?",
                    new String[]{String.valueOf(id)}
            );

            db.setTransactionSuccessful();
            return rows > 0;

        } finally {
            db.endTransaction();
        }
    }

    /**
     * เก็บสินค้าเข้าคลังถาวรโดยไม่ลบประวัติการขายเดิม
     */
    public boolean archiveProduct(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("is_archived", 1);
        values.put("updated_at", now());

        return db.update(
                "products",
                values,
                "id = ?",
                new String[]{String.valueOf(id)}
        ) > 0;
    }

    /**
     * ปรับสต็อกและบันทึกประวัติในธุรกรรมเดียวกัน
     * quantityChange เป็นบวกเมื่อรับเข้า และเป็นลบเมื่อจ่ายออก
     */
    public boolean adjustStock(
            long productId,
            int quantityChange,
            String movementType,
            String referenceId) {

        if (quantityChange == 0) {
            throw new IllegalArgumentException(
                    "จำนวนที่เปลี่ยนต้องไม่เป็นศูนย์"
            );
        }

        if (movementType == null || movementType.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "กรุณาระบุประเภทการเคลื่อนไหวสต็อก"
            );
        }

        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            Product product = getById(productId);

            if (product == null || product.archived) {
                return false;
            }

            long newStockLong =
                    (long) product.stock + quantityChange;

            if (newStockLong < 0 || newStockLong > Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        "จำนวนสต็อกใหม่ไม่ถูกต้อง"
                );
            }

            int newStock = (int) newStockLong;

            ContentValues values = new ContentValues();
            values.put("stock", newStock);
            values.put("updated_at", now());

            int rows = db.update(
                    "products",
                    values,
                    "id = ? AND stock = ?",
                    new String[]{
                            String.valueOf(productId),
                            String.valueOf(product.stock)
                    }
            );

            if (rows != 1) {
                throw new IllegalStateException(
                        "สต็อกเปลี่ยนระหว่างดำเนินการ กรุณาลองใหม่"
                );
            }

            insertStockMovement(
                    db,
                    productId,
                    movementType.trim(),
                    quantityChange,
                    product.stock,
                    newStock,
                    referenceId
            );

            db.setTransactionSuccessful();
            return true;

        } finally {
            db.endTransaction();
        }
    }

    private void insertStockMovement(
            SQLiteDatabase db,
            long productId,
            String movementType,
            int quantity,
            int stockBefore,
            int stockAfter,
            String referenceId) {

        ContentValues movement = new ContentValues();
        movement.put("product_id", productId);
        movement.put("movement_type", movementType);
        movement.put("quantity", quantity);
        movement.put("stock_before", stockBefore);
        movement.put("stock_after", stockAfter);
        movement.put("reference_id", referenceId);
        movement.put("created_at", now());

        db.insertOrThrow(
                "stock_movements", null, movement
        );
    }

    public void close() {
        helper.close();
    }
}
