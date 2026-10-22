package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.HashSet;

public class CreditAccountScreen extends Activity {
    private PosDatabase helper;
    private LinearLayout root;

    @Override protected void onCreate(Bundle state){super.onCreate(state);helper=PosDatabase.get(this);home();}

    private void home(){
        root=PosUi.page(this,"บัญชีลูกค้าสินเชื่อ");
        ArrayList<ReportingService.CustomerDue> customers=ReportingService.unpaidCustomers(helper);
        for(ReportingService.CustomerDue customer:customers){
            PosUi.button(this,root,customer.name+" — "+PosDatabase.money(customer.dueSatang)+" บาท",
                    v->customerBills(customer.id,customer.name));
        }
        if(customers.isEmpty())PosUi.line(this,root,"ไม่มีลูกค้าที่มียอดค้างชำระ");
        PosUi.back(this,root);
    }

    private void customerBills(long customerId,String name){
        root=PosUi.page(this,name+" • บัญชีลูกหนี้");
        ArrayList<ReportingService.BillDue> bills=ReportingService.unpaidBills(helper,customerId);
        ArrayList<Long> allIds=new ArrayList<>();
        HashSet<Long> selected=new HashSet<>();
        long dueTotal=0;
        for(ReportingService.BillDue bill:bills){
            allIds.add(bill.id);selected.add(bill.id);dueTotal=Math.addExact(dueTotal,bill.amountSatang);
            CheckBox box=new CheckBox(this);
            box.setTextSize(24f);
            box.setText(bill.code+" • "+PosDatabase.money(bill.amountSatang)+" บาท • "+
                    new java.text.SimpleDateFormat("dd/MM/yyyy",java.util.Locale.getDefault())
                            .format(new java.util.Date(bill.createdAt)));
            box.setChecked(true);
            box.setOnCheckedChangeListener((button,checked)->{if(checked)selected.add(bill.id);else selected.remove(bill.id);});
            root.addView(box,new LinearLayout.LayoutParams(-1,-2));
        }
        PosUi.line(this,root,"ยอดหนี้รวม "+PosDatabase.money(dueTotal)+" บาท");
        if(!allIds.isEmpty()){
            PosUi.button(this,root,"ชำระเต็มจำนวนทุกบิล",v->payAll(customerId,allIds));
            PosUi.button(this,root,"ชำระเฉพาะบิลที่เลือก",v->{
                ArrayList<Long> chosen=new ArrayList<>(selected);java.util.Collections.sort(chosen);
                if(chosen.isEmpty()){
                    new AlertDialog.Builder(this).setMessage("เลือกอย่างน้อยหนึ่งบิล").setPositiveButton("ตกลง",null).show();
                    return;
                }
                payAll(customerId,chosen);
            });
        }else PosUi.line(this,root,"ไม่มีบิลค้างชำระ");
        PosUi.back(this,root);
    }

    private void payAll(long customerId,ArrayList<Long> ids){
        try{
            CreditPaymentService.Quote quote=CreditPaymentService.quote(helper,customerId,ids,PosDatabase.now());
            StringBuilder spoken=new StringBuilder();
            SQLiteDatabase readDb=helper.getReadableDatabase();
            for(CreditPaymentService.BillQuote bill:quote.bills){
                spoken.append("บิล ").append(bill.billCode).append(" ยอดสินค้า ")
                        .append(PosDatabase.money(bill.subtotalSatang)).append(" บาท ");
                if(bill.feeSatang>0){
                    if(bill.feeWaived)spoken.append("ค่าบริการ ").append(PosDatabase.money(bill.feeSatang))
                            .append(" บาท ได้รับการยกเว้นเนื่องจากชำระภายใน 24 ชั่วโมง ");
                    else spoken.append("ค่าบริการ ").append(PosDatabase.money(bill.feeSatang)).append(" บาท ");
                }
                spoken.append("ยอดที่ต้องชำระ ").append(PosDatabase.money(bill.payableSatang)).append(" บาท ");
                Cursor items=readDb.query("bill_items",new String[]{"product_name","quantity","unit","unit_price_satang"},
                        "bill_id=?",new String[]{String.valueOf(bill.billId)},null,null,null);
                try{
                    while(items.moveToNext())spoken.append(items.getString(0)).append(" ")
                            .append(items.getInt(1)).append(" ").append(items.getString(2))
                            .append(" ราคา ").append(PosDatabase.money(items.getLong(3))).append(" บาท ");
                }finally{items.close();}
            }
            spoken.append("ยอดรับชำระเงินสด ").append(PosDatabase.money(quote.totalSatang)).append(" บาท");
            PosUi.speakThen(this,spoken.toString(),()->new AlertDialog.Builder(this)
                    .setTitle("ยืนยันรับชำระหนี้")
                    .setMessage("รับเงินสด "+PosDatabase.money(quote.totalSatang)+" บาท เพื่อชำระบิลที่เลือกหรือไม่?")
                    .setNegativeButton("ยกเลิก",null)
                    .setPositiveButton("ยืนยัน",(dialog,which)->recordPayment(customerId,ids,quote.totalSatang)).show());
        }catch(Exception e){
            new AlertDialog.Builder(this).setMessage("คำนวณยอดชำระไม่สำเร็จ: "+
                    (e.getMessage()==null?"ข้อมูลบิลไม่ถูกต้อง":e.getMessage())).setPositiveButton("ตกลง",null).show();
        }
    }

    private void recordPayment(long customerId,ArrayList<Long> ids,long total){
        try{
            CreditPaymentService.Result result=CreditPaymentService.commit(helper,customerId,ids,total,PosDatabase.now());
            String receipt=ReceiptService.formatPayment(result);
            new AlertDialog.Builder(this).setTitle("รับชำระหนี้สำเร็จ")
                    .setMessage("บันทึกรับชำระหนี้เรียบร้อย\n"+receipt)
                    .setPositiveButton("ตกลง",(dialog,which)->home()).show();
        }catch(Exception e){
            PosUi.showResult(this,false,"รับชำระไม่สำเร็จ",e.getMessage()==null?"ข้อผิดพลาดฐานข้อมูล":e.getMessage(),null);
        }
    }
}