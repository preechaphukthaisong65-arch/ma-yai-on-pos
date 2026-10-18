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
        public int cost;
        public int stock;
        public int minStock;
        public String barcode;
        public String unit;
        public boolean archived = false; // ป้องกัน error ตัวแปร archived
        public byte[] imageData = null;  // ป้องกัน error ตัวแปร imageData

        public Product(long id, String name, double price, int cost, int stock, int minStock, String barcode, String unit) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.cost = cost;
            this.stock = stock;
            this.minStock = minStock;
            this.barcode = barcode;
            this.unit = unit;
        }

        public boolean isLowStock() {
            return this.stock <= this.minStock;
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
                return new Product(id, name, price, cost, stock, minStock, barcode, unit);
            }
        } catch (Exception e) {
            // Log error
        } finally {
            db.close();
        }
        return null;
    }

    public List<Product> getAllProducts() {
        List<Product> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("SELECT id, name, price, cost, stock, min_stock, barcode, unit FROM products ORDER BY id DESC", null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(0);
                    String name = cursor.getString(1);
                    double price = cursor.getDouble(2);
                    int cost = cursor.getInt(3);
                    int stock = cursor.getInt(4);
                    int minStock = cursor.getInt(5);
                    String barcode = cursor.getString(6);
                    String unit = cursor.getString(7);
                    list.add(new Product(id, name, price, cost, stock, minStock, barcode, unit));
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

    // รองรับ ArrayList<Product> ตามที่ StockScreen เรียกใช้งาน
    public ArrayList<Product> getActiveProducts() {
        return new ArrayList<>(getAllProducts());
    }

    // ดึงรายการสินค้าที่สต็อกต่ำ
    public List<Product> getLowStockProducts() {
        List<Product> lowStockList = new ArrayList<>();
        for (Product p : getAllProducts()) {
            if (p.isLowStock()) {
                lowStockList.add(p);
            }
        }
        return lowStockList;
    }

    // ค้นหาด้วย ID
    public Product getById(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = null;
        Product product = null;

        try {
            cursor = db.rawQuery("SELECT id, name, price, cost, stock, min_stock, barcode, unit FROM products WHERE id = ? LIMIT 1", new String[]{String.valueOf(id)});
            if (cursor != null && cursor.moveToFirst()) {
                long prodId = cursor.getLong(0);
                String name = cursor.getString(1);
                double price = cursor.getDouble(2);
                int cost = cursor.getInt(3);
                int stock = cursor.getInt(4);
                int minStock = cursor.getInt(5);
                String bc = cursor.getString(6);
                String unit = cursor.getString(7);
                product = new Product(prodId, name, price, cost, stock, minStock, bc, unit);
            }
        } catch (Exception e) {
            // Log error
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }

        return product;
    }

    // ค้นหาด้วย Barcode
    public Product getByBarcode(String barcode) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = null;
        Product product = null;

        try {
            cursor = db.rawQuery("SELECT id, name, price, cost, stock, min_stock, barcode, unit FROM products WHERE barcode = ? LIMIT 1", new String[]{barcode});
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                double price = cursor.getDouble(2);
                int cost = cursor.getInt(3);
                int stock = cursor.getInt(4);
                int minStock = cursor.getInt(5);
                String bc = cursor.getString(6);
                String unit = cursor.getString(7);
                product = new Product(id, name, price, cost, stock, minStock, bc, unit);
            }
        } catch (Exception e) {
            // Log error
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }

        return product;
    }

    // ปรับปรุงสต็อก (รองรับทั้งแบบ 3 และ 4 พารามิเตอร์)
    public boolean adjustStock(long productId, int quantityChange, String reason) {
        return adjustStock(productId, quantityChange, reason, null);
    }

    public boolean adjustStock(long productId, int quantityChange, String reason, String referenceId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        boolean success = false;

        try {
            db.beginTransaction();
            Cursor cursor = db.rawQuery("SELECT stock FROM products WHERE id = ? LIMIT 1", new String[]{String.valueOf(productId)});
            if (cursor != null && cursor.moveToFirst()) {
                int currentStock = cursor.getInt(0);
                int newStock = currentStock + quantityChange;
                if (newStock < 0) newStock = 0;

                ContentValues values = new ContentValues();
                values.put("stock", newStock);
                int rows = db.update("products", values, "id = ?", new String[]{String.valueOf(productId)});
                
                if (rows > 0) {
                    db.setTransactionSuccessful();
                    success = true;
                }
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            // Log error
        } finally {
            db.endTransaction();
            db.close();
        }

        return success;
    }

    public void close() {
        helper.close();
    }
}
