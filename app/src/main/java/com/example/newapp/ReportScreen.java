package com.example.newapp;

import android.app.Activity;
import android.database.Cursor;
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
        PosUi.button(this,root,"ยอดขายวันนี้",v->period("วันนี้",startOfDay(),PosDatabase.now()+1L));
        PosUi.button(this,root,"ยอดขายสัปดาห์นี้",v->period("สัปดาห์นี้",startOfWeek(),PosDatabase.now()+1L));
        PosUi.button(this,root,"ยอดขายเดือนนี้",v->period("เดือนนี้",startOfMonth(),PosDatabase.now()+1L));
        PosUi.button(this,root,"กำหนดช่วงวันที่เอง",v->custom());
        PosUi.button(this,root,"สินค้าขายดี 20 อันดับ (30 วัน)",v->topProducts(true));
        PosUi.button(this,root,"สินค้าขายน้อย 20 อันดับ (30 วัน)",v->topProducts(false));
        PosUi.button(this,root,"รายการบิลคืนสินค้า / ยกเลิก",v->returnedCancelled());
        PosUi.button(this,root,"ประวัติการเปลี่ยนแปลงระบบ",v->auditHistory());
        PosUi.button(this,root,"ตรวจสอบความถูกต้องของข้อมูล",v->checkIntegrity());
        PosUi.back(this,root);
    }
    private void returnedCancelled(){
        root=PosUi.page(this,"รายการบิลคืนสินค้า / ยกเลิก");
        java.util.ArrayList<ReportingService.ReturnCancel> rows=ReportingService.returnCancelEvents(helper);
        if(rows.isEmpty())PosUi.line(this,root,"ยังไม่มีบิลคืนสินค้าหรือยกเลิก");
        for(ReportingService.ReturnCancel row:rows){
            String feeText="";
            if("CREDIT".equals(row.saleType)&&row.displayFeeSatang!=0){
                feeText=row.feeSatang<0
                        ?" • ยกเลิกค่าบริการ "+PosDatabase.money(Math.abs(row.displayFeeSatang))+" บาท"
                        :" • ค่าบริการที่ไม่คืน "+PosDatabase.money(Math.abs(row.displayFeeSatang))+" บาท";
            }
            PosUi.line(this,root,row.billCode+" • "+("RETURN".equals(row.eventType)?"คืนสินค้า":"ยกเลิก")+
                    " • ยอดสินค้า "+PosDatabase.money(Math.abs(row.subtotalSatang))+" บาท"+feeText+
                    " • "+new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(row.createdAt)));
        }
        PosUi.back(this,root);
    }
    private long startOfDay(){Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private long startOfWeek(){Calendar c=Calendar.getInstance();c.setFirstDayOfWeek(Calendar.MONDAY);c.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private long startOfMonth(){Calendar c=Calendar.getInstance();c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private void custom(){root=PosUi.page(this,"รายงานตามช่วงวันที่");EditText from=PosUi.input(this,root,"วันที่เริ่ม YYYY-MM-DD");EditText to=PosUi.input(this,root,"วันที่สิ้นสุด YYYY-MM-DD");
        PosUi.button(this,root,"แสดงรายงาน",v->{try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setLenient(false);long a=f.parse(PosUi.str(from)).getTime();long z=Math.addExact(f.parse(PosUi.str(to)).getTime(),86400000L);if(z<=a)throw new IllegalArgumentException();period("ช่วงที่เลือก",a,z);}catch(Exception e){from.setError("กรอกวันที่รูปแบบ YYYY-MM-DD ให้ถูกต้อง");}});PosUi.back(this,root);}
    private void period(String label,long from,long toExclusive){
        root=PosUi.page(this,"รายงานยอดขาย • "+label);
        try{
            ReportingService.PeriodSummary s=ReportingService.periodSummary(helper,from,toExclusive);
            PosUi.line(this,root,"ยอดขายก่อนคืน/ยกเลิก: "+PosDatabase.money(s.grossSalesSatang)+" บาท");
            PosUi.line(this,root,"ยอดสินค้า (สุทธิ): "+PosDatabase.money(s.netSubtotalSatang)+" บาท");
            if(s.creditEntries>0)PosUi.line(this,root,"ค่าบริการสุทธิ (เฉพาะขายเชื่อ): "+PosDatabase.money(s.netFeesSatang)+" บาท");
            PosUi.line(this,root,"ยอดขายสุทธิ: "+PosDatabase.money(s.netSalesSatang)+" บาท");
            PosUi.line(this,root,"จำนวนรายการบัญชี: "+s.ledgerEntries);
            PosUi.line(this,root,"จำนวนสินค้าที่ขาย: "+s.soldQuantity+" ชิ้น");
            PosUi.line(this,root,"จำนวนสินค้าสุทธิหลังคืน/ยกเลิก: "+s.netQuantity+" ชิ้น");
            PosUi.line(this,root,"ยอดขายเงินสดก่อนคืน/ยกเลิก: "+PosDatabase.money(s.cashGrossSalesSatang)+" บาท");
        }catch(Exception e){
            PosUi.line(this,root,"คำนวณรายงานไม่สำเร็จ: "+(e.getMessage()==null?"ข้อมูลไม่ถูกต้อง":e.getMessage()));
        }
        PosUi.back(this,root);
    }
    private void topProducts(boolean best){
        root=PosUi.page(this,best?"สินค้าขายดี 20 อันดับ":"สินค้าขายน้อย 20 อันดับ");
        java.util.ArrayList<ReportingService.ProductQuantity> rows=ReportingService.topProducts(helper,best,PosDatabase.now());
        int i=1;
        for(ReportingService.ProductQuantity row:rows){
            String note=!best&&row.quantity==0?" • ไม่มีประวัติขายสุทธิใน 30 วัน":"";
            PosUi.line(this,root,i+++". "+row.name+" — "+row.quantity+" ชิ้น"+note);
        }
        if(i==1)PosUi.line(this,root,best?"ยังไม่มีสินค้าที่มียอดขายสุทธิในช่วงนี้":"ยังไม่มีสินค้าในคลัง");
        PosUi.back(this,root);
    }

    private void auditHistory(){
        root=PosUi.page(this,"ประวัติการเปลี่ยนแปลงระบบ");
        java.util.ArrayList<AuditService.Event> events=AuditService.recent(helper,100);
        if(events.isEmpty())PosUi.line(this,root,"ยังไม่มีประวัติการเปลี่ยนแปลง");
        for(AuditService.Event event:events){
            String date=new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.getDefault()).format(new Date(event.createdAt));
            PosUi.line(this,root,date+" • "+event.action+" • "+event.entityType+" "+event.entityId+
                    (event.details.isEmpty()?"":"\n"+event.details));
        }
        PosUi.back(this,root);
    }

    private void checkIntegrity(){
        root=PosUi.page(this,"ตรวจสอบความถูกต้องของข้อมูล");
        try{
            IntegrityCheckService.Result result=IntegrityCheckService.run(helper);
            PosUi.line(this,root,"ตรวจสอบแล้ว "+result.checkedRules+" หมวด");
            if(result.isClean()){
                PosUi.line(this,root,"ไม่พบความผิดปกติจากรายการตรวจสอบอัตโนมัติ");
            }else{
                PosUi.line(this,root,"พบประเด็นที่ต้องตรวจสอบเพิ่มเติม:");
                for(String issue:result.issues)PosUi.line(this,root,"• "+issue);
                PosUi.line(this,root,"ระบบไม่ได้แก้ไขข้อมูลให้อัตโนมัติ เพื่อป้องกันการเปลี่ยนแปลงข้อมูลโดยไม่ยืนยัน");
            }
        }catch(Exception e){
            PosUi.line(this,root,"ตรวจสอบไม่สำเร็จ: "+(e.getMessage()==null?"ข้อผิดพลาดฐานข้อมูล":e.getMessage()));
        }
        PosUi.back(this,root);
    }

}