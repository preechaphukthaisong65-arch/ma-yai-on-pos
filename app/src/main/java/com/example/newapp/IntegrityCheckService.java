package com.example.newapp;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;

/** Read-only consistency checks; never changes or repairs user data automatically. */
final class IntegrityCheckService {
    static final class Result {
        final int checkedRules;
        final ArrayList<String> issues;
        Result(int checkedRules,ArrayList<String> issues){this.checkedRules=checkedRules;this.issues=issues;}
        boolean isClean(){return issues.isEmpty();}
    }
    private IntegrityCheckService() {}

    static Result run(PosDatabase helper) {
        SQLiteDatabase db=helper.getReadableDatabase();
        ArrayList<String> issues=new ArrayList<>();
        int rules=0;

        rules++;
        long invalidProducts=count(db,"SELECT COUNT(*) FROM products WHERE stock<0 OR price_satang<0 OR cost_satang<0 OR stock_limit<0",null);
        if(invalidProducts>0)issues.add("พบสินค้า "+invalidProducts+" รายการที่มีจำนวนหรือราคาไม่ถูกต้อง");

        rules++;
        long registryMismatch=count(db,"SELECT COUNT(*) FROM products p JOIN barcode_registry r ON r.barcode=p.barcode "+
                "WHERE p.name<>r.name COLLATE NOCASE OR p.price_satang<>r.price_satang",null);
        if(registryMismatch>0)issues.add("พบสินค้า "+registryMismatch+" รายการที่ข้อมูลคลังบาร์โค้ดไม่ตรงกับสินค้า");

        rules++;
        long badBills=count(db,"SELECT COUNT(*) FROM bills b WHERE NOT EXISTS "+
                "(SELECT 1 FROM bill_items i WHERE i.bill_id=b.id) OR b.subtotal_satang<>"+
                "(SELECT COALESCE(SUM(i.line_total_satang),0) FROM bill_items i WHERE i.bill_id=b.id)",null);
        if(badBills>0)issues.add("พบบิล "+badBills+" ใบที่ไม่มีรายการสินค้าหรือยอดสินค้าไม่ตรงกับรายละเอียด");

        rules++;
        long badSaleLedger=count(db,"SELECT COUNT(*) FROM sales_ledger l JOIN bills b ON b.id=l.bill_id "+
                "WHERE l.event_type='SALE' AND (l.subtotal_satang<>b.subtotal_satang OR "+
                "l.fee_satang<>b.fee_satang OR l.total_satang<>b.total_satang)",null);
        if(badSaleLedger>0)issues.add("พบรายการบัญชีขาย "+badSaleLedger+" รายการที่ยอดไม่ตรงกับบิล");

        rules++;
        long invalidUnpaid=count(db,"SELECT COUNT(*) FROM bills WHERE status='UNPAID' AND sale_type<>'CREDIT'",null);
        if(invalidUnpaid>0)issues.add("พบบิลค้างชำระ "+invalidUnpaid+" ใบที่ไม่ได้เป็นบิลขายเชื่อ");

        rules++;
        long invalidPayments=count(db,"SELECT COUNT(*) FROM payments p JOIN bills b ON b.id=p.bill_id WHERE b.status<>'PAID'",null);
        if(invalidPayments>0)issues.add("พบประวัติรับชำระ "+invalidPayments+" รายการที่บิลไม่ได้อยู่ในสถานะชำระแล้ว");

        rules++;
        Cursor fk=db.rawQuery("PRAGMA foreign_key_check",null);
        long foreignKeyErrors=0;
        try{while(fk.moveToNext())foreignKeyErrors++;}
        finally{fk.close();}
        if(foreignKeyErrors>0)issues.add("พบความสัมพันธ์ฐานข้อมูลผิดพลาด "+foreignKeyErrors+" จุด");

        return new Result(rules,issues);
    }

    private static long count(SQLiteDatabase db,String sql,String[] args) {
        Cursor c=db.rawQuery(sql,args);
        try{return c.moveToFirst()?c.getLong(0):0L;}finally{c.close();}
    }
}