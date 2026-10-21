package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.LinearLayout;
import java.util.ArrayList;

public class HistoryScreen extends Activity {
    private PosDatabase helper; private LinearLayout root;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);home();}
    private void home(){root=PosUi.page(this,"ประวัติการขาย");
        PosUi.button(this,root,"บิลที่ชำระแล้ว",v->listBills("PAID"));
        PosUi.button(this,root,"บิลค้างชำระ",v->listBills("UNPAID"));
        PosUi.button(this,root,"บัญชีลูกค้าเชื่อทั้งหมด",v->customers());
        PosUi.back(this,root);
    }
    private void listBills(String status){
        root=PosUi.page(this,status.equals("PAID")?"บิลที่ชำระแล้ว":"บิลค้างชำระ");
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"bill_code","total_satang","created_at","id"},"status=?",new String[]{status},null,null,"created_at DESC");
        while(c.moveToNext()){final long id=c.getLong(3);PosUi.button(this,root,c.getString(0)+" • "+PosDatabase.money(c.getLong(1))+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(2))),v->details(id));}
        c.close();PosUi.back(this,root);
    }
    private void details(long id){
        root=PosUi.page(this,"รายละเอียดบิล");
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"bill_code","sale_type","status","subtotal_satang","fee_satang","total_satang","created_at"},"id=?",new String[]{String.valueOf(id)},null,null,null);
        if(c.moveToFirst()){PosUi.line(this,root,"เลขที่บิล: "+c.getString(0));PosUi.line(this,root,"ประเภท: "+c.getString(1));PosUi.line(this,root,"สถานะ: "+c.getString(2));PosUi.line(this,root,"ยอดสินค้า: "+PosDatabase.money(c.getLong(3)));if("CREDIT".equals(c.getString(1))&&c.getLong(4)>0)PosUi.line(this,root,"ค่าบริการ: "+PosDatabase.money(c.getLong(4)));PosUi.line(this,root,"รวม: "+PosDatabase.money(c.getLong(5)));}c.close();
        Cursor it=helper.getReadableDatabase().query("bill_items",new String[]{"product_name","quantity","unit","unit_price_satang","line_total_satang"},"bill_id=?",new String[]{String.valueOf(id)},null,null,null);
        while(it.moveToNext())PosUi.line(this,root,it.getString(0)+" "+it.getInt(1)+" "+it.getString(2)+" × "+PosDatabase.money(it.getLong(3))+" = "+PosDatabase.money(it.getLong(4)));it.close();PosUi.back(this,root);
    }
    private void customers(){
        root=PosUi.page(this,"บัญชีลูกค้าเชื่อทั้งหมด");
        Cursor c=helper.getReadableDatabase().rawQuery("SELECT c.id,c.full_name,SUM(b.total_satang) due,MIN(b.created_at) oldest FROM customers c JOIN bills b ON b.customer_id=c.id WHERE b.status='UNPAID' GROUP BY c.id ORDER BY due DESC,oldest ASC",null);
        while(c.moveToNext()){long id=c.getLong(0);String customerName=c.getString(1);PosUi.button(this,root,customerName+" — "+PosDatabase.money(c.getLong(2))+" บาท",v->customerBills(id,customerName));}
        c.close();PosUi.back(this,root);
    }
    private void customerBills(long customerId,String name){
        root=PosUi.page(this,name+" • บิลค้างชำระ");
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"id","bill_code","total_satang","created_at"},"customer_id=? AND status='UNPAID'",new String[]{String.valueOf(customerId)},null,null,"created_at ASC");
        ArrayList<Long> allIds=new ArrayList<>();final java.util.HashSet<Long> selected=new java.util.HashSet<>();final java.util.HashMap<Long,Long> amounts=new java.util.HashMap<>();long total=0;
        while(c.moveToNext()){
            long id=c.getLong(0),amount=c.getLong(2);allIds.add(id);selected.add(id);amounts.put(id,amount);total+=amount;
            android.widget.CheckBox box=new android.widget.CheckBox(this);
            box.setText(c.getString(1)+" • "+PosDatabase.money(amount)+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(3))));
            box.setChecked(true);box.setOnCheckedChangeListener((button,checked)->{if(checked)selected.add(id);else selected.remove(id);});
            root.addView(box,new LinearLayout.LayoutParams(-1,-2));
        }
        c.close();PosUi.line(this,root,"ยอดหนี้รวม "+PosDatabase.money(total)+" บาท");
        if(!allIds.isEmpty()){
            PosUi.button(this,root,"ชำระเต็มจำนวนทุกบิล",v->payAll(customerId,allIds,payableTotal(allIds)));
            PosUi.button(this,root,"ชำระเฉพาะบิลที่เลือก",v->{
                ArrayList<Long> chosen=new ArrayList<>(selected);long selectedTotal=payableTotal(chosen);
                if(chosen.isEmpty()){new AlertDialog.Builder(this).setMessage("เลือกอย่างน้อยหนึ่งบิล").setPositiveButton("ตกลง",null).show();return;}
                payAll(customerId,chosen,selectedTotal);
            });
        }
        PosUi.back(this,root);
    }
    private long payableTotal(ArrayList<Long> ids){
        long total=0,now=PosDatabase.now();SQLiteDatabase db=helper.getReadableDatabase();
        for(long id:ids){Cursor c=db.query("bills",new String[]{"sale_type","subtotal_satang","total_satang","created_at"},"id=?",new String[]{String.valueOf(id)},null,null,null);
            if(c.moveToFirst()){String type=c.getString(0);long subtotal=c.getLong(1),amount=c.getLong(2),created=c.getLong(3);total+=("CREDIT".equals(type)&&now>=created&&now-created<=86400000L)?subtotal:amount;}c.close();}
        return total;
    }
    private void payAll(long customerId,ArrayList<Long> ids,long total){
        StringBuilder spoken=new StringBuilder();
        SQLiteDatabase readDb=helper.getReadableDatabase();
        for(long id:ids){
            Cursor bill=readDb.query("bills",new String[]{"bill_code","sale_type","subtotal_satang","fee_satang","total_satang","created_at"},"id=?",new String[]{String.valueOf(id)},null,null,null);
            if(bill.moveToFirst()){String type=bill.getString(1);long sub=bill.getLong(2),fee=bill.getLong(3),amount=bill.getLong(4),created=bill.getLong(5),now=PosDatabase.now();boolean waive="CREDIT".equals(type)&&now>=created&&now-created<=86400000L;spoken.append("บิล ").append(bill.getString(0)).append(" ");spoken.append("ยอดสินค้า ").append(PosDatabase.money(sub)).append(" บาท ");if(fee>0){if(waive)spoken.append("ค่าบริการ ").append(PosDatabase.money(fee)).append(" บาท ได้รับการยกเว้นเนื่องจากชำระภายใน 24 ชั่วโมง ");else spoken.append("ค่าบริการ ").append(PosDatabase.money(fee)).append(" บาท ");}spoken.append("ยอดที่ต้องชำระ ").append(PosDatabase.money(waive?sub:amount)).append(" บาท ");}bill.close();
            Cursor items=readDb.query("bill_items",new String[]{"product_name","quantity","unit","unit_price_satang"},"bill_id=?",new String[]{String.valueOf(id)},null,null,null);
            while(items.moveToNext())spoken.append(items.getString(0)).append(" ").append(items.getInt(1)).append(" ").append(items.getString(2)).append(" ราคา ").append(PosDatabase.money(items.getLong(3))).append(" บาท ");
            items.close();
        }
        spoken.append("ยอดรับชำระเงินสด ").append(PosDatabase.money(total)).append(" บาท");
        PosUi.speakThen(this,spoken.toString(),()->{
        new AlertDialog.Builder(this).setTitle("ยืนยันรับชำระหนี้").setMessage("รับเงินสด "+PosDatabase.money(total)+" บาท เพื่อชำระบิลทั้งหมดหรือไม่?").setNegativeButton("ยกเลิก",null).setPositiveButton("ยืนยัน",(d,w)->{
            SQLiteDatabase db=null;boolean transactionStarted=false,paid=false;String paymentError=null;StringBuilder receipt=new StringBuilder("ใบรับชำระเงิน\n");
            try{
                db=helper.getWritableDatabase();db.beginTransaction();transactionStarted=true;long now=PosDatabase.now();long actualTotal=0;
                for(long id:ids){
                    Cursor c=db.query("bills",new String[]{"bill_code","sale_type","subtotal_satang","fee_satang","total_satang","status"},"id=? AND customer_id=?",new String[]{String.valueOf(id),String.valueOf(customerId)},null,null,null);
                    if(!c.moveToFirst()){c.close();throw new IllegalStateException("ไม่พบบิล");}
                    String code=c.getString(0),type=c.getString(1),status=c.getString(5);long sub=c.getLong(2),fee=c.getLong(3),amount=c.getLong(4);c.close();
                    if(!"UNPAID".equals(status))throw new IllegalStateException("บิลนี้ถูกชำระไปแล้ว");
                    boolean waiveFee="CREDIT".equals(type)&&now>=getBillTime(db,id)&&now-getBillTime(db,id)<=86400000L;
                    long collected=waiveFee?sub:amount;actualTotal+=collected;
                    ContentValues p=new ContentValues();p.put("status","PAID");p.put("paid_at",now);
                    if(db.update("bills",p,"id=? AND customer_id=? AND status='UNPAID'",new String[]{String.valueOf(id),String.valueOf(customerId)})!=1)throw new IllegalStateException("สถานะบิลเปลี่ยนแล้ว กรุณาตรวจสอบใหม่");
                    ContentValues pay=new ContentValues();pay.put("bill_id",id);pay.put("amount_satang",collected);pay.put("created_at",now);db.insertOrThrow("payments",null,pay);
                    ContentValues audit=new ContentValues();audit.put("bill_id",id);audit.put("bill_code",code);audit.put("amount_satang",collected);audit.put("created_at",now);db.insertOrThrow("payment_history",null,audit);
                    receipt.append("บิล ").append(code).append(" (บาร์โค้ดเดิม) รับชำระ ").append(PosDatabase.money(collected)).append(" บาท\n");
                    if(waiveFee){ContentValues adj=new ContentValues();adj.put("bill_id",id);adj.put("bill_code",code);adj.put("sale_type",type);adj.put("event_type","CREDIT_FEE_WAIVED");adj.put("subtotal_satang",0);adj.put("fee_satang",-fee);adj.put("total_satang",-fee);adj.put("created_at",getBillTime(db,id));db.insertOrThrow("sales_ledger",null,adj);}
                }
                if(actualTotal!=total)throw new IllegalStateException("ยอดชำระเปลี่ยนระหว่างยืนยัน กรุณาตรวจสอบยอดใหม่ก่อนรับเงิน");
                db.setTransactionSuccessful();paid=true;
            }catch(Exception e){paymentError=e.getMessage()==null?"รับชำระไม่สำเร็จ":e.getMessage();paid=false;}
            finally{if(db!=null&&transactionStarted){try{db.endTransaction();}catch(Exception e){paid=false;paymentError="ยืนยันธุรกรรมไม่สำเร็จ: "+(e.getMessage()==null?"ข้อผิดพลาดฐานข้อมูล":e.getMessage());}}}
            if(paid)new AlertDialog.Builder(this).setTitle("รับชำระหนี้สำเร็จ").setMessage("บันทึกรับชำระหนี้เรียบร้อย\n"+receipt).setPositiveButton("ตกลง",(x,y)->home()).show();
            else new AlertDialog.Builder(this).setMessage("รับชำระไม่สำเร็จ: "+(paymentError==null?"ข้อผิดพลาดฐานข้อมูล":paymentError)).setPositiveButton("ตกลง",null).show();
        }).show();
        });
    }
    private long getBillTime(SQLiteDatabase db,long id){Cursor c=db.query("bills",new String[]{"created_at"},"id=?",new String[]{String.valueOf(id)},null,null,null);long t=c.moveToFirst()?c.getLong(0):0;c.close();return t;}
}