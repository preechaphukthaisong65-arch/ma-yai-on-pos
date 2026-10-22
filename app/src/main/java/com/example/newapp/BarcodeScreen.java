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

public class BarcodeScreen extends Activity {
    private PosDatabase helper;private LinearLayout root;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);home();}
    private void home(){root=PosUi.page(this,"สร้างบาร์โค้ด");
        PosUi.button(this,root,"สร้างบาร์โค้ดใหม่",v->create());
        PosUi.button(this,root,"คลังบาร์โค้ด",v->registry());
        PosUi.back(this,root);
    }
    private void create(){
        root=PosUi.page(this,"สร้างบาร์โค้ดใหม่");EditText name=PosUi.input(this,root,"ชื่อสินค้า");EditText price=PosUi.input(this,root,"ราคาขาย (บาท)");
        PosUi.line(this,root,"ระบบกำหนดเลขบาร์โค้ดอัตโนมัติ ไม่สามารถเลือกเลขเองได้");
        PosUi.button(this,root,"สร้างและบันทึก",v->{try{
            String n=PosUi.str(name);if(n.isEmpty()){name.setError("กรอกชื่อสินค้า");return;}long p=PosUi.satang(price);
            SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();String code=null;boolean reused=false;
            try{
                Cursor found=db.query("barcode_registry",new String[]{"barcode","price_satang"},"name=? COLLATE NOCASE",new String[]{n},null,null,"created_at ASC","1");
                if(found.moveToFirst()){code=found.getString(0);p=found.getLong(1);price.setText(PosDatabase.money(p));reused=true;}found.close();
                if(!reused){
                    long seq=100000000000L;Cursor c=db.query("app_meta",new String[]{"value"},"key='barcode_sequence'",null,null,null,null);if(c.moveToFirst())seq=Long.parseLong(c.getString(0));c.close();
                    while(seq<=999999999999L){String candidate=String.format(java.util.Locale.US,"%012d",seq);Cursor exists=db.rawQuery("SELECT 1 FROM barcode_registry WHERE barcode=? UNION ALL SELECT 1 FROM products WHERE barcode=? LIMIT 1",new String[]{candidate,candidate});boolean used=exists.moveToFirst();exists.close();if(!used){code=candidate;break;}seq++;}
                    if(code==null)throw new IllegalStateException("ไม่สามารถหาเลขบาร์โค้ดว่างได้");
                    ContentValues reg=new ContentValues();reg.put("barcode",code);reg.put("name",n);reg.put("price_satang",p);reg.put("source","STORE");reg.put("created_at",PosDatabase.now());db.insertOrThrow("barcode_registry",null,reg);
                    ContentValues meta=new ContentValues();meta.put("value",String.valueOf(seq+1));db.update("app_meta",meta,"key='barcode_sequence'",null);
                }
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            final String generatedCode=code;final long generatedPrice=p;final boolean wasReused=reused;
            PosUi.result(this,true,"สินค้า: "+n+"\\nบาร์โค้ด: "+generatedCode+"\\nราคา: "+PosDatabase.money(generatedPrice)+" บาท"+(wasReused?"\\nใช้บาร์โค้ดเดิมจากคลัง":""),this::home);
        }catch(Exception e){PosUi.result(this,false,"สร้างไม่สำเร็จ: "+e.getMessage(),null);}});
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
    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){super.onActivityResult(requestCode,resultCode,data);IntentResult r=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(r!=null){if(r.getContents()!=null)search(r.getContents());else home();}}
}