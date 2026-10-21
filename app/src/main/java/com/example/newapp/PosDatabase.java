package com.example.newapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Single-device, offline-first database shared by all POS menus. Money is stored in satang. */
public final class PosDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "maeyai_on_pos.db";
    private static final int DB_VERSION = 2;
    private static volatile PosDatabase instance;

    public static synchronized PosDatabase get(Context context) {
        if (instance == null) instance = new PosDatabase(context.getApplicationContext());
        return instance;
    }

    private PosDatabase(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, barcode TEXT NOT NULL UNIQUE, name TEXT NOT NULL," +
                "price_satang INTEGER NOT NULL DEFAULT 0 CHECK(price_satang>=0)," +
                "cost_satang INTEGER NOT NULL DEFAULT 0 CHECK(cost_satang>=0)," +
                "unit TEXT NOT NULL DEFAULT 'ชิ้น', stock INTEGER NOT NULL DEFAULT 0 CHECK(stock>=0)," +
                "stock_limit INTEGER NOT NULL DEFAULT 0 CHECK(stock_limit>=0), active INTEGER NOT NULL DEFAULT 1," +
                "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_products_name ON products(name COLLATE NOCASE)");
        db.execSQL("CREATE TABLE barcode_registry (barcode TEXT PRIMARY KEY, name TEXT NOT NULL," +
                "price_satang INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, full_name TEXT NOT NULL UNIQUE COLLATE NOCASE, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE bills (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_code TEXT NOT NULL UNIQUE," +
                "customer_id INTEGER, sale_type TEXT NOT NULL CHECK(sale_type IN ('CASH','CREDIT'))," +
                "status TEXT NOT NULL CHECK(status IN ('PAID','UNPAID','CANCELLED','RETURNED'))," +
                "subtotal_satang INTEGER NOT NULL, fee_satang INTEGER NOT NULL DEFAULT 0," +
                "total_satang INTEGER NOT NULL, cash_received_satang INTEGER NOT NULL DEFAULT 0," +
                "change_satang INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, paid_at INTEGER," +
                "archived_at INTEGER, archive_until INTEGER, FOREIGN KEY(customer_id) REFERENCES customers(id))");
        db.execSQL("CREATE INDEX idx_bills_customer_status ON bills(customer_id,status,created_at)");
        db.execSQL("CREATE INDEX idx_bills_created ON bills(created_at)");
        db.execSQL("CREATE TABLE bill_items (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_id INTEGER NOT NULL," +
                "product_id INTEGER, barcode TEXT NOT NULL, product_name TEXT NOT NULL, unit TEXT NOT NULL," +
                "quantity INTEGER NOT NULL CHECK(quantity>0), unit_price_satang INTEGER NOT NULL," +
                "line_total_satang INTEGER NOT NULL, FOREIGN KEY(bill_id) REFERENCES bills(id) ON DELETE CASCADE," +
                "FOREIGN KEY(product_id) REFERENCES products(id))");
        db.execSQL("CREATE INDEX idx_bill_items_product ON bill_items(product_id)");
        db.execSQL("CREATE TABLE stock_history (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER," +
                "barcode TEXT NOT NULL, product_name TEXT NOT NULL, change_type TEXT NOT NULL," +
                "before_qty INTEGER NOT NULL, after_qty INTEGER NOT NULL, difference INTEGER NOT NULL," +
                "reference TEXT, created_at INTEGER NOT NULL, FOREIGN KEY(product_id) REFERENCES products(id))");
        db.execSQL("CREATE INDEX idx_stock_history_barcode ON stock_history(barcode,created_at DESC)");
        db.execSQL("CREATE TABLE price_history (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER," +
                "barcode TEXT NOT NULL, product_name TEXT NOT NULL, old_price_satang INTEGER NOT NULL," +
                "new_price_satang INTEGER NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(product_id) REFERENCES products(id))");
        db.execSQL("CREATE INDEX idx_price_history_barcode ON price_history(barcode,created_at)");
        db.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_id INTEGER NOT NULL UNIQUE," +
                "amount_satang INTEGER NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(bill_id) REFERENCES bills(id))");
        db.execSQL("CREATE TABLE sales_ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_id INTEGER NOT NULL," +
                "bill_code TEXT NOT NULL, sale_type TEXT NOT NULL, event_type TEXT NOT NULL," +
                "subtotal_satang INTEGER NOT NULL, fee_satang INTEGER NOT NULL, total_satang INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL, UNIQUE(bill_id,event_type))");
        db.execSQL("CREATE INDEX idx_sales_ledger_created ON sales_ledger(created_at)");
        db.execSQL("CREATE TABLE app_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        db.execSQL("INSERT INTO app_meta(key,value) VALUES('barcode_sequence','100000000000')");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.beginTransaction();
            try {
                db.execSQL("ALTER TABLE sales_ledger RENAME TO sales_ledger_v1");
                db.execSQL("CREATE TABLE sales_ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_id INTEGER NOT NULL, bill_code TEXT NOT NULL, sale_type TEXT NOT NULL, event_type TEXT NOT NULL, subtotal_satang INTEGER NOT NULL, fee_satang INTEGER NOT NULL, total_satang INTEGER NOT NULL, created_at INTEGER NOT NULL, UNIQUE(bill_id,event_type))");
                db.execSQL("INSERT INTO sales_ledger SELECT * FROM sales_ledger_v1");
                db.execSQL("DROP TABLE sales_ledger_v1");
                db.execSQL("CREATE INDEX idx_sales_ledger_created ON sales_ledger(created_at)");
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
    }

    public static long now() { return System.currentTimeMillis(); }
    public static ContentValues values() { return new ContentValues(); }
    public static String money(long satang) {
        return String.format(java.util.Locale.US, "%.2f", satang / 100.0d);
    }
    public static long toSatang(String value) {
        java.math.BigDecimal d = new java.math.BigDecimal(value.trim());
        if (d.signum() < 0) throw new IllegalArgumentException("ราคาต้องไม่ติดลบ");
        return d.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }
    public static long creditFee(long subtotalSatang) {
        // 5% in satang, rounded upward to the next satang.
        return (subtotalSatang + 19L) / 20L;
    }
    public Cursor findProduct(SQLiteDatabase db, String barcode) {
        return db.query("products", null, "barcode=? AND active=1", new String[]{barcode}, null, null, null, "1");
    }
}
