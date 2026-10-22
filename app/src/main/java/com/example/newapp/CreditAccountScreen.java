package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
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

    private long payableTotal(long customerId,ArrayList<Long> ids){
        long total=0,now=PosDatabase.now();SQLiteDatabase db=helper.getReadableDatabase();
        for(long id:ids){
            Cursor c=db.query("bills",new String[]{"sale_type","subtotal_satang","total_satang","created_at"},"id=? AND customer_id=? AND status='UNPAID'",new String[]{String.valueOf(id),String.valueOf(customerId)},null,null,null);
            if(c.moveToFirst()){
                String type=c.getString(0);long subtotal=c.getLong(1),amount=c.getLong(2),created=c.getLong(3);
                total+=("CREDIT".equals(type)&&now>=created&&now-created<=86400000L)?subtotal:amount;
            }
            c.close();
        }
        return total;
    }

    private void payAll(long customerId,ArrayList<Long> ids,long total){
        StringBuilder spoken=new StringBuilder();
        SQLiteDatabase readDb=helper.getReadableDatabase();
        for(long id:ids){
            Cursor bill=readDb.query("bills",new String[]{"bill_code","sale_type","subtotal_satang","fee_satang","total_satang","created_at"},"id=? AND customer_id=? AND status='UNPAID'",new String[]{String.valueOf(id),String.valueOf(customerId)},null,null,null);
            if(bill.moveToFirst()){
                String type=bill.getString(1);long sub=bill.getLong(2),fee=bill.getLong(3),amount=bill.getLong(4),created=bill.getLong(5),now=PosDatabase.now();
                boolean waive="CREDIT".equals(type)&&now>=created&&now-created<=86400000L;
                spoken.append("บิล ").append(bill.getString(0)).append(" ยอดสินค้า ").append(PosDatabase.money(sub)).append(" บาท ");
                if(fee>0){if(waive)spoken.append("ค่าบริการ ").append(PosDatabase.money(fee)).append(" บาท ได้รับการยกเว้นเนื่องจากชำระภายใน 24 ชั่วโมง ");else spoken.append("ค่าบริการ ").append(PosDatabase.money(fee)).append(" บาท ");}
                spoken.append("ยอดที่ต้องชำระ ").append(PosDatabase.money(waive?sub:amount)).append(" บาท ");
            }
            bill.close();
            Cursor items=readDb.query("bill_items",new String[]{"product_name","quantity","unit","unit_price_satang"},"bill_id=?",new String[]{String.valueOf(id)},null,null,null);
            while(items.moveToNext())spoken.append(items.getString(0)).append(" ").append(items.getInt(1)).append(" ").append(items.getString(2)).append(" ราคา ").append(PosDatabase.money(items.getLong(3))).append(" บาท ");
            items.close();
        }
        spoken.append("ยอดรับชำระเงินสด ").append(PosDatabase.money(total)).append(" บาท");
        PosUi.speakThen(this,spoken.toString(),()->new AlertDialog.Builder(this).setTitle("ยืนยันรับชำระหนี้")
                .setMessage("รับเงินสด "+PosDatabase.money(total)+" บาท เพื่อชำระบิลที่เลือกหรือไม่?")
                .setNegativeButton("ยกเลิก",null).setPositiveButton("ยืนยัน",(dialog,which)->recordPayment(customerId,ids,total)).show());
    }

    private void recordPayment(long customerId,ArrayList<Long> ids,long total){
        SQLiteDatabase db=null;boolean transactionStarted=false,paid=false;String error=null;
        StringBuilder receipt=new StringBuilder("ใบรับชำระเงิน\n");
        try{
            db=helper.getWritableDatabase();db.beginTransaction();transactionStarted=true;long now=PosDatabase.now();long actualTotal=0;
            for(long id:ids){
                Cursor c=db.query("bills",new String[]{"bill_code","sale_type","subtotal_satang","fee_satang","total_satang","status"},"id=? AND customer_id=?",new String[]{String.valueOf(id),String.valueOf(customerId)},null,null,null);
                if(!c.moveToFirst()){c.close();throw new IllegalStateException("ไม่พบบิล");}
                String code=c.getString(0),type=c.getString(1),status=c.getString(5);long sub=c.getLong(2),fee=c.getLong(3),amount=c.getLong(4);c.close();
                if(!"UNPAID".equals(status))throw new IllegalStateException("บิลนี้ถูกชำระไปแล้ว");
                long created=getBillTime(db,id);boolean waiveFee="CREDIT".equals(type)&&now>=created&&now-created<=86400000L;
                long collected=waiveFee?sub:amount;actualTotal+=collected;
                ContentValues p=new ContentValues();p.put("status","PAID");p.put("paid_at",now);
                if(db.update("bills",p,"id=? AND customer_id=? AND status='UNPAID'",new String[]{String.valueOf(id),String.valueOf(customerId)})!=1)throw new IllegalStateException("สถานะบิลเปลี่ยนแล้ว กรุณาตรวจสอบใหม่");
                ContentValues pay=new ContentValues();pay.put("bill_id",id);pay.put("amount_satang",collected);pay.put("created_at",now);db.insertOrThrow("payments",null,pay);
                ContentValues audit=new ContentValues();audit.put("bill_id",id);audit.put("bill_code",code);audit.put("amount_satang",collected);audit.put("created_at",now);db.insertOrThrow("payment_history",null,audit);
                receipt.append("บิล ").append(code).append(" (บาร์โค้ดเดิม) รับชำระ ").append(PosDatabase.money(collected)).append(" บาท\n");
                if(waiveFee){ContentValues adj=new ContentValues();adj.put("bill_id",id);adj.put("bill_code",code);adj.put("sale_type",type);adj.put("event_type","CREDIT_FEE_WAIVED");adj.put("subtotal_satang",0);adj.put("fee_satang",-fee);adj.put("total_satang",-fee);adj.put("created_at",now);db.insertOrThrow("sales_ledger",null,adj);}
            }
            if(actualTotal!=total)throw new IllegalStateException("ยอดชำระเปลี่ยนระหว่างยืนยัน กรุณาตรวจสอบยอดใหม่ก่อนรับเงิน");
            db.setTransactionSuccessful();paid=true;
        }catch(Exception e){error=e.getMessage()==null?"รับชำระไม่สำเร็จ":e.getMessage();paid=false;}
        finally{if(db!=null&&transactionStarted){try{db.endTransaction();}catch(Exception e){paid=false;error="ยืนยันธุรกรรมไม่สำเร็จ: "+(e.getMessage()==null?"ข้อผิดพลาดฐานข้อมูล":e.getMessage());}}}
        if(paid)PosUi.result(this,true,"บันทึกรับชำระหนี้เรียบร้อย\n"+receipt,this::home);
        else PosUi.result(this,false,"รับชำระไม่สำเร็จ: "+(error==null?"ข้อผิดพลาดฐานข้อมูล":error),null);
    }

    private long getBillTime(SQLiteDatabase db,long id){
        Cursor c=db.query("bills",new String[]{"created_at"},"id=?",new String[]{String.valueOf(id)},null,null,null);
        long created=c.moveToFirst()?c.getLong(0):0;c.close();return created;
    }
}