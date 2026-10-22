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
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class SellScreen extends Activity {
    private static final class Item { long id,price;String barcode,name;int qty,stock; Item(long i,String b,String n,long p,int s){id=i;barcode=b;name=n;price=p;stock=s;qty=1;} }
    private PosDatabase helper; private final LinkedHashMap<Long,Item> cart=new LinkedHashMap<>(); private LinearLayout root; private boolean scanning=false; private boolean saleInProgress=false,duplicatePromptOpen=false;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);home();}
    private void home(){root=PosUi.page(this,"ขายสินค้า");PosUi.line(this,root,"เพิ่มสินค้าเข้าบิลด้วยการสแกนบาร์โค้ดเท่านั้น");
        PosUi.button(this,root,"เริ่มสแกนสินค้า",v->{cart.clear();scan();});PosUi.button(this,root,"บัญชีลูกค้าสินเชื่อ",v->startActivity(new Intent(SellScreen.this,CreditAccountScreen.class)));
    }
    private void scan(){
        // Keep the cart screen as the return destination after each barcode scan.
        scanning=true;
        PosUi.scan(this,"สแกนบาร์โค้ดสินค้า");
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        IntentResult result=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(result==null)return;
        scanning=false;
        if(result.getContents()!=null){
            addScanned(result.getContents());
        }else{
            showScanScreen();
        }
    }
    private void addScanned(String barcode){
        Cursor c=helper.getReadableDatabase().query("products",new String[]{"id","name","price_satang","stock"},"barcode=? AND active=1",new String[]{barcode},null,null,null,"1");
        if(!c.moveToFirst()){c.close();PosUi.speakThen(this,"ไม่พบสินค้าในคลังค่ะ",this::showScanScreen);return;}
        long id=c.getLong(0);String name=c.getString(1);long price=c.getLong(2);int stock=c.getInt(3);c.close();
        Item old=cart.get(id);
        if(stock<=0||(old!=null&&old.qty>=old.stock)){PosUi.speakThen(this,"สินค้า "+name+" ไม่มีสต๊อกเพียงพอค่ะ",this::showScanScreen);return;}
        if(old==null){Item item=new Item(id,barcode,name,price,stock);cart.put(id,item);}else old.qty++;
        try{subtotal();}catch(ArithmeticException e){if(old==null)cart.remove(id);else old.qty--;new AlertDialog.Builder(this).setTitle("ยอดเกินขอบเขต").setMessage("ยอดรวมสินค้าเกินขอบเขตที่ระบบรองรับ จึงไม่เพิ่มรายการนี้").setPositiveButton("ตกลง",(d,w)->showScanScreen()).show();return;}
        showScanScreen();
    }
    private long lineTotal(Item i){return Math.multiplyExact(i.price,(long)i.qty);}
    private long subtotal(){long s=0;for(Item i:cart.values())s=Math.addExact(s,lineTotal(i));return s;}
    private void addSideBySideButtons(LinearLayout parent,String leftLabel,android.view.View.OnClickListener leftAction,String rightLabel,android.view.View.OnClickListener rightAction){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button left=new Button(this);left.setText(leftLabel);left.setTextSize(17);left.setMinHeight(PosUi.dp(this,56));left.setOnClickListener(leftAction);
        Button right=new Button(this);right.setText(rightLabel);right.setTextSize(17);right.setMinHeight(PosUi.dp(this,56));right.setOnClickListener(rightAction);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);lp.rightMargin=PosUi.dp(this,6);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1f);rp.leftMargin=PosUi.dp(this,6);
        row.addView(left,lp);row.addView(right,rp);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private void showScanScreen(){
        root=PosUi.page(this,"สแกนสินค้า");
        addSideBySideButtons(root,"สแกนสินค้าเพิ่ม",v->scan(),"สแกนเสร็จสิ้น",v->finishScanning());
        if(cart.isEmpty()){
            PosUi.line(this,root,"ยังไม่มีสินค้าในตะกร้า");
            addClearCartButton();
            return;
        }
        for(Item i:cart.values()){
            android.widget.TextView line=new android.widget.TextView(this);
            line.setText(i.name+"  จำนวน "+i.qty+" × "+PosDatabase.money(i.price)+" = "+PosDatabase.money(lineTotal(i))+" บาท");
            line.setTextSize(14.4f);line.setTextColor(android.graphics.Color.DKGRAY);
            line.setPadding(0,PosUi.dp(this,3),0,PosUi.dp(this,3));
            root.addView(line,new LinearLayout.LayoutParams(-1,-2));
        }
        android.widget.TextView total=new android.widget.TextView(this);
        total.setText("รวมสินค้า "+PosDatabase.money(subtotal())+" บาท");total.setTextSize(14.4f);
        total.setTextColor(android.graphics.Color.DKGRAY);total.setPadding(0,PosUi.dp(this,6),0,PosUi.dp(this,6));
        root.addView(total,new LinearLayout.LayoutParams(-1,-2));
        addClearCartButton();
    }
    private void addClearCartButton(){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.END);
        Button clear=new Button(this);
        clear.setText("เคลียร์ตะกร้า");
        clear.setTextSize(16);
        clear.setMinHeight(PosUi.dp(this,52));
        clear.setOnClickListener(v->{
            if(cart.isEmpty()){
                new AlertDialog.Builder(this).setMessage("ตะกร้ายังว่างอยู่").setPositiveButton("ตกลง",null).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("เคลียร์ตะกร้า")
                    .setMessage("ต้องการลบสินค้าทั้งหมดในตะกร้า เพื่อเริ่มสแกนใหม่หรือไม่?")
                    .setNegativeButton("ยกเลิก",null)
                    .setPositiveButton("เคลียร์ตะกร้า",(dialog,which)->{
                        cart.clear();
                        showScanScreen();
                    }).show();
        });
        row.addView(clear,new LinearLayout.LayoutParams(-2,-2));
        LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);
        rowParams.topMargin=PosUi.dp(this,12);
        root.addView(row,rowParams);
    }
    private void finishScanning(){
        if(cart.isEmpty()){new AlertDialog.Builder(this).setMessage("ยังไม่มีสินค้าในตะกร้า").setPositiveButton("ตกลง",null).show();return;}
        StringBuilder summary=new StringBuilder("รายการสินค้าในตะกร้าทั้งหมด ");
        for(Item i:cart.values())summary.append(i.name).append(" จำนวน ").append(i.qty).append(" รายการ ");
        PosUi.speakThen(this,summary.toString(),this::paymentChoice);
    }
    private void paymentChoice(){
        root=PosUi.page(this,"เลือกวิธีขาย");
        addSideBySideButtons(root,"ชำระเงินสด",v->cashSale(),"ค้างชำระ",v->creditSale());
    }
    private void cashSale(){
        root=PosUi.page(this,"รับชำระเงินสด");PosUi.line(this,root,"ยอดที่ต้องชำระ "+PosDatabase.money(subtotal())+" บาท");
        EditText received=PosUi.input(this,root,"รับเงิน (บาท)");
        PosUi.button(this,root,"ยืนยันขายเงินสด",v->{try{long cash=PosUi.satang(received);if(cash<subtotal()){received.setError("เงินรับไม่พอ");return;}commitSale(false,-1,null,null,cash);}catch(Exception e){received.setError("กรอกจำนวนเงินให้ถูกต้อง");}});
        PosUi.back(this,root);
    }
    private void creditSale(){
        root=PosUi.page(this,"ขายเชื่อ");
        PosUi.line(this,root,"เลือกลูกค้าเดิมด้วยรหัส หรือกรอกชื่อและนามสกุลแยกช่องเพื่อสร้างลูกค้าใหม่");
        AutoCompleteTextView existing= new AutoCompleteTextView(this);
        existing.setSingleLine(true);existing.setTextSize(22);existing.setMinHeight(PosUi.dp(this,60));
        existing.setPadding(PosUi.dp(this,14),PosUi.dp(this,10),PosUi.dp(this,14),PosUi.dp(this,10));
        existing.setHint("เลือกลูกค้าเดิม (รหัสลูกค้า)");
        existing.setThreshold(1);
        java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        Map<String,Long> idsByLabel=new LinkedHashMap<>();
        Cursor names=helper.getReadableDatabase().query("customers",new String[]{"id","first_name","last_name"},null,null,null,null,"first_name COLLATE NOCASE ASC,last_name COLLATE NOCASE ASC,id ASC");
        while(names.moveToNext()){
            long id=names.getLong(0);String first=names.getString(1),last=names.getString(2);
            String name=PosDatabase.customerName(first,last);
            String label="#"+id+" — "+(first==null||first.trim().isEmpty()||last==null||last.trim().isEmpty()?"ข้อมูลชื่อ/นามสกุลไม่ครบ":name);
            labels.add(label);idsByLabel.put(label,id);
        }
        names.close();
        existing.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,labels));
        root.addView(existing,new LinearLayout.LayoutParams(-1,-2));
        PosUi.button(this,root,"ยืนยันขายเชื่อให้ลูกค้าเดิม",v->{
            Long id=idsByLabel.get(existing.getText().toString().trim());
            if(id==null){existing.setError("เลือกลูกค้าจากรายการ เพื่อยืนยันรหัสลูกค้า");return;}
            Cursor profile=helper.getReadableDatabase().query("customers",new String[]{"first_name","last_name"},"id=?",new String[]{String.valueOf(id)},null,null,null);
            boolean complete=profile.moveToFirst()&&!profile.getString(0).trim().isEmpty()&&!profile.getString(1).trim().isEmpty();
            profile.close();
            if(!complete){existing.setError("ข้อมูลลูกค้ายังไม่ครบ กรุณาไปที่บัญชีลูกค้าเพื่อแก้ไขชื่อและนามสกุล");return;}
            commitSale(true,id,null,null,0);
        });
        PosUi.line(this,root,"หรือสร้างลูกค้าใหม่");
        EditText first=PosUi.input(this,root,"ชื่อ");
        EditText last=PosUi.input(this,root,"นามสกุล");
        long sub,fee,total;
        try{sub=subtotal();fee=PosDatabase.creditFee(sub);total=Math.addExact(sub,fee);}
        catch(ArithmeticException e){PosUi.line(this,root,"ยอดเกินขอบเขตที่ระบบรองรับ จึงไม่สามารถขายเชื่อได้");PosUi.back(this,root);return;}
        PosUi.line(this,root,"ยอดสินค้า "+PosDatabase.money(sub)+" บาท");
        PosUi.line(this,root,"ค่าบริการ 5% "+PosDatabase.money(fee)+" บาท");
        PosUi.line(this,root,"ยอดหนี้รวม "+PosDatabase.money(total)+" บาท");
        PosUi.button(this,root,"สร้างลูกค้าใหม่และยืนยันขายเชื่อ",v->{
            String firstName=first.getText().toString().trim(),lastName=last.getText().toString().trim();
            if(firstName.isEmpty()){first.setError("กรอกชื่อ");return;}
            if(lastName.isEmpty()){last.setError("กรอกนามสกุล");return;}
            commitSale(true,-1,firstName,lastName,0);
        });
        PosUi.back(this,root);
    }
    private void commitSale(boolean credit,long customerId,String firstName,String lastName,long cash){
        if(saleInProgress||duplicatePromptOpen)return;
        if(cart.isEmpty()){new AlertDialog.Builder(this).setMessage("ไม่มีสินค้าในบิล").setPositiveButton("ตกลง",null).show();return;}
        if(credit&&(customerId<=0&&(firstName==null||firstName.trim().isEmpty()||lastName==null||lastName.trim().isEmpty()))){
            new AlertDialog.Builder(this).setMessage("กรอกชื่อและนามสกุลลูกค้าให้ครบก่อนขายเชื่อ").setPositiveButton("ตกลง",null).show();return;
        }
        String duplicate;
        try{duplicate=findDuplicate(credit,customerId,cash);}catch(Exception e){new AlertDialog.Builder(this).setMessage("ตรวจสอบบิลซ้ำไม่สำเร็จ จึงยังไม่บันทึกการขาย").setPositiveButton("ตกลง",null).show();return;}
        if(duplicate!=null){
            duplicatePromptOpen=true;
            new AlertDialog.Builder(this).setTitle("พบบิลที่อาจซ้ำ")
                    .setMessage("พบบิลที่มีรายละเอียดตรงกันใน 1 ชั่วโมงที่ผ่านมา ("+duplicate+") ต้องการสร้างบิลใหม่หรือไม่?")
                    .setNegativeButton("ยกเลิก",(d,w)->{duplicatePromptOpen=false;home();})
                    .setPositiveButton("ยืนยันว่าเป็นการขายจริง",(d,w)->{duplicatePromptOpen=false;saveSale(credit,customerId,firstName,lastName,cash);})
                    .setOnCancelListener(d->duplicatePromptOpen=false).show();
        }else saveSale(credit,customerId,firstName,lastName,cash);
    }
    private String findDuplicate(boolean credit,long customerId,long cash){
        SQLiteDatabase db=helper.getReadableDatabase();
        long now=PosDatabase.now(),sub=subtotal(),fee=credit?PosDatabase.creditFee(sub):0,total=Math.addExact(sub,fee),change=credit?0:Math.subtractExact(cash,sub);
        Cursor bills=db.rawQuery("SELECT b.id,b.bill_code,b.customer_id,b.cash_received_satang,b.change_satang,b.subtotal_satang,b.fee_satang,b.total_satang FROM bills b WHERE b.created_at>=? AND b.created_at<=? AND b.sale_type=? AND b.status IN ('PAID','UNPAID') ORDER BY b.created_at DESC",
                new String[]{String.valueOf(now-3600000L),String.valueOf(now),credit?"CREDIT":"CASH"});
        java.util.ArrayList<Item> current=new java.util.ArrayList<>(cart.values());
        current.sort((a,b)->a.barcode.compareTo(b.barcode));
        String match=null;
        while(bills.moveToNext()){
            if(credit&&(customerId<=0||bills.isNull(2)||bills.getLong(2)!=customerId))continue;
            if(bills.getLong(3)!=cash||bills.getLong(4)!=change||bills.getLong(5)!=sub||bills.getLong(6)!=fee||bills.getLong(7)!=total)continue;
            long billId=bills.getLong(0);
            Cursor items=db.query("bill_items",new String[]{"barcode","product_name","quantity","unit_price_satang","line_total_satang"},"bill_id=?",new String[]{String.valueOf(billId)},null,null,"barcode ASC");
            boolean same=true;int index=0;
            while(items.moveToNext()){
                if(index>=current.size()){same=false;break;}
                Item item=current.get(index++);
                if(!item.barcode.equals(items.getString(0))||!item.name.equals(items.getString(1))
                        ||item.qty!=items.getInt(2)||item.price!=items.getLong(3)||lineTotal(item)!=items.getLong(4)){same=false;break;}
            }
            items.close();
            if(same&&index==current.size()){match=bills.getString(1);break;}
        }
        bills.close();return match;
    }
    private void saveSale(boolean credit,long customerId,String firstName,String lastName,long cash){
        if(saleInProgress)return;
        if(cart.isEmpty()){new AlertDialog.Builder(this).setMessage("ไม่มีสินค้าในบิล").setPositiveButton("ตกลง",null).show();return;}
        saleInProgress=true;
        SQLiteDatabase db=null;boolean transactionStarted=false,saved=false;String error=null,code=null;
        long now=PosDatabase.now(),sub=subtotal(),fee=credit?PosDatabase.creditFee(sub):0,total=Math.addExact(sub,fee);
        try{
            db=helper.getWritableDatabase();db.beginTransaction();transactionStarted=true;
            code="B"+now;int suffix=1;
            while(true){Cursor existing=db.query("bills",new String[]{"id"},"bill_code=?",new String[]{code},null,null,null,"1");boolean used=existing.moveToFirst();existing.close();if(!used)break;code="B"+now+"-"+(suffix++);}
            Long selectedCustomerId=null;
            if(credit){
                if(customerId>0){
                    Cursor c=db.query("customers",new String[]{"first_name","last_name"},"id=?",new String[]{String.valueOf(customerId)},null,null,null);
                    if(!c.moveToFirst()){c.close();throw new IllegalStateException("ไม่พบรหัสลูกค้าที่เลือก");}
                    String storedFirst=c.getString(0),storedLast=c.getString(1);c.close();
                    if(storedFirst==null||storedFirst.trim().isEmpty()||storedLast==null||storedLast.trim().isEmpty())throw new IllegalStateException("ข้อมูลชื่อและนามสกุลลูกค้าไม่ครบ");
                    selectedCustomerId=customerId;
                }else{
                    String f=firstName==null?"":firstName.trim(),l=lastName==null?"":lastName.trim();
                    if(f.isEmpty()||l.isEmpty())throw new IllegalStateException("กรอกชื่อและนามสกุลลูกค้าให้ครบ");
                    ContentValues cv=new ContentValues();
                    cv.put("full_name",f+" "+l+" ["+java.util.UUID.randomUUID().toString()+"]");
                    cv.put("first_name",f);cv.put("last_name",l);cv.put("created_at",now);
                    selectedCustomerId=db.insertOrThrow("customers",null,cv);
                }
            }
            ContentValues bill=new ContentValues();bill.put("bill_code",code);if(selectedCustomerId!=null)bill.put("customer_id",selectedCustomerId);bill.put("sale_type",credit?"CREDIT":"CASH");bill.put("status",credit?"UNPAID":"PAID");bill.put("subtotal_satang",sub);bill.put("fee_satang",fee);bill.put("total_satang",total);bill.put("cash_received_satang",cash);bill.put("change_satang",credit?0:cash-total);bill.put("created_at",now);if(!credit)bill.put("paid_at",now);
            long billId=db.insertOrThrow("bills",null,bill);
            for(Item i:cart.values()){
                if(i.qty>i.stock)throw new IllegalStateException("สินค้า "+i.name+" มีสต๊อกไม่พอ");
                ContentValues p=new ContentValues();p.put("stock",i.stock-i.qty);p.put("updated_at",now);
                if(db.update("products",p,"id=? AND stock=?",new String[]{String.valueOf(i.id),String.valueOf(i.stock)})!=1)throw new IllegalStateException("สต๊อกเปลี่ยนระหว่างเปิดบิล กรุณาสแกนสินค้าและทำรายการใหม่");
                ContentValues it=new ContentValues();it.put("bill_id",billId);it.put("product_id",i.id);it.put("barcode",i.barcode);it.put("product_name",i.name);it.put("quantity",i.qty);it.put("unit_price_satang",i.price);it.put("line_total_satang",lineTotal(i));db.insertOrThrow("bill_items",null,it);
                ContentValues li=new ContentValues();li.put("bill_id",billId);li.put("bill_code",code);li.put("event_type","SALE");li.put("barcode",i.barcode);li.put("product_name",i.name);li.put("quantity",i.qty);li.put("unit_price_satang",i.price);li.put("line_total_satang",lineTotal(i));li.put("created_at",now);db.insertOrThrow("sales_ledger_items",null,li);
                ContentValues h=new ContentValues();h.put("product_id",i.id);h.put("barcode",i.barcode);h.put("product_name",i.name);h.put("change_type","SALE");h.put("before_qty",i.stock);h.put("after_qty",i.stock-i.qty);h.put("difference",-i.qty);h.put("reference",code);h.put("created_at",now);db.insertOrThrow("stock_history",null,h);
            }
            ContentValues ledger=new ContentValues();ledger.put("bill_id",billId);ledger.put("bill_code",code);ledger.put("sale_type",credit?"CREDIT":"CASH");ledger.put("event_type","SALE");ledger.put("subtotal_satang",sub);ledger.put("fee_satang",fee);ledger.put("total_satang",total);ledger.put("created_at",now);db.insertOrThrow("sales_ledger",null,ledger);
            PosDatabase.audit(db,"SALE", "BILL", code, "type="+(credit?"CREDIT":"CASH")+"; subtotal_satang="+sub+"; fee_satang="+fee+"; total_satang="+total);
            db.setTransactionSuccessful();saved=true;
        }catch(Exception e){error=e.getMessage()==null?"บันทึกการขายไม่สำเร็จ":e.getMessage();saved=false;}
        finally{
            if(db!=null&&transactionStarted){try{db.endTransaction();}catch(Exception e){saved=false;error="ยืนยันธุรกรรมไม่สำเร็จ: "+(e.getMessage()==null?"ข้อผิดพลาดฐานข้อมูล":e.getMessage());}}
            saleInProgress=false;
        }
        if(saved){
            cart.clear();StockAlertManager.check(this);
            PosUi.result(this,true,"เลขที่บิล "+code+"\nยอดสินค้า "+PosDatabase.money(sub)+" บาท\n"+(credit?"ค่าบริการ 5% "+PosDatabase.money(fee)+" บาท\nยอดหนี้ "+PosDatabase.money(total)+" บาท":"รับเงิน "+PosDatabase.money(cash)+" บาท\nเงินทอน "+PosDatabase.money(cash-total)+" บาท"),this::home);
        }else PosUi.result(this,false,error==null?"บันทึกการขายไม่สำเร็จ":error,null);
    }
}