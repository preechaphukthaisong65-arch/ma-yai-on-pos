package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.google.zxing.integration.android.IntentResult;

public class BarcodeScreen extends Activity {
    private PosDatabase helper;private LinearLayout root;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);home();}
    private void home(){PosUi.resetNavigation(this);root=PosUi.page(this,"สร้างบาร์โค้ด");
        PosUi.button(this,root,"สร้างบาร์โค้ดใหม่",v->create());
        PosUi.button(this,root,"คลังบาร์โค้ด",v->registry());
        PosUi.back(this,root);
    }
    private void create(){
        root=PosUi.page(this,"สร้างบาร์โค้ดใหม่");EditText name=PosUi.input(this,root,"ชื่อสินค้า");EditText price=PosUi.input(this,root,"ราคาขาย (บาท)");
        PosUi.line(this,root,"ระบบกำหนดเลขบาร์โค้ดอัตโนมัติ ไม่สามารถเลือกเลขเองได้");
        PosUi.button(this,root,"สร้างและบันทึก",v->{try{
            String n=PosUi.str(name);
            if(n.isEmpty()){name.setError("กรอกชื่อสินค้า");return;}
            long p=PosUi.satang(price);
            BarcodeRegistry.CreateResult result=BarcodeRegistry.createOrReuse(helper,n,p,PosDatabase.now());
            final String generatedCode=result.entry.barcode;
            final long generatedPrice=result.entry.priceSatang;
            final String generatedName=result.entry.name;
            final boolean wasReused=result.reused;
            if(wasReused)price.setText(PosDatabase.money(generatedPrice));
            PosUi.result(this,true,wasReused?"ใช้บาร์โค้ดเดิมสำเร็จ":"สร้างบาร์โค้ดสำเร็จ",
                    "สินค้า: "+generatedName+"\nบาร์โค้ด: "+generatedCode+"\nราคา: "+
                            PosDatabase.money(generatedPrice)+" บาท"+(wasReused?"\nใช้บาร์โค้ดเดิมจากคลัง":""),()->home());
        }catch(Exception e){
            String error=e.getMessage()==null?"ข้อมูลบาร์โค้ดไม่ถูกต้อง":e.getMessage();
            PosUi.result(this,false,"สร้างบาร์โค้ดไม่สำเร็จ",error,null);
        }});
        PosUi.back(this,root);
    }
    private void registry(){
        root=PosUi.page(this,"คลังบาร์โค้ด");EditText query=PosUi.input(this,root,"ค้นหาจากชื่อหรือบาร์โค้ด");PosUi.button(this,root,"ค้นหา",v->search(query.getText().toString().trim()));PosUi.button(this,root,"สแกนบาร์โค้ดเพื่อค้นหา",v->PosUi.scan(this));PosUi.button(this,root,"แสดงทั้งหมด",v->search(""));
        PosUi.back(this,root);
    }
    private void search(String q){
        root=PosUi.page(this,"ผลการค้นหาคลังบาร์โค้ด");Cursor c;
        if(q.isEmpty())c=helper.getReadableDatabase().query("barcode_registry",new String[]{"barcode","name","price_satang","source"} ,null,null,null,null,"created_at DESC","100");
        else c=helper.getReadableDatabase().query("barcode_registry",new String[]{"barcode","name","price_satang","source"},"barcode=? OR name LIKE ?",new String[]{q,"%"+q+"%"},null,null,"name COLLATE NOCASE","100");
        while(c.moveToNext())PosUi.line(this,root,c.getString(1)+" • "+c.getString(0)+" • "+PosDatabase.money(c.getLong(2))+" บาท • "+c.getString(3));c.close();PosUi.back(this,root);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){super.onActivityResult(requestCode,resultCode,data);IntentResult r=BarcodeScanner.parseResult(requestCode,resultCode,data);if(r!=null){if(r.getContents()!=null)search(r.getContents());else home();}}
    @Override protected void onDestroy(){PosUi.clearNavigation(this);super.onDestroy();}
}