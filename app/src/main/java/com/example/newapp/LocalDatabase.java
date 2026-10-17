package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;

public class LocalDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME =
            "maeyai_on_pos.db";

    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_PRODUCTS = "products";
    private static final String TABLE_SALES = "sales";
    private static final String TABLE_SALE_ITEMS = "sale_items";
    private static final String TABLE_STOCK_MOVEMENTS =
            "stock_movements";
    private static final String TABLE_SETTINGS = "app_settings";

    public LocalDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // 1. ข้อมูลสินค้า
        db.execSQL(
            "CREATE TABLE " + TABLE_PRODUCTS + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "barcode TEXT NOT NULL UNIQUE, " +
            "name TEXT NOT NULL, " +
            "price REAL NOT NULL DEFAULT 0, " +
            "cost REAL NOT NULL DEFAULT 0, " +
            "stock INTEGER NOT NULL DEFAULT 0, " +
            "min_stock INTEGER NOT NULL DEFAULT 0, " +
            "image_data TEXT, " +
            "created_at TEXT NOT NULL, " +
            "updated_at TEXT NOT NULL" +
            ")"
        );

        // 2. หัวรายการขาย
        db.execSQL(
            "CREATE TABLE " + TABLE_SALES + " (" +
            "id TEXT PRIMARY KEY, " +
            "receipt_number TEXT NOT NULL UNIQUE, " +
            "sold_at TEXT NOT NULL, " +
            "subtotal REAL NOT NULL DEFAULT 0, " +
            "discount REAL NOT NULL DEFAULT 0, " +
            "total REAL NOT NULL DEFAULT 0, " +
            "payment_method TEXT, " +
            "amount_received REAL NOT NULL DEFAULT 0, " +
            "change_amount REAL NOT NULL DEFAULT 0" +
            ")"
        );

        // 3. รายละเอียดสินค้าในใบเสร็จ
        // เก็บข้อมูล ณ เวลาขาย เพื่อให้ใบเสร็จเก่ายังคงเดิม
        // แม้ภายหลังจะเปลี่ยนชื่อหรือราคาสินค้า
        db.execSQL(
            "CREATE TABLE " + TABLE_SALE_ITEMS + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "sale_id TEXT NOT NULL, " +
            "product_id INTEGER, " +
            "product_name TEXT NOT NULL, " +
            "barcode TEXT, " +
            "unit_price REAL NOT NULL, " +
            "unit_cost REAL NOT NULL DEFAULT 0, " +
            "quantity INTEGER NOT NULL, " +
            "line_total REAL NOT NULL, " +
            "FOREIGN KEY (sale_id) REFERENCES sales(id) " +
            "ON DELETE CASCADE, " +
            "FOREIGN KEY (product_id) REFERENCES products(id) " +
            "ON DELETE SET NULL" +
            ")"
        );

        // 4. ประวัติการเปลี่ยนแปลงสต็อก
        db.execSQL(
            "CREATE TABLE " + TABLE_STOCK_MOVEMENTS + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "product_id INTEGER, " +
            "movement_type TEXT NOT NULL, " +
            "quantity INTEGER NOT NULL, " +
            "stock_before INTEGER NOT NULL, " +
            "stock_after INTEGER NOT NULL, " +
            "reference_id TEXT, " +
            "created_at TEXT NOT NULL, " +
            "FOREIGN KEY (product_id) REFERENCES products(id) " +
            "ON DELETE SET NULL" +
            ")"
        );

        // 5. ค่าตั้งต้นของระบบ
        db.execSQL(
            "CREATE TABLE " + TABLE_SETTINGS + " (" +
            "setting_key TEXT PRIMARY KEY, " +
            "setting_value TEXT NOT NULL" +
            ")"
        );

        ContentValues values = new ContentValues();
        values.put("setting_key", "next_barcode");
        values.put("setting_value", "2000001");

        db.insertOrThrow(TABLE_SETTINGS, null, values);

        // ดัชนีสำหรับค้นหาประวัติได้เร็วขึ้น
        db.execSQL(
            "CREATE INDEX idx_sales_sold_at " +
            "ON sales(sold_at)"
        );

        db.execSQL(
            "CREATE INDEX idx_sale_items_sale_id " +
            "ON sale_items(sale_id)"
        );

        db.execSQL(
            "CREATE INDEX idx_stock_product " +
            "ON stock_movements(product_id)"
        );
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion) {

        // เมื่อมีการเปลี่ยนโครงสร้างในอนาคต
        // ต้องเพิ่มแผนย้ายข้อมูลตามเวอร์ชัน
        // ห้ามลบตารางเดิมโดยอัตโนมัติ
    }
}
