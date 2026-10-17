
package com.example.newapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class LocalDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME =
            "maeyai_on_pos.db";

    private static final int DATABASE_VERSION = 2;

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
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        createProductsTable(db);
        createSalesTable(db);
        createSaleItemsTable(db);
        createStockMovementsTable(db);
        createSettingsTable(db);
        createAdditionalTables(db);
        createIndexes(db);
        insertDefaultSettings(db);
    }

    private void createProductsTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS products (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "barcode TEXT NOT NULL UNIQUE, " +
                "name TEXT NOT NULL, " +
                "price REAL NOT NULL DEFAULT 0, " +
                "cost REAL NOT NULL DEFAULT 0, " +
                "stock INTEGER NOT NULL DEFAULT 0, " +
                "min_stock INTEGER NOT NULL DEFAULT 0, " +
                "image_data TEXT, " +
                "unit TEXT NOT NULL DEFAULT 'ชิ้น', " +
                "is_archived INTEGER NOT NULL DEFAULT 0, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL" +
                ")"
        );
    }

    private void createSalesTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS sales (" +
                "id TEXT PRIMARY KEY, " +
                "receipt_number TEXT NOT NULL UNIQUE, " +
                "sold_at TEXT NOT NULL, " +
                "subtotal REAL NOT NULL DEFAULT 0, " +
                "discount REAL NOT NULL DEFAULT 0, " +
                "surcharge REAL NOT NULL DEFAULT 0, " +
                "total REAL NOT NULL DEFAULT 0, " +
                "payment_method TEXT, " +
                "payment_status TEXT NOT NULL DEFAULT 'paid', " +
                "customer_id INTEGER, " +
                "customer_name TEXT, " +
                "amount_received REAL NOT NULL DEFAULT 0, " +
                "change_amount REAL NOT NULL DEFAULT 0, " +
                "paid_at TEXT, " +
                "original_sale_id TEXT, " +
                "print_status TEXT NOT NULL DEFAULT 'not_printed', " +
                "idempotency_key TEXT" +
                ")"
        );
    }

    private void createSaleItemsTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS sale_items (" +
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
    }

    private void createStockMovementsTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS stock_movements (" +
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
    }

    private void createSettingsTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS app_settings (" +
                "setting_key TEXT PRIMARY KEY, " +
                "setting_value TEXT NOT NULL" +
                ")"
        );
    }

    private void createAdditionalTables(SQLiteDatabase db) {

        // รายชื่อลูกค้า
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS customers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "first_name TEXT NOT NULL, " +
                "last_name TEXT NOT NULL, " +
                "full_name_key TEXT NOT NULL UNIQUE, " +
                "created_at TEXT NOT NULL" +
                ")"
        );

        // ประวัติการแก้ไขราคาขาย
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS price_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "product_id INTEGER, " +
                "product_name TEXT NOT NULL, " +
                "old_price REAL NOT NULL, " +
                "new_price REAL NOT NULL, " +
                "changed_at TEXT NOT NULL, " +
                "FOREIGN KEY (product_id) REFERENCES products(id) " +
                "ON DELETE SET NULL" +
                ")"
        );

        // ประวัติการตรวจนับสต็อก
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS stock_count_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "product_id INTEGER, " +
                "product_name TEXT NOT NULL, " +
                "system_stock INTEGER NOT NULL, " +
                "counted_stock INTEGER NOT NULL, " +
                "difference INTEGER NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "reference_id TEXT, " +
                "FOREIGN KEY (product_id) REFERENCES products(id) " +
                "ON DELETE SET NULL" +
                ")"
        );

        // เชื่อมการชำระบิลเชื่อกับบิลต้นฉบับและใบเสร็จการชำระ
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS credit_settlements (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "original_sale_id TEXT NOT NULL, " +
                "settlement_sale_id TEXT UNIQUE, " +
                "amount REAL NOT NULL, " +
                "paid_at TEXT NOT NULL, " +
                "FOREIGN KEY (original_sale_id) REFERENCES sales(id) " +
                "ON DELETE CASCADE, " +
                "FOREIGN KEY (settlement_sale_id) REFERENCES sales(id) " +
                "ON DELETE SET NULL" +
                ")"
        );
    }

    private void createIndexes(SQLiteDatabase db) {

        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_sales_sold_at " +
                "ON sales(sold_at)"
        );

        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_sale_items_sale_id " +
                "ON sale_items(sale_id)"
        );

        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_stock_product " +
                "ON stock_movements(product_id)"
        );

        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_price_product " +
                "ON price_history(product_id, changed_at)"
        );

        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_stock_count_product " +
                "ON stock_count_history(product_id, created_at)"
        );

        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_sales_payment_status " +
                "ON sales(payment_status, sold_at)"
        );

        db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_sales_idempotency " +
                "ON sales(idempotency_key) " +
                "WHERE idempotency_key IS NOT NULL"
        );
    }

    private void insertDefaultSettings(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put("setting_key", "next_barcode");
        values.put("setting_value", "2000001");

        db.insertWithOnConflict(
                TABLE_SETTINGS,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion) {

        // ย้ายจากโครงสร้างเวอร์ชัน 1 ไปเวอร์ชัน 2
        // เพิ่มคอลัมน์โดยไม่ลบตารางหรือข้อมูลเดิม
        if (oldVersion < 2) {
            migrateToVersion2(db);
        }
    }

    private void migrateToVersion2(SQLiteDatabase db) {

        addColumnIfMissing(
                db, TABLE_PRODUCTS,
                "unit",
                "TEXT NOT NULL DEFAULT 'ชิ้น'"
        );

        addColumnIfMissing(
                db, TABLE_PRODUCTS,
                "is_archived",
                "INTEGER NOT NULL DEFAULT 0"
        );

        addColumnIfMissing(
                db, TABLE_SALES,
                "surcharge",
                "REAL NOT NULL DEFAULT 0"
        );

        addColumnIfMissing(
                db, TABLE_SALES,
                "payment_status",
                "TEXT NOT NULL DEFAULT 'paid'"
        );

        addColumnIfMissing(
                db, TABLE_SALES,
                "customer_id",
                "INTEGER"
        );

        addColumnIfMissing(
                db, TABLE_SALES,
                "customer_name",
                "TEXT"
        );

        addColumnIfMissing(
                db, TABLE_SALES,
                "paid_at",
                "TEXT"
        );

        addColumnIfMissing(
                db, TABLE_SALES,
                "original_sale_id",
                "TEXT"
        );

        addColumnIfMissing(
                db, TABLE_SALES,
                "print_status",
                "TEXT NOT NULL DEFAULT 'not_printed'"
        );

        addColumnIfMissing(
                db, TABLE_SALES,
                "idempotency_key",
                "TEXT"
        );

        createAdditionalTables(db);
        createIndexes(db);
        insertDefaultSettings(db);
    }

    private void addColumnIfMissing(
            SQLiteDatabase db,
            String table,
            String column,
            String definition) {

        Cursor cursor = db.rawQuery(
                "PRAGMA table_info(" + table + ")",
                null
        );

        boolean exists = false;

        try {
            int nameIndex = cursor.getColumnIndex("name");

            while (cursor.moveToNext()) {
                if (nameIndex >= 0 &&
                        column.equals(cursor.getString(nameIndex))) {
                    exists = true;
                    break;
                }
            }
        } finally {
            cursor.close();
        }

        if (!exists) {
            db.execSQL(
                    "ALTER TABLE " + table +
                    " ADD COLUMN " + column + " " + definition
            );
        }
    }

    @Override
    public void onDowngrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion) {

        // ไม่ลดเวอร์ชันหรือลบข้อมูลเดิมโดยอัตโนมัติ
        throw new IllegalStateException(
                "ไม่รองรับการลดเวอร์ชันฐานข้อมูลจาก " +
                oldVersion + " เป็น " + newVersion
        );
    }
}
