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
        PosUi.back(this,root);
    }
    private long startOfDay(){Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private long startOfWeek(){Calendar c=Calendar.getInstance();c.setFirstDayOfWeek(Calendar.MONDAY);c.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private long startOfMonth(){Calendar c=Calendar.getInstance();c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private void custom(){root=PosUi.page(this,"รายงานตามช่วงวันที่");EditText from=PosUi.input(this,root,"วันที่เริ่ม YYYY-MM-DD");EditText to=PosUi.input(this,root,"วันที่สิ้นสุด YYYY-MM-DD");
        PosUi.button(this,root,"แสดงรายงาน",v->{try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setLenient(false);long a=f.parse(PosUi.str(from)).getTime();long z=f.parse(PosUi.str(to)).getTime()+86400000L-1;if(z<a)throw new IllegalArgumentException();period("ช่วงที่เลือก",a,z);}catch(Exception e){from.setError("กรอกวันที่รูปแบบ YYYY-MM-DD ให้ถูกต้อง");}});PosUi.back(this,root);}
    private void period(String label,long from,long to){
        root=PosUi.page(this,"รายงานยอดขาย • "+label);SQLiteDatabase db=helper.getReadableDatabase();
        Cursor c=db.rawQuery("SELECT COALESCE(SUM(subtotal_satang),0),COALESCE(SUM(fee_satang),0),COALESCE(SUM(total_satang),0),COUNT(*) FROM sales_ledger WHERE created_at BETWEEN ? AND ? AND event_type IN ('SALE','CANCEL','RETURN','CREDIT_FEE_WAIVED')",new String[]{String.valueOf(from),String.valueOf(to)});
        if(c.moveToFirst()){PosUi.line(this,root,"ยอดสินค้า (สุทธิ): "+PosDatabase.money(c.getLong(0))+" บาท");PosUi.line(this,root,"ค่าบริการสุทธิ: "+PosDatabase.money(c.getLong(1))+" บาท");PosUi.line(this,root,"ยอดขายสุทธิ: "+PosDatabase.money(c.getLong(2))+" บาท");PosUi.line(this,root,"จำนวนรายการบัญชี: "+c.getInt(3));}c.close();
        Cursor cash=db.rawQuery("SELECT COALESCE(SUM(total_satang),0) FROM sales_ledger WHERE created_at BETWEEN ? AND ? AND sale_type='CASH' AND event_type='SALE'",new String[]{String.valueOf(from),String.valueOf(to)});if(cash.moveToFirst())PosUi.line(this,root,"ยอดขายเงินสดก่อนคืน/ยกเลิก: "+PosDatabase.money(cash.getLong(0))+" บาท");cash.close();
        PosUi.back(this,root);
    }
    private void topProducts(boolean best){
        root=PosUi.page(this,best?"สินค้าขายดี 20 อันดับ":"สินค้าขายน้อย 20 อันดับ");long from=PosDatabase.now()-30L*86400000L;
        String sql="SELECT bi.product_name, SUM(bi.quantity) qty FROM bill_items bi JOIN bills b ON b.id=bi.bill_id WHERE b.created_at>=? AND b.status IN ('PAID','UNPAID') GROUP BY bi.product_id,bi.product_name ORDER BY qty "+(best?"DESC":"ASC")+",bi.product_name LIMIT 20";
        Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(from)});int i=1;while(c.moveToNext())PosUi.line(this,root,i+++". "+c.getString(0)+" — "+c.getLong(1)+" ชิ้น");if(i==1)PosUi.line(this,root,"ยังไม่มีข้อมูลการขายในช่วงนี้");c.close();PosUi.back(this,root);
    }
}