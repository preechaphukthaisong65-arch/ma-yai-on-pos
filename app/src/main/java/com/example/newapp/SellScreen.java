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
import java.util.LinkedHashMap;
import java.util.Map;

public class SellScreen extends Activity {
    private static final class Item { long id,price;String barcode,name,unit;int qty,stock; Item(long i,String b,String n,String u,long p,int s){id=i;barcode=b;name=n;unit=u;price=p;stock=s;qty=1;} }
    private PosDatabase helper; private final LinkedHashMap<Long,Item> cart=new LinkedHashMap<>(); private LinearLayout root;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);home();}
    private void home(){root=PosUi.page(this,"ขายสินค้า");PosUi.line(this,root,"เพิ่มสินค้าเข้าบิลด้วยการสแกนบาร์โค้ดเท่านั้น");
        PosUi.button(this,root,"เริ่มสแกนสินค้า",v->{cart.clear();scan();});PosUi.button(this,root,"กลับหน้าหลัก",v->finish());}
    private void scan(){PosUi.scan(this);}
    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){super.onActivityResult(requestCode,resultCode,data);IntentResult r=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(r!=null){if(r.getContents()==null){showCart();return;}addScanned(r.getContents());}}
    private void addScanned(String barcode){
        Cursor c=helper.getReadableDatabase().query("products",new String[]{"id","name","unit","price_satang","stock"},"barcode=? AND active=1",new String[]{barcode},null,null,null,"1");
        if(!c.moveToFirst()){c.close();new AlertDialog.Builder(this).setMessage("ไม่พบสินค้านี้ในคลังสินค้า").setPositiveButton("ตกลง",(d,w)->scan()).setNegativeButton("จบบิล",(d,w)->showCart()).show();return;}
        long id=c.getLong(0);String name=c.getString(1),unit=c.getString(2);long price=c.getLong(3);int stock=c.getInt(4);c.close();
        Item old=cart.get(id);if(old==null){Item item=new Item(id,barcode,name,unit,price,stock);cart.put(id,item);}else old.qty++;
        PosUi.speakThen(this,name+" "+cart.get(id).qty+" "+unit+"ค่ะ",()->new AlertDialog.Builder(this).setMessage("สแกนแล้ว: "+name+"\\nต้องการสแกนสินค้าต่อหรือจบบิล?").setPositiveButton("สแกนต่อ",(d,w)->scan()).setNegativeButton("จบบิล",(d,w)->showCart()).show());
    }
    private long subtotal(){long s=0;for(Item i:cart.values())s+=i.price*i.qty;return s;}
    private void showCart(){
        root=PosUi.page(this,"ตรวจสอบรายการขาย");
        if(cart.isEmpty()){PosUi.line(this,root,"ยังไม่มีสินค้าในบิล");PosUi.button(this,root,"เริ่มใหม่",v->home());return;}
        StringBuilder speech=new StringBuilder();for(Item i:cart.values()){PosUi.line(this,root,i.name+"  "+i.qty+" "+i.unit+" × "+PosDatabase.money(i.price)+" = "+PosDatabase.money(i.price*i.qty)+" บาท");speech.append(i.name).append(" ").append(i.qty).append(" ").append(i.unit).append(" ");}PosUi.line(this,root,"รวมสินค้า "+PosDatabase.money(subtotal())+" บาท");PosUi.speak(this,"ยอดรวม "+PosDatabase.money(subtotal())+" บาท");
        PosUi.button(this,root,"สแกนเพิ่ม",v->scan());PosUi.button(this,root,"ขายเงินสด",v->cashSale());PosUi.button(this,root,"ขายเชื่อ",v->creditSale());PosUi.button(this,root,"ยกเลิกบิลนี้",v->home());
    }
    private void cashSale(){
        root=PosUi.page(this,"รับชำระเงินสด");PosUi.line(this,root,"ยอดที่ต้องชำระ "+PosDatabase.money(subtotal())+" บาท");
        EditText received=PosUi.input(this,root,"รับเงิน (บาท)");
        PosUi.button(this,root,"ยืนยันขายเงินสด",v->{try{long cash=PosUi.satang(received);if(cash<subtotal()){received.setError("เงินรับไม่พอ");return;}commitSale(false,null,cash);}catch(Exception e){received.setError("กรอกจำนวนเงินให้ถูกต้อง");}});
        PosUi.back(this,root);
    }
    private void creditSale(){
        root=PosUi.page(this,"ขายเชื่อ");EditText customer=PosUi.input(this,root,"ชื่อลูกค้า-ชื่อและนามสกุล");
        PosUi.line(this,root,"ยอดสินค้า "+PosDatabase.money(subtotal())+" บาท");
        PosUi.line(this,root,"ค่าบริการ 5% "+PosDatabase.money(PosDatabase.creditFee(subtotal()))+" บาท");
        PosUi.line(this,root,"ยอดหนี้รวม "+PosDatabase.money(subtotal()+PosDatabase.creditFee(subtotal()))+" บาท");
        PosUi.button(this,root,"ยืนยันขายเชื่อ",v->{String n=PosUi.str(customer);if(n.isEmpty()){customer.setError("กรอกชื่อเต็มลูกค้า");return;}commitSale(true,n,0);});PosUi.back(this,root);
    }
    private void commitSale(boolean credit,String customerName,long cash){
        String duplicate=findDuplicate(credit,customerName);
        if(duplicate!=null){
            new AlertDialog.Builder(this).setTitle("พบบิลที่อาจซ้ำ").setMessage("พบบิลที่มีรายละเอียดสินค้าเหมือนกันใน 1 ชั่วโมงที่ผ่านมา ("+duplicate+") ต้องการสร้างบิลใหม่หรือไม่?")
                    .setNegativeButton("ยกเลิก",(d,w)->home())
                    .setPositiveButton("สร้างบิลใหม่",(d,w)->saveSale(credit,customerName,cash)).show();
        }else saveSale(credit,customerName,cash);
    }
    private String findDuplicate(boolean credit,String customerName){
        SQLiteDatabase db=helper.getReadableDatabase();
        Cursor bills=db.rawQuery("SELECT b.id,b.bill_code,c.full_name FROM bills b LEFT JOIN customers c ON c.id=b.customer_id WHERE b.created_at>=? AND b.sale_type=? AND b.status IN ('PAID','UNPAID') ORDER BY b.created_at DESC",
                new String[]{String.valueOf(PosDatabase.now()-3600000L),credit?"CREDIT":"CASH"});
        String match=null;
        while(bills.moveToNext()){
            if(credit&&(bills.isNull(2)||!customerName.equalsIgnoreCase(bills.getString(2))))continue;
            long billId=bills.getLong(0);Cursor items=db.query("bill_items",new String[]{"product_id","quantity","unit_price_satang"},"bill_id=?",new String[]{String.valueOf(billId)},null,null,null);
            java.util.HashMap<Long,long[]> existing=new java.util.HashMap<>();
            while(items.moveToNext())existing.put(items.getLong(0),new long[]{items.getInt(1),items.getLong(2)});items.close();
            if(existing.size()!=cart.size())continue;boolean same=true;
            for(Item item:cart.values()){long[] x=existing.get(item.id);if(x==null||x[0]!=item.qty||x[1]!=item.price){same=false;break;}}
            if(same){match=bills.getString(1);break;}
        }
        bills.close();return match;
    }
    private void saveSale(boolean credit,String customerName,long cash){
        SQLiteDatabase db=helper.getWritableDatabase();long now=PosDatabase.now();String code="B"+now;
        long sub=subtotal(),fee=credit?PosDatabase.creditFee(sub):0,total=sub+fee;db.beginTransaction();
        try{
            Long customerId=null;
            if(credit){Cursor c=db.query("customers",new String[]{"id"},"full_name=?",new String[]{customerName},null,null,null);if(c.moveToFirst())customerId=c.getLong(0);c.close();
                if(customerId==null){ContentValues cv=new ContentValues();cv.put("full_name",customerName);cv.put("created_at",now);customerId=db.insertOrThrow("customers",null,cv);}}
            ContentValues bill=new ContentValues();bill.put("bill_code",code);if(customerId!=null)bill.put("customer_id",customerId);bill.put("sale_type",credit?"CREDIT":"CASH");bill.put("status",credit?"UNPAID":"PAID");bill.put("subtotal_satang",sub);bill.put("fee_satang",fee);bill.put("total_satang",total);bill.put("cash_received_satang",cash);bill.put("change_satang",credit?0:cash-total);bill.put("created_at",now);if(!credit)bill.put("paid_at",now);
            long billId=db.insertOrThrow("bills",null,bill);
            for(Item i:cart.values()){
                if(i.qty>i.stock)throw new IllegalStateException("สินค้า "+i.name+" มีสต๊อกไม่พอ");
                ContentValues p=new ContentValues();p.put("stock",i.stock-i.qty);p.put("updated_at",now);
                if(db.update("products",p,"id=? AND stock>=?",new String[]{String.valueOf(i.id),String.valueOf(i.qty)})!=1)throw new IllegalStateException("สต๊อกเปลี่ยน กรุณาทำรายการใหม่");
                ContentValues it=new ContentValues();it.put("bill_id",billId);it.put("product_id",i.id);it.put("barcode",i.barcode);it.put("product_name",i.name);it.put("unit",i.unit);it.put("quantity",i.qty);it.put("unit_price_satang",i.price);it.put("line_total_satang",i.price*i.qty);db.insertOrThrow("bill_items",null,it);
                ContentValues h=new ContentValues();h.put("product_id",i.id);h.put("barcode",i.barcode);h.put("product_name",i.name);h.put("change_type","SALE");h.put("before_qty",i.stock);h.put("after_qty",i.stock-i.qty);h.put("difference",-i.qty);h.put("reference",code);h.put("created_at",now);db.insertOrThrow("stock_history",null,h);
            }
            ContentValues ledger=new ContentValues();ledger.put("bill_id",billId);ledger.put("bill_code",code);ledger.put("sale_type",credit?"CREDIT":"CASH");ledger.put("event_type","SALE");ledger.put("subtotal_satang",sub);ledger.put("fee_satang",fee);ledger.put("total_satang",total);ledger.put("created_at",now);db.insertOrThrow("sales_ledger",null,ledger);
            db.setTransactionSuccessful();
            new AlertDialog.Builder(this).setTitle("บันทึกการขายสำเร็จ").setMessage("เลขที่บิล "+code+"\nยอดสินค้า "+PosDatabase.money(sub)+" บาท\n"+(credit?"ค่าบริการ 5% "+PosDatabase.money(fee)+" บาท\nยอดหนี้ "+PosDatabase.money(total)+" บาท":"รับเงิน "+PosDatabase.money(cash)+" บาท\nเงินทอน "+PosDatabase.money(cash-total)+" บาท")).setPositiveButton("ตกลง",(d,w)->home()).show();
            cart.clear();
        }catch(Exception e){new AlertDialog.Builder(this).setTitle("บันทึกไม่สำเร็จ").setMessage(e.getMessage()).setPositiveButton("ตกลง",null).show();}
        finally{db.endTransaction();}
    }
}