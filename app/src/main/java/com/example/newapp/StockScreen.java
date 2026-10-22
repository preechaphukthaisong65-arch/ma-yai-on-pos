package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.google.zxing.integration.android.IntentResult;

public class StockScreen extends Activity {
    private PosDatabase helper; private LinearLayout root; private String mode=""; private String scanned="";
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);home();}
    private void home(){
        root=PosUi.page(this,"คลังสินค้า");
        PosUi.button(this,root,"สินค้าทั้งหมด",v->showAll());
        PosUi.button(this,root,"นับสต๊อกสินค้า",v->countMenu());
        PosUi.button(this,root,"ตั้งค่าลิมิตสต๊อก",v->{mode="limit";scanAgain();});
        PosUi.button(this,root,"เพิ่มสินค้าเข้าสต๊อก",v->{mode="add";scanAgain();});
        PosUi.back(this,root);
    }
    private void countMenu(){
        root=PosUi.page(this,"นับสต๊อกสินค้า");
        PosUi.button(this,root,"เริ่มนับสต๊อก",v->{mode="count";scanAgain();});
        PosUi.button(this,root,"ค้นหาประวัตินับสต๊อก",v->{mode="countHistory";scanAgain();});
        PosUi.back(this,root);
    }
    private void showAll(){showAll(0);}
    private void showAll(int offset){
        root=PosUi.page(this,"สินค้าทั้งหมด");
        Cursor c=helper.getReadableDatabase().query("products",new String[]{"name","stock"},"active=1",null,null,null,"stock ASC,name COLLATE NOCASE ASC","100 OFFSET "+offset);
        int count=0;while(c.moveToNext()){PosUi.line(this,root,c.getString(0)+" — "+c.getInt(1));count++;}c.close();
        if(offset>0)PosUi.button(this,root,"หน้าก่อนหน้า",v->showAll(Math.max(0,offset-100)));
        if(count==100)PosUi.button(this,root,"โหลดสินค้าเพิ่ม",v->showAll(offset+100));
        PosUi.button(this,root,"สแกนเพื่อดูรายละเอียด",v->{mode="detail";scanAgain();});PosUi.back(this,root);
    }
    private void scanAgain(){PosUi.scan(this);}
    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        IntentResult result=BarcodeScanner.parseResult(requestCode,resultCode,data);
        if(result!=null){if(result.getContents()==null){home();return;} scanned=result.getContents(); handleScan(scanned);}
    }
    private void handleScan(String barcode){
        if("countHistory".equals(mode)){showStockHistory(barcode);return;}
        SQLiteDatabase db=helper.getReadableDatabase();
        Cursor c=db.query("products",null,"barcode=? AND active=1",new String[]{barcode},null,null,null);
        if(!c.moveToFirst()){c.close();if("add".equals(mode))newProduct(barcode);else if("countHistory".equals(mode)){showStockHistory(barcode);return;}else{new AlertDialog.Builder(this).setMessage("ไม่พบสินค้าในคลัง").setPositiveButton("ตกลง",(d,w)->scanAgain()).show();}return;}
        long id=c.getLong(c.getColumnIndexOrThrow("id"));String name=c.getString(c.getColumnIndexOrThrow("name"));String unit=c.getString(c.getColumnIndexOrThrow("unit"));
        int stock=c.getInt(c.getColumnIndexOrThrow("stock"));int limit=c.getInt(c.getColumnIndexOrThrow("stock_limit"));long price=c.getLong(c.getColumnIndexOrThrow("price_satang"));
        c.close();
        if("detail".equals(mode)){new AlertDialog.Builder(this).setTitle(name).setMessage("บาร์โค้ด: "+barcode+"\nคงเหลือ: "+stock+" "+unit+"\nราคาขาย: "+PosDatabase.money(price)+" บาท").setPositiveButton("ตกลง",(d,w)->scanAgain()).show();return;}
        root=PosUi.page(this,name);
        PosUi.line(this,root,"บาร์โค้ด: "+barcode); PosUi.line(this,root,"คงเหลือปัจจุบัน: "+stock+" "+unit);
        if("count".equals(mode)){
            EditText actual=PosUi.input(this,root,"จำนวนที่นับได้จริง");
            PosUi.button(this,root,"บันทึกผลนับ",v->{try{int qty=Integer.parseInt(PosUi.str(actual));if(qty<0)throw new IllegalArgumentException();if(saveStock(id,barcode,name,stock,qty,"COUNT"))home();}catch(Exception e){actual.setError("กรอกจำนวนเต็มตั้งแต่ 0 ขึ้นไป");}});
        } else if("limit".equals(mode)){
            EditText lim=PosUi.input(this,root,"ลิมิตสต๊อกปัจจุบัน "+limit);
            PosUi.button(this,root,"บันทึกลิมิต",v->{try{int q=Integer.parseInt(PosUi.str(lim));if(q<0)throw new IllegalArgumentException();if(saveLimit(id,limit,q))home();}catch(Exception e){lim.setError("กรอกจำนวนเต็มตั้งแต่ 0 ขึ้นไป");}});
        } else if("add".equals(mode)){
            PosUi.speak(this,name+"ค่ะ"); EditText qty=PosUi.input(this,root,"จำนวนสินค้าที่รับเข้า");
            PosUi.button(this,root,"บันทึกรับเข้า",v->{try{int q=Integer.parseInt(PosUi.str(qty));if(q<=0)throw new IllegalArgumentException();if(saveStock(id,barcode,name,stock,stock+q,"RECEIVE"))home();}catch(Exception e){qty.setError("กรอกจำนวนเต็มมากกว่า 0");}});
        }
        PosUi.button(this,root,"สแกนรายการถัดไป",v->scanAgain()); PosUi.back(this,root);
    }
    private void showStockHistory(String barcode){
        root=PosUi.page(this,"ประวัติสต๊อก");
        Cursor c=helper.getReadableDatabase().query("stock_history",new String[]{"product_name","change_type","before_qty","after_qty","difference","created_at","reference"},"barcode=?",new String[]{barcode},null,null,"created_at DESC");
        if(!c.moveToFirst())PosUi.line(this,root,"ไม่พบประวัติของบาร์โค้ดนี้");
        else do{String ref=c.getString(6)==null?"":" • "+c.getString(6);PosUi.line(this,root,c.getString(0)+" • "+c.getString(1)+" • "+c.getInt(2)+" → "+c.getInt(3)+" ("+(c.getInt(4)>0?"+":"")+c.getInt(4)+")"+ref+" • "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(5))));}while(c.moveToNext());
        c.close();PosUi.button(this,root,"สแกนบาร์โค้ดอื่น",v->scanAgain());PosUi.back(this,root);
    }
    private boolean saveLimit(long id,int oldLimit,int newLimit){
        SQLiteDatabase db=null;boolean started=false,saved=false;String error=null;
        try{
            db=helper.getWritableDatabase();db.beginTransaction();started=true;
            ContentValues cv=new ContentValues();cv.put("stock_limit",newLimit);cv.put("updated_at",PosDatabase.now());
            if(db.update("products",cv,"id=? AND stock_limit=?",new String[]{String.valueOf(id),String.valueOf(oldLimit)})!=1)throw new IllegalStateException("ลิมิตเปลี่ยนระหว่างทำรายการ กรุณาสแกนใหม่");
            db.setTransactionSuccessful();saved=true;
        }catch(Exception e){error=e.getMessage()==null?"บันทึกลิมิตไม่สำเร็จ":e.getMessage();}
        finally{if(db!=null&&started){try{db.endTransaction();}catch(Exception e){saved=false;error="ยืนยันธุรกรรมไม่สำเร็จ";}}}
        if(saved)StockAlertManager.check(this);
        if(!saved)new AlertDialog.Builder(this).setMessage(error==null?"บันทึกลิมิตไม่สำเร็จ":error).setPositiveButton("ตกลง",null).show();
        return saved;
    }

    private boolean saveStock(long id,String barcode,String name,int before,int after,String type){
        SQLiteDatabase db=null;boolean transactionStarted=false,saved=false;String error=null;
        try{
            db=helper.getWritableDatabase();db.beginTransaction();transactionStarted=true;
            InventoryService.changeStock(db,id,barcode,name,before,after,type,null,PosDatabase.now());
            db.setTransactionSuccessful();saved=true;
        }catch(Exception e){error=e.getMessage()==null?"บันทึกสต๊อกไม่สำเร็จ":e.getMessage();saved=false;}
        finally{
            if(db!=null&&transactionStarted){try{db.endTransaction();}catch(Exception e){saved=false;error="ยืนยันธุรกรรมไม่สำเร็จ: "+(e.getMessage()==null?"ข้อผิดพลาดฐานข้อมูล":e.getMessage());}}
        }
        if(saved)StockAlertManager.check(this);
        if(!saved)new AlertDialog.Builder(this).setMessage(error==null?"บันทึกสต๊อกไม่สำเร็จ":error).setPositiveButton("ตกลง",null).show();
        return saved;
    }
    private void newProduct(String barcode){
        root=PosUi.page(this,"เพิ่มสินค้าใหม่");
        PosUi.line(this,root,"บาร์โค้ด: "+barcode);
        PosUi.speak(this,"สินค้าใหม่ค่ะ");

        EditText name=PosUi.input(this,root,"ชื่อสินค้า");
        EditText price=PosUi.input(this,root,"ราคาขาย (บาท)");
        Cursor registry=helper.getReadableDatabase().query(
                "barcode_registry",new String[]{"name","price_satang"},
                "barcode=?",new String[]{barcode},null,null,null,"1");
        if(registry.moveToFirst()){
            name.setText(registry.getString(0));
            price.setText(PosDatabase.money(registry.getLong(1)));
            name.setEnabled(false);
            price.setEnabled(false);
        }
        registry.close();

        price.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText unit=PosUi.input(this,root,"หน่วย เช่น ขวด");
        EditText lim=PosUi.input(this,root,"ลิมิตสต๊อก");
        lim.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        PosUi.button(this,root,"บันทึกสินค้า",v->{
            String nm=PosUi.str(name), un=PosUi.str(unit);
            String priceText=PosUi.str(price).replace(",","");
            String limitText=PosUi.str(lim);

            if(nm.isEmpty()){name.setError("กรอกชื่อสินค้า");name.requestFocus();return;}
            if(un.isEmpty()){unit.setError("กรอกหน่วยสินค้า เช่น ชิ้นหรือขวด");unit.requestFocus();return;}
            if(priceText.isEmpty()){price.setError("กรอกราคาขาย");price.requestFocus();return;}
            if(limitText.isEmpty()){lim.setError("กรอกลิมิตสต๊อก (ใส่ 0 ได้)");lim.requestFocus();return;}

            final int l;
            final long p;
            try{
                l=Integer.parseInt(limitText);
                if(l<0)throw new NumberFormatException();
            }catch(NumberFormatException e){lim.setError("ลิมิตสต๊อกต้องเป็นจำนวนเต็มตั้งแต่ 0 ขึ้นไป");lim.requestFocus();return;}
            try{p=PosDatabase.toSatang(priceText);}
            catch(Exception e){price.setError("ราคาขายไม่ถูกต้อง เช่น 25 หรือ 25.50");price.requestFocus();return;}

            SQLiteDatabase db=null;
            boolean started=false,saved=false;
            String error=null;
            try{
                db=helper.getWritableDatabase();
                db.beginTransaction();
                started=true;

                Cursor existingProduct=db.query("products",new String[]{"active"},
                        "barcode=?",new String[]{barcode},null,null,null,"1");
                if(existingProduct.moveToFirst()){
                    int active=existingProduct.getInt(0);
                    existingProduct.close();
                    throw new IllegalStateException(active==1
                            ?"สินค้านี้มีอยู่ในคลังแล้ว กรุณาใช้เมนูเพิ่มสินค้าเข้าสต๊อก"
                            :"บาร์โค้ดนี้เป็นสินค้าที่ปิดใช้งานอยู่ กรุณาตรวจสอบรายการเดิมก่อนเพิ่มใหม่");
                }
                existingProduct.close();

                BarcodeRegistry.Entry registryEntry=BarcodeRegistry.find(db,barcode);
                boolean registryExists=registryEntry!=null;
                BarcodeRegistry.ensureConsistent(db,barcode,nm,p);

                ContentValues cv=new ContentValues();
                cv.put("barcode",barcode);cv.put("name",nm);cv.put("price_satang",p);
                cv.put("cost_satang",0L);cv.put("unit",un);cv.put("stock",0);
                cv.put("stock_limit",l);cv.put("created_at",PosDatabase.now());
                cv.put("updated_at",PosDatabase.now());
                long id=db.insertOrThrow("products",null,cv);

                if(!registryExists)BarcodeRegistry.registerIfMissing(db,barcode,nm,p,"FACTORY",PosDatabase.now());

                ContentValues h=new ContentValues();
                h.put("product_id",id);h.put("barcode",barcode);h.put("product_name",nm);
                h.put("change_type","INITIAL");h.put("before_qty",0);
                h.put("after_qty",0);h.put("difference",0);h.put("created_at",PosDatabase.now());
                db.insertOrThrow("stock_history",null,h);
                db.setTransactionSuccessful();
                saved=true;
            }catch(Exception e){
                error=e.getMessage()==null?"ข้อผิดพลาดฐานข้อมูล":e.getMessage();
                saved=false;
            }finally{
                if(db!=null&&started){
                    try{db.endTransaction();}
                    catch(Exception e){saved=false;error="ยืนยันการบันทึกไม่สำเร็จ: "
                            +(e.getMessage()==null?"ข้อผิดพลาดฐานข้อมูล":e.getMessage());}
                }
            }
            if(saved){
                StockAlertManager.check(this);
                home();
            }else{
                new AlertDialog.Builder(this)
                        .setTitle("บันทึกสินค้าไม่สำเร็จ")
                        .setMessage(error==null?"กรุณาตรวจสอบข้อมูลแล้วลองอีกครั้ง":error)
                        .setPositiveButton("ตกลง",null)
                        .show();
            }
        });
        PosUi.back(this,root);
    }
}