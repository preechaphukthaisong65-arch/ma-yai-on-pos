package com.example.newapp;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ReportScreen extends Activity {
    private PosDatabase helper;private LinearLayout root;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);home();}
    private SalesChartView annualChart;
    private int chartYear;
    private void home(){
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN|android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE|android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        LinearLayout screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);screen.setBackgroundColor(android.graphics.Color.WHITE);
        annualChart=new SalesChartView(this);Calendar yearCal=AppClock.calendar();chartYear=yearCal.get(Calendar.YEAR);updateAnnualChart(chartYear);screen.addView(annualChart,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout lower=new LinearLayout(this);lower.setOrientation(LinearLayout.VERTICAL);lower.setPadding(PosUi.dp(this,8),PosUi.dp(this,4),PosUi.dp(this,8),PosUi.dp(this,2));lower.setBackgroundColor(android.graphics.Color.WHITE);
        addMenuButton(lower,"📊  สรุปยอดขาย",v->summaryReport());
        addMenuButton(lower,"📈  สินค้าขายดี 20 อันดับ (30 วัน)",v->topProducts(true));
        addMenuButton(lower,"📉  สินค้าขายน้อย 20 อันดับ (30 วัน)",v->topProducts(false));
        addMenuButton(lower,"🧾  รายการบิลคืนสินค้า / ยกเลิก",v->returnedCancelled());
        addMenuButton(lower,"📋  ประวัติการทำรายการสำคัญ",v->auditLog());
        screen.addView(lower,new LinearLayout.LayoutParams(-1,0,1f));
        PosUi.showRootWithBack(this,screen,this::finish);
    }
    private void addMenuButton(LinearLayout parent,String label,android.view.View.OnClickListener action){
        android.widget.Button button=new android.widget.Button(this);button.setText(label);button.setTextSize(14);button.setSingleLine(true);button.setMinHeight(0);button.setPadding(PosUi.dp(this,6),0,PosUi.dp(this,6),0);button.setOnClickListener(action);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1f);lp.setMargins(0,PosUi.dp(this,1),0,PosUi.dp(this,1));parent.addView(button,lp);
    }
    private void summaryReport(){
        root=PosUi.page(this,"สรุปยอดขาย");
        TextView summaryTitle=PosUi.text(this,"สรุปยอดขาย • วันนี้ / สัปดาห์นี้ / เดือนนี้",14,android.graphics.Color.rgb(35,80,48));root.addView(summaryTitle,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout summaries=new LinearLayout(this);summaries.setOrientation(LinearLayout.HORIZONTAL);
        addSummary(summaries,"วันนี้",startOfDay(),PosDatabase.now());addSummary(summaries,"สัปดาห์นี้",startOfWeek(),PosDatabase.now());addSummary(summaries,"เดือนนี้",startOfMonth(),PosDatabase.now());
        root.addView(summaries,new LinearLayout.LayoutParams(-1,-2));
        TextView chooseTitle=PosUi.text(this,"เลือกช่วงรายงาน",14,android.graphics.Color.rgb(35,80,48));root.addView(chooseTitle,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);addPeriodButton(row1,"วัน",v->period("วันนี้",startOfDay(),PosDatabase.now()));addPeriodButton(row1,"สัปดาห์",v->period("สัปดาห์นี้",startOfWeek(),PosDatabase.now()));root.addView(row1,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);addPeriodButton(row2,"เดือน",v->period("เดือนนี้",startOfMonth(),PosDatabase.now()));addPeriodButton(row2,"ปี",v->period("ปี "+chartYear,yearStart(chartYear),yearEnd(chartYear)));root.addView(row2,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout row3=new LinearLayout(this);row3.setOrientation(LinearLayout.HORIZONTAL);addPeriodButton(row3,"เลือกปีกราฟ",v->chooseChartYear());addPeriodButton(row3,"กำหนดช่วงวันที่",v->custom());root.addView(row3,new LinearLayout.LayoutParams(-1,-2));
    }
    private void addSummary(LinearLayout row,String label,long from,long to){
        long amount=netSales(from,to);LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(PosUi.dp(this,5),PosUi.dp(this,5),PosUi.dp(this,5),PosUi.dp(this,5));card.setBackgroundColor(android.graphics.Color.WHITE);
        TextView title=PosUi.text(this,label,12,android.graphics.Color.DKGRAY);title.setGravity(android.view.Gravity.CENTER);
        TextView value=PosUi.text(this,PosDatabase.money(amount),12,android.graphics.Color.rgb(20,20,20));value.setGravity(android.view.Gravity.CENTER);value.setSingleLine(true);
        card.addView(title,new LinearLayout.LayoutParams(-1,-2));card.addView(value,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);lp.setMargins(PosUi.dp(this,2),PosUi.dp(this,2),PosUi.dp(this,2),PosUi.dp(this,4));row.addView(card,lp);
    }
    private void addPeriodButton(LinearLayout row,String label,android.view.View.OnClickListener action){
        android.widget.Button b=new android.widget.Button(this);b.setText(label);b.setTextSize(16);b.setMinHeight(PosUi.dp(this,42));b.setPadding(PosUi.dp(this,3),0,PosUi.dp(this,3),0);b.setOnClickListener(action);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,PosUi.dp(this,44),1f);lp.setMargins(PosUi.dp(this,2),PosUi.dp(this,1),PosUi.dp(this,2),PosUi.dp(this,1));row.addView(b,lp);
    }
    private long netSales(long from,long to){
        Cursor c=helper.getReadableDatabase().rawQuery("SELECT COALESCE(SUM(total_satang),0) FROM sales_ledger WHERE created_at BETWEEN ? AND ? AND event_type IN ('SALE','CANCEL','RETURN','CREDIT_FEE_WAIVED')",new String[]{String.valueOf(from),String.valueOf(to)});
        long total=0;if(c.moveToFirst())total=c.getLong(0);c.close();return total;
    }
    private long yearStart(int year){Calendar c=AppClock.calendar();c.set(Calendar.YEAR,year);c.set(Calendar.MONTH,Calendar.JANUARY);c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private long yearEnd(int year){Calendar c=AppClock.calendar();c.set(Calendar.YEAR,year);c.set(Calendar.MONTH,Calendar.DECEMBER);c.set(Calendar.DAY_OF_MONTH,31);c.set(Calendar.HOUR_OF_DAY,23);c.set(Calendar.MINUTE,59);c.set(Calendar.SECOND,59);c.set(Calendar.MILLISECOND,999);return c.getTimeInMillis();}
    private void updateAnnualChart(int year){
        chartYear=year;String[] labels={"ม.ค.","ก.พ.","มี.ค.","เม.ย.","พ.ค.","มิ.ย.","ก.ค.","ส.ค.","ก.ย.","ต.ค.","พ.ย.","ธ.ค."};long[] values=new long[12];
        Calendar c=AppClock.calendar();c.set(Calendar.YEAR,year);c.set(Calendar.MONTH,Calendar.JANUARY);c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        for(int i=0;i<12;i++){long from=c.getTimeInMillis();c.add(Calendar.MONTH,1);long to=c.getTimeInMillis()-1;values[i]=netSales(from,to);}
        annualChart.setData("กราฟยอดขายสุทธิรายเดือน • "+year,labels,values);
    }
    private void chooseChartYear(){
        EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setSingleLine(true);input.setText(String.valueOf(chartYear));input.setSelectAllOnFocus(true);
        new android.app.AlertDialog.Builder(this).setTitle("เลือกปีของกราฟ").setMessage("กรอกปี พ.ศ. เช่น 2569").setView(input).setPositiveButton("แสดงกราฟ",(d,w)->{try{int year=Integer.parseInt(PosUi.str(input));if(year<2400||year>2700)throw new IllegalArgumentException();updateAnnualChart(year);}catch(Exception e){new android.app.AlertDialog.Builder(this).setMessage("กรุณากรอกปี พ.ศ. ระหว่าง 2400–2700").setPositiveButton("ตกลง",null).show();}}).setNegativeButton("ยกเลิก",null).show();
    }
    private void auditLog(){
        root=PosUi.page(this,"ประวัติการทำรายการสำคัญ");
        Cursor c=helper.getReadableDatabase().query("audit_log",new String[]{"actor_label","action","entity_type","entity_id","details","created_at"},null,null,null,null,"created_at DESC","200");
        if(!c.moveToFirst())PosUi.line(this,root,"ยังไม่มีประวัติการทำรายการสำคัญ");
        else do{
            String entity=c.getString(2)+(c.isNull(3)?"":" #"+c.getString(3));
            String details=c.isNull(4)||c.getString(4).isEmpty()?"":"\n"+c.getString(4);
            PosUi.line(this,root,new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.getDefault()).format(new Date(c.getLong(5)))+" • "+c.getString(1)+" • "+entity+"\nผู้ทำรายการ: "+c.getString(0)+details);
        }while(c.moveToNext());
        c.close();
        PosUi.line(this,root,"หมายเหตุ: จนกว่าจะเพิ่มระบบบัญชีผู้ใช้ รายการยังไม่สามารถระบุตัวบุคคลผู้ทำรายการได้");
        PosUi.back(this,root);
    }

    private void returnedCancelled(){
        root=PosUi.page(this,"รายการบิลคืนสินค้า / ยกเลิก");
        Cursor c=helper.getReadableDatabase().rawQuery("SELECT bill_code,event_type,subtotal_satang,fee_satang,total_satang,created_at,sale_type,CASE WHEN event_type IN ('RETURN','CANCEL') AND sale_type='CREDIT' AND fee_satang=0 AND NOT EXISTS (SELECT 1 FROM sales_ledger w WHERE w.bill_id=sales_ledger.bill_id AND w.event_type='CREDIT_FEE_WAIVED') THEN COALESCE((SELECT s.fee_satang FROM sales_ledger s WHERE s.bill_id=sales_ledger.bill_id AND s.event_type='SALE'),0) ELSE fee_satang END AS display_fee FROM sales_ledger WHERE event_type IN ('RETURN','CANCEL') ORDER BY created_at DESC",null);
        if(!c.moveToFirst())PosUi.line(this,root,"ยังไม่มีบิลคืนสินค้าหรือยกเลิก");
        else do{String event=c.getString(1);long shownFee=c.getLong(7),ledgerFee=c.getLong(3);String feeText="";if("CREDIT".equals(c.getString(6))&&shownFee!=0){feeText=ledgerFee<0?" • ยกเลิกค่าบริการ "+PosDatabase.money(Math.abs(shownFee))+" บาท":" • ค่าบริการที่ไม่คืน "+PosDatabase.money(Math.abs(shownFee))+" บาท";}PosUi.line(this,root,c.getString(0)+" • "+(event.equals("RETURN")?"คืนสินค้า":"ยกเลิก")+" • ยอดสินค้า "+PosDatabase.money(Math.abs(c.getLong(2)))+" บาท"+feeText+" • "+new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(c.getLong(5))));}while(c.moveToNext());
        c.close();PosUi.back(this,root);
    }
    private long startOfDay(){Calendar c=AppClock.calendar();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private long startOfWeek(){Calendar c=AppClock.calendar();c.setFirstDayOfWeek(Calendar.MONDAY);c.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private long startOfMonth(){Calendar c=AppClock.calendar();c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private void custom(){root=PosUi.page(this,"รายงานตามช่วงวันที่");EditText from=PosUi.input(this,root,"วันที่เริ่ม YYYY-MM-DD");EditText to=PosUi.input(this,root,"วันที่สิ้นสุด YYYY-MM-DD");
        PosUi.button(this,root,"แสดงรายงาน",v->{try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setLenient(false);long a=f.parse(PosUi.str(from)).getTime();long z=f.parse(PosUi.str(to)).getTime()+86400000L-1;if(z<a)throw new IllegalArgumentException();period("ช่วงที่เลือก",a,z);}catch(Exception e){from.setError("กรอกวันที่รูปแบบ YYYY-MM-DD ให้ถูกต้อง");}});PosUi.back(this,root);}
    private void period(String label,long from,long to){
        root=PosUi.page(this,"รายงานยอดขาย • "+label);SQLiteDatabase db=helper.getReadableDatabase();
        Cursor gross=db.rawQuery("SELECT COALESCE(SUM(total_satang),0) FROM sales_ledger WHERE created_at BETWEEN ? AND ? AND event_type='SALE'",new String[]{String.valueOf(from),String.valueOf(to)});if(gross.moveToFirst())PosUi.line(this,root,"ยอดขายก่อนคืน/ยกเลิก: "+PosDatabase.money(gross.getLong(0))+" บาท");gross.close();
        Cursor c=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN event_type IN ('SALE','CANCEL','RETURN') THEN subtotal_satang ELSE 0 END),0),COALESCE(SUM(CASE WHEN event_type IN ('SALE','CANCEL','RETURN','CREDIT_FEE_WAIVED') THEN fee_satang ELSE 0 END),0),COALESCE(SUM(CASE WHEN event_type IN ('SALE','CANCEL','RETURN','CREDIT_FEE_WAIVED') THEN total_satang ELSE 0 END),0),SUM(CASE WHEN event_type IN ('SALE','CANCEL','RETURN') THEN 1 ELSE 0 END),SUM(CASE WHEN sale_type='CREDIT' AND event_type IN ('SALE','CANCEL','RETURN') THEN 1 ELSE 0 END) FROM sales_ledger WHERE created_at BETWEEN ? AND ? AND event_type IN ('SALE','CANCEL','RETURN','CREDIT_FEE_WAIVED')",new String[]{String.valueOf(from),String.valueOf(to)});
        if(c.moveToFirst()){PosUi.line(this,root,"ยอดสินค้า (สุทธิ): "+PosDatabase.money(c.getLong(0))+" บาท");if(c.getInt(4)>0)PosUi.line(this,root,"ค่าบริการสุทธิจากการขาย/คืน/ยกเลิก: "+PosDatabase.money(c.getLong(1))+" บาท");PosUi.line(this,root,"ยอดขายสุทธิหลังคืน/ยกเลิก: "+PosDatabase.money(c.getLong(2))+" บาท");PosUi.line(this,root,"จำนวนรายการบัญชี: "+c.getInt(3));}c.close();
        Cursor soldQty=db.rawQuery("SELECT COALESCE(SUM(quantity),0) FROM sales_ledger_items WHERE created_at BETWEEN ? AND ? AND event_type='SALE'",new String[]{String.valueOf(from),String.valueOf(to)});if(soldQty.moveToFirst())PosUi.line(this,root,"จำนวนสินค้าที่ขาย: "+soldQty.getLong(0)+" ชิ้น");soldQty.close();
        Cursor netQty=db.rawQuery("SELECT COALESCE(SUM(quantity),0) FROM sales_ledger_items WHERE created_at BETWEEN ? AND ? AND event_type IN ('SALE','CANCEL','RETURN')",new String[]{String.valueOf(from),String.valueOf(to)});if(netQty.moveToFirst())PosUi.line(this,root,"จำนวนสินค้าสุทธิหลังคืน/ยกเลิก: "+netQty.getLong(0)+" ชิ้น");netQty.close();
        Cursor cash=db.rawQuery("SELECT COALESCE(SUM(total_satang),0) FROM sales_ledger WHERE created_at BETWEEN ? AND ? AND sale_type='CASH' AND event_type='SALE'",new String[]{String.valueOf(from),String.valueOf(to)});
        long cashSales=0;if(cash.moveToFirst()){cashSales=cash.getLong(0);PosUi.line(this,root,"ยอดขายเงินสดก่อนคืน/ยกเลิก: "+PosDatabase.money(cashSales)+" บาท");}cash.close();
        Cursor receipts=db.rawQuery("SELECT COALESCE(SUM(amount_satang),0) FROM payment_history WHERE created_at BETWEEN ? AND ?",new String[]{String.valueOf(from),String.valueOf(to)});
        long debtReceipts=0;if(receipts.moveToFirst())debtReceipts=receipts.getLong(0);receipts.close();
        Cursor refunds=db.rawQuery("SELECT COALESCE(SUM(-total_satang),0) FROM sales_ledger WHERE created_at BETWEEN ? AND ? AND event_type IN ('RETURN','CANCEL') AND total_satang<0 AND (sale_type='CASH' OR (sale_type='CREDIT' AND fee_satang=0))",new String[]{String.valueOf(from),String.valueOf(to)});
        long refundTotal=0;if(refunds.moveToFirst())refundTotal=refunds.getLong(0);refunds.close();
        Cursor waived=db.rawQuery("SELECT COALESCE(SUM(-fee_satang),0) FROM sales_ledger WHERE created_at BETWEEN ? AND ? AND event_type='CREDIT_FEE_WAIVED'",new String[]{String.valueOf(from),String.valueOf(to)});
        long waivedFees=0;if(waived.moveToFirst())waivedFees=waived.getLong(0);waived.close();
        long cashNet=Math.subtractExact(Math.addExact(cashSales,debtReceipts),refundTotal);
        PosUi.line(this,root,"รับชำระหนี้ในช่วงนี้: "+PosDatabase.money(debtReceipts)+" บาท");
        PosUi.line(this,root,"เงินคืนจากการคืนสินค้า/ยกเลิก: "+PosDatabase.money(refundTotal)+" บาท");
        PosUi.line(this,root,"ค่าบริการขายเชื่อที่ยกเว้นในช่วงนี้: "+PosDatabase.money(waivedFees)+" บาท");
        PosUi.line(this,root,"เงินสดสุทธิ (ขายเงินสด + รับชำระหนี้ − เงินคืน): "+PosDatabase.money(cashNet)+" บาท");
        PosUi.line(this,root,"หมายเหตุ: รายการคืน/ยกเลิกและการยกเว้นค่าบริการนับตามวันที่บันทึกรายการ เพื่อให้ตรวจสอบก่อนตัดยอดแต่ละวันได้");
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
            PosUi.line(this,root,i+++". "+c.getString(0)+" — จำนวน "+qty+note);
        }
        if(i==1)PosUi.line(this,root,best?"ยังไม่มีสินค้าที่มียอดขายสุทธิในช่วงนี้":"ยังไม่มีสินค้าในคลัง");
        c.close();PosUi.back(this,root);
    }
}