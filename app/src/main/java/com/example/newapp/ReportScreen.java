package com.example.newapp;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ReportScreen extends Activity {
    private PosDatabase helper;private LinearLayout root;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);home();}
    private void home(){root=PosUi.page(this,"รายงานยอดขาย");
        PosUi.button(this,root,"ยอดขายวันนี้",v->period("วันนี้",startOfDay(),PosDatabase.now()));
        PosUi.button(this,root,"ยอดขายสัปดาห์นี้",v->period("สัปดาห์นี้",startOfWeek(),PosDatabase.now()));
        PosUi.button(this,root,"ยอดขายเดือนนี้",v->period("เดือนนี้",startOfMonth(),PosDatabase.now()));
        PosUi.button(this,root,"กำหนดช่วงวันที่เอง",v->custom());
        PosUi.button(this,root,"สินค้าขายดี 20 อันดับ (30 วัน)",v->topProducts(true));
        PosUi.button(this,root,"สินค้าขายน้อย 20 อันดับ (30 วัน)",v->topProducts(false));
        PosUi.button(this,root,"รายการบิลคืนสินค้า / ยกเลิก",v->returnedCancelled());
        PosUi.back(this,root);
    }
    private void returnedCancelled(){
        root=PosUi.page(this,"รายการบิลคืนสินค้า / ยกเลิก");
        Cursor c=helper.getReadableDatabase().rawQuery("SELECT bill_code,event_type,subtotal_satang,fee_satang,total_satang,created_at,sale_type,CASE WHEN event_type IN ('RETURN','CANCEL') AND sale_type='CREDIT' AND fee_satang=0 AND NOT EXISTS (SELECT 1 FROM sales_ledger w WHERE w.bill_id=sales_ledger.bill_id AND w.event_type='CREDIT_FEE_WAIVED') THEN COALESCE((SELECT s.fee_satang FROM sales_ledger s WHERE s.bill_id=sales_ledger.bill_id AND s.event_type='SALE'),0) ELSE fee_satang END AS display_fee FROM sales_ledger WHERE event_type IN ('RETURN','CANCEL') ORDER BY created_at DESC",null);
        if(!c.moveToFirst())PosUi.line(this,root,"ยังไม่มีบิลคืนสินค้าหรือยกเลิก");
        else do{String event=c.getString(1);long shownFee=c.getLong(7),ledgerFee=c.getLong(3);String feeText="";if("CREDIT".equals(c.getString(6))&&shownFee!=0){feeText=ledgerFee<0?" • ยกเลิกค่าบริการ "+PosDatabase.money(Math.abs(shownFee))+" บาท":" • ค่าบริการที่ไม่คืน "+PosDatabase.money(Math.abs(shownFee))+" บาท";}PosUi.line(this,root,c.getString(0)+" • "+(event.equals("RETURN")?"คืนสินค้า":"ยกเลิก")+" • ยอดสินค้า "+PosDatabase.money(Math.abs(c.getLong(2)))+" บาท"+feeText+" • "+new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(c.getLong(5))));}while(c.moveToNext());
        c.close();PosUi.back(this,root);
    }
    private long startOfDay(){Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private long startOfWeek(){Calendar c=Calendar.getInstance();c.setFirstDayOfWeek(Calendar.MONDAY);c.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private long startOfMonth(){Calendar c=Calendar.getInstance();c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private void custom(){root=PosUi.page(this,"รายงานตามช่วงวันที่");EditText from=PosUi.input(this,root,"วันที่เริ่ม YYYY-MM-DD");EditText to=PosUi.input(this,root,"วันที่สิ้นสุด YYYY-MM-DD");
        PosUi.button(this,root,"แสดงรายงาน",v->{try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setLenient(false);long a=f.parse(PosUi.str(from)).getTime();long z=f.parse(PosUi.str(to)).getTime()+86400000L-1;if(z<a)throw new IllegalArgumentException();period("ช่วงที่เลือก",a,z);}catch(Exception e){from.setError("กรอกวันที่รูปแบบ YYYY-MM-DD ให้ถูกต้อง");}});PosUi.back(this,root);}
    private void period(String label,long from,long to){
        root=PosUi.page(this,"รายงานยอดขาย • "+label);SQLiteDatabase db=helper.getReadableDatabase();
        Cursor gross=db.rawQuery("SELECT COALESCE(SUM(total_satang),0) FROM sales_ledger WHERE created_at BETWEEN ? AND ? AND event_type='SALE'",new String[]{String.valueOf(from),String.valueOf(to)});if(gross.moveToFirst())PosUi.line(this,root,"ยอดขายก่อนคืน/ยกเลิก: "+PosDatabase.money(gross.getLong(0))+" บาท");gross.close();
        Cursor c=db.rawQuery("SELECT COALESCE(SUM(subtotal_satang),0),COALESCE(SUM(fee_satang),0),COALESCE(SUM(total_satang),0),COUNT(*) FROM sales_ledger WHERE created_at BETWEEN ? AND ? AND event_type IN ('SALE','CANCEL','RETURN','CREDIT_FEE_WAIVED')",new String[]{String.valueOf(from),String.valueOf(to)});
        if(c.moveToFirst()){PosUi.line(this,root,"ยอดสินค้า (สุทธิ): "+PosDatabase.money(c.getLong(0))+" บาท");PosUi.line(this,root,"ค่าบริการสุทธิ: "+PosDatabase.money(c.getLong(1))+" บาท");PosUi.line(this,root,"ยอดขายสุทธิ: "+PosDatabase.money(c.getLong(2))+" บาท");PosUi.line(this,root,"จำนวนรายการบัญชี: "+c.getInt(3));}c.close();
        Cursor soldQty=db.rawQuery("SELECT COALESCE(SUM(quantity),0) FROM sales_ledger_items WHERE created_at BETWEEN ? AND ? AND event_type='SALE'",new String[]{String.valueOf(from),String.valueOf(to)});if(soldQty.moveToFirst())PosUi.line(this,root,"จำนวนสินค้าที่ขาย: "+soldQty.getLong(0)+" ชิ้น");soldQty.close();
        Cursor netQty=db.rawQuery("SELECT COALESCE(SUM(quantity),0) FROM sales_ledger_items WHERE created_at BETWEEN ? AND ? AND event_type IN ('SALE','CANCEL','RETURN')",new String[]{String.valueOf(from),String.valueOf(to)});if(netQty.moveToFirst())PosUi.line(this,root,"จำนวนสินค้าสุทธิหลังคืน/ยกเลิก: "+netQty.getLong(0)+" ชิ้น");netQty.close();
        Cursor cash=db.rawQuery("SELECT COALESCE(SUM(total_satang),0) FROM sales_ledger WHERE created_at BETWEEN ? AND ? AND sale_type='CASH' AND event_type='SALE'",new String[]{String.valueOf(from),String.valueOf(to)});if(cash.moveToFirst())PosUi.line(this,root,"ยอดขายเงินสดก่อนคืน/ยกเลิก: "+PosDatabase.money(cash.getLong(0))+" บาท");cash.close();
        PosUi.back(this,root);
    }
    private void topProducts(boolean best){
        root=PosUi.page(this,best?"สินค้าขายดี 20 อันดับ":"สินค้าขายน้อย 20 อันดับ");
        long now=PosDatabase.now(),from=now-30L*86400000L;
        String eligible="WITH eligible AS (SELECT DISTINCT bill_id FROM sales_ledger_items WHERE event_type='SALE' AND created_at>=? AND created_at<=?), qty AS (SELECT barcode, SUM(quantity) AS qty FROM sales_ledger_items WHERE bill_id IN (SELECT bill_id FROM eligible) AND event_type IN ('SALE','CANCEL','RETURN') GROUP BY barcode) ";
        String sql;
        if(best){
            sql=eligible+"SELECT i.product_name, SUM(i.quantity) AS qty FROM sales_ledger_items i WHERE i.bill_id IN (SELECT bill_id FROM eligible) AND i.event_type IN ('SALE','CANCEL','RETURN') GROUP BY i.barcode,i.product_name HAVING SUM(i.quantity)>0 ORDER BY qty DESC,i.product_name COLLATE NOCASE ASC LIMIT 20";
        }else{
            sql=eligible+"SELECT p.name, MAX(0,COALESCE(q.qty,0)) AS qty FROM products p LEFT JOIN qty q ON q.barcode=p.barcode WHERE p.active=1 ORDER BY qty ASC,p.name COLLATE NOCASE ASC LIMIT 20";
        }
        Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(from),String.valueOf(now)});
        int i=1;
        while(c.moveToNext()){
            long qty=c.getLong(1);
            String note=!best&&qty==0?" • ไม่มีประวัติขายสุทธิใน 30 วัน":"";
            PosUi.line(this,root,i+++". "+c.getString(0)+" — "+qty+" ชิ้น"+note);
        }
        if(i==1)PosUi.line(this,root,best?"ยังไม่มีสินค้าที่มียอดขายสุทธิในช่วงนี้":"ยังไม่มีสินค้าในคลัง");
        c.close();PosUi.back(this,root);
    }
}