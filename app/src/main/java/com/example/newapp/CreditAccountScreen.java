package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.widget.EditText;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.HashSet;

public class CreditAccountScreen extends Activity {
    private PosDatabase helper;
    private LinearLayout root;

    @Override protected void onCreate(Bundle state){super.onCreate(state);helper=PosDatabase.get(this);if(getIntent().getBooleanExtra("arrears_search_mode",false))arrearsSearch();else home();}

    private void arrearsSearch(){
        root=PosUi.page(this,"ชำระบิลค้างชำระ");
        PosUi.line(this,root,"กรอกชื่อและนามสกุลเพื่อค้นหาลูกค้าที่มียอดค้างชำระ");
        EditText first=PosUi.input(this,root,"ชื่อ");
        EditText last=PosUi.input(this,root,"นามสกุล");
        PosUi.button(this,root,"ค้นหาลูกค้า",v->{
            String f=PosUi.str(first),l=PosUi.str(last);
            if(f.isEmpty()){first.setError("กรอกชื่อ");return;}
            if(l.isEmpty()){last.setError("กรอกนามสกุล");return;}
            showCustomerMatches(f,l);
        });
        PosUi.back(this,root);
    }

    private String normalizeName(String value){
        return value==null?"":value.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+","").trim();
    }

    private boolean approximateNameMatch(String input,String stored){
        String q=normalizeName(input),s=normalizeName(stored);
        if(q.isEmpty()||s.isEmpty())return false;
        if(s.contains(q)||q.contains(s))return true;
        if(q.length()<3||s.length()<3)return false;
        int limit=Math.max(1,Math.min(2,Math.max(q.length(),s.length())/4));
        return editDistance(q,s,limit)<=limit;
    }

