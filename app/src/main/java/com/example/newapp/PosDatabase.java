package com.example.newapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Single-device, offline-first database shared by all POS menus. Money is stored in satang. */
public final class PosDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "maeyai_on_pos.db";
    private static final int DB_VERSION = 8;
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
        db.execSQL("CREATE INDEX idx_products_stock_name ON products(active,stock,name COLLATE NOCASE)");
        db.execSQL("CREATE TABLE barcode_registry (barcode TEXT PRIMARY KEY, name TEXT NOT NULL," +
                "price_satang INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_barcode_registry_name ON barcode_registry(name COLLATE NOCASE)");
        db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, full_name TEXT NOT NULL UNIQUE COLLATE NOCASE, first_name TEXT NOT NULL DEFAULT '', last_name TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE bills (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_code TEXT NOT NULL UNIQUE," +
                "customer_id INTEGER, sale_type TEXT NOT NULL CHECK(sale_type IN ('CASH','CREDIT'))," +
                "status TEXT NOT NULL CHECK(status IN ('PAID','UNPAID','CANCELLED','RETURNED'))," +
                "subtotal_satang INTEGER NOT NULL, fee_satang INTEGER NOT NULL DEFAULT 0," +
                "total_satang INTEGER NOT NULL, cash_received_satang INTEGER NOT NULL DEFAULT 0," +
                "change_satang INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, paid_at INTEGER," +
                "archived_at INTEGER, archive_until INTEGER, FOREIGN KEY(customer_id) REFERENCES customers(id))");
        db.execSQL("CREATE INDEX idx_bills_customer_status ON bills(customer_id,status,created_at)");
        db.execSQL("CREATE INDEX idx_bills_created ON bills(created_at)");
        db.execSQL("CREATE INDEX idx_bills_paid_at ON bills(paid_at)");
        db.execSQL("CREATE INDEX idx_bills_archive_until ON bills(archive_until)");
        db.execSQL("CREATE TABLE bill_items (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_id INTEGER NOT NULL," +
                "product_id INTEGER, barcode TEXT NOT NULL, product_name TEXT NOT NULL, unit TEXT NOT NULL," +
                "quantity INTEGER NOT NULL CHECK(quantity>0), unit_price_satang INTEGER NOT NULL," +
                "line_total_satang INTEGER NOT NULL, FOREIGN KEY(bill_id) REFERENCES bills(id) ON DELETE CASCADE," +
                "FOREIGN KEY(product_id) REFERENCES products(id))");
        db.execSQL("CREATE INDEX idx_bill_items_product ON bill_items(product_id)");
        db.execSQL("CREATE INDEX idx_bill_items_bill_id ON bill_items(bill_id)");
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
        db.execSQL("CREATE TABLE payment_history (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_id INTEGER NOT NULL UNIQUE, bill_code TEXT NOT NULL, amount_satang INTEGER NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_payment_history_created ON payment_history(created_at)");
        db.execSQL("CREATE TABLE sales_ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_id INTEGER NOT NULL," +
                "bill_code TEXT NOT NULL, sale_type TEXT NOT NULL, event_type TEXT NOT NULL," +
                "subtotal_satang INTEGER NOT NULL, fee_satang INTEGER NOT NULL, total_satang INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL, UNIQUE(bill_id,event_type))");
        db.execSQL("CREATE INDEX idx_sales_ledger_created ON sales_ledger(created_at)");
        db.execSQL("CREATE TABLE sales_ledger_items (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_id INTEGER NOT NULL, bill_code TEXT NOT NULL, event_type TEXT NOT NULL, barcode TEXT NOT NULL, product_name TEXT NOT NULL, quantity INTEGER NOT NULL, unit_price_satang INTEGER NOT NULL, line_total_satang INTEGER NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_ledger_items_created ON sales_ledger_items(created_at)");
        db.execSQL("CREATE INDEX idx_ledger_items_product ON sales_ledger_items(barcode,created_at)");
        db.execSQL("CREATE INDEX idx_ledger_items_bill_id ON sales_ledger_items(bill_id,event_type)");
        db.execSQL("CREATE TABLE app_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT, actor_id INTEGER, actor_label TEXT NOT NULL DEFAULT 'ยังไม่ระบุผู้ใช้', action TEXT NOT NULL, entity_type TEXT NOT NULL, entity_id TEXT, details TEXT, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_audit_log_created ON audit_log(created_at)");
        db.execSQL("CREATE INDEX idx_audit_log_entity ON audit_log(entity_type,entity_id,created_at)");
        db.execSQL("INSERT INTO app_meta(key,value) VALUES('barcode_sequence','100000000000')");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.beginTransaction();
            try {
                db.execSQL("DROP INDEX IF EXISTS idx_sales_ledger_created");
                db.execSQL("ALTER TABLE sales_ledger RENAME TO sales_ledger_v1");
                db.execSQL("CREATE TABLE sales_ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_id INTEGER NOT NULL, bill_code TEXT NOT NULL, sale_type TEXT NOT NULL, event_type TEXT NOT NULL, subtotal_satang INTEGER NOT NULL, fee_satang INTEGER NOT NULL, total_satang INTEGER NOT NULL, created_at INTEGER NOT NULL, UNIQUE(bill_id,event_type))");
                db.execSQL("INSERT INTO sales_ledger SELECT * FROM sales_ledger_v1");
                db.execSQL("DROP TABLE sales_ledger_v1");
                db.execSQL("CREATE INDEX idx_sales_ledger_created ON sales_ledger(created_at)");
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bills_paid_at ON bills(paid_at)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bills_archive_until ON bills(archive_until)");
        }
        if (oldVersion < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS sales_ledger_items (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_id INTEGER NOT NULL, bill_code TEXT NOT NULL, event_type TEXT NOT NULL, barcode TEXT NOT NULL, product_name TEXT NOT NULL, quantity INTEGER NOT NULL, unit_price_satang INTEGER NOT NULL, line_total_satang INTEGER NOT NULL, created_at INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ledger_items_created ON sales_ledger_items(created_at)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ledger_items_product ON sales_ledger_items(barcode,created_at)");
            db.execSQL("INSERT INTO sales_ledger_items(bill_id,bill_code,event_type,barcode,product_name,quantity,unit_price_satang,line_total_satang,created_at) SELECT bi.bill_id,b.bill_code,'SALE',bi.barcode,bi.product_name,bi.quantity,bi.unit_price_satang,bi.line_total_satang,b.created_at FROM bill_items bi JOIN bills b ON b.id=bi.bill_id WHERE EXISTS (SELECT 1 FROM sales_ledger l WHERE l.bill_id=b.id AND l.event_type='SALE')");
            db.execSQL("INSERT INTO sales_ledger_items(bill_id,bill_code,event_type,barcode,product_name,quantity,unit_price_satang,line_total_satang,created_at) SELECT bi.bill_id,b.bill_code,l.event_type,bi.barcode,bi.product_name,-bi.quantity,bi.unit_price_satang,-bi.line_total_satang,l.created_at FROM bill_items bi JOIN bills b ON b.id=bi.bill_id JOIN sales_ledger l ON l.bill_id=b.id AND l.event_type IN ('CANCEL','RETURN')");
        }
        if (oldVersion < 5) {
            db.execSQL("CREATE TABLE IF NOT EXISTS payment_history (id INTEGER PRIMARY KEY AUTOINCREMENT, bill_id INTEGER NOT NULL UNIQUE, bill_code TEXT NOT NULL, amount_satang INTEGER NOT NULL, created_at INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_payment_history_created ON payment_history(created_at)");
            db.execSQL("INSERT OR IGNORE INTO payment_history(bill_id,bill_code,amount_satang,created_at) SELECT p.bill_id,b.bill_code,p.amount_satang,p.created_at FROM payments p JOIN bills b ON b.id=p.bill_id");
        }
        if (oldVersion < 6) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_stock_name ON products(active,stock,name COLLATE NOCASE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_barcode_registry_name ON barcode_registry(name COLLATE NOCASE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bill_items_bill_id ON bill_items(bill_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ledger_items_bill_id ON sales_ledger_items(bill_id,event_type)");
        }
        if (oldVersion < 7) {
            db.execSQL("INSERT OR IGNORE INTO barcode_registry(barcode,name,price_satang,source,created_at) SELECT barcode,name,price_satang,'LEGACY',created_at FROM products");
            db.execSQL("UPDATE barcode_registry SET price_satang=(SELECT p.price_satang FROM products p WHERE p.barcode=barcode_registry.barcode) WHERE EXISTS (SELECT 1 FROM products p WHERE p.barcode=barcode_registry.barcode)");
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE customers ADD COLUMN first_name TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE customers ADD COLUMN last_name TEXT NOT NULL DEFAULT ''");
            Cursor legacy = db.query("customers", new String[]{"id","full_name"}, null, null, null, null, null);
            while (legacy.moveToNext()) {
                long id = legacy.getLong(0);
                String oldName = legacy.getString(1) == null ? "" : legacy.getString(1).trim();
                int split = oldName.indexOf(' ');
                String first = split > 0 ? oldName.substring(0, split).trim() : oldName;
                String last = split > 0 ? oldName.substring(split + 1).trim() : "";
                ContentValues profile = new ContentValues();
                profile.put("first_name", first);
                profile.put("last_name", last);
                db.update("customers", profile, "id=?", new String[]{String.valueOf(id)});
            }
            legacy.close();
            db.execSQL("CREATE TABLE IF NOT EXISTS audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT, actor_id INTEGER, actor_label TEXT NOT NULL DEFAULT 'ยังไม่ระบุผู้ใช้', action TEXT NOT NULL, entity_type TEXT NOT NULL, entity_id TEXT, details TEXT, created_at INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log(entity_type,entity_id,created_at)");
        }
    }

    public static long now() { return AppClock.now(); }

    /** Append-only operational audit entry. Actor identity remains unknown until user accounts are implemented. */
    public static void audit(SQLiteDatabase db, String action, String entityType, String entityId, String details) {
        ContentValues row = new ContentValues();
        row.put("actor_label", "ยังไม่ระบุผู้ใช้");
        row.put("action", action);
        row.put("entity_type", entityType);
        if (entityId != null) row.put("entity_id", entityId);
        if (details != null) row.put("details", details);
        row.put("created_at", now());
        db.insertOrThrow("audit_log", null, row);
    }

    public static String customerName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        return (first + " " + last).trim();
    }
    public static ContentValues values() { return new ContentValues(); }
    public static String money(long satang) {
        return String.format(java.util.Locale.US, "%.2f", satang / 100.0d);
    }
    public static long toSatang(String value) {
        java.math.BigDecimal d = new java.math.BigDecimal(value.trim());
        if (d.signum() < 0) throw new IllegalArgumentException("ราคาต้องไม่ติดลบ");
        return d.movePointRight(2).setScale(0, java.math.RoundingMode.CEILING).longValueExact();
    }
    public static long creditFee(long subtotalSatang) {
        // 5% in satang, rounded upward to the next satang.
        if (subtotalSatang < 0L) throw new IllegalArgumentException("ยอดขายเชื่อต้องไม่ติดลบ");
        long whole = subtotalSatang / 20L;
        return whole + (subtotalSatang % 20L == 0L ? 0L : 1L);
    }
    public Cursor findProduct(SQLiteDatabase db, String barcode) {
        return db.query("products", null, "barcode=? AND active=1", new String[]{barcode}, null, null, null, "1");
    }
}
