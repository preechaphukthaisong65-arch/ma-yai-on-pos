
package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteConstraintException;

public class InventoryRepository {

    private final LocalDatabase helper;

    public InventoryRepository(LocalDatabase helper) {
        if (helper == null) {
            throw new IllegalArgumentException("ฐานข้อมูลห้ามเป็น null");
        }
        this.helper = helper;
    }

    /**
     * รับสินค้าเข้าคลัง
     * สินค้าเดิม: เพิ่มจำนวนเข้าจากยอดเดิม
     * บันทึกประวัติการรับสินค้าในธุรกรรมเดียวกัน
     */
    public boolean receiveStock(
            String barcode,
            int receivedQty,
            String note
    ) {
        if (isBlank(barcode) || receivedQty <= 0) {
            return false;
        }

        SQLiteDatabase db = helper.getWritableDatabase();
        boolean success = false;

        db.beginTransaction();
        try {
            ProductData product = findProduct(db, barcode.trim());

            if (product == null) {
                return false;
            }

            long newQty = (long) product.stockQty + receivedQty;
            if (newQty > Integer.MAX_VALUE) {
                return false;
            }

            ContentValues update = new ContentValues();
            update.put("stock_qty", (int) newQty);
            update.put("updated_at", LocalDatabase.now());

            int changed = db.update(
                    "products",
                    update,
                    "id = ?",
                    new String[]{String.valueOf(product.id)}
            );

            if (changed != 1) {
                return false;
            }

            insertMovement(
                    db,
                    product.id,
                    "RECEIVE",
                    product.stockQty,
                    (int) newQty,
                    note
            );

            db.setTransactionSuccessful();
            success = true;
        } catch (Exception e) {
            success = false;
        } finally {
            db.endTransaction();
        }

        return success;
    }

    /**
     * สร้างสินค้าใหม่และบันทึกจำนวนรับเข้าครั้งแรก
     * การสร้างสินค้าและประวัติรับเข้าจะสำเร็จหรือยกเลิกพร้อมกัน
     */
    public boolean createProductWithInitialStock(
            String barcode,
            String name,
            double salePrice,
            double costPrice,
            String unit,
            int minStock,
            int initialQty
    ) {
        if (isBlank(barcode)
                || isBlank(name)
                || isBlank(unit)
                || !validMoney(salePrice)
                || !validMoney(costPrice)
                || minStock < 0
                || initialQty < 0) {
            return false;
        }

        barcode = barcode.trim();
        name = name.trim();
        unit = unit.trim();

        SQLiteDatabase db = helper.getWritableDatabase();
        boolean success = false;

        db.beginTransaction();
        try {
            if (findProduct(db, barcode) != null) {
                return false;
            }

            String timestamp = LocalDatabase.now();

            ContentValues values = new ContentValues();
            values.put("barcode", barcode);
            values.put("name", name);
            values.put("price", salePrice);
            values.put("cost", costPrice);
            values.put("unit", unit);
            values.put("stock_qty", initialQty);
            values.put("min_stock", minStock);
            values.put("created_at", timestamp);
            values.put("updated_at", timestamp);

            long productId = db.insertOrThrow(
                    "products",
                    null,
                    values
            );

            if (initialQty > 0) {
                insertMovement(
                        db,
                        productId,
                        "INITIAL_RECEIPT",
                        0,
                        initialQty,
                        "รับสินค้าเข้าคลังครั้งแรก"
                );
            }

            db.setTransactionSuccessful();
            success = true;
        } catch (SQLiteConstraintException e) {
            success = false;
        } catch (Exception e) {
            success = false;
        } finally {
            db.endTransaction();
        }

        return success;
    }

    /**
     * ตรวจนับสต๊อก:
     * ใช้จำนวนที่นับได้แทนยอดเดิมทันที
     * บันทึกประวัติทุกครั้ง แม้จำนวนเท่าเดิม
     */
    public boolean stocktake(
            String barcode,
            int countedQty,
            String note
    ) {
        if (isBlank(barcode) || countedQty < 0) {
            return false;
        }

        SQLiteDatabase db = helper.getWritableDatabase();
        boolean success = false;

        db.beginTransaction();
        try {
            ProductData product = findProduct(db, barcode.trim());

            if (product == null) {
                return false;
            }

            long difference = (long) countedQty - product.stockQty;

            ContentValues update = new ContentValues();
            update.put("stock_qty", countedQty);
            update.put("updated_at", LocalDatabase.now());

            int changed = db.update(
                    "products",
                    update,
                    "id = ?",
                    new String[]{String.valueOf(product.id)}
            );

            if (changed != 1) {
                return false;
            }

            insertMovement(
                    db,
                    product.id,
                    "STOCKTAKE",
                    product.stockQty,
                    countedQty,
                    note == null ? "" : note
            );

            db.setTransactionSuccessful();
            success = true;
        } catch (Exception e) {
            success = false;
        } finally {
            db.endTransaction();
        }

        return success;
    }

