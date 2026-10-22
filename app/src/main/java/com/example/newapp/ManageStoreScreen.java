package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.google.zxing.integration.android.IntentResult;

public class ManageStoreScreen extends Activity {
    private PosDatabase helper;private LinearLayout root;private String mode="";
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);home();}
    private void home(){root=PosUi.page(this,"จัดการร้านค้า");PosUi.button(this,root,"แก้ไขราคาสินค้า",v->{mode="price";PosUi.scan(this);});PosUi.button(this,root,"ค้นหาประวัติแก้ไขราคา",v->{mode="history";PosUi.scan(this);});PosUi.button(this,root,"คืนสินค้า / ยกเลิกบิล",v->returnMenu());PosUi.back(this,root);}
    private void returnMenu(){
        root=PosUi.page(this,"คืนสินค้า / ยกเลิกบิล");
        PosUi.button(this,root,"สแกนบาร์โค้ดบนใบเสร็จ",v->{mode="cancel";PosUi.scan(this,"สแกนบาร์โค้ดบนใบเสร็จ");});
        PosUi.button(this,root,"คลังบิลคืนสินค้า / ยกเลิก",v->archive());
        PosUi.back(this,root);
    }
    private void archive(){
        root=PosUi.page(this,"คลังบิลคืนสินค้า / ยกเลิก");
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"bill_code","status","subtotal_satang","fee_satang","total_satang","archived_at","archive_until"},"status IN ('CANCELLED','RETURNED') AND archive_until>=?",new String[]{String.valueOf(PosDatabase.now())},null,null,"archived_at DESC");
        while(c.moveToNext())PosUi.line(this,root,c.getString(0)+" • "+(c.getString(1).equals("RETURNED")?"คืนสินค้า":"ยกเลิก")+" • "+PosDatabase.money(c.getLong(4))+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(5))));
        c.close();PosUi.back(this,root);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){super.onActivityResult(requestCode,resultCode,data);IntentResult r=BarcodeScanner.parseResult(requestCode,resultCode,data);if(r!=null){if(r.getContents()==null){home();return;}if("cancel".equals(mode))findBill(r.getContents());else findProduct(r.getContents());}}
    private void findProduct(String barcode){
        ProductCatalogService.Product product=ProductCatalogService.findActiveByBarcode(helper,barcode);
        if(product==null){
            PosUi.dialog(this).setMessage("ไม่พบสินค้า")
                    .setPositiveButton("ตกลง",(d,w)->home()).show();return;
        }
        long id=product.id,old=product.priceSatang;String name=product.name;
        if(!"history".equals(mode))PosUi.speak(this,name+"ค่ะ");
        if("history".equals(mode)){history(barcode,name);return;}
        root=PosUi.page(this,"แก้ไขราคาสินค้า");PosUi.line(this,root,name);
        PosUi.line(this,root,"บาร์โค้ด: "+barcode);
        PosUi.line(this,root,"ราคาเดิม: "+PosDatabase.money(old)+" บาท");
        EditText price=PosUi.input(this,root,"ราคาขายใหม่ (บาท)");
        PosUi.button(this,root,"บันทึกราคาใหม่",v->{
            try{
                long p=PosUi.satang(price);
                PosUi.dialog(this).setMessage("ยืนยันเปลี่ยนราคา "+PosDatabase.money(old)+" เป็น "+PosDatabase.money(p)+" บาท?")
                        .setNegativeButton("ยกเลิก",null)
                        .setPositiveButton("ยืนยัน",(d,w)->savePrice(id,barcode,name,old,p)).show();
            }catch(Exception e){price.setError("กรอกราคาให้ถูกต้อง");}
        });
        PosUi.back(this,root);
    }
    private void savePrice(long id,String barcode,String name,long old,long next){
        try{
            ProductCatalogService.updatePrice(helper,id,barcode,name,old,next,PosDatabase.now());
            try{StoreNotificationManager.priceChanged(this,name);}catch(Exception ignored){}
            PosUi.result(this,true,"บันทึกราคาใหม่สำเร็จ","บันทึกราคา "+name+" เรียบร้อย",()->home());
        }catch(Exception e){
            String error=e.getMessage()==null?"บันทึกราคาไม่สำเร็จ":e.getMessage();
            PosUi.result(this,false,"บันทึกราคาไม่สำเร็จ",error,null);
        }
    }
    private void history(String barcode,String name){
        root=PosUi.page(this,"ประวัติแก้ไขราคา • "+name);PosUi.line(this,root,"บาร์โค้ด: "+barcode);Cursor c=helper.getReadableDatabase().query("price_history",new String[]{"old_price_satang","new_price_satang","created_at"},"barcode=?",new String[]{barcode},null,null,"created_at ASC");
        if(!c.moveToFirst())PosUi.line(this,root,"ไม่พบประวัติแก้ไขราคา");else do{PosUi.line(this,root,PosDatabase.money(c.getLong(0))+" → "+PosDatabase.money(c.getLong(1))+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(2))));}while(c.moveToNext());c.close();PosUi.back(this,root);
    }
    private void findBill(String code){
        RetentionManager.run(this);
        final ReturnCancellationService.BillInfo bill;
        try{
            bill=ReturnCancellationService.inspect(helper,code,PosDatabase.now());
        }catch(Exception e){
            String message=e.getMessage()==null?"ตรวจสอบบิลไม่สำเร็จ":e.getMessage();
            PosUi.dialog(this).setMessage(message).setPositiveButton("ตกลง",(d,w)->home()).show();
            return;
        }
        boolean feeWaived=bill.feeWaived;
        root=PosUi.page(this,"คืนสินค้า / ยกเลิกบิล");
        PosUi.line(this,root,"เลขที่บิล: "+bill.code);
        PosUi.line(this,root,"ประเภท: "+bill.type);
        PosUi.line(this,root,"สถานะ: "+bill.status);
        PosUi.line(this,root,"ยอดสินค้า: "+PosDatabase.money(bill.subtotal)+" บาท");
        if("CREDIT".equals(bill.type)&&bill.displayFee()>0)
            PosUi.line(this,root,"ค่าบริการ: "+PosDatabase.money(bill.displayFee())+" บาท");
        PosUi.line(this,root,"ยอดบิล: "+PosDatabase.money(bill.displayTotal())+" บาท");

        String returnConfirm="ยืนยันคืนสินค้าทั้งบิลหรือไม่?";
        if("PAID".equals(bill.status)){
            returnConfirm+="\nยอดเงินคืน: "+PosDatabase.money(bill.subtotal)+" บาท";
            if("CREDIT".equals(bill.type)&&feeWaived)
                returnConfirm+=" (ค่าบริการได้รับการยกเว้นตั้งแต่ชำระหนี้)";
            else if("CREDIT".equals(bill.type)&&bill.displayFee()>0)
                returnConfirm+=" (ไม่คืนค่าบริการ 5%)";
        }else if("CREDIT".equals(bill.type)){
            returnConfirm+="\nจะยกเลิกหนี้เต็มจำนวนของบิลนี้";
        }
        final String confirmationMessage=returnConfirm;
        PosUi.button(this,root,"คืนสินค้าทั้งบิล",v->PosUi.dialog(this)
                .setMessage(confirmationMessage).setNegativeButton("ไม่ใช่",null)
                .setPositiveButton("ยืนยัน",(d,w)->processBill(bill.id,bill.code,true)).show());

        String cancelConfirm="ยืนยันยกเลิกทั้งบิลหรือไม่?";
        if("PAID".equals(bill.status)){
            cancelConfirm+="\nยอดเงินคืน: "+PosDatabase.money(bill.subtotal)+" บาท";
            if("CREDIT".equals(bill.type)&&bill.displayFee()>0)
                cancelConfirm+=" (ไม่คืนค่าบริการ 5%)";
        }
        final String cancelConfirmation=cancelConfirm;
        PosUi.button(this,root,"ยกเลิกบิล",v->PosUi.dialog(this)
                .setMessage(cancelConfirmation).setNegativeButton("ไม่ใช่",null)
                .setPositiveButton("ยืนยัน",(d,w)->processBill(bill.id,bill.code,false)).show());
        PosUi.back(this,root);
    }
    private void processBill(long id,String code,boolean returning){
        try{
            ReturnCancellationService.commit(helper,id,code,returning,PosDatabase.now());
            StockAlertManager.check(this);
            PosUi.result(this,true,returning?"คืนสินค้าสำเร็จ":"ยกเลิกบิลสำเร็จ",
                    (returning?"คืนสินค้าทั้งบิล":"ยกเลิกบิล")+" และคืนสต๊อกเรียบร้อย",()->home());
        }catch(Exception e){
            String error=e.getMessage()==null?"ทำรายการไม่สำเร็จ":e.getMessage();
            PosUi.result(this,false,"ทำรายการไม่สำเร็จ",error,null);
        }
    }
}