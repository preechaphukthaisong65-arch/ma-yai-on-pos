package com.example.newapp;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/**
 * Central stock mutation rules. Call inside the caller's SQLite transaction so
 * stock changes and their history are committed or rolled back together.
 */
final class InventoryService {
    private InventoryService() {}

    static void changeStock(PosDatabase helper,long productId,String barcode,String productName,
                            int expectedBefore,int after,String changeType,String reference,long timestamp) {
        if(helper==null)throw new IllegalArgumentException("ฐานข้อมูลไม่พร้อมใช้งาน");
        SQLiteDatabase db=helper.getWritableDatabase();
        db.beginTransaction();
        try {
            changeStock(db,productId,barcode,productName,expectedBefore,after,changeType,reference,timestamp);
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    static void changeStock(SQLiteDatabase db, long productId, String barcode,
                            String productName, int expectedBefore, int after,
                            String changeType, String reference, long timestamp) {
        if (db == null) throw new IllegalArgumentException("ฐานข้อมูลไม่พร้อมใช้งาน");
        if (productId <= 0) throw new IllegalArgumentException("รหัสสินค้าไม่ถูกต้อง");
        if (barcode == null || barcode.trim().isEmpty()) throw new IllegalArgumentException("ไม่มีบาร์โค้ดสินค้า");
        if (productName == null || productName.trim().isEmpty()) throw new IllegalArgumentException("ไม่มีชื่อสินค้า");
        if (expectedBefore < 0 || after < 0) throw new IllegalArgumentException("จำนวนสต๊อกต้องไม่ติดลบ");
        if (changeType == null || changeType.trim().isEmpty()) throw new IllegalArgumentException("ไม่ระบุประเภทการเปลี่ยนสต๊อก");

        ContentValues product = new ContentValues();
        product.put("stock", after);
        product.put("updated_at", timestamp);
        int updated = db.update("products", product, "id=? AND stock=?",
                new String[]{String.valueOf(productId), String.valueOf(expectedBefore)});
        if (updated != 1) {
            throw new IllegalStateException("สต๊อกเปลี่ยนระหว่างทำรายการ กรุณาตรวจสอบสินค้าแล้วลองใหม่");
        }

        ContentValues history = new ContentValues();
        history.put("product_id", productId);
        history.put("barcode", barcode);
        history.put("product_name", productName);
        history.put("change_type", changeType);
        history.put("before_qty", expectedBefore);
        history.put("after_qty", after);
        history.put("difference", after - expectedBefore);
        if (reference != null && !reference.trim().isEmpty()) history.put("reference", reference);
        history.put("created_at", timestamp);
        db.insertOrThrow("stock_history", null, history);
        AuditService.record(db,"PRODUCT",String.valueOf(productId),"STOCK_"+changeType,
                "barcode="+barcode+";before="+expectedBefore+";after="+after+";reference="+(reference==null?"":reference),timestamp);
    }
}
