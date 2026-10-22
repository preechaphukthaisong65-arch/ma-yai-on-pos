package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Shared business rules and atomic persistence for full-bill returns/cancellations. */
final class ReturnCancellationService {
    static final long WINDOW_MS=86400000L;
    static final long ARCHIVE_MS=30L*86400000L;

    static final class BillInfo {
        final long id,subtotal,fee,total,createdAt,archiveUntil;
        final String code,type,status;
        final boolean feeWaived;
        BillInfo(long id,String code,String type,String status,long subtotal,long fee,long total,
                 long createdAt,long archiveUntil,boolean feeWaived) {
            this.id=id;this.code=code;this.type=type;this.status=status;this.subtotal=subtotal;
            this.fee=fee;this.total=total;this.createdAt=createdAt;this.archiveUntil=archiveUntil;
            this.feeWaived=feeWaived;
        }
        long displayFee(){return feeWaived?0:fee;}
        long displayTotal(){return feeWaived?subtotal:total;}
    }

    private ReturnCancellationService() {}

    static BillInfo inspect(PosDatabase helper,String code,long now) {
        if(helper==null||code==null||code.trim().isEmpty())throw new IllegalArgumentException("เลขที่บิลไม่ถูกต้อง");
        SQLiteDatabase db=helper.getReadableDatabase();
        Cursor c=db.query("bills",new String[]{"id","bill_code","sale_type","status","subtotal_satang",
                "fee_satang","total_satang","created_at","archive_until"},"bill_code=?",
                new String[]{code.trim()},null,null,null,"1");
        try {
            if(!c.moveToFirst())throw new IllegalStateException("ไม่พบบิลนี้");
            long id=c.getLong(0),sub=c.getLong(4),fee=c.getLong(5),total=c.getLong(6),created=c.getLong(7);
            long archive=c.isNull(8)?0:c.getLong(8);
            String type=c.getString(2),status=c.getString(3);
            if(("CANCELLED".equals(status)||"RETURNED".equals(status))&&archive<=now)
                throw new IllegalStateException("ไม่พบบิลนี้");
            validateWindow(created,now);
            if(!"PAID".equals(status)&&!"UNPAID".equals(status))
                throw new IllegalStateException("บิลนี้ถูกยกเลิกหรือคืนไปแล้ว");
            boolean waived="CREDIT".equals(type)&&"PAID".equals(status)&&hasWaiver(db,id);
            return new BillInfo(id,c.getString(1),type,status,sub,fee,total,created,archive,waived);
        } finally { c.close(); }
    }

    static void commit(PosDatabase helper,long id,String code,boolean returning,long now) {
        if(helper==null||id<=0||code==null||code.trim().isEmpty())
            throw new IllegalArgumentException("ข้อมูลบิลไม่ถูกต้อง");
        SQLiteDatabase db=helper.getWritableDatabase();
        db.beginTransaction();
        try {
            Cursor bill=db.query("bills",new String[]{"sale_type","status","subtotal_satang","fee_satang","created_at"},
                    "id=? AND bill_code=?",new String[]{String.valueOf(id),code.trim()},null,null,null);
            String type,status;long subtotal,fee,created;
            try {
                if(!bill.moveToFirst())throw new IllegalStateException("ไม่พบบิล");
                type=bill.getString(0);status=bill.getString(1);subtotal=bill.getLong(2);
                fee=bill.getLong(3);created=bill.getLong(4);
            } finally { bill.close(); }
            validateWindow(created,now);
            if(!"PAID".equals(status)&&!"UNPAID".equals(status))
                throw new IllegalStateException("บิลถูกดำเนินการไปแล้ว");

            Cursor items=db.query("bill_items",new String[]{"product_id","barcode","product_name","quantity",
                    "unit_price_satang","line_total_satang"},"bill_id=?",new String[]{String.valueOf(id)},null,null,null);
            int count=0;long itemTotal=0;
            try {
                while(items.moveToNext()) {
                    count++;long storedId=items.isNull(0)?0:items.getLong(0);
                    String barcode=items.getString(1),name=items.getString(2);
                    int qty=items.getInt(3);long price=items.getLong(4),line=items.getLong(5);
                    if(qty<=0||price<0||line<0||Math.multiplyExact(price,(long)qty)!=line)
                        throw new IllegalStateException("รายละเอียดสินค้าในบิลไม่ถูกต้อง: "+name);
                    Cursor p=storedId>0
                            ?db.query("products",new String[]{"id","stock"},"id=?",new String[]{String.valueOf(storedId)},null,null,null)
                            :db.query("products",new String[]{"id","stock"},"barcode=? AND active=1",new String[]{barcode},null,null,null);
                    long pid;int before;
                    try {
                        if(!p.moveToFirst())throw new IllegalStateException("ไม่พบสินค้าในคลัง: "+name);
                        pid=p.getLong(0);before=p.getInt(1);
                    } finally { p.close(); }
                    int after;
                    try { after=Math.addExact(before,qty); }
                    catch(ArithmeticException e){throw new IllegalStateException("จำนวนสต๊อกเกินขอบเขต: "+name);}
                    InventoryService.changeStock(db,pid,barcode,name,before,after,"RETURN_CANCEL",code.trim(),now);
                    ContentValues li=new ContentValues();li.put("bill_id",id);li.put("bill_code",code.trim());
                    li.put("event_type",returning?"RETURN":"CANCEL");li.put("barcode",barcode);
                    li.put("product_name",name);li.put("quantity",-qty);li.put("unit_price_satang",price);
                    li.put("line_total_satang",-line);li.put("created_at",now);
                    db.insertOrThrow("sales_ledger_items",null,li);
                    itemTotal=Math.addExact(itemTotal,line);
                }
            } finally { items.close(); }
            if(count==0||itemTotal!=subtotal)
                throw new IllegalStateException("รายละเอียดสินค้าในบิลไม่ตรงกับยอดบิล จึงยกเลิกการดำเนินการ");

            ContentValues b=new ContentValues();b.put("status",returning?"RETURNED":"CANCELLED");
            b.put("archived_at",now);b.put("archive_until",Math.addExact(now,ARCHIVE_MS));
            if(db.update("bills",b,"id=? AND bill_code=? AND status=?",
                    new String[]{String.valueOf(id),code.trim(),status})!=1)
                throw new IllegalStateException("สถานะบิลเปลี่ยนแล้ว ไม่สามารถดำเนินการซ้ำได้");
            long ledgerFee="CREDIT".equals(type)?("PAID".equals(status)?0:-fee):0;
            ContentValues l=new ContentValues();l.put("bill_id",id);l.put("bill_code",code.trim());
            l.put("sale_type",type);l.put("event_type",returning?"RETURN":"CANCEL");
            l.put("subtotal_satang",-subtotal);l.put("fee_satang",ledgerFee);
            l.put("total_satang",Math.addExact(-subtotal,ledgerFee));l.put("created_at",now);
            db.insertOrThrow("sales_ledger",null,l);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static void validateWindow(long created,long now) {
        if(now<created||now-created>WINDOW_MS)
            throw new IllegalStateException("ซื้อสินค้าเกิน 1 วัน ไม่สามารถคืนสินค้าหรือยกเลิกบิลได้");
    }

    private static boolean hasWaiver(SQLiteDatabase db,long id) {
        Cursor c=db.query("sales_ledger",new String[]{"id"},"bill_id=? AND event_type='CREDIT_FEE_WAIVED'",
                new String[]{String.valueOf(id)},null,null,null,"1");
        try { return c.moveToFirst(); } finally { c.close(); }
    }
}