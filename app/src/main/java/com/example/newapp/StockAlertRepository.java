
package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class StockAlertRepository {

    private final LocalDatabase database;

    public StockAlertRepository(LocalDatabase database) {
        this.database = database;
    }

    public static class StockAlert {
        public final long productId;
        public final String barcode;
        public final String name;
        public final double quantity;
        public final double limit;

        public StockAlert(
                long productId,
                String barcode,
                String name,
                double quantity,
                double limit
        ) {
            this.productId = productId;
            this.barcode = barcode;
            this.name = name;
            this.quantity = quantity;
            this.limit = limit;
        }
    }

    /**
     * ตรวจทุกสินค้าและคืนเฉพาะสินค้าที่เพิ่งเปลี่ยนจากสถานะปกติ
     * เป็นสถานะสต๊อกต่ำในรอบนี้
     *
     * ต้องเรียกหลังจากมีการเปลี่ยนสต๊อกหรือเปลี่ยนลิมิตสินค้า
     */
    public java.util.List<StockAlert> detectNewLowStockEvents() {
        java.util.List<StockAlert> newAlerts = new java.util.ArrayList<>();
        SQLiteDatabase db = database.getWritableDatabase();

        db.beginTransaction();

        try (Cursor cursor = db.rawQuery(
                "SELECT id, barcode, name, stock_qty, min_stock " +
                "FROM products ORDER BY name COLLATE NOCASE",
                null)) {

            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String barcode = cursor.getString(1);
                String name = cursor.getString(2);
                double quantity = cursor.getDouble(3);
                double limit = cursor.getDouble(4);

                boolean isLow = quantity <= limit;
                String key = "stock_low_active_" + id;
                boolean wasLow = getSettingBoolean(db, key, false);

                if (isLow && !wasLow) {
                    newAlerts.add(new StockAlert(
                            id, barcode, name, quantity, limit
                    ));
                    setSetting(db, key, "1");
                } else if (!isLow && wasLow) {
                    setSetting(db, key, "0");
                }
            }

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }

        return newAlerts;
    }

    public java.util.List<StockAlert> getAllLowStockProducts() {
        java.util.List<StockAlert> result = new java.util.ArrayList<>();
        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT id, barcode, name, stock_qty, min_stock " +
                "FROM products WHERE stock_qty <= min_stock " +
                "ORDER BY stock_qty ASC, name COLLATE NOCASE",
                null)) {

            while (cursor.moveToNext()) {
                result.add(new StockAlert(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getDouble(3),
                        cursor.getDouble(4)
                ));
            }
        }

        return result;
    }

    private boolean getSettingBoolean(
            SQLiteDatabase db,
            String key,
            boolean defaultValue
    ) {
        try (Cursor cursor = db.rawQuery(
                "SELECT setting_value FROM app_settings WHERE setting_key = ? LIMIT 1",
                new String[]{key})) {

            if (!cursor.moveToFirst()) {
                return defaultValue;
            }

            return "1".equals(cursor.getString(0))
                    || "true".equalsIgnoreCase(cursor.getString(0));
        }
    }

    private void setSetting(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("setting_key", key);
        values.put("setting_value", value);

        db.insertWithOnConflict(
                "app_settings",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }
}
