package com.example.newapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

public class SalesRepository {

    private final LocalDatabase helper;

    public SalesRepository(Context context) {
        helper = new LocalDatabase(context.getApplicationContext());
    }

    /**
     * บันทึกรายการขายลงฐานข้อมูล
     */
    public boolean saveSale(String receiptNo, double totalAmount, String paymentMethod, String soldAt) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("receipt_no", receiptNo);
        values.put("total_amount", totalAmount);
        values.put("payment_method", paymentMethod);
        values.put("sold_at", soldAt);

        try {
            long id = db.insertOrThrow("sales", null, values);
            return id != -1;
        } catch (Exception e) {
            return false;
        }
    }

    public void close() {
        helper.close();
    }
}
