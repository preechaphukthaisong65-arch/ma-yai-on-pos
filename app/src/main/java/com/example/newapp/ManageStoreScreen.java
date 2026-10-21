package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class ManageStoreScreen extends Activity {
    private PosDatabase helper;private LinearLayout root;private String mode="";
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);home();}
    private void home(){root=PosUi.page(this,"จัดการร้านค้า");PosUi.button(this,root,"แก้ไขราคาสินค้า",v->{mode="price";PosUi.scan(this);});PosUi.button(this,root,"ค้นหาประวัติแก้ไขราคา",v->{mode="history";PosUi.scan(this);});PosUi.button(this,root,"คืนสินค้า / ยกเลิกบิล",v->{mode="cancel";PosUi.scan(this);});PosUi.back(this,root);}
    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){super.onActivityResult(requestCode,resultCode,data);IntentResult r=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(r!=null){if(r.getContents()==null){home();return;}if("cancel".equals(mode))findBill(r.getContents());else findProduct(r.getContents());}}
    private void findProduct(String barcode){
        Cursor c=helper.getReadableDatabase().query("products",new String[]{"id","name","price_satang"},"barcode=? AND active=1",new String[]{barcode},null,null,null,"1");
        if(!c.moveToFirst()){c.close();new AlertDialog.Builder(this).setMessage("ไม่พบสินค้า").setPositiveButton("ตกลง",(d,w)->home()).show();return;}
        long id=c.getLong(0),old=c.getLong(2);String name=c.getString(1);c.close();
        if("history".equals(mode)){history(barcode,name);return;}
        root=PosUi.page(this,"แก้ไขราคาสินค้า");PosUi.line(this,root,name);PosUi.line(this,root,"บาร์โค้ด: "+barcode);PosUi.line(this,root,"ราคาเดิม: "+PosDatabase.money(old)+" บาท");EditText price=PosUi.input(this,root,"ราคาขายใหม่ (บาท)");
        PosUi.button(this,root,"บันทึกราคาใหม่",v->{try{long p=PosUi.satang(price);new AlertDialog.Builder(this).setMessage("ยืนยันเปลี่ยนราคา "+PosDatabase.money(old)+" เป็น "+PosDatabase.money(p)+" บาท?").setNegativeButton("ยกเลิก",null).setPositiveButton("ยืนยัน",(d,w)->savePrice(id,barcode,name,old,p)).show();}catch(Exception e){price.setError("กรอกราคาให้ถูกต้อง");}});
        PosUi.back(this,root);
    }
    private void savePrice(long id,String barcode,String name,long old,long next){
        SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{Cursor c=db.query("products",new String[]{"price_satang"},"id=?",new String[]{String.valueOf(id)},null,null,null);if(!c.moveToFirst()||c.getLong(0)!=old){c.close();throw new IllegalStateException("ราคาสินค้าเปลี่ยนแล้ว กรุณาสแกนใหม่");}c.close();
            ContentValues p=new ContentValues();p.put("price_satang",next);p.put("updated_at",PosDatabase.now());db.update("products",p,"id=?",new String[]{String.valueOf(id)});
            ContentValues h=new ContentValues();h.put("product_id",id);h.put("barcode",barcode);h.put("product_name",name);h.put("old_price_satang",old);h.put("new_price_satang",next);h.put("created_at",PosDatabase.now());db.insertOrThrow("price_history",null,h);db.setTransactionSuccessful();
            new AlertDialog.Builder(this).setMessage("บันทึกราคาใหม่เรียบร้อย").setPositiveButton("ตกลง",(d,w)->home()).show();
        }catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("ตกลง",null).show();}finally{db.endTransaction();}
    }
    private void history(String barcode,String name){
        root=PosUi.page(this,"ประวัติแก้ไขราคา • "+name);Cursor c=helper.getReadableDatabase().query("price_history",new String[]{"old_price_satang","new_price_satang","created_at"},"barcode=?",new String[]{barcode},null,null,"created_at ASC");
        if(!c.moveToFirst())PosUi.line(this,root,"ไม่พบประวัติแก้ไขราคา");else do{PosUi.line(this,root,PosDatabase.money(c.getLong(0))+" → "+PosDatabase.money(c.getLong(1))+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(2))));}while(c.moveToNext());c.close();PosUi.back(this,root);
    }
    private void findBill(String code){
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"id","sale_type","status","subtotal_satang","fee_satang","total_satang","created_at"},"bill_code=?",new String[]{code},null,null,null,"1");
        if(!c.moveToFirst()){c.close();new AlertDialog.Builder(this).setMessage("ไม่พบบิลนี้").setPositiveButton("ตกลง",(d,w)->home()).show();return;}
        long id=c.getLong(0),sub=c.getLong(3),fee=c.getLong(4),total=c.getLong(5),created=c.getLong(6);String type=c.getString(1),status=c.getString(2);c.close();
        if(PosDatabase.now()-created>86400000L){new AlertDialog.Builder(this).setMessage("ซื้อสินค้าเกิน 1 วัน ไม่สามารถคืนสินค้าหรือยกเลิกบิลได้").setPositiveButton("ตกลง",(d,w)->home()).show();return;}
        if(!"PAID".equals(status)&&!"UNPAID".equals(status)){new AlertDialog.Builder(this).setMessage("บิลนี้ถูกยกเลิกหรือคืนไปแล้ว").setPositiveButton("ตกลง",(d,w)->home()).show();return;}
        root=PosUi.page(this,"คืนสินค้า / ยกเลิกบิล");PosUi.line(this,root,"เลขที่บิล: "+code);PosUi.line(this,root,"ประเภท: "+type);PosUi.line(this,root,"สถานะ: "+status);PosUi.line(this,root,"ยอดสินค้า: "+PosDatabase.money(sub)+" บาท");if(fee>0)PosUi.line(this,root,"ค่าบริการ: "+PosDatabase.money(fee)+" บาท");PosUi.line(this,root,"ยอดบิล: "+PosDatabase.money(total)+" บาท");
        PosUi.button(this,root,"ยืนยันคืนสินค้าทั้งบิล / ยกเลิกบิล",v->new AlertDialog.Builder(this).setMessage("ยืนยันยกเลิกทั้งบิลและคืนสินค้าทั้งหมดหรือไม่?").setNegativeButton("ไม่ใช่",null).setPositiveButton("ยืนยัน",(d,w)->cancelBill(id,code,created)).show());PosUi.back(this,root);
    }
    private void cancelBill(long id,String code,long created){
        if(PosDatabase.now()-created>86400000L){new AlertDialog.Builder(this).setMessage("ซื้อสินค้าเกิน 1 วัน ไม่สามารถคืนสินค้าหรือยกเลิกบิลได้").setPositiveButton("ตกลง",null).show();return;}
        SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
            Cursor bill=db.query("bills",new String[]{"sale_type","status","subtotal_satang","fee_satang","total_satang"},"id=? AND bill_code=?",new String[]{String.valueOf(id),code},null,null,null);if(!bill.moveToFirst())throw new IllegalStateException("ไม่พบบิล");String type=bill.getString(0),status=bill.getString(1);long sub=bill.getLong(2),fee=bill.getLong(3),total=bill.getLong(4);bill.close();
            if(!"PAID".equals(status)&&!"UNPAID".equals(status))throw new IllegalStateException("บิลถูกดำเนินการไปแล้ว");
            Cursor items=db.query("bill_items",new String[]{"product_id","barcode","product_name","quantity","unit_price_satang","line_total_satang"},"bill_id=?",new String[]{String.valueOf(id)},null,null,null);
            while(items.moveToNext()){long pid=items.getLong(0);String barcode=items.getString(1),name=items.getString(2);int qty=items.getInt(3);long unitPrice=items.getLong(4),lineTotal=items.getLong(5);
                Cursor p=db.query("products",new String[]{"stock"},"id=?",new String[]{String.valueOf(pid)},null,null,null);if(!p.moveToFirst()){p.close();throw new IllegalStateException("ไม่พบสินค้าในคลัง: "+name);}int before=p.getInt(0);p.close();
                ContentValues up=new ContentValues();up.put("stock",before+qty);up.put("updated_at",PosDatabase.now());db.update("products",up,"id=?",new String[]{String.valueOf(pid)});
                ContentValues h=new ContentValues();h.put("product_id",pid);h.put("barcode",barcode);h.put("product_name",name);h.put("change_type","RETURN_CANCEL");h.put("before_qty",before);h.put("after_qty",before+qty);h.put("difference",qty);h.put("reference",code);h.put("created_at",PosDatabase.now());db.insertOrThrow("stock_history",null,h);
                ContentValues li=new ContentValues();li.put("bill_id",id);li.put("bill_code",code);li.put("event_type","CANCEL");li.put("barcode",barcode);li.put("product_name",name);li.put("quantity",-qty);li.put("unit_price_satang",unitPrice);li.put("line_total_satang",-lineTotal);li.put("created_at",PosDatabase.now());db.insertOrThrow("sales_ledger_items",null,li);
            }items.close();
            ContentValues b=new ContentValues();b.put("status","CANCELLED");b.put("archived_at",PosDatabase.now());b.put("archive_until",PosDatabase.now()+30L*86400000L);db.update("bills",b,"id=? AND status=?",new String[]{String.valueOf(id),status});
            ContentValues l=new ContentValues();l.put("bill_id",id);l.put("bill_code",code);l.put("sale_type",type);l.put("event_type","CANCEL");l.put("subtotal_satang",-sub);l.put("fee_satang",-fee);l.put("total_satang",-total);l.put("created_at",PosDatabase.now());db.insertOrThrow("sales_ledger",null,l);
            db.setTransactionSuccessful();new AlertDialog.Builder(this).setMessage("ยกเลิกบิลและคืนสต๊อกเรียบร้อย").setPositiveButton("ตกลง",(d,w)->home()).show();
        }catch(Exception e){new AlertDialog.Builder(this).setMessage("ทำรายการไม่สำเร็จ: "+e.getMessage()).setPositiveButton("ตกลง",null).show();}finally{db.endTransaction();}
    }
}