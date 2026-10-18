
package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class CustomerRepository {

    private final LocalDatabase database;

    public CustomerRepository(LocalDatabase database) {
        this.database = database;
    }

    public static class Customer {
        public long id;
        public String firstName;
        public String lastName;

        public Customer(long id, String firstName, String lastName) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
        }

        public String getFullName() {
            return (firstName == null ? "" : firstName.trim())
                    + " "
                    + (lastName == null ? "" : lastName.trim());
        }

        @Override
        public String toString() {
            return getFullName().trim();
        }
    }

    public List<Customer> search(String keyword) {
        List<Customer> customers = new ArrayList<>();
        SQLiteDatabase db = database.getReadableDatabase();

        String text = keyword == null ? "" : keyword.trim();
        String pattern = "%" + text + "%";

        try (Cursor cursor = db.rawQuery(
                "SELECT id, first_name, last_name " +
                "FROM customers " +
                "WHERE first_name LIKE ? OR last_name LIKE ? " +
                "OR (first_name || ' ' || last_name) LIKE ? " +
                "ORDER BY first_name COLLATE NOCASE, last_name COLLATE NOCASE",
                new String[]{pattern, pattern, pattern})) {

            while (cursor.moveToNext()) {
                customers.add(new Customer(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2)
                ));
            }
        }

        return customers;
    }

    public Customer findById(long customerId) {
        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT id, first_name, last_name " +
                "FROM customers WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(customerId)})) {

            if (!cursor.moveToFirst()) {
                return null;
            }

            return new Customer(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getString(2)
            );
        }
    }

    public Customer findExactName(String firstName, String lastName) {
        if (firstName == null || lastName == null) {
            return null;
        }

        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT id, first_name, last_name " +
                "FROM customers " +
                "WHERE TRIM(first_name) = TRIM(?) " +
                "AND TRIM(last_name) = TRIM(?) LIMIT 1",
                new String[]{firstName.trim(), lastName.trim()})) {

            if (!cursor.moveToFirst()) {
                return null;
            }

            return new Customer(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getString(2)
            );
        }
    }

    public Customer create(String firstName, String lastName) {
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("กรุณากรอกชื่อลูกค้า");
        }

        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("กรุณากรอกนามสกุลลูกค้า");
        }

        String first = firstName.trim();
        String last = lastName.trim();

        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();

        try {
            Customer existing = findExactName(first, last);

            if (existing != null) {
                db.setTransactionSuccessful();
                return existing;
            }

            ContentValues values = new ContentValues();
            values.put("first_name", first);
            values.put("last_name", last);
            values.put("created_at", LocalDatabase.now());

            long id = db.insertOrThrow("customers", null, values);

            db.setTransactionSuccessful();

            return new Customer(id, first, last);

        } finally {
            db.endTransaction();
        }
    }

    public int count() {
        SQLiteDatabase db = database.getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM customers", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }
}
