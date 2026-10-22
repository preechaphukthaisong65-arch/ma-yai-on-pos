package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
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
        Cursor c=helper.getReadableDatabase().rawQuery("SELECT c.id,c.full_name,SUM(b.total_satang) due,MIN(b.created_at) oldest FROM customers c JOIN bills b ON b.customer_id=c.id WHERE b.status='UNPAID' GROUP BY c.id ORDER BY due DESC,oldest ASC",null);
        boolean found=false;
        while(c.moveToNext()){
            found=true;long id=c.getLong(0);String name=c.getString(1);long due=c.getLong(2);
            PosUi.button(this,root,name+" — "+PosDatabase.money(due)+" บาท",v->customerBills(id,name));
        }
        c.close();
        if(!found)PosUi.line(this,root,"ไม่มีลูกค้าที่มียอดค้างชำระ");
        PosUi.back(this,root);
    }

    private void customerBills(long customerId,String name){
        root=PosUi.page(this,name+" • บัญชีลูกหนี้");
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"id","bill_code","total_satang","created_at"},"customer_id=? AND status='UNPAID'",new String[]{String.valueOf(customerId)},null,null,"created_at ASC");
        ArrayList<Long> allIds=new ArrayList<>();
        HashSet<Long> selected=new HashSet<>();
        long dueTotal=0;
        while(c.moveToNext()){
            long id=c.getLong(0),amount=c.getLong(2);String code=c.getString(1);long created=c.getLong(3);
            allIds.add(id);selected.add(id);dueTotal+=amount;
            CheckBox box=new CheckBox(this);
            box.setText(code+" • "+PosDatabase.money(amount)+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy",java.util.Locale.getDefault()).format(new java.util.Date(created)));
            box.setChecked(true);box.setOnCheckedChangeListener((button,checked)->{if(checked)selected.add(id);else selected.remove(id);});
            root.addView(box,new LinearLayout.LayoutParams(-1,-2));
        }
        c.close();
        PosUi.line(this,root,"ยอดหนี้รวม "+PosDatabase.money(dueTotal)+" บาท");
        if(!allIds.isEmpty()){
            PosUi.button(this,root,"ชำระเต็มจำนวนทุกบิล",v->payAll(customerId,allIds,payableTotal(customerId,allIds)));
            PosUi.button(this,root,"ชำระเฉพาะบิลที่เลือก",v->{
                ArrayList<Long> chosen=new ArrayList<>(selected);java.util.Collections.sort(chosen);
                if(chosen.isEmpty()){new AlertDialog.Builder(this).setMessage("เลือกอย่างน้อยหนึ่งบิล").setPositiveButton("ตกลง",null).show();return;}
                payAll(customerId,chosen,payableTotal(customerId,chosen));
            });
        }else PosUi.line(this,root,"ไม่มีบิลค้างชำระ");
        PosUi.back(this,root);
    }

    private void payAll(long customerId,ArrayList<Long> ids,long ignoredTotal){
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
            StringBuilder receipt=new StringBuilder("ใบรับชำระเงิน\\n");
            for(CreditPaymentService.BillQuote bill:result.bills){
                receipt.append("บิล ").append(bill.billCode).append(" รับชำระ ")
                        .append(PosDatabase.money(bill.payableSatang)).append(" บาท\\n");
                if(bill.feeWaived&&bill.feeSatang>0){
                    receipt.append("ยกเว้นค่าบริการ ").append(PosDatabase.money(bill.feeSatang)).append(" บาท\\n");
                }
            }
            new AlertDialog.Builder(this).setTitle("รับชำระหนี้สำเร็จ")
                    .setMessage("บันทึกรับชำระหนี้เรียบร้อย\\n"+receipt)
                    .setPositiveButton("ตกลง",(dialog,which)->home()).show();
        }catch(Exception e){
            new AlertDialog.Builder(this).setMessage("รับชำระไม่สำเร็จ: "+
                    (e.getMessage()==null?"ข้อผิดพลาดฐานข้อมูล":e.getMessage())).setPositiveButton("ตกลง",null).show();
        }
    }
}