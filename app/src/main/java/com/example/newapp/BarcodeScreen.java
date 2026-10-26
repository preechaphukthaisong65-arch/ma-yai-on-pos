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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
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
        PosUi.button(this,root,"สร้างและบันทึก",v->{OwnerAccess.confirmOwnerAction(this,"สร้างบาร์โค้ดสินค้า",()->{try{
            String n=PosUi.str(name);if(n.isEmpty()){name.setError("กรอกชื่อสินค้า");return;}long enteredPrice=PosUi.satang(price);if(enteredPrice<=0){price.setError("ราคาขายต้องมากกว่า 0 บาท");return;}final long[] generatedPriceHolder={enteredPrice};
            final String[] codeHolder={null};final boolean[] reusedHolder={false};
            SQLiteDatabase db=helper.getWritableDatabase();
            PosTransactions.execute(db,transaction->{
                Cursor found=transaction.query("barcode_registry",new String[]{"barcode","price_satang"},"name=? COLLATE NOCASE",new String[]{n},null,null,"created_at ASC","1");
                if(found.moveToFirst()){
                    String existingCode=found.getString(0);long existingPrice=found.getLong(1);found.close();
                    throw new IllegalStateException("ชื่อนี้มีบาร์โค้ดอยู่แล้ว: "+existingCode+" ราคาเดิม "+PosDatabase.money(existingPrice)+" บาท กรุณาใช้รายการเดิมหรือแก้ราคาที่เมนูจัดการร้านค้า");
                }
                found.close();
                long seq=200000000000L;
                Cursor c=transaction.query("app_meta",new String[]{"value"},"key='barcode_sequence'",null,null,null,null);
                if(c.moveToFirst())seq=Long.parseLong(c.getString(0));c.close();
                if(seq<200000000000L||seq>299999999999L)seq=200000000000L;
                while(seq<=299999999999L){
                    String payload=String.format(java.util.Locale.US,"%012d",seq);
                    String candidate=payload+PosBusinessRules.ean13CheckDigit(payload);
                    Cursor exists=transaction.rawQuery("SELECT 1 FROM barcode_registry WHERE barcode=? UNION ALL SELECT 1 FROM products WHERE barcode=? LIMIT 1",new String[]{candidate,candidate});
                    boolean used=exists.moveToFirst();exists.close();
                    if(!used){codeHolder[0]=candidate;break;}
                    seq++;
                }
                if(codeHolder[0]==null)throw new IllegalStateException("ไม่สามารถหาเลขบาร์โค้ดว่างได้");
                ContentValues reg=new ContentValues();reg.put("barcode",codeHolder[0]);reg.put("name",n);reg.put("price_satang",generatedPriceHolder[0]);reg.put("source","STORE");reg.put("created_at",PosDatabase.now());transaction.insertOrThrow("barcode_registry",null,reg);
                ContentValues meta=new ContentValues();meta.put("value",String.valueOf(seq+1));transaction.update("app_meta",meta,"key='barcode_sequence'",null);
                PosEvents.barcodeRegistered(transaction,codeHolder[0],n,generatedPriceHolder[0],"STORE");
                return null;
            });
            price.setText(PosDatabase.money(generatedPriceHolder[0]));
            showBarcodeResult(n,codeHolder[0],generatedPriceHolder[0]);
        }catch(Exception e){PosUi.result(this,false,"สร้างไม่สำเร็จ: "+e.getMessage(),null);}});});
        PosUi.back(this,root);
    }
    private void showBarcodeResult(String name,String code,long price){
        root=PosUi.page(this,"บาร์โค้ดสินค้า");
        PosUi.line(this,root,"สินค้า: "+name);
        PosUi.line(this,root,"บาร์โค้ด EAN-13: "+code);
        PosUi.line(this,root,"ราคา: "+PosDatabase.money(price)+" บาท");
        try{
            BitMatrix matrix=new MultiFormatWriter().encode(code,BarcodeFormat.EAN_13,PosUi.dp(this,320),PosUi.dp(this,110));
            Bitmap bitmap=Bitmap.createBitmap(matrix.getWidth(),matrix.getHeight(),Bitmap.Config.ARGB_8888);
            for(int x=0;x<matrix.getWidth();x++)for(int y=0;y<matrix.getHeight();y++)bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
            ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);
            root.addView(image,new LinearLayout.LayoutParams(-1,PosUi.dp(this,120)));
        }catch(Exception e){PosUi.line(this,root,"ไม่สามารถแสดงภาพบาร์โค้ดได้ แต่บันทึกรหัสไว้แล้ว");}
        PosUi.back(this,root);
    }
    private void registry(){
        root=PosUi.page(this,"คลังบาร์โค้ด");EditText query=PosUi.input(this,root,"ค้นหาจากชื่อหรือบาร์โค้ด");PosUi.button(this,root,"ค้นหา",v->search(query.getText().toString().trim()));PosUi.button(this,root,"สแกนบาร์โค้ดเพื่อค้นหา",v->PosBarcode.launch(this,"สแกนบาร์โค้ดเพื่อค้นหา"));PosUi.button(this,root,"แสดงทั้งหมด",v->search(""));
        PosUi.back(this,root);
    }
    private void search(String q){
        root=PosUi.page(this,"ผลการค้นหาคลังบาร์โค้ด");Cursor c;
        if(q.isEmpty())c=helper.getReadableDatabase().query("barcode_registry",new String[]{"barcode","name","price_satang","source"} ,null,null,null,null,"created_at DESC","100");
        else c=helper.getReadableDatabase().query("barcode_registry",new String[]{"barcode","name","price_satang","source"},"barcode=? OR name LIKE ?",new String[]{q,"%"+q+"%"},null,null,"name COLLATE NOCASE","100");
        while(c.moveToNext())PosUi.line(this,root,c.getString(1)+" • "+c.getString(0)+" • "+PosDatabase.money(c.getLong(2))+" บาท • "+c.getString(3));c.close();PosUi.back(this,root);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){super.onActivityResult(requestCode,resultCode,data);IntentResult r=PosBarcode.parseResult(requestCode,resultCode,data);if(r!=null){if(r.getContents()!=null)search(r.getContents());else home();}}
}