package com.example.newapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class ProductRepository {

    private final LocalDatabase helper;

    public ProductRepository(Context context) {
        helper = new LocalDatabase(context.getApplicationContext());
    }

    public static class Product {
        public long id;
        public String name;
        public double price;
        public int stock;
        public String barcode;

        public Product(long id, String name, double price, int stock, String barcode) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.barcode = barcode;
        }
    }

    public Product addProduct(String name, double price, int cost, int stock, int minStock, String barcode, String unit) {
        SQLiteDatabase db = helper.getWritableDatabase();
        if (barcode == null || barcode.isEmpty()) {
            barcode = "BAR" + System.currentTimeMillis();
        }

        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("price", price);
        values.put("cost", cost);
        values.put("stock", stock);
        values.put("min_stock", minStock);
        values.put("barcode", barcode);
        values.put("unit", unit != null ? unit : "ชิ้น");

        try {
            long id = db.insertOrThrow("products", null, values);
            if (id != -1) {
                return new Product(id, name, price, stock, barcode);
            }
        } catch (Exception e) {
            // Log error
        }
        return null;
    }

    public List<Product> getAllProducts() {
        List<Product> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("SELECT id, name, price, stock, barcode FROM products ORDER BY id DESC", null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(0);
                    String name = cursor.getString(1);
                    double price = cursor.getDouble(2);
                    int stock = cursor.getInt(3);
                    String barcode = cursor.getString(4);
                    list.add(new Product(id, name, price, stock, barcode));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            // Log error
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }

        return list;
    }

    public void close() {
        helper.close();
    }
}
