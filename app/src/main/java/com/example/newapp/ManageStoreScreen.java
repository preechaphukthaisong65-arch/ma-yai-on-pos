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
    private void home(){root=PosUi.page(this,"จัดการร้านค้า");PosUi.button(this,root,"แก้ไขราคาสินค้า",v->{mode="price";PosBarcode.launch(this,"สแกนบาร์โค้ดสินค้า");});PosUi.button(this,root,"ค้นหาประวัติแก้ไขราคา",v->{mode="history";PosBarcode.launch(this,"สแกนบาร์โค้ดสินค้า");});PosUi.button(this,root,"คืนสินค้า / ยกเลิกบิล",v->returnMenu());PosUi.back(this,root);}
    private void returnMenu(){
        root=PosUi.page(this,"คืนสินค้า / ยกเลิกบิล");
        PosUi.button(this,root,"สแกนบาร์โค้ดบนใบเสร็จ",v->{mode="cancel";PosBarcode.launch(this,"สแกนบาร์โค้ดบนใบเสร็จ");});
        PosUi.button(this,root,"คลังบิลคืนสินค้า / ยกเลิก",v->archive());
        PosUi.back(this,root);
    }
    private void archive(){
        root=PosUi.page(this,"คลังบิลคืนสินค้า / ยกเลิก");
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"bill_code","status","subtotal_satang","fee_satang","total_satang","archived_at","archive_until"},"status IN ('CANCELLED','RETURNED') AND archive_until>=?",new String[]{String.valueOf(PosDatabase.now())},null,null,"archived_at DESC");
        while(c.moveToNext())PosUi.line(this,root,c.getString(0)+" • "+(c.getString(1).equals("RETURNED")?"คืนสินค้า":"ยกเลิก")+" • "+PosDatabase.money(c.getLong(4))+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(5))));
        c.close();PosUi.back(this,root);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){super.onActivityResult(requestCode,resultCode,data);IntentResult r=PosBarcode.parseResult(requestCode,resultCode,data);if(r!=null){if(r.getContents()==null){home();return;}if("cancel".equals(mode))findBill(r.getContents());else findProduct(r.getContents());}}
    private void findProduct(String barcode){
        Cursor c=helper.findProduct(helper.getReadableDatabase(),PosBarcode.trimmed(barcode));
        if(!c.moveToFirst()){c.close();new AlertDialog.Builder(this).setMessage("ไม่พบสินค้า").setPositiveButton("ตกลง",(d,w)->home()).show();return;}
        long id=c.getLong(c.getColumnIndexOrThrow("id"));
        long old=c.getLong(c.getColumnIndexOrThrow("price_satang"));
        String name=c.getString(c.getColumnIndexOrThrow("name"));c.close();
        if(!"history".equals(mode))PosUi.speak(this,name+"ค่ะ");
        if("history".equals(mode)){history(barcode,name);return;}
        root=PosUi.page(this,"แก้ไขราคาสินค้า");PosUi.line(this,root,name);PosUi.line(this,root,"บาร์โค้ด: "+barcode);PosUi.line(this,root,"ราคาเดิม: "+PosDatabase.money(old)+" บาท");EditText price=PosUi.input(this,root,"ราคาขายใหม่ (บาท)");
        PosUi.button(this,root,"บันทึกราคาใหม่",v->{try{long p=PosUi.satang(price);new AlertDialog.Builder(this).setMessage("ยืนยันเปลี่ยนราคา "+PosDatabase.money(old)+" เป็น "+PosDatabase.money(p)+" บาท?").setNegativeButton("ยกเลิก",null).setPositiveButton("ยืนยัน",(d,w)->savePrice(id,barcode,name,old,p)).show();}catch(Exception e){price.setError("กรอกราคาให้ถูกต้อง");}});
        PosUi.back(this,root);
    }
    private void savePrice(long id,String barcode,String name,long old,long next){
        boolean saved=false;String error=null;
        try{
            SQLiteDatabase db=helper.getWritableDatabase();
            PosTransactions.execute(db,transaction->{
                Cursor c=transaction.query("products",new String[]{"price_satang"},"id=?",new String[]{String.valueOf(id)},null,null,null);
                if(!c.moveToFirst()||c.getLong(0)!=old){c.close();throw new IllegalStateException("ราคาสินค้าเปลี่ยนแล้ว กรุณาสแกนใหม่");}c.close();
                ContentValues p=new ContentValues();p.put("price_satang",next);p.put("updated_at",PosDatabase.now());
                if(transaction.update("products",p,"id=?",new String[]{String.valueOf(id)})!=1)throw new IllegalStateException("บันทึกราคาไม่สำเร็จ");
                ContentValues registryPrice=new ContentValues();registryPrice.put("price_satang",next);
                if(transaction.update("barcode_registry",registryPrice,"barcode=?",new String[]{barcode})==0){
                    ContentValues legacy=new ContentValues();legacy.put("barcode",barcode);legacy.put("name",name);legacy.put("price_satang",next);legacy.put("source","LEGACY");legacy.put("created_at",PosDatabase.now());transaction.insertOrThrow("barcode_registry",null,legacy);
                }
                PosRecords.priceChange(transaction,id,barcode,name,old,next,PosDatabase.now());
                PosDatabase.audit(transaction,"PRICE_CHANGED","PRODUCT",String.valueOf(id),"barcode="+barcode+"; old_price_satang="+old+"; new_price_satang="+next);
                return null;
            });
            saved=true;
        }catch(Exception e){error=e.getMessage();}
        if(saved){
            try{StoreNotificationManager.priceChanged(this,name);}catch(Exception ignored){}
            PosUi.result(this,true,"บันทึกราคาใหม่เรียบร้อย",this::home);
        }else PosUi.result(this,false,error==null?"บันทึกราคาไม่สำเร็จ":error,null);
    }

    private void history(String barcode,String name){
        root=PosUi.page(this,"ประวัติแก้ไขราคา • "+name);PosUi.line(this,root,"บาร์โค้ด: "+barcode);Cursor c=helper.getReadableDatabase().query("price_history",new String[]{"old_price_satang","new_price_satang","created_at"},"barcode=?",new String[]{barcode},null,null,"created_at ASC");
        if(!c.moveToFirst())PosUi.line(this,root,"ไม่พบประวัติแก้ไขราคา");else do{PosUi.line(this,root,PosDatabase.money(c.getLong(0))+" → "+PosDatabase.money(c.getLong(1))+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(2))));}while(c.moveToNext());c.close();PosUi.back(this,root);
    }
    private void findBill(String code){
        RetentionManager.run(this);
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"id","sale_type","status","subtotal_satang","fee_satang","total_satang","created_at","archive_until"},"bill_code=?",new String[]{code},null,null,null,"1");
        if(!c.moveToFirst()){c.close();new AlertDialog.Builder(this).setMessage("ไม่พบบิลนี้").setPositiveButton("ตกลง",(d,w)->home()).show();return;}
        long id=c.getLong(0),sub=c.getLong(3),fee=c.getLong(4),total=c.getLong(5),created=c.getLong(6),archiveUntil=c.getLong(7);String type=c.getString(1),status=c.getString(2);c.close();
        boolean feeWaived=false;if("CREDIT".equals(type)&&"PAID".equals(status)){Cursor waived=helper.getReadableDatabase().query("sales_ledger",new String[]{"id"},"bill_id=? AND event_type='CREDIT_FEE_WAIVED'",new String[]{String.valueOf(id)},null,null,null,"1");if(waived.moveToFirst()){feeWaived=true;fee=0;total=sub;}waived.close();}
        if(("CANCELLED".equals(status)||"RETURNED".equals(status))&&archiveUntil<=PosDatabase.now()){new AlertDialog.Builder(this).setMessage("ไม่พบบิลนี้").setPositiveButton("ตกลง",(d,w)->home()).show();return;}
        if(PosDatabase.now()<created||PosDatabase.now()-created>86400000L){new AlertDialog.Builder(this).setMessage("ซื้อสินค้าเกิน 1 วัน ไม่สามารถคืนสินค้าหรือยกเลิกบิลได้").setPositiveButton("ตกลง",(d,w)->home()).show();return;}
        if(!"PAID".equals(status)&&!"UNPAID".equals(status)){new AlertDialog.Builder(this).setMessage("บิลนี้ถูกยกเลิกหรือคืนไปแล้ว").setPositiveButton("ตกลง",(d,w)->home()).show();return;}
        root=PosUi.page(this,"คืนสินค้า / ยกเลิกบิล");PosUi.line(this,root,"เลขที่บิล: "+code);PosUi.line(this,root,"ประเภท: "+type);PosUi.line(this,root,"สถานะ: "+status);PosUi.line(this,root,"ยอดสินค้า: "+PosDatabase.money(sub)+" บาท");if("CREDIT".equals(type)&&fee>0)PosUi.line(this,root,"ค่าบริการ: "+PosDatabase.money(fee)+" บาท");PosUi.line(this,root,"ยอดบิล: "+PosDatabase.money(total)+" บาท");
        String returnConfirm="ยืนยันคืนสินค้าทั้งบิลหรือไม่?";if("PAID".equals(status)){returnConfirm+="\nยอดเงินคืน: "+PosDatabase.money(sub)+" บาท";if("CREDIT".equals(type)&&feeWaived)returnConfirm+=" (ค่าบริการได้รับการยกเว้นตั้งแต่ชำระหนี้)";else if("CREDIT".equals(type)&&fee>0)returnConfirm+=" (ไม่คืนค่าบริการ 5%)";}else if("CREDIT".equals(type))returnConfirm+="\nจะยกเลิกหนี้เต็มจำนวนของบิลนี้";final String confirmationMessage=returnConfirm;PosUi.button(this,root,"คืนสินค้าทั้งบิล",v->new AlertDialog.Builder(this).setMessage(confirmationMessage).setNegativeButton("ไม่ใช่",null).setPositiveButton("ยืนยัน",(d,w)->processBill(id,code,created,true)).show());
        String cancelConfirm="ยืนยันยกเลิกทั้งบิลหรือไม่?";if("PAID".equals(status)){cancelConfirm+="\nยอดเงินคืน: "+PosDatabase.money(sub)+" บาท";if("CREDIT".equals(type)&&fee>0&&!feeWaived)cancelConfirm+=" (ไม่คืนค่าบริการ 5%)";}final String cancelConfirmation=cancelConfirm;PosUi.button(this,root,"ยกเลิกบิล",v->new AlertDialog.Builder(this).setMessage(cancelConfirmation).setNegativeButton("ไม่ใช่",null).setPositiveButton("ยืนยัน",(d,w)->processBill(id,code,created,false)).show());PosUi.back(this,root);
    }
    private void processBill(long id,String code,long created,boolean returning){
        long firstCheck=PosDatabase.now();
        if(firstCheck<created||firstCheck-created>PosBusinessRules.CREDIT_FEE_WINDOW_MILLIS){new AlertDialog.Builder(this).setMessage("ซื้อสินค้าเกิน 1 วัน ไม่สามารถคืนสินค้าหรือยกเลิกบิลได้").setPositiveButton("ตกลง",null).show();return;}
        boolean saved=false;String error=null;
        try{
            SQLiteDatabase db=helper.getWritableDatabase();
            PosTransactions.execute(db,transaction->{
                Cursor bill=transaction.query("bills",new String[]{"sale_type","status","subtotal_satang","fee_satang","total_satang","created_at"},"id=? AND bill_code=?",new String[]{String.valueOf(id),code},null,null,null);
                if(!bill.moveToFirst()){bill.close();throw new IllegalStateException("ไม่พบบิล");}
                String type=bill.getString(0),status=bill.getString(1);long sub=bill.getLong(2),fee=bill.getLong(3),currentCreated=bill.getLong(5);bill.close();
                long now=PosDatabase.now();
                if(now<currentCreated||now-currentCreated>PosBusinessRules.CREDIT_FEE_WINDOW_MILLIS)throw new IllegalStateException("ซื้อสินค้าเกิน 1 วัน ไม่สามารถคืนสินค้าหรือยกเลิกบิลได้");
                if(!"PAID".equals(status)&&!"UNPAID".equals(status))throw new IllegalStateException("บิลถูกดำเนินการไปแล้ว");
                Cursor items=transaction.query("bill_items",new String[]{"product_id","barcode","product_name","quantity","unit_price_satang","line_total_satang"},"bill_id=?",new String[]{String.valueOf(id)},null,null,null);
                int[] itemCount={0};long[] itemSubtotal={0L};
                try{
                    while(items.moveToNext()){
                        itemCount[0]++;long storedPid=items.isNull(0)?0:items.getLong(0);String barcode=items.getString(1),name=items.getString(2);int qty=items.getInt(3);long unitPrice=items.getLong(4),lineTotal=items.getLong(5);
                        Cursor p=storedPid>0?transaction.query("products",new String[]{"id","stock"},"id=?",new String[]{String.valueOf(storedPid)},null,null,null):transaction.query("products",new String[]{"id","stock"},"barcode=? AND active=1",new String[]{barcode},null,null,null);
                        if(!p.moveToFirst()){p.close();throw new IllegalStateException("ไม่พบสินค้าในคลัง: "+name);}
                        long pid=p.getLong(0);int before=p.getInt(1);p.close();itemSubtotal[0]=Math.addExact(itemSubtotal[0],lineTotal);
                        ContentValues up=new ContentValues();up.put("stock",Math.addExact(before,qty));up.put("updated_at",PosDatabase.now());
                        if(transaction.update("products",up,"id=? AND stock=?",new String[]{String.valueOf(pid),String.valueOf(before)})!=1)throw new IllegalStateException("สต๊อกเปลี่ยนระหว่างคืนบิล กรุณาลองใหม่");
                        PosRecords.stockMovement(transaction,pid,barcode,name,"RETURN_CANCEL",before,Math.addExact(before,qty),code,PosDatabase.now());
                        PosRecords.salesLedgerItem(transaction,id,code,returning?"RETURN":"CANCEL",barcode,name,-qty,unitPrice,-lineTotal,PosDatabase.now());
                    }
                }finally{items.close();}
                if(itemCount[0]==0||itemSubtotal[0]!=sub)throw new IllegalStateException("รายละเอียดสินค้าในบิลไม่ตรงกับยอดบิล จึงยกเลิกการดำเนินการ");
                ContentValues b=new ContentValues();b.put("status",returning?"RETURNED":"CANCELLED");b.put("archived_at",PosDatabase.now());b.put("archive_until",PosDatabase.now()+30L*86400000L);
                if(transaction.update("bills",b,"id=? AND bill_code=? AND status=?",new String[]{String.valueOf(id),code,status})!=1)throw new IllegalStateException("สถานะบิลเปลี่ยนแล้ว ไม่สามารถดำเนินการซ้ำได้");
                long ledgerFee=PosBusinessRules.reversalFee(type,status,fee);
                long ledgerSubtotal=PosBusinessRules.reversalSubtotal(sub);
                long ledgerTotal=PosBusinessRules.reversalTotal(ledgerSubtotal,ledgerFee);
                PosRecords.salesLedger(transaction,id,code,type,returning?"RETURN":"CANCEL",ledgerSubtotal,ledgerFee,ledgerTotal,PosDatabase.now());
                PosDatabase.audit(transaction,returning?"RETURN":"CANCEL","BILL",code,"subtotal_satang="+ledgerSubtotal+"; fee_satang="+ledgerFee+"; total_satang="+ledgerTotal);
                return null;
            });
            saved=true;
        }catch(Exception e){error=e.getMessage()==null?"ทำรายการไม่สำเร็จ":e.getMessage();saved=false;}
        if(saved){StockAlertManager.check(this);PosUi.result(this,true,(returning?"คืนสินค้าทั้งบิล":"ยกเลิกบิล")+"และคืนสต๊อกเรียบร้อย",this::home);}
        else PosUi.result(this,false,"ทำรายการไม่สำเร็จ: "+(error==null?"ข้อผิดพลาดฐานข้อมูล":error),null);
    }
}