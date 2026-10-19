
package com.example.newapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * ฐานข้อมูล SQLite กลางของร้านแม่ใหญ่อ่อน POS
 *
 * ใช้ฐานข้อมูลเดียวร่วมกันทุกระบบ
 * การ Migration ต้องรักษาข้อมูลเดิมทั้งหมด
 */
public class LocalDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "maeyai_on_pos.db";
    private static final int DATABASE_VERSION = 3;

    private static final TimeZone THAILAND_TIME_ZONE =
            TimeZone.getTimeZone("Asia/Bangkok");

    private static volatile LocalDatabase instance;

    public static synchronized LocalDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new LocalDatabase(
                    context.getApplicationContext()
            );
        }
        return instance;
    }

    private LocalDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * เวลาปัจจุบันในรูปแบบ yyyy-MM-dd HH:mm:ss
     * กำหนดเขตเวลา Asia/Bangkok (UTC+7)
     */
    public static String now() {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
        );
        format.setTimeZone(THAILAND_TIME_ZONE);
        return format.format(new Date());
    }

    /**
     * เวลาย้อนหลังหนึ่งเดือนตามปฏิทินไทย
     * ใช้รูปแบบเดียวกับ now() เพื่อให้เปรียบเทียบข้อความวันที่ได้
     */
    public static String oneMonthAgo() {
        Calendar calendar = Calendar.getInstance(THAILAND_TIME_ZONE);
        calendar.add(Calendar.MONTH, -1);

        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
        );
        format.setTimeZone(THAILAND_TIME_ZONE);

        return format.format(calendar.getTime());
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // สินค้าหลัก
        db.execSQL(
                "CREATE TABLE products (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "barcode TEXT NOT NULL UNIQUE," +
                "name TEXT NOT NULL," +
                "price REAL NOT NULL DEFAULT 0 CHECK(price >= 0)," +
                "cost REAL NOT NULL DEFAULT 0 CHECK(cost >= 0)," +
                "unit TEXT NOT NULL DEFAULT 'ชิ้น'," +
                "stock_qty INTEGER NOT NULL DEFAULT 0 CHECK(stock_qty >= 0)," +
                "min_stock INTEGER NOT NULL DEFAULT 0 CHECK(min_stock >= 0)," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL" +
                ")"
        );

        db.execSQL(
                "CREATE INDEX idx_products_name ON products(name)"
        );

        // ประวัติรับสินค้า ตรวจนับ และการเคลื่อนไหวสต๊อก
        db.execSQL(
                "CREATE TABLE stock_movements (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "product_id INTEGER NOT NULL," +
                "operation TEXT NOT NULL," +
                "qty_before INTEGER NOT NULL," +
                "qty_after INTEGER NOT NULL," +
                "qty_diff INTEGER NOT NULL," +
                "note TEXT," +
                "created_at TEXT NOT NULL," +
                "FOREIGN KEY(product_id) REFERENCES products(id)" +
                ")"
        );

        db.execSQL(
                "CREATE INDEX idx_stock_product_time " +
                "ON stock_movements(product_id, created_at)"
        );

        // ลูกค้าเชื่อ
        db.execSQL(
                "CREATE TABLE customers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "first_name TEXT NOT NULL," +
                "last_name TEXT NOT NULL," +
                "phone TEXT," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL" +
                ")"
        );

        db.execSQL(
                "CREATE INDEX idx_customers_name " +
                "ON customers(first_name, last_name)"
        );

        // บิลขายทั้งเงินสดและเงินเชื่อ
        db.execSQL(
                "CREATE TABLE sales (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "bill_number TEXT NOT NULL UNIQUE," +
                "created_at TEXT NOT NULL," +
                "payment_type TEXT NOT NULL CHECK(" +
                "payment_type IN ('CASH','CREDIT'))," +
                "status TEXT NOT NULL CHECK(" +
                "status IN ('PAID','PENDING'))," +
                "customer_id INTEGER," +
                "subtotal REAL NOT NULL CHECK(subtotal >= 0)," +
                "service_fee REAL NOT NULL DEFAULT 0 CHECK(service_fee >= 0)," +
                "total REAL NOT NULL CHECK(total >= 0)," +
                "cash_received REAL NOT NULL DEFAULT 0 CHECK(cash_received >= 0)," +
                "change_due REAL NOT NULL DEFAULT 0 CHECK(change_due >= 0)," +
                "paid_at TEXT," +
                "receipt_text TEXT," +
                "print_status TEXT NOT NULL DEFAULT 'NOT_PRINTED'," +
                "created_by TEXT," +
                "FOREIGN KEY(customer_id) REFERENCES customers(id)" +
                ")"
        );

        db.execSQL(
                "CREATE INDEX idx_sales_date ON sales(created_at)"
        );

        db.execSQL(
                "CREATE INDEX idx_sales_status ON sales(status, payment_type)"
        );

        db.execSQL(
                "CREATE INDEX idx_sales_customer ON sales(customer_id, status)"
        );

        // รายการสินค้าในบิล เก็บข้อมูล ณ เวลาขาย
        // เพื่อให้บิลเก่าไม่เปลี่ยนตามราคาสินค้าปัจจุบัน
        db.execSQL(
                "CREATE TABLE sale_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "sale_id INTEGER NOT NULL," +
                "product_id INTEGER NOT NULL," +
                "barcode TEXT NOT NULL," +
                "product_name TEXT NOT NULL," +
                "unit TEXT NOT NULL," +
                "quantity INTEGER NOT NULL CHECK(quantity > 0)," +
                "unit_price REAL NOT NULL CHECK(unit_price >= 0)," +
                "line_total REAL NOT NULL CHECK(line_total >= 0)," +
                "FOREIGN KEY(sale_id) REFERENCES sales(id)," +
                "FOREIGN KEY(product_id) REFERENCES products(id)" +
                ")"
        );

        db.execSQL(
                "CREATE INDEX idx_sale_items_sale ON sale_items(sale_id)"
        );

        db.execSQL(
                "CREATE INDEX idx_sale_items_product ON sale_items(product_id)"
        );

        // ประวัติการแก้ไขราคาขาย
        createPriceHistoryTable(db);

        // การชำระหนี้แต่ละครั้ง เชื่อมกับบิลต้นฉบับ
        db.execSQL(
                "CREATE TABLE credit_payments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "payment_number TEXT NOT NULL UNIQUE," +
                "sale_id INTEGER NOT NULL," +
                "amount REAL NOT NULL CHECK(amount >= 0)," +
                "cash_received REAL NOT NULL CHECK(cash_received >= 0)," +
                "change_due REAL NOT NULL DEFAULT 0 CHECK(change_due >= 0)," +
                "paid_at TEXT NOT NULL," +
                "receipt_text TEXT," +
                "print_status TEXT NOT NULL DEFAULT 'NOT_PRINTED'," +
                "FOREIGN KEY(sale_id) REFERENCES sales(id)" +
                ")"
        );

        db.execSQL(
                "CREATE INDEX idx_credit_payments_sale " +
                "ON credit_payments(sale_id, paid_at)"
        );

        // เลขรันและการตั้งค่าของแอป
        db.execSQL(
                "CREATE TABLE app_settings (" +
                "setting_key TEXT PRIMARY KEY," +
                "setting_value TEXT NOT NULL" +
                ")"
        );

        ContentValues settings = new ContentValues();
        settings.put("setting_key", "next_barcode_number");
        settings.put("setting_value", "2000001");
        db.insertOrThrow("app_settings", null, settings);

        // คิวซิงก์สำหรับการต่อ Cloud ในอนาคต
        createCloudSyncQueueTable(db);

        // ข้อความแจ้งเตือนจากเหตุการณ์จริงของระบบ
        createNotificationMessagesTable(db);
    }

    /**
     * สร้างตารางประวัติราคาและดัชนี
     * ใช้ได้ทั้งฐานข้อมูลใหม่และ Migration
     */
    private void createPriceHistoryTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS price_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "product_id INTEGER NOT NULL," +
                "barcode TEXT NOT NULL," +
                "product_name TEXT NOT NULL," +
                "old_price REAL NOT NULL," +
                "new_price REAL NOT NULL," +
                "changed_at TEXT NOT NULL," +
                "FOREIGN KEY(product_id) REFERENCES products(id)" +
                ")"
        );

        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_price_history_product " +
                "ON price_history(product_id, changed_at)"
        );
    }

    /**
     * สร้างตารางข้อความแจ้งเตือน
     * event_key ใช้แยกเหตุการณ์และป้องกันการบันทึกซ้ำ
     *
     * event_key อนุญาตให้เป็น NULL เพื่อรองรับข้อมูลเดิม
     */
    private void createNotificationMessagesTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS notification_messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "message TEXT NOT NULL," +
                "event_type TEXT NOT NULL," +
                "created_at TEXT NOT NULL," +
                "is_read INTEGER NOT NULL DEFAULT 0," +
                "event_key TEXT" +
                ")"
        );

        // SQLite อนุญาตให้มี NULL หลายรายการใน UNIQUE INDEX
        db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_notification_event_key " +
                "ON notification_messages(event_key)"
        );
    }

    /**
     * สร้างคิวซิงก์ในฐานข้อมูลใหม่และฐานข้อมูลเก่า
     * ยังเป็นเพียงคิวภายในเครื่อง ไม่ได้เชื่อมต่อ Cloud จริง
     */
    private void createCloudSyncQueueTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS cloud_sync_queue (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "entity_type TEXT NOT NULL," +
                "entity_id TEXT NOT NULL," +
                "operation TEXT NOT NULL," +
                "payload TEXT," +
                "status TEXT NOT NULL DEFAULT 'PENDING'," +
                "attempts INTEGER NOT NULL DEFAULT 0," +
                "created_at TEXT NOT NULL," +
                "last_error TEXT," +
                "UNIQUE(entity_type, entity_id, operation)" +
                ")"
        );
    }

    /**
     * ตรวจสอบว่าตารางมีคอลัมน์ที่ระบุหรือไม่
     * ใช้ก่อน ALTER TABLE เพื่อไม่เพิ่มคอลัมน์ซ้ำ
     */
    private boolean columnExists(
            SQLiteDatabase db,
            String tableName,
            String columnName
    ) {
        try (Cursor cursor = db.rawQuery(
                "PRAGMA table_info(" + tableName + ")",
                null
        )) {
            int nameIndex = cursor.getColumnIndex("name");

            while (cursor.moveToNext()) {
                if (nameIndex >= 0 &&
                        columnName.equals(cursor.getString(nameIndex))) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion
    ) {
        // Migration จากเวอร์ชัน 1 เป็น 2
        // ไม่ลบตารางเดิม ไม่รีเซ็ตข้อมูลสินค้า และไม่ล้างประวัติ
        if (oldVersion < 2 && newVersion >= 2) {
            createPriceHistoryTable(db);
            createNotificationMessagesTable(db);
        }

        // Migration จากเวอร์ชัน 2 เป็น 3
        // เพิ่มเฉพาะสิ่งที่ขาด โดยรักษาข้อมูลเดิมไว้
        if (oldVersion < 3 && newVersion >= 3) {

            // เพิ่ม event_key เฉพาะเมื่อยังไม่มีคอลัมน์นี้
            if (!columnExists(
                    db,
                    "notification_messages",
                    "event_key"
            )) {
                db.execSQL(
                        "ALTER TABLE notification_messages " +
                        "ADD COLUMN event_key TEXT"
                );
            }

            db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "idx_notification_event_key " +
                    "ON notification_messages(event_key)"
            );

            // ฐานข้อมูลเวอร์ชันเก่าต้องมีคิวซิงก์ด้วย
            createCloudSyncQueueTable(db);
        }
    }

    /**
     * ค้นหาสินค้าด้วยบาร์โค้ดแบบตรงตัว
     * คืน Cursor ให้ผู้เรียกต้องปิดด้วย close()
     */
    public Cursor findProductByBarcode(String barcode) {
        return getReadableDatabase().query(
                "products",
                null,
                "barcode = ?",
                new String[]{barcode},
                null,
                null,
                null
        );
    }

    /**
     * จองเลขบาร์โค้ดถัดไปแบบ transaction
     * เริ่มที่ 2000001 และเพิ่มทีละ 1
     *
     * เลขที่จองแล้วอาจมีช่องว่างได้หากยกเลิกการสร้างสินค้า
     */
    public String reserveNextBarcode() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            String value = "2000001";

            try (Cursor cursor = db.query(
                    "app_settings",
                    new String[]{"setting_value"},
                    "setting_key = ?",
                    new String[]{"next_barcode_number"},
                    null,
                    null,
                    null
            )) {
                if (cursor.moveToFirst()) {
                    value = cursor.getString(0);
                }
            }

            long current = Long.parseLong(value);
            String next = Long.toString(current + 1);

            ContentValues values = new ContentValues();
            values.put("setting_value", next);

            int updated = db.update(
                    "app_settings",
                    values,
                    "setting_key = ?",
                    new String[]{"next_barcode_number"}
            );

            if (updated == 0) {
                throw new IllegalStateException(
                        "ไม่สามารถบันทึกเลขบาร์โค้ดถัดไปได้"
                );
            }

            db.setTransactionSuccessful();
            return Long.toString(current);

        } finally {
            db.endTransaction();
        }
    }

    /**
     * ปิดฐานข้อมูลเมื่อไม่ต้องใช้งานแล้ว
     * โดยปกติให้ใช้ getInstance() ร่วมกันทั้งแอป
     */
    public static synchronized void closeInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}
