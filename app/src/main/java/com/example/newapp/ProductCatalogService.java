package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Shared product creation, duplicate checks, price changes and price history. */
final class ProductCatalogService {
    private ProductCatalogService() {}

    static long create(PosDatabase helper,String barcode,String name,long priceSatang,
                       String unit,int stockLimit,long now) {
        if(helper==null||barcode==null||barcode.trim().isEmpty()||name==null||name.trim().isEmpty()
                ||priceSatang<0||unit==null||unit.trim().isEmpty()||stockLimit<0)
            throw new IllegalArgumentException("ข้อมูลสินค้าไม่ถูกต้อง");
        SQLiteDatabase db=helper.getWritableDatabase();
        db.beginTransaction();
        try {
            Cursor existing=db.query("products",new String[]{"active"},"barcode=?",
                    new String[]{barcode.trim()},null,null,null,"1");
            try {
                if(existing.moveToFirst()) {
                    throw new IllegalStateException(existing.getInt(0)==1
                            ?"สินค้านี้มีอยู่ในคลังแล้ว กรุณาใช้เมนูเพิ่มสินค้าเข้าสต๊อก"
                            :"บาร์โค้ดนี้เป็นสินค้าที่ปิดใช้งานอยู่ กรุณาตรวจสอบรายการเดิมก่อนเพิ่มใหม่");
                }
            } finally { existing.close(); }

            BarcodeRegistry.Entry registered=BarcodeRegistry.find(db,barcode.trim());
            BarcodeRegistry.ensureConsistent(db,barcode.trim(),name.trim(),priceSatang);
            ContentValues product=new ContentValues();
            product.put("barcode",barcode.trim());product.put("name",name.trim());
            product.put("price_satang",priceSatang);product.put("cost_satang",0L);
            product.put("unit",unit.trim());product.put("stock",0);product.put("stock_limit",stockLimit);
            product.put("created_at",now);product.put("updated_at",now);
            long id=db.insertOrThrow("products",null,product);
            if(registered==null)BarcodeRegistry.registerIfMissing(db,barcode.trim(),name.trim(),
                    priceSatang,"FACTORY",now);
            InventoryService.changeStock(db,id,barcode.trim(),name.trim(),0,0,"INITIAL",null,now);
            db.setTransactionSuccessful();
            return id;
        } finally { db.endTransaction(); }
    }

    static void updatePrice(PosDatabase helper,long id,String barcode,String name,
                            long expectedOldSatang,long newPriceSatang,long now) {
        if(helper==null||id<=0||barcode==null||barcode.trim().isEmpty()
                ||name==null||name.trim().isEmpty()||expectedOldSatang<0||newPriceSatang<0)
            throw new IllegalArgumentException("ข้อมูลราคาไม่ถูกต้อง");
        SQLiteDatabase db=helper.getWritableDatabase();
        db.beginTransaction();
        try {
            Cursor current=db.query("products",new String[]{"price_satang","barcode","name"},
                    "id=?",new String[]{String.valueOf(id)},null,null,null);
            try {
                if(!current.moveToFirst())throw new IllegalStateException("ไม่พบสินค้า");
                if(current.getLong(0)!=expectedOldSatang||!barcode.equals(current.getString(1))
                        ||!name.equals(current.getString(2)))
                    throw new IllegalStateException("ข้อมูลสินค้าหรือราคาเปลี่ยนแล้ว กรุณาสแกนใหม่");
            } finally { current.close(); }

            BarcodeRegistry.Entry registry=BarcodeRegistry.find(db,barcode.trim());
            if(registry!=null&&!name.equals(registry.name))
                throw new IllegalStateException("ชื่อสินค้าในคลังบาร์โค้ดไม่ตรงกับสินค้า กรุณาตรวจสอบข้อมูลก่อนเปลี่ยนราคา");
            ContentValues update=new ContentValues();update.put("price_satang",newPriceSatang);update.put("updated_at",now);
            if(db.update("products",update,"id=? AND price_satang=?",
                    new String[]{String.valueOf(id),String.valueOf(expectedOldSatang)})!=1)
                throw new IllegalStateException("บันทึกราคาไม่สำเร็จ กรุณาสแกนสินค้าใหม่");
            BarcodeRegistry.updatePrice(db,barcode.trim(),name.trim(),newPriceSatang,"LEGACY",now);
            ContentValues history=new ContentValues();history.put("product_id",id);history.put("barcode",barcode.trim());
            history.put("product_name",name.trim());history.put("old_price_satang",expectedOldSatang);
            history.put("new_price_satang",newPriceSatang);history.put("created_at",now);
            db.insertOrThrow("price_history",null,history);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    static void updateStockLimit(PosDatabase helper,long id,int expectedOld,int newLimit,long now) {
        if(helper==null||id<=0||expectedOld<0||newLimit<0)
            throw new IllegalArgumentException("ลิมิตสต๊อกไม่ถูกต้อง");
        SQLiteDatabase db=helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values=new ContentValues();values.put("stock_limit",newLimit);values.put("updated_at",now);
            if(db.update("products",values,"id=? AND stock_limit=?",
                    new String[]{String.valueOf(id),String.valueOf(expectedOld)})!=1)
                throw new IllegalStateException("ลิมิตเปลี่ยนระหว่างทำรายการ กรุณาสแกนใหม่");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}