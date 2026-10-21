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
        if(c.moveToFirst()){PosUi.line(this,root,"เลขที่บิล: "+c.getString(0));PosUi.line(this,root,"ประเภท: "+c.getString(1));PosUi.line(this,root,"สถานะ: "+c.getString(2));PosUi.line(this,root,"ยอดสินค้า: "+PosDatabase.money(c.getLong(3)));if(c.getLong(4)>0)PosUi.line(this,root,"ค่าบริการ: "+PosDatabase.money(c.getLong(4)));PosUi.line(this,root,"รวม: "+PosDatabase.money(c.getLong(5)));}c.close();
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
        ArrayList<Long> ids=new ArrayList<>();long total=0;while(c.moveToNext()){long id=c.getLong(0);ids.add(id);total+=c.getLong(2);PosUi.line(this,root,c.getString(1)+" • "+PosDatabase.money(c.getLong(2))+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(3))));}
        c.close();PosUi.line(this,root,"ยอดหนี้รวม "+PosDatabase.money(total)+" บาท");
        if(!ids.isEmpty()){final long totalDue=total;PosUi.button(this,root,"ชำระเต็มจำนวนทุกบิล",v->payAll(customerId,ids,totalDue));}
        PosUi.back(this,root);
    }
    private void payAll(long customerId,ArrayList<Long> ids,long total){
        new AlertDialog.Builder(this).setTitle("ยืนยันรับชำระหนี้").setMessage("รับเงินสด "+PosDatabase.money(total)+" บาท เพื่อชำระบิลทั้งหมดหรือไม่?").setNegativeButton("ยกเลิก",null).setPositiveButton("ยืนยัน",(d,w)->{
            SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{long now=PosDatabase.now();
                for(long id:ids){Cursor c=db.query("bills",new String[]{"bill_code","sale_type","subtotal_satang","fee_satang","total_satang","status"},"id=? AND customer_id=?",new String[]{String.valueOf(id),String.valueOf(customerId)},null,null,null);if(!c.moveToFirst()){c.close();throw new IllegalStateException("ไม่พบบิล");}
                    String code=c.getString(0),type=c.getString(1),status=c.getString(5);long sub=c.getLong(2),fee=c.getLong(3),amount=c.getLong(4);c.close();if(!"UNPAID".equals(status))throw new IllegalStateException("บิลนี้ถูกชำระไปแล้ว");
                    ContentValues p=new ContentValues();p.put("status","PAID");p.put("paid_at",now);db.update("bills",p,"id=? AND status='UNPAID'",new String[]{String.valueOf(id)});
                    boolean waiveFee="CREDIT".equals(type)&&now-getBillTime(db,id)<=86400000L;
                    long collected=waiveFee?sub:amount;
                    ContentValues pay=new ContentValues();pay.put("bill_id",id);pay.put("amount_satang",collected);pay.put("created_at",now);db.insertOrThrow("payments",null,pay);
                    // Within 24 hours, collect merchandise subtotal only; the original sale ledger is adjusted by the waived fee.
                    if(waiveFee){ContentValues waive=new ContentValues();waive.put("fee_satang",0);waive.put("total_satang",sub);db.update("bills",waive,"id=?",new String[]{String.valueOf(id)});ContentValues adj=new ContentValues();adj.put("bill_id",id);adj.put("bill_code",code);adj.put("sale_type",type);adj.put("event_type","CREDIT_FEE_WAIVED");adj.put("subtotal_satang",0);adj.put("fee_satang",-fee);adj.put("total_satang",-fee);adj.put("created_at",now);db.insertOrThrow("sales_ledger",null,adj);}
                }db.setTransactionSuccessful();new AlertDialog.Builder(this).setMessage("บันทึกรับชำระหนี้เรียบร้อย").setPositiveButton("ตกลง",(x,y)->home()).show();
            }catch(Exception e){new AlertDialog.Builder(this).setMessage("รับชำระไม่สำเร็จ: "+e.getMessage()).setPositiveButton("ตกลง",null).show();}finally{db.endTransaction();}
        }).show();
    }
    private long getBillTime(SQLiteDatabase db,long id){Cursor c=db.query("bills",new String[]{"created_at"},"id=?",new String[]{String.valueOf(id)},null,null,null);long t=c.moveToFirst()?c.getLong(0):0;c.close();return t;}
}