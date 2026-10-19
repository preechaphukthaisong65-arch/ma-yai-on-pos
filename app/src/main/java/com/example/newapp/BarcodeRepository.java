package com.example.newapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * จัดการข้อมูลบาร์โค้ดผ่านฐานข้อมูลหลักของแอป
 *
 * ใช้ฐานข้อมูลเดียวร่วมกับระบบ POS
 * สร้างเลขและบันทึกสินค้าใน transaction เดียวกัน
 */
public final class BarcodeRepository {

    private static final String SETTING_KEY = "next_barcode_number";
    private static final long FIRST_BARCODE_NUMBER = 2000001L;

    private final LocalDatabase database;

    public BarcodeRepository(Context context) {
        database = LocalDatabase.getInstance(context);
    }

    /**
     * สร้างเลขบาร์โค้ดและบันทึกสินค้าแบบ atomic
     *
     * หากบันทึกไม่สำเร็จ การเปลี่ยนแปลงฐานข้อมูลจะ rollback
     * หากเลขซ้ำ จะข้ามไปหาเลขถัดไป
     */
    public ProductRepository.Product createProduct(
            String name,
            double price
    ) {
        String cleanName = name == null ? "" : name.trim();

        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException(
                    "กรุณาระบุชื่อสินค้า"
            );
        }

        if (!Double.isFinite(price) || price < 0) {
            throw new IllegalArgumentException(
                    "ราคาสินค้าไม่ถูกต้อง"
            );
        }

        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();