    /**
     * แก้ไขเฉพาะลิมิตสต๊อก ไม่เปลี่ยนจำนวนสินค้า
     */
    public boolean setMinStock(String barcode, int minStock) {
        if (isBlank(barcode) || minStock < 0) {
            return false;
        }

        SQLiteDatabase db = helper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("min_stock", minStock);
        values.put("updated_at", LocalDatabase.now());

        int changed = db.update(
                "products",
                values,
                "barcode = ?",
                new String[]{barcode.trim()}
        );

        return changed == 1;
    }

    /**
     * รายการสินค้าที่จำนวนคงเหลือน้อยกว่าหรือเท่ากับลิมิต
     * ผู้เรียกต้องปิด Cursor หลังใช้งาน
     */
    public Cursor getLowStockProducts() {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.query(
                "products",
                new String[]{
                        "id",
                        "barcode",
                        "name",
                        "stock_qty",
                        "min_stock",
                        "unit"
                },
                "stock_qty <= min_stock",
                null,
                null,
                null,
                "stock_qty ASC, name COLLATE NOCASE ASC"
        );
    }

    /**
     * ประวัติความเคลื่อนไหวของสินค้าหนึ่งรายการ
     * เรียงรายการล่าสุดขึ้นก่อน
     */
    public Cursor getStockHistory(String barcode) {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.rawQuery(
                "SELECT sm.id, p.barcode, p.name, " +
                        "sm.operation, sm.qty_before, sm.qty_after, " +
                        "sm.qty_diff, sm.note, sm.created_at " +
                        "FROM stock_movements sm " +
                        "INNER JOIN products p ON p.id = sm.product_id " +
                        "WHERE p.barcode = ? " +
                        "ORDER BY sm.id DESC",
                new String[]{barcode == null ? "" : barcode.trim()}
        );
    }

    /**
     * ค้นหาสินค้าด้วยบาร์โค้ดตรงตัว
     */
    public Cursor findProductCursor(String barcode) {
        SQLiteDatabase db = helper.getReadableDatabase();

        return db.query(
                "products",
                new String[]{
                        "id",
                        "barcode",
                        "name",
                        "price",
                        "cost",
                        "unit",
                        "stock_qty",
                        "min_stock",
                        "created_at",
                        "updated_at"
                },
                "barcode = ?",
                new String[]{barcode == null ? "" : barcode.trim()},
                null,
                null,
                null,
                "1"
        );
    }

    private ProductData findProduct(
            SQLiteDatabase db,
            String barcode
    ) {
        Cursor cursor = null;

        try {
            cursor = db.query(
                    "products",
                    new String[]{"id", "stock_qty"},
                    "barcode = ?",
                    new String[]{barcode},
                    null,
                    null,
                    null,
                    "1"
            );

            if (!cursor.moveToFirst()) {
                return null;
            }

            return new ProductData(
                    cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("stock_qty"))
            );
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void insertMovement(
            SQLiteDatabase db,
            long productId,
            String operation,
            int beforeQty,
            int afterQty,
            String note
    ) {
        ContentValues movement = new ContentValues();
        movement.put("product_id", productId);
        movement.put("operation", operation);
        movement.put("qty_before", beforeQty);
        movement.put("qty_after", afterQty);
        movement.put("qty_diff", afterQty - beforeQty);
        movement.put("note", note == null ? "" : note);
        movement.put("created_at", LocalDatabase.now());

        long id = db.insertOrThrow(
                "stock_movements",
                null,
                movement
        );

        if (id == -1) {
            throw new IllegalStateException(
                    "ไม่สามารถบันทึกประวัติสต๊อกได้"
            );
        }
    }

    private boolean validMoney(double value) {
        return !Double.isNaN(value)
                && !Double.isInfinite(value)
                && value >= 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class ProductData {
        final long id;
        final int stockQty;

        ProductData(long id, int stockQty) {
            this.id = id;
            this.stockQty = stockQty;
        }
    }
}
