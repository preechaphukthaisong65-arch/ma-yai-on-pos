package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class StockRepository {

    private final LocalDatabase helper;

    public StockRepository(android.content.Context context) {
        helper = new LocalDatabase(context.getApplicationContext());
    }

    /**
     * บันทึกประวัติการเคลื่อนไหวของสต็อก
     */
    public boolean recordMovement(long productId, String movementType, int quantity, int stockBefore, int stockAfter, String referenceId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("product_id", productId);
        values.put("movement_type", movementType);
        values.put("quantity", quantity);
        values.put("stock_before", stockBefore);
        values.put("stock_after", stockAfter);
        values.put("reference_id", referenceId);
        values.put("created_at", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date()));

        try {
            long id = db.insertOrThrow("stock_movements", null, values);
            return id != -1;
        } catch (Exception e) {
            return false;
        }
    }

    public void close() {
        helper.close();
    }
}