        try {
            long nextNumber = readNextNumber(db);

            if (nextNumber < FIRST_BARCODE_NUMBER) {
                nextNumber = FIRST_BARCODE_NUMBER;
            }

            String barcode;

            // ข้ามเลขที่มีสินค้าใช้อยู่แล้ว
            while (true) {
                if (nextNumber == Long.MAX_VALUE) {
                    throw new IllegalStateException(
                            "เลขบาร์โค้ดหมดช่วงที่รองรับ"
                    );
                }

                barcode = String.format(
                        Locale.US,
                        "%07d",
                        nextNumber
                );

                boolean exists;

                try (Cursor cursor = db.query(
                        "products",
                        new String[]{"id"},
                        "barcode = ?",
                        new String[]{barcode},
                        null,
                        null,
                        null,
                        "1"
                )) {
                    exists = cursor.moveToFirst();
                }

                if (!exists) {
                    break;
                }

                nextNumber++;
            }

            String timestamp = LocalDatabase.now();

            ContentValues productValues = new ContentValues();
            productValues.put("barcode", barcode);
            productValues.put("name", cleanName);
            productValues.put("price", price);
            productValues.put("cost", 0.0);
            productValues.put("unit", "ชิ้น");
            productValues.put("stock_qty", 0);
            productValues.put("min_stock", 0);
            productValues.put("created_at", timestamp);
            productValues.put("updated_at", timestamp);

            long productId = db.insertOrThrow(
                    "products",
                    null,
                    productValues
            );

            if (productId <= 0) {
                throw new IllegalStateException(
                        "บันทึกสินค้าไม่สำเร็จ"
                );
            }

            // บันทึกเลขถัดไป
            ContentValues settingValues = new ContentValues();
            settingValues.put(
                    "setting_value",
                    Long.toString(nextNumber + 1L)
            );

            int updated = db.update(
                    "app_settings",
                    settingValues,
                    "setting_key = ?",
                    new String[]{SETTING_KEY}
            );

            if (updated == 0) {
                ContentValues newSetting = new ContentValues();
                newSetting.put("setting_key", SETTING_KEY);
                newSetting.put(
                        "setting_value",
                        Long.toString(nextNumber + 1L)
                );

                db.insertOrThrow(
                        "app_settings",
                        null,
                        newSetting
                );
            }

            ProductRepository.Product product =
                    new ProductRepository.Product();

            product.id = productId;
            product.barcode = barcode;
            product.name = cleanName;
            product.price = price;
            product.cost = 0.0;
            product.unit = "ชิ้น";
            product.stockQty = 0;
            product.minStock = 0;
            product.createdAt = timestamp;
            product.updatedAt = timestamp;

            db.setTransactionSuccessful();

            return product;

        } finally {
            db.endTransaction();
        }
    }

    private long readNextNumber(SQLiteDatabase db) {
        try (Cursor cursor = db.query(
                "app_settings",
                new String[]{"setting_value"},
                "setting_key = ?",
                new String[]{SETTING_KEY},
                null,
                null,
                null,
                "1"
        )) {
            if (!cursor.moveToFirst()) {
                return FIRST_BARCODE_NUMBER;
            }

            String value = cursor.getString(0);

            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "ค่าตัวนับบาร์โค้ดในฐานข้อมูลไม่ถูกต้อง",
                        e
                );
            }
        }
    }

    /**
     * ค้นหาสินค้าจากชื่อหรือหมายเลขบาร์โค้ด
     */
    public List<ProductRepository.Product> search(String query) {
        String keyword = query == null ? "" : query.trim();

        List<ProductRepository.Product> results =
                new ArrayList<>();

        if (keyword.isEmpty()) {
            return results;
        }

        SQLiteDatabase db = database.getReadableDatabase();
        String pattern = "%" + keyword + "%";

        try (Cursor cursor = db.query(
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
                "name LIKE ? OR barcode LIKE ?",
                new String[]{pattern, pattern},
                null,
                null,
                "name COLLATE NOCASE ASC",
                "100"
        )) {
            while (cursor.moveToNext()) {
                ProductRepository.Product product =
                        new ProductRepository.Product();

                product.id = cursor.getLong(
                        cursor.getColumnIndexOrThrow("id")
                );
                product.barcode = cursor.getString(
                        cursor.getColumnIndexOrThrow("barcode")
                );
                product.name = cursor.getString(
                        cursor.getColumnIndexOrThrow("name")
                );
                product.price = cursor.getDouble(
                        cursor.getColumnIndexOrThrow("price")
                );
                product.cost = cursor.getDouble(
                        cursor.getColumnIndexOrThrow("cost")
                );
                product.unit = cursor.getString(
                        cursor.getColumnIndexOrThrow("unit")
                );
                product.stockQty = cursor.getInt(
                        cursor.getColumnIndexOrThrow("stock_qty")
                );
                product.minStock = cursor.getInt(
                        cursor.getColumnIndexOrThrow("min_stock")
                );
                product.createdAt = cursor.getString(
                        cursor.getColumnIndexOrThrow("created_at")
                );
                product.updatedAt = cursor.getString(
                        cursor.getColumnIndexOrThrow("updated_at")
                );

                results.add(product);
            }
        }

        return results;
    }

    /**
     * ค้นหาสินค้าด้วยบาร์โค้ดแบบตรงตัว
     */
    public ProductRepository.Product findByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return null;
        }

        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.query(
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
                "barcode = ?",
                new String[]{barcode.trim()},
                null,
                null,
                null,
                "1"
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            ProductRepository.Product product =
                    new ProductRepository.Product();

            product.id = cursor.getLong(
                    cursor.getColumnIndexOrThrow("id")
            );
            product.barcode = cursor.getString(
                    cursor.getColumnIndexOrThrow("barcode")
            );
            product.name = cursor.getString(
                    cursor.getColumnIndexOrThrow("name")
            );
            product.price = cursor.getDouble(
                    cursor.getColumnIndexOrThrow("price")
            );
            product.cost = cursor.getDouble(
                    cursor.getColumnIndexOrThrow("cost")
            );
            product.unit = cursor.getString(
                    cursor.getColumnIndexOrThrow("unit")
            );
            product.stockQty = cursor.getInt(
                    cursor.getColumnIndexOrThrow("stock_qty")
            );
            product.minStock = cursor.getInt(
                    cursor.getColumnIndexOrThrow("min_stock")
            );
            product.createdAt = cursor.getString(
                    cursor.getColumnIndexOrThrow("created_at")
            );
            product.updatedAt = cursor.getString(
                    cursor.getColumnIndexOrThrow("updated_at")
            );

            return product;
        }
    }
            }
