
package com.example.newapp;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * อ่านรายการสินค้าที่มีจำนวนต่ำกว่าลิมิตจากฐานข้อมูลเดิม
 * ไม่สร้างหรือลบตาราง และไม่เปลี่ยนโครงสร้างฐานข้อมูล
 */
public class StockAlertRepository {

    private final LocalDatabase database;

    public StockAlertRepository(LocalDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "ฐานข้อมูลห้ามเป็น null"
            );
        }
        this.database = database;
    }

    public static class StockAlert {
        public final long productId;
        public final String name;
        public final String barcode;
        public final int quantity;
        public final int limit;

        public StockAlert(
                long productId,
                String name,
                String barcode,
                int quantity,
                int limit
        ) {
            this.productId = productId;
            this.name = name;
            this.barcode = barcode;
            this.quantity = quantity;
            this.limit = limit;
        }
    }

    /**
     * คืนรายการที่สต๊อกต่ำกว่าลิมิตอย่างเคร่งครัด
     * จำนวนเท่ากับลิมิตจะไม่ถูกจัดเป็นสินค้าต่ำกว่าลิมิต
     */
    public List<StockAlert> detectNewLowStockEvents() {
        List<StockAlert> alerts = new ArrayList<>();
        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.query(
                "products",
                new String[]{
                        "id",
                        "name",
                        "barcode",
                        "stock_qty",
                        "min_stock"
                },
                "stock_qty < min_stock",
                null,
                null,
                null,
                "stock_qty ASC, name COLLATE NOCASE ASC"
        )) {
            while (cursor.moveToNext()) {
                alerts.add(new StockAlert(
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow("id")
                        ),
                        cursor.getString(
                                cursor.getColumnIndexOrThrow("name")
                        ),
                        cursor.getString(
                                cursor.getColumnIndexOrThrow("barcode")
                        ),
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow("stock_qty")
                        ),
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow("min_stock")
                        )
                ));
            }
        }

        return alerts;
    }
}
