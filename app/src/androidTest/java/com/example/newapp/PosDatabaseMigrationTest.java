package com.example.newapp;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PosDatabaseMigrationTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @After public void cleanUp() {
        PosDatabase.closeInstance();
        context.deleteDatabase(PosDatabase.databaseFileName());
    }

    @Test public void v11MigrationPreservesCustomerAndBillIdsAndAllowsDuplicateNames() {
        PosDatabase.closeInstance();
        context.deleteDatabase(PosDatabase.databaseFileName());
        File file = context.getDatabasePath(PosDatabase.databaseFileName());
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) assertTrue(parent.mkdirs());

        SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(file, null);
        old.execSQL("PRAGMA foreign_keys=OFF");
        old.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, full_name TEXT NOT NULL UNIQUE COLLATE NOCASE, first_name TEXT NOT NULL DEFAULT '', last_name TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE bills (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER, FOREIGN KEY(customer_id) REFERENCES customers(id))");
        old.execSQL("INSERT INTO customers(id,full_name,first_name,last_name,created_at) VALUES(41,'Somchai Chai','Somchai','Chai',1000)");
        old.execSQL("INSERT INTO bills(id,customer_id) VALUES(77,41)");
        old.execSQL("PRAGMA user_version=11");
        old.close();

        SQLiteDatabase upgraded = PosDatabase.get(context).getWritableDatabase();
        Cursor version = upgraded.rawQuery("PRAGMA user_version", null);
        assertTrue(version.moveToFirst());
        assertEquals(PosDatabase.DB_VERSION, version.getInt(0));
        version.close();

        Cursor customer = upgraded.rawQuery("SELECT id,full_name FROM customers WHERE id=41", null);
        assertTrue(customer.moveToFirst());
        assertEquals("Somchai Chai", customer.getString(1));
        customer.close();

        Cursor bill = upgraded.rawQuery("SELECT customer_id FROM bills WHERE id=77", null);
        assertTrue(bill.moveToFirst());
        assertEquals(41L, bill.getLong(0));
        bill.close();

        upgraded.execSQL("INSERT INTO customers(full_name,first_name,last_name,created_at) VALUES('Somchai Chai','Somchai','Chai',2000)");
        Cursor duplicates = upgraded.rawQuery("SELECT COUNT(*) FROM customers WHERE full_name='Somchai Chai' COLLATE NOCASE", null);
        assertTrue(duplicates.moveToFirst());
        assertEquals(2, duplicates.getInt(0));
        duplicates.close();

        Cursor integrity = upgraded.rawQuery("PRAGMA integrity_check", null);
        assertTrue(integrity.moveToFirst());
        assertEquals("ok", integrity.getString(0));
        integrity.close();
        Cursor foreignKeys = upgraded.rawQuery("PRAGMA foreign_key_check", null);
        assertFalse("Migration must preserve foreign-key integrity", foreignKeys.moveToFirst());
        foreignKeys.close();
    }
    @Test public void v12MigrationCreatesStaffTableWithoutChangingCustomerIds() {
        PosDatabase.closeInstance();
        context.deleteDatabase(PosDatabase.databaseFileName());
        File file = context.getDatabasePath(PosDatabase.databaseFileName());
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) assertTrue(parent.mkdirs());

        SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(file, null);
        old.execSQL("PRAGMA foreign_keys=OFF");
        old.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, full_name TEXT NOT NULL COLLATE NOCASE, first_name TEXT NOT NULL DEFAULT '', last_name TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE bills (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER, FOREIGN KEY(customer_id) REFERENCES customers(id))");
        old.execSQL("INSERT INTO customers(id,full_name,first_name,last_name,created_at) VALUES(52,'Mali Dee','Mali','Dee',2000)");
        old.execSQL("INSERT INTO bills(id,customer_id) VALUES(88,52)");
        old.execSQL("PRAGMA user_version=12");
        old.close();

        SQLiteDatabase upgraded = PosDatabase.get(context).getWritableDatabase();
        Cursor version = upgraded.rawQuery("PRAGMA user_version", null);
        assertTrue(version.moveToFirst());
        assertEquals(13, version.getInt(0));
        version.close();

        Cursor staff = upgraded.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='staff_users'", null);
        assertTrue(staff.moveToFirst());
        assertEquals(1, staff.getInt(0));
        staff.close();

        Cursor bill = upgraded.rawQuery("SELECT customer_id FROM bills WHERE id=88", null);
        assertTrue(bill.moveToFirst());
        assertEquals(52L, bill.getLong(0));
        bill.close();

        Cursor integrity = upgraded.rawQuery("PRAGMA integrity_check", null);
        assertTrue(integrity.moveToFirst());
        assertEquals("ok", integrity.getString(0));
        integrity.close();
    }


    @Test public void v9UpgradeRemovesLegacyUnitColumnsAndPreservesProductBillAndCustomerIds() {
        PosDatabase.closeInstance();
        context.deleteDatabase(PosDatabase.databaseFileName());

        // Start from the current schema, then add the legacy v9 columns and representative
        // linked records. This exercises the v10->v13 destructive table rebuild path.
        SQLiteDatabase seed = PosDatabase.get(context).getWritableDatabase();
        seed.execSQL("ALTER TABLE products ADD COLUMN unit TEXT");
        seed.execSQL("ALTER TABLE bill_items ADD COLUMN unit TEXT");
        seed.execSQL("INSERT INTO products(id,barcode,name,price_satang,stock,stock_limit,active,created_at,updated_at,unit) VALUES(7,'8850000000001','Rice',10000,8,10,1,1000,1000,'bag')");
        seed.execSQL("INSERT INTO customers(id,full_name,first_name,last_name,created_at) VALUES(61,'Mali Dee','Mali','Dee',1000)");
        seed.execSQL("INSERT INTO bills(id,bill_code,customer_id,sale_type,status,subtotal_satang,fee_satang,total_satang,cash_received_satang,change_satang,created_at) VALUES(91,'B-91',61,'CREDIT','UNPAID',20000,1000,21000,0,0,1000)");
        seed.execSQL("INSERT INTO bill_items(id,bill_id,product_id,barcode,product_name,quantity,unit_price_satang,line_total_satang) VALUES(101,91,7,'8850000000001','Rice',2,10000,20000)");
        seed.execSQL("PRAGMA user_version=9");
        PosDatabase.closeInstance();

        SQLiteDatabase upgraded = PosDatabase.get(context).getWritableDatabase();
        Cursor version = upgraded.rawQuery("PRAGMA user_version", null);
        assertTrue(version.moveToFirst());
        assertEquals(PosDatabase.DB_VERSION, version.getInt(0));
        version.close();

        Cursor product = upgraded.rawQuery("SELECT id,stock FROM products WHERE id=7", null);
        assertTrue(product.moveToFirst());
        assertEquals(7L, product.getLong(0));
        assertEquals(8, product.getInt(1));
        product.close();

        Cursor columns = upgraded.rawQuery("PRAGMA table_info(products)", null);
        boolean unitColumnExists = false;
        while (columns.moveToNext()) if ("unit".equalsIgnoreCase(columns.getString(1))) unitColumnExists = true;
        columns.close();
        assertFalse("Legacy unit column must be removed", unitColumnExists);

        Cursor bill = upgraded.rawQuery("SELECT customer_id FROM bills WHERE id=91", null);
        assertTrue(bill.moveToFirst());
        assertEquals(61L, bill.getLong(0));
        bill.close();

        Cursor item = upgraded.rawQuery("SELECT product_id,quantity FROM bill_items WHERE id=101", null);
        assertTrue(item.moveToFirst());
        assertEquals(7L, item.getLong(0));
        assertEquals(2, item.getInt(1));
        item.close();

        Cursor integrity = upgraded.rawQuery("PRAGMA integrity_check", null);
        assertTrue(integrity.moveToFirst());
        assertEquals("ok", integrity.getString(0));
        integrity.close();
        Cursor foreignKeys = upgraded.rawQuery("PRAGMA foreign_key_check", null);
        assertFalse("Upgrade must preserve foreign-key integrity", foreignKeys.moveToFirst());
        foreignKeys.close();
    }

}
