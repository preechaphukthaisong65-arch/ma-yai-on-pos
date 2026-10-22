package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import com.google.zxing.integration.android.IntentResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class SellScreen extends Activity {
    private static final class Item { long id,price;String barcode,name,unit;int qty,stock; Item(long i,String b,String n,String u,long p,int s){id=i;barcode=b;name=n;unit=u;price=p;stock=s;qty=1;} }
    private PosDatabase helper; private final LinkedHashMap<Long,Item> cart=new LinkedHashMap<>(); private LinearLayout root; private boolean scanning=false; private boolean saleInProgress=false,duplicatePromptOpen=false;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);home();}
    private void home(){root=PosUi.page(this,"ขายสินค้า");PosUi.line(this,root,"เพิ่มสินค้าเข้าบิลด้วยการสแกนบาร์โค้ดเท่านั้น");
        PosUi.button(this,root,"เริ่มสแกนสินค้า",v->{cart.clear();scan();});PosUi.button(this,root,"บัญชีลูกค้าสินเชื่อ",v->startActivity(new Intent(SellScreen.this,CreditAccountScreen.class)));PosUi.button(this,root,"กลับหน้าหลัก",v->finish());
        addSalesChart("ยอดขายรายสัปดาห์ • 8 สัปดาห์ล่าสุด","week",8);
        addSalesChart("ยอดขายรายเดือน • 12 เดือนล่าสุด","month",12);
        addSalesChart("ยอดขายรายปี • ปีปัจจุบันและข้อมูลที่เหลือของปีก่อน","year",2);
    }
    private void addSalesChart(String title,String unit,int count){
        String[] labels=new String[count];long[] values=new long[count];Calendar base=Calendar.getInstance();
        if(unit.equals("week")){base.setFirstDayOfWeek(Calendar.MONDAY);base.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);}
        else if(unit.equals("month"))base.set(Calendar.DAY_OF_MONTH,1);
        else{base.set(Calendar.MONTH,Calendar.JANUARY);base.set(Calendar.DAY_OF_MONTH,1);}
        base.set(Calendar.HOUR_OF_DAY,0);base.set(Calendar.MINUTE,0);base.set(Calendar.SECOND,0);base.set(Calendar.MILLISECOND,0);
        for(int i=0;i<count;i++){
            Calendar start=(Calendar)base.clone();int offset=i-(count-1);
            if(unit.equals("week"))start.add(Calendar.WEEK_OF_YEAR,offset);else if(unit.equals("month"))start.add(Calendar.MONTH,offset);else start.add(Calendar.YEAR,offset);
            Calendar end=(Calendar)start.clone();if(unit.equals("week"))end.add(Calendar.WEEK_OF_YEAR,1);else if(unit.equals("month"))end.add(Calendar.MONTH,1);else end.add(Calendar.YEAR,1);
            Cursor q=helper.getReadableDatabase().rawQuery("SELECT COALESCE(SUM(total_satang),0) FROM sales_ledger WHERE created_at BETWEEN ? AND ? AND event_type IN ('SALE','CANCEL','RETURN','CREDIT_FEE_WAIVED')",new String[]{String.valueOf(start.getTimeInMillis()),String.valueOf(end.getTimeInMillis()-1)});
            if(q.moveToFirst())values[i]=q.getLong(0);q.close();
            labels[i]=unit.equals("week")?new SimpleDateFormat("dd/MM",Locale.getDefault()).format(start.getTime()):unit.equals("month")?new SimpleDateFormat("MM/yy",Locale.getDefault()).format(start.getTime()):new SimpleDateFormat("yyyy",Locale.getDefault()).format(start.getTime());
        }
        SalesChartView chart=new SalesChartView(this);chart.setData(title,labels,values);root.addView(chart,new LinearLayout.LayoutParams(-1,PosUi.dp(this,180)));
    }
    private void scan(){
        // Use the same ZXing IntentIntegrator flow already used by BarcodeScreen.
        // This avoids constructing a custom camera preview inside this Activity.
        scanning=true;
        PosUi.scan(this,"สแกนบาร์โค้ดสินค้า");
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        IntentResult result=BarcodeScanner.parseResult(requestCode,resultCode,data);
        if(result==null)return;
        scanning=false;
        if(result.getContents()!=null){
            addScanned(result.getContents());
        }else{
            showCart();
        }
    }
    private void addScanned(String barcode){
        Cursor c=helper.getReadableDatabase().query("products",new String[]{"id","name","unit","price_satang","stock"},"barcode=? AND active=1",new String[]{barcode},null,null,null,"1");
        if(!c.moveToFirst()){c.close();PosUi.speakThen(this,"ไม่พบสินค้าในคลังค่ะ",this::showCart);return;}
        long id=c.getLong(0);String name=c.getString(1),unit=c.getString(2);long price=c.getLong(3);int stock=c.getInt(4);c.close();
        Item old=cart.get(id);if(old==null){Item item=new Item(id,barcode,name,unit,price,stock);cart.put(id,item);}else old.qty++;
        PosUi.speakThen(this,name+" "+cart.get(id).qty+" "+unit+"ค่ะ",this::showCart);
    }
    private long subtotal(){long s=0;for(Item i:cart.values())s+=i.price*i.qty;return s;}
    private void showCart(){
        root=PosUi.page(this,"ตรวจสอบรายการขาย");
        if(cart.isEmpty()){PosUi.line(this,root,"ยังไม่มีสินค้าในบิล");PosUi.button(this,root,"เริ่มใหม่",v->home());return;}
        StringBuilder speech=new StringBuilder();
        for(Item i:cart.values()){
            PosUi.line(this,root,i.name+"  "+i.qty+" "+i.unit+" × "+PosDatabase.money(i.price)+" = "+PosDatabase.money(i.price*i.qty)+" บาท");
            speech.append(i.name).append(" ").append(i.qty).append(" ").append(i.unit).append(" ราคา ").append(PosDatabase.money(i.price)).append(" บาท รวม ").append(PosDatabase.money(i.price*i.qty)).append(" บาท ");
        }
        PosUi.line(this,root,"รวมสินค้า "+PosDatabase.money(subtotal())+" บาท");
        final String summary=speech.append("ยอดรวม ").append(PosDatabase.money(subtotal())).append(" บาท").toString();
        PosUi.speakThen(this,summary,()->{
            PosUi.button(this,root,"สแกนเพิ่ม",v->scan());
            PosUi.button(this,root,"ขายเงินสด",v->cashSale());
            PosUi.button(this,root,"ขายเชื่อ",v->creditSale());
            PosUi.button(this,root,"ยกเลิกบิลนี้",v->home());
        });
    }
    private void cashSale(){
        root=PosUi.page(this,"รับชำระเงินสด");PosUi.line(this,root,"ยอดที่ต้องชำระ "+PosDatabase.money(subtotal())+" บาท");
        EditText received=PosUi.input(this,root,"รับเงิน (บาท)");
        PosUi.button(this,root,"ยืนยันขายเงินสด",v->{try{long cash=PosUi.satang(received);if(cash<subtotal()){received.setError("เงินรับไม่พอ");return;}commitSale(false,null,cash);}catch(Exception e){received.setError("กรอกจำนวนเงินให้ถูกต้อง");}});
        PosUi.back(this,root);
    }
    private void creditSale(){
        root=PosUi.page(this,"ขายเชื่อ");
        PosUi.line(this,root,"เลือกลูกค้าเดิมจากคำแนะนำ หรือกรอกชื่อเต็มเพื่อสร้างลูกค้าใหม่");
        AutoCompleteTextView customer=new AutoCompleteTextView(this);customer.setSingleLine(true);customer.setHint("ชื่อลูกค้า-ชื่อและนามสกุล");customer.setThreshold(1);
        java.util.ArrayList<String> customerNames=new java.util.ArrayList<>();
        Cursor names=helper.getReadableDatabase().query("customers",new String[]{"full_name"},null,null,null,null,"full_name COLLATE NOCASE ASC");
        while(names.moveToNext())customerNames.add(names.getString(0));names.close();
        customer.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,customerNames));
        root.addView(customer,new LinearLayout.LayoutParams(-1,-2));
        long sub=subtotal(),fee=PosDatabase.creditFee(sub),total=sub+fee;
        PosUi.line(this,root,"ยอดสินค้า "+PosDatabase.money(sub)+" บาท");
        PosUi.line(this,root,"ค่าบริการ 5% "+PosDatabase.money(fee)+" บาท");
        PosUi.line(this,root,"ยอดหนี้รวม "+PosDatabase.money(total)+" บาท");
        PosUi.speakThen(this,"ขายเชื่อ ค่าบริการ 5 เปอร์เซ็นต์ "+PosDatabase.money(fee)+" บาท ยอดหนี้รวม "+PosDatabase.money(total)+" บาท",()->PosUi.button(this,root,"ยืนยันขายเชื่อ",v->{String n=customer.getText().toString().trim();if(n.isEmpty()){customer.setError("กรอกชื่อเต็มลูกค้า");return;}commitSale(true,n,0);}));
        PosUi.back(this,root);
    }
    private void commitSale(boolean credit,String customerName,long cash){
        if(saleInProgress||duplicatePromptOpen)return;
        if(cart.isEmpty()){new AlertDialog.Builder(this).setMessage("ไม่มีสินค้าในบิล").setPositiveButton("ตกลง",null).show();return;}
        String duplicate;
        try{duplicate=findDuplicate(credit,customerName,cash);}catch(Exception e){new AlertDialog.Builder(this).setMessage("ตรวจสอบบิลซ้ำไม่สำเร็จ จึงยังไม่บันทึกการขาย").setPositiveButton("ตกลง",null).show();return;}
        if(duplicate!=null){
            duplicatePromptOpen=true;
            new AlertDialog.Builder(this).setTitle("พบบิลที่อาจซ้ำ")
                    .setMessage("พบบิลที่มีรายละเอียดตรงกันใน 1 ชั่วโมงที่ผ่านมา ("+duplicate+") ต้องการสร้างบิลใหม่หรือไม่?")
                    .setNegativeButton("ยกเลิก",(d,w)->{duplicatePromptOpen=false;home();})
                    .setPositiveButton("ยืนยันว่าเป็นการขายจริง",(d,w)->{duplicatePromptOpen=false;saveSale(credit,customerName,cash);})
                    .setOnCancelListener(d->duplicatePromptOpen=false).show();
        }else saveSale(credit,customerName,cash);
    }
    private String findDuplicate(boolean credit,String customerName,long cash){
        SQLiteDatabase db=helper.getReadableDatabase();
        long now=PosDatabase.now(),sub=subtotal(),fee=credit?PosDatabase.creditFee(sub):0,total=sub+fee,change=credit?0:cash-sub;
        Cursor bills=db.rawQuery("SELECT b.id,b.bill_code,c.full_name,b.cash_received_satang,b.change_satang,b.subtotal_satang,b.fee_satang,b.total_satang FROM bills b LEFT JOIN customers c ON c.id=b.customer_id WHERE b.created_at>=? AND b.created_at<=? AND b.sale_type=? AND b.status IN ('PAID','UNPAID') ORDER BY b.created_at DESC",
                new String[]{String.valueOf(now-3600000L),String.valueOf(now),credit?"CREDIT":"CASH"});
        java.util.ArrayList<Item> current=new java.util.ArrayList<>(cart.values());
        current.sort((a,b)->a.barcode.compareTo(b.barcode));
        String match=null;
        while(bills.moveToNext()){
            if(credit&&(bills.isNull(2)||!customerName.equalsIgnoreCase(bills.getString(2))))continue;
            if(bills.getLong(3)!=cash||bills.getLong(4)!=change||bills.getLong(5)!=sub||bills.getLong(6)!=fee||bills.getLong(7)!=total)continue;
            long billId=bills.getLong(0);
            Cursor items=db.query("bill_items",new String[]{"barcode","product_name","unit","quantity","unit_price_satang","line_total_satang"},"bill_id=?",new String[]{String.valueOf(billId)},null,null,"barcode ASC");
            boolean same=true;int index=0;
            while(items.moveToNext()){
                if(index>=current.size()){same=false;break;}
                Item item=current.get(index++);
                if(!item.barcode.equals(items.getString(0))||!item.name.equals(items.getString(1))||!item.unit.equals(items.getString(2))
                        ||item.qty!=items.getInt(3)||item.price!=items.getLong(4)||item.price*item.qty!=items.getLong(5)){same=false;break;}
            }
            items.close();
            if(same&&index==current.size()){match=bills.getString(1);break;}
        }
        bills.close();return match;
    }
    private void saveSale(boolean credit,String customerName,long cash){
        if(saleInProgress)return;
        if(cart.isEmpty()){new AlertDialog.Builder(this).setMessage("ไม่มีสินค้าในบิล").setPositiveButton("ตกลง",null).show();return;}
        saleInProgress=true;
        SQLiteDatabase db=null;boolean transactionStarted=false,saved=false;String error=null,code=null;
        long now=PosDatabase.now(),sub=subtotal(),fee=credit?PosDatabase.creditFee(sub):0,total=sub+fee;
        try{
            db=helper.getWritableDatabase();db.beginTransaction();transactionStarted=true;
            code="B"+now;int suffix=1;
            while(true){Cursor existing=db.query("bills",new String[]{"id"},"bill_code=?",new String[]{code},null,null,null,"1");boolean used=existing.moveToFirst();existing.close();if(!used)break;code="B"+now+"-"+(suffix++);}
            Long customerId=null;
            if(credit){
                Cursor c=db.query("customers",new String[]{"id"},"full_name=?",new String[]{customerName},null,null,null);
                if(c.moveToFirst())customerId=c.getLong(0);c.close();
                if(customerId==null){ContentValues cv=new ContentValues();cv.put("full_name",customerName);cv.put("created_at",now);customerId=db.insertOrThrow("customers",null,cv);}
            }
            ContentValues bill=new ContentValues();bill.put("bill_code",code);if(customerId!=null)bill.put("customer_id",customerId);bill.put("sale_type",credit?"CREDIT":"CASH");bill.put("status",credit?"UNPAID":"PAID");bill.put("subtotal_satang",sub);bill.put("fee_satang",fee);bill.put("total_satang",total);bill.put("cash_received_satang",cash);bill.put("change_satang",credit?0:cash-total);bill.put("created_at",now);if(!credit)bill.put("paid_at",now);
            long billId=db.insertOrThrow("bills",null,bill);
            for(Item i:cart.values()){
                if(i.qty>i.stock)throw new IllegalStateException("สินค้า "+i.name+" มีสต๊อกไม่พอ");
                ContentValues p=new ContentValues();p.put("stock",i.stock-i.qty);p.put("updated_at",now);
                if(db.update("products",p,"id=? AND stock=?",new String[]{String.valueOf(i.id),String.valueOf(i.stock)})!=1)throw new IllegalStateException("สต๊อกเปลี่ยนระหว่างเปิดบิล กรุณาสแกนสินค้าและทำรายการใหม่");
                ContentValues it=new ContentValues();it.put("bill_id",billId);it.put("product_id",i.id);it.put("barcode",i.barcode);it.put("product_name",i.name);it.put("unit",i.unit);it.put("quantity",i.qty);it.put("unit_price_satang",i.price);it.put("line_total_satang",i.price*i.qty);db.insertOrThrow("bill_items",null,it);
                ContentValues li=new ContentValues();li.put("bill_id",billId);li.put("bill_code",code);li.put("event_type","SALE");li.put("barcode",i.barcode);li.put("product_name",i.name);li.put("quantity",i.qty);li.put("unit_price_satang",i.price);li.put("line_total_satang",i.price*i.qty);li.put("created_at",now);db.insertOrThrow("sales_ledger_items",null,li);
                ContentValues h=new ContentValues();h.put("product_id",i.id);h.put("barcode",i.barcode);h.put("product_name",i.name);h.put("change_type","SALE");h.put("before_qty",i.stock);h.put("after_qty",i.stock-i.qty);h.put("difference",-i.qty);h.put("reference",code);h.put("created_at",now);db.insertOrThrow("stock_history",null,h);
            }
            ContentValues ledger=new ContentValues();ledger.put("bill_id",billId);ledger.put("bill_code",code);ledger.put("sale_type",credit?"CREDIT":"CASH");ledger.put("event_type","SALE");ledger.put("subtotal_satang",sub);ledger.put("fee_satang",fee);ledger.put("total_satang",total);ledger.put("created_at",now);db.insertOrThrow("sales_ledger",null,ledger);
            db.setTransactionSuccessful();saved=true;
        }catch(Exception e){error=e.getMessage()==null?"บันทึกการขายไม่สำเร็จ":e.getMessage();saved=false;}
        finally{
            if(db!=null&&transactionStarted){try{db.endTransaction();}catch(Exception e){saved=false;error="ยืนยันธุรกรรมไม่สำเร็จ: "+(e.getMessage()==null?"ข้อผิดพลาดฐานข้อมูล":e.getMessage());}}
            saleInProgress=false;
        }
        if(saved){
            cart.clear();StockAlertManager.check(this);
            new AlertDialog.Builder(this).setTitle("บันทึกการขายสำเร็จ").setMessage("เลขที่บิล "+code+"\nยอดสินค้า "+PosDatabase.money(sub)+" บาท\n"+(credit?"ค่าบริการ 5% "+PosDatabase.money(fee)+" บาท\nยอดหนี้ "+PosDatabase.money(total)+" บาท":"รับเงิน "+PosDatabase.money(cash)+" บาท\nเงินทอน "+PosDatabase.money(cash-total)+" บาท")).setPositiveButton("ตกลง",(d,w)->home()).show();
        }else new AlertDialog.Builder(this).setTitle("บันทึกไม่สำเร็จ").setMessage(error==null?"บันทึกการขายไม่สำเร็จ":error).setPositiveButton("ตกลง",null).show();
    }
}