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

}