    private int editDistance(String a,String b,int stopAt){
        if(Math.abs(a.length()-b.length())>stopAt)return stopAt+1;
        int[] prev=new int[b.length()+1],curr=new int[b.length()+1];
        for(int j=0;j<=b.length();j++)prev[j]=j;
        for(int i=1;i<=a.length();i++){
            curr[0]=i;int rowMin=curr[0];
            for(int j=1;j<=b.length();j++){
                curr[j]=Math.min(Math.min(curr[j-1]+1,prev[j]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
                rowMin=Math.min(rowMin,curr[j]);
            }
            if(rowMin>stopAt)return stopAt+1;
            int[] swap=prev;prev=curr;curr=swap;
        }
        return prev[b.length()];
    }

    private void showCustomerMatches(String first,String last){
        root=PosUi.page(this,"เลือกลูกค้า");
        Cursor c=helper.getReadableDatabase().rawQuery(
            "SELECT c.id,c.first_name,c.last_name,COUNT(b.id),COALESCE(SUM(b.total_satang),0) FROM customers c JOIN bills b ON b.customer_id=c.id WHERE b.status='UNPAID' GROUP BY c.id,c.first_name,c.last_name ORDER BY c.first_name COLLATE NOCASE,c.last_name COLLATE NOCASE,c.id",null);
        int found=0;
        while(c.moveToNext()){
            long id=c.getLong(0);String f=c.getString(1),l=c.getString(2);int count=c.getInt(3);long due=c.getLong(4);
            if(!approximateNameMatch(first,f)||!approximateNameMatch(last,l))continue;
            found++;String name=PosDatabase.customerName(f,l);
            PosUi.button(this,root,"#"+id+" — "+name+"\nค้าง "+count+" บิล • "+PosDatabase.money(due)+" บาท",v->customerBills(id,name));
        }
        c.close();
        if(found==0)PosUi.line(this,root,"ไม่พบลูกค้าที่ตรงกับชื่อใกล้เคียงและมียอดค้างชำระ กรุณาตรวจสอบการสะกด");
        PosUi.back(this,root);
    }

    private void home(){
        root=PosUi.page(this,"บัญชีลูกค้าสินเชื่อ");
        PosUi.button(this,root,"จัดการข้อมูลลูกค้า",v->customerDirectory());
        Cursor c=helper.getReadableDatabase().rawQuery("SELECT c.id,c.first_name,c.last_name,SUM(b.total_satang) due,MIN(b.created_at) oldest FROM customers c JOIN bills b ON b.customer_id=c.id WHERE b.status='UNPAID' GROUP BY c.id ORDER BY due DESC,oldest ASC",null);
        boolean found=false;
        while(c.moveToNext()){
            found=true;long id=c.getLong(0);String name=PosDatabase.customerName(c.getString(1),c.getString(2));long due=c.getLong(3);
            PosUi.button(this,root,name+" — "+PosDatabase.money(due)+" บาท",v->customerBills(id,name));
        }
        c.close();
        if(!found)PosUi.line(this,root,"ไม่มีลูกค้าที่มียอดค้างชำระ");
        PosUi.back(this,root);
    }

    private void customerDirectory(){
        root=PosUi.page(this,"จัดการข้อมูลลูกค้า");
        Cursor c=helper.getReadableDatabase().query("customers",new String[]{"id","first_name","last_name"},null,null,null,null,"first_name COLLATE NOCASE ASC,last_name COLLATE NOCASE ASC,id ASC");
        boolean found=false;
        while(c.moveToNext()){
            found=true;long id=c.getLong(0);String first=c.getString(1),last=c.getString(2);
            String name=PosDatabase.customerName(first,last);
            if(name.isEmpty())name="ข้อมูลชื่อยังไม่ครบ";
            PosUi.button(this,root,"#"+id+" — "+name,v->editCustomer(id));
        }
        c.close();
        if(!found)PosUi.line(this,root,"ยังไม่มีข้อมูลลูกค้า");
        PosUi.back(this,root);
    }

    private void editCustomer(long customerId){
        Cursor c=helper.getReadableDatabase().query("customers",new String[]{"first_name","last_name"},"id=?",new String[]{String.valueOf(customerId)},null,null,null);
        if(!c.moveToFirst()){c.close();PosUi.result(this,false,"ไม่พบรหัสลูกค้า",this::customerDirectory);return;}
        String oldFirst=c.getString(0),oldLast=c.getString(1);c.close();
        root=PosUi.page(this,"แก้ไขข้อมูลลูกค้า #"+customerId);
        EditText first=PosUi.input(this,root,"ชื่อ");first.setText(oldFirst==null?"":oldFirst);
        EditText last=PosUi.input(this,root,"นามสกุล");last.setText(oldLast==null?"":oldLast);
        PosUi.button(this,root,"บันทึกข้อมูลลูกค้า",v->{
            String f=first.getText().toString().trim(),l=last.getText().toString().trim();
            if(f.isEmpty()){first.setError("กรอกชื่อ");return;}
            if(l.isEmpty()){last.setError("กรอกนามสกุล");return;}
            boolean saved=false;String error=null;
            try{
                SQLiteDatabase db=helper.getWritableDatabase();
                PosTransactions.execute(db,transaction->{
                    ContentValues values=new ContentValues();values.put("first_name",f);values.put("last_name",l);
                    if(transaction.update("customers",values,"id=?",new String[]{String.valueOf(customerId)})!=1)throw new IllegalStateException("บันทึกข้อมูลลูกค้าไม่สำเร็จ");
                    PosEvents.customerProfileUpdated(transaction,customerId,
                            PosDatabase.customerName(oldFirst,oldLast),PosDatabase.customerName(f,l));
                    return null;
                });
                saved=true;
            }catch(Exception e){error=e.getMessage();}
            PosUi.result(this,saved,saved?"บันทึกข้อมูลลูกค้าเรียบร้อย":(error==null?"บันทึกข้อมูลลูกค้าไม่สำเร็จ":error),saved?this::customerDirectory:null);
        });
        PosUi.back(this,root);
    }

    private void customerBills(long customerId,String name){
        root=PosUi.page(this,name+" • บัญชีลูกหนี้");
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"id","bill_code","total_satang","created_at"},"customer_id=? AND status='UNPAID'",new String[]{String.valueOf(customerId)},null,null,"created_at ASC");
        ArrayList<Long> allIds=new ArrayList<>();
        HashSet<Long> selected=new HashSet<>();
        long dueTotal=0;
        while(c.moveToNext()){
            long id=c.getLong(0),amount=c.getLong(2);String code=c.getString(1);long created=c.getLong(3);
            allIds.add(id);dueTotal=Math.addExact(dueTotal,amount);
            CheckBox box=new CheckBox(this);
            box.setText(code+" • "+PosDatabase.money(amount)+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy",java.util.Locale.getDefault()).format(new java.util.Date(created)));
            box.setChecked(false);box.setOnCheckedChangeListener((button,checked)->{if(checked)selected.add(id);else selected.remove(id);});
            root.addView(box,new LinearLayout.LayoutParams(-1,-2));
        }
        c.close();
        PosUi.line(this,root,"ยอดหนี้รวม "+PosDatabase.money(dueTotal)+" บาท");
        if(!allIds.isEmpty()){
            PosUi.button(this,root,"ชำระเต็มจำนวนทุกบิล",v->preparePayment(customerId,allIds));
            PosUi.button(this,root,"ชำระเฉพาะบิลที่เลือก",v->{
                ArrayList<Long> chosen=new ArrayList<>(selected);java.util.Collections.sort(chosen);
                if(chosen.isEmpty()){new AlertDialog.Builder(this).setMessage("เลือกอย่างน้อยหนึ่งบิล").setPositiveButton("ตกลง",null).show();return;}
                preparePayment(customerId,chosen);
            });
        }else PosUi.line(this,root,"ไม่มีบิลค้างชำระ");
        PosUi.back(this,root);
    }

    private long payableTotal(long customerId,ArrayList<Long> ids){
        long total=0,now=PosDatabase.now();SQLiteDatabase db=helper.getReadableDatabase();
        for(long id:ids){
            Cursor c=db.query("bills",new String[]{"sale_type","subtotal_satang","total_satang","created_at"},"id=? AND customer_id=? AND status='UNPAID'",new String[]{String.valueOf(id),String.valueOf(customerId)},null,null,null);
            if(c.moveToFirst()){
                String type=c.getString(0);long subtotal=c.getLong(1),amount=c.getLong(2),created=c.getLong(3);
                total=Math.addExact(total,PosBusinessRules.paymentDue(type,subtotal,amount,created,now));
            }
            c.close();
        }
        return total;
    }

    private void preparePayment(long customerId,ArrayList<Long> ids){
        try{payAll(customerId,ids,payableTotal(customerId,ids));}
        catch(ArithmeticException e){new AlertDialog.Builder(this).setMessage("ยอดรวมเกินขอบเขตที่ระบบรองรับ กรุณาแบ่งชำระเป็นชุดเล็กลง").setPositiveButton("ตกลง",null).show();}
    }

    private void payAll(long customerId,ArrayList<Long> ids,long total){
        cashPaymentScreen(customerId,ids,total);
    }

    private void cashPaymentScreen(long customerId,ArrayList<Long> ids,long total){
        root=PosUi.page(this,"รับชำระบิลค้างชำระ");
        PosUi.line(this,root,"ยอดที่ต้องชำระ "+PosDatabase.money(total)+" บาท");
        EditText received=PosUi.input(this,root,"รับเงินสด (บาท)");
        PosUi.button(this,root,"ยืนยันรับเงินสด",v->{
            long cash;
            try{cash=PosUi.satang(received);}catch(Exception e){received.setError("กรอกจำนวนเงินให้ถูกต้อง");return;}
            if(cash<total){received.setError("เงินรับไม่พอ");return;}
            long change;
            try{change=Math.subtractExact(cash,total);}catch(ArithmeticException e){received.setError("จำนวนเงินเกินขอบเขตที่ระบบรองรับ");return;}
            new AlertDialog.Builder(this).setTitle("ยืนยันรับชำระหนี้")
                .setMessage("ยอดที่ต้องชำระ "+PosDatabase.money(total)+" บาท\\nรับเงิน "+PosDatabase.money(cash)+" บาท\\nเงินทอน "+PosDatabase.money(change)+" บาท")
                .setNegativeButton("กลับไปแก้ไข",null)
                .setPositiveButton("ยืนยันรับเงิน",(dialog,which)->recordPayment(customerId,ids,total,cash,change)).show();
        });
        PosUi.back(this,root);
    }

    private void recordPayment(long customerId,ArrayList<Long> ids,long total,long cashReceived,long change){
        boolean paid=false;String error=null;
        StringBuilder receipt=new StringBuilder("ใบรับชำระเงิน\n");
        try{
            SQLiteDatabase db=helper.getWritableDatabase();
            PosTransactions.execute(db,transaction->{
                long now=PosDatabase.now();long[] actualTotal={0L};
                for(long id:ids){
                    Cursor c=transaction.query("bills",new String[]{"bill_code","sale_type","subtotal_satang","fee_satang","total_satang","status"},"id=? AND customer_id=?",new String[]{String.valueOf(id),String.valueOf(customerId)},null,null,null);
                    if(!c.moveToFirst()){c.close();throw new IllegalStateException("ไม่พบบิล");}
                    String code=c.getString(0),type=c.getString(1),status=c.getString(5);long sub=c.getLong(2),fee=c.getLong(3),amount=c.getLong(4);c.close();
                    if(!"UNPAID".equals(status))throw new IllegalStateException("บิลนี้ถูกชำระไปแล้ว");
                    long created=getBillTime(transaction,id);boolean waiveFee="CREDIT".equals(type)&&PosBusinessRules.withinCreditFeeWindow(created,now);
                    long collected=PosBusinessRules.paymentDue(type,sub,amount,created,now);actualTotal[0]=Math.addExact(actualTotal[0],collected);
                    ContentValues p=new ContentValues();p.put("status","PAID");p.put("paid_at",now);
                    if(transaction.update("bills",p,"id=? AND customer_id=? AND status='UNPAID'",new String[]{String.valueOf(id),String.valueOf(customerId)})!=1)throw new IllegalStateException("สถานะบิลเปลี่ยนแล้ว กรุณาตรวจสอบใหม่");
                    ContentValues pay=new ContentValues();pay.put("bill_id",id);pay.put("amount_satang",collected);pay.put("created_at",now);transaction.insertOrThrow("payments",null,pay);
                    PosEvents.payment(transaction,id,code,collected,cashReceived,change,now);
                    receipt.append("บิล ").append(code).append(" (บาร์โค้ดเดิม) รับชำระ ").append(PosDatabase.money(collected)).append(" บาท\n");
                    if(waiveFee)PosEvents.creditFeeWaived(transaction,id,code,type,fee,now);
                }
                if(actualTotal[0]!=total)throw new IllegalStateException("ยอดชำระเปลี่ยนระหว่างยืนยัน กรุณาตรวจสอบยอดใหม่ก่อนรับเงิน");
                return null;
            });
            paid=true;
        }catch(Exception e){error=e.getMessage()==null?"รับชำระไม่สำเร็จ":e.getMessage();paid=false;}
        if(paid)PosUi.result(this,true,"บันทึกรับชำระหนี้เรียบร้อย\n"+receipt+"ยอดรับเงินสด "+PosDatabase.money(cashReceived)+" บาท\nเงินทอน "+PosDatabase.money(change)+" บาท",this::arrearsSearch);
        else PosUi.result(this,false,"รับชำระไม่สำเร็จ: "+(error==null?"ข้อผิดพลาดฐานข้อมูล":error),null);
    }

    private long getBillTime(SQLiteDatabase db,long id){
        Cursor c=db.query("bills",new String[]{"created_at"},"id=?",new String[]{String.valueOf(id)},null,null,null);
        long created=c.moveToFirst()?c.getLong(0):0;c.close();return created;
    }
}