package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
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
        ProductCatalogService.Product product=ProductCatalogService.findActiveByBarcode(helper,barcode);
        if(product==null){
            if("add".equals(mode)){newProduct(barcode);return;}
            PosUi.dialog(this).setMessage("ไม่พบสินค้าในคลัง")
                    .setPositiveButton("ตกลง",(d,w)->scanAgain()).show();return;
        }
        long id=product.id;String name=product.name,unit=product.unit;
        int stock=product.stock,limit=product.stockLimit;long price=product.priceSatang;
        if("detail".equals(mode)){
            PosUi.dialog(this).setTitle(name)
                    .setMessage("บาร์โค้ด: "+barcode+"\nคงเหลือ: "+stock+" "+unit+"\nราคาขาย: "+PosDatabase.money(price)+" บาท")
                    .setPositiveButton("ตกลง",(d,w)->scanAgain()).show();return;
        }
        root=PosUi.page(this,name);
        PosUi.line(this,root,"บาร์โค้ด: "+barcode);PosUi.line(this,root,"คงเหลือปัจจุบัน: "+stock+" "+unit);
        if("count".equals(mode)){
            EditText actual=PosUi.input(this,root,"จำนวนที่นับได้จริง");
            PosUi.button(this,root,"บันทึกผลนับ",v->{try{
                int qty=Integer.parseInt(PosUi.str(actual));if(qty<0)throw new IllegalArgumentException();
                saveStock(id,barcode,name,stock,qty,"COUNT");
            }catch(Exception e){actual.setError("กรอกจำนวนเต็มตั้งแต่ 0 ขึ้นไป");}});
        }else if("limit".equals(mode)){
            EditText lim=PosUi.input(this,root,"ลิมิตสต๊อกปัจจุบัน "+limit);
            PosUi.button(this,root,"บันทึกลิมิต",v->{try{
                int q=Integer.parseInt(PosUi.str(lim));if(q<0)throw new IllegalArgumentException();
                saveLimit(id,limit,q);
            }catch(Exception e){lim.setError("กรอกจำนวนเต็มตั้งแต่ 0 ขึ้นไป");}});
        }else if("add".equals(mode)){
            PosUi.speak(this,name+"ค่ะ");EditText qty=PosUi.input(this,root,"จำนวนสินค้าที่รับเข้า");
            PosUi.button(this,root,"บันทึกรับเข้า",v->{try{
                int q=Integer.parseInt(PosUi.str(qty));if(q<=0)throw new IllegalArgumentException();
                int after=Math.addExact(stock,q);
                saveStock(id,barcode,name,stock,after,"RECEIVE");
            }catch(Exception e){qty.setError("กรอกจำนวนเต็มมากกว่า 0 และไม่เกินขอบเขต");}});
        }
        PosUi.button(this,root,"สแกนรายการถัดไป",v->scanAgain());PosUi.back(this,root);
    }
    private void showStockHistory(String barcode){
        root=PosUi.page(this,"ประวัติสต๊อก");
        Cursor c=helper.getReadableDatabase().query("stock_history",new String[]{"product_name","change_type","before_qty","after_qty","difference","created_at","reference"},"barcode=?",new String[]{barcode},null,null,"created_at DESC");
        if(!c.moveToFirst())PosUi.line(this,root,"ไม่พบประวัติของบาร์โค้ดนี้");
        else do{String ref=c.getString(6)==null?"":" • "+c.getString(6);PosUi.line(this,root,c.getString(0)+" • "+c.getString(1)+" • "+c.getInt(2)+" → "+c.getInt(3)+" ("+(c.getInt(4)>0?"+":"")+c.getInt(4)+")"+ref+" • "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(5))));}while(c.moveToNext());
        c.close();PosUi.button(this,root,"สแกนบาร์โค้ดอื่น",v->scanAgain());PosUi.back(this,root);
    }
    private boolean saveLimit(long id,int oldLimit,int newLimit){
        try{
            ProductCatalogService.updateStockLimit(helper,id,oldLimit,newLimit,PosDatabase.now());
            StockAlertManager.check(this);
            PosUi.result(this,true,"บันทึกลิมิตสำเร็จ","บันทึกลิมิตสต๊อกเรียบร้อย",()->home());
            return true;
        }catch(Exception e){
            String error=e.getMessage()==null?"บันทึกลิมิตไม่สำเร็จ":e.getMessage();
            PosUi.dialog(this).setMessage(error).setPositiveButton("ตกลง",null).show();
            return false;
        }
    }

    private boolean saveStock(long id,String barcode,String name,int before,int after,String type){
        try{
            InventoryService.changeStock(helper,id,barcode,name,before,after,type,null,PosDatabase.now());
            StockAlertManager.check(this);
            PosUi.result(this,true,"บันทึกสต๊อกสำเร็จ","บันทึกการเปลี่ยนแปลงสต๊อกเรียบร้อย",()->home());
            return true;
        }catch(Exception e){
            String error=e.getMessage()==null?"บันทึกสต๊อกไม่สำเร็จ":e.getMessage();
            PosUi.dialog(this).setMessage(error).setPositiveButton("ตกลง",null).show();
            return false;
        }
    }
    private void newProduct(String barcode){
        root=PosUi.page(this,"เพิ่มสินค้าใหม่");
        PosUi.line(this,root,"บาร์โค้ด: "+barcode);
        PosUi.speak(this,"สินค้าใหม่ค่ะ");
        EditText name=PosUi.input(this,root,"ชื่อสินค้า");
        EditText price=PosUi.input(this,root,"ราคาขาย (บาท)");
        BarcodeRegistry.Entry registry=BarcodeRegistry.find(helper.getReadableDatabase(),barcode);
        if(registry!=null){
            name.setText(registry.name);price.setText(PosDatabase.money(registry.priceSatang));
            name.setEnabled(false);price.setEnabled(false);
        }
        price.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText unit=PosUi.input(this,root,"หน่วย เช่น ขวด");
        EditText lim=PosUi.input(this,root,"ลิมิตสต๊อก");
        lim.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        PosUi.button(this,root,"บันทึกสินค้า",v->{
            String nm=PosUi.str(name),un=PosUi.str(unit);
            String priceText=PosUi.str(price).replace(",","");
            String limitText=PosUi.str(lim);
            if(nm.isEmpty()){name.setError("กรอกชื่อสินค้า");name.requestFocus();return;}
            if(un.isEmpty()){unit.setError("กรอกหน่วยสินค้า เช่น ชิ้นหรือขวด");unit.requestFocus();return;}
            if(priceText.isEmpty()){price.setError("กรอกราคาขาย");price.requestFocus();return;}
            if(limitText.isEmpty()){lim.setError("กรอกลิมิตสต๊อก (ใส่ 0 ได้)");lim.requestFocus();return;}
            final int limit;
            final long priceSatang;
            try{limit=Integer.parseInt(limitText);if(limit<0)throw new NumberFormatException();}
            catch(NumberFormatException e){lim.setError("ลิมิตสต๊อกต้องเป็นจำนวนเต็มตั้งแต่ 0 ขึ้นไป");lim.requestFocus();return;}
            try{priceSatang=PosDatabase.toSatang(priceText);}
            catch(Exception e){price.setError("ราคาขายไม่ถูกต้อง เช่น 25 หรือ 25.50");price.requestFocus();return;}
            try{
                ProductCatalogService.create(helper,barcode,nm,priceSatang,un,limit,PosDatabase.now());
                StockAlertManager.check(this);
                PosUi.result(this,true,"บันทึกสินค้าสำเร็จ","บันทึกสินค้า "+nm+" เรียบร้อย",()->home());
            }catch(Exception e){
                String error=e.getMessage()==null?"ข้อผิดพลาดฐานข้อมูล":e.getMessage();
                PosUi.dialog(this).setTitle("บันทึกสินค้าไม่สำเร็จ").setMessage(error)
                        .setPositiveButton("ตกลง",null).show();
            }
        });
        PosUi.back(this,root);
    }
}