package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class BarcodeScreen extends Activity {
    private PosDatabase helper;private LinearLayout root;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);PosAuth.requireOwner(this,this::home);}
    private void home(){root=PosUi.page(this,"สร้างบาร์โค้ด");
        PosUi.button(this,root,"สร้างบาร์โค้ดใหม่",v->create());
        PosUi.button(this,root,"คลังบาร์โค้ด",v->registry());
        PosUi.back(this,root);
    }
    private void create(){
        root=PosUi.page(this,"สร้างบาร์โค้ดใหม่");EditText name=PosUi.input(this,root,"ชื่อสินค้า");EditText price=PosUi.input(this,root,"ราคาขาย (บาท)");
        PosUi.line(this,root,"ระบบกำหนดเลขบาร์โค้ดอัตโนมัติ ไม่สามารถเลือกเลขเองได้");
        PosUi.button(this,root,"สร้างและบันทึก",v->{try{
            String n=PosUi.str(name);if(n.isEmpty()){name.setError("กรอกชื่อสินค้า");return;}final long generatedPriceHolder=PosUi.satang(price);
            final String[] codeHolder={null};
            SQLiteDatabase db=helper.getWritableDatabase();
            PosTransactions.execute(db,transaction->{
                // Every create request gets a fresh barcode. Duplicate names are allowed;
                // silently reusing a prior code/price can mislabel a different product.
                long seq=0L;Cursor c=transaction.query("app_meta",new String[]{"value"},"key='barcode_sequence'",null,null,null,null);if(c.moveToFirst())seq=Math.floorMod(Long.parseLong(c.getString(0)),1000000000L);c.close();
                while(seq<1000000000L){String body=String.format(java.util.Locale.US,"29%09d",seq);String candidate=body+upcCheckDigit(body);Cursor exists=transaction.rawQuery("SELECT 1 FROM barcode_registry WHERE barcode=? UNION ALL SELECT 1 FROM products WHERE barcode=? LIMIT 1",new String[]{candidate,candidate});boolean used=exists.moveToFirst();exists.close();if(!used){codeHolder[0]=candidate;break;}seq++;}
                if(codeHolder[0]==null)throw new IllegalStateException("ไม่สามารถหาเลขบาร์โค้ดว่างได้");
                ContentValues reg=new ContentValues();reg.put("barcode",codeHolder[0]);reg.put("name",n);reg.put("price_satang",generatedPriceHolder);reg.put("source","STORE");reg.put("created_at",PosDatabase.now());transaction.insertOrThrow("barcode_registry",null,reg);
                ContentValues meta=new ContentValues();meta.put("value",String.valueOf(seq+1));transaction.update("app_meta",meta,"key='barcode_sequence'",null);
                PosEvents.barcodeRegistered(transaction,codeHolder[0],n,generatedPriceHolder,"STORE");
                return null;
            });
            price.setText(PosDatabase.money(generatedPriceHolder));
            final String generatedCode=codeHolder[0];final long generatedPrice=generatedPriceHolder;
            PosUi.result(this,true,"สินค้า: "+n+"\nบาร์โค้ด: "+generatedCode+"\nราคา: "+PosDatabase.money(generatedPrice)+" บาท",()->showBarcode(n,generatedCode,generatedPrice));
        }catch(Exception e){PosUi.result(this,false,"สร้างไม่สำเร็จ: "+e.getMessage(),null);}});
        PosUi.back(this,root);
    }
    private static int upcCheckDigit(String elevenDigits){
        if(elevenDigits==null||elevenDigits.length()!=11||!elevenDigits.matches("[0-9]{11}"))throw new IllegalArgumentException("เลขบาร์โค้ดภายในไม่ถูกต้อง");
        int sum=0;
        for(int i=0;i<elevenDigits.length();i++)sum+=(elevenDigits.charAt(i)-'0')*((i%2==0)?3:1);
        return (10-(sum%10))%10;
    }
    private void registry(){
        root=PosUi.page(this,"คลังบาร์โค้ด");EditText query=PosUi.input(this,root,"ค้นหาจากชื่อหรือบาร์โค้ด");PosUi.button(this,root,"ค้นหา",v->search(query.getText().toString().trim()));PosUi.button(this,root,"สแกนบาร์โค้ดเพื่อค้นหา",v->PosBarcode.launch(this,"สแกนบาร์โค้ดเพื่อค้นหา"));PosUi.button(this,root,"แสดงทั้งหมด",v->search(""));
        PosUi.back(this,root);
    }
    private void search(String q){
        root=PosUi.page(this,"ผลการค้นหาคลังบาร์โค้ด");Cursor c;
        if(q.isEmpty())c=helper.getReadableDatabase().query("barcode_registry",new String[]{"barcode","name","price_satang","source"} ,null,null,null,null,"created_at DESC","100");
        else c=helper.getReadableDatabase().query("barcode_registry",new String[]{"barcode","name","price_satang","source"},"barcode=? OR name LIKE ?",new String[]{q,"%"+q+"%"},null,null,"name COLLATE NOCASE","100");
        while(c.moveToNext()){
            String code=c.getString(0),name=c.getString(1),source=c.getString(3);long price=c.getLong(2);
            PosUi.button(this,root,name+" • "+code+" • "+PosDatabase.money(price)+" บาท • "+source,v->showBarcode(name,code,price));
        }c.close();PosUi.back(this,root);
    }
    private void showBarcode(String name,String code,long price){
        try{
            BitMatrix matrix=new MultiFormatWriter().encode(code,BarcodeFormat.UPC_A,720,220);
            Bitmap bitmap=Bitmap.createBitmap(matrix.getWidth(),matrix.getHeight(),Bitmap.Config.ARGB_8888);
            for(int x=0;x<matrix.getWidth();x++)for(int y=0;y<matrix.getHeight();y++)bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);int pad=PosUi.dp(this,16);box.setPadding(pad,pad,pad,pad);
            PosUi.line(this,box,"สินค้า: "+name);
            PosUi.line(this,box,"บาร์โค้ด: "+code);
            PosUi.line(this,box,"ราคา: "+PosDatabase.money(price)+" บาท");
            ImageView image=new ImageView(this);image.setImageDrawable(new BitmapDrawable(getResources(),bitmap));image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(image,new LinearLayout.LayoutParams(-1,PosUi.dp(this,130)));
            new AlertDialog.Builder(this).setTitle("แสดงบาร์โค้ด").setView(box).setPositiveButton("ปิด",null).show();
        }catch(Exception e){PosUi.result(this,false,"แสดงบาร์โค้ดไม่สำเร็จ: "+(e.getMessage()==null?"ข้อผิดพลาด":e.getMessage()),null);}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){super.onActivityResult(requestCode,resultCode,data);IntentResult r=PosBarcode.parseResult(requestCode,resultCode,data);if(r!=null){if(r.getContents()!=null)search(r.getContents());else home();}}
}