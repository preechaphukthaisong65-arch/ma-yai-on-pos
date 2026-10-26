package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.LinearLayout;

public class HistoryScreen extends Activity {
    private static final int BILLS_PAGE_SIZE=100;
    private PosDatabase helper; private LinearLayout root; private int billsPage=0;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);PosAuth.requireOwner(this,this::home);}
    private void home(){root=PosUi.page(this,"ประวัติการขาย");
        PosUi.button(this,root,"รายการขายทั้งหมด",v->{billsPage=0;listBills();});
        PosUi.back(this,root);
    }
    private void listBills(){
        root=PosUi.page(this,"รายการขายทั้งหมด");
        Cursor countCursor=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM bills",null);
        int totalBills=countCursor.moveToFirst()?countCursor.getInt(0):0;countCursor.close();
        PosUi.line(this,root,"หน้า "+(billsPage+1)+" • บิลทั้งหมด "+totalBills);
        Cursor c=helper.getReadableDatabase().rawQuery("SELECT bill_code,total_satang,created_at,id,sale_type,subtotal_satang,status FROM bills ORDER BY created_at DESC LIMIT ? OFFSET ?",new String[]{String.valueOf(BILLS_PAGE_SIZE),String.valueOf(billsPage*BILLS_PAGE_SIZE)});
        while(c.moveToNext()){
            final long id=c.getLong(3);
            long amount=c.getLong(1);
            String type=c.getString(4),status=c.getString(6);
            String amountLabel="ยอดบิล";
            if("PAID".equals(status)&&"CREDIT".equals(type)&&feeWaived(id)){amount=c.getLong(5);amountLabel="รับจริง";}
            String statusLabel="PAID".equals(status)?"ชำระแล้ว":("UNPAID".equals(status)?"ค้างชำระ":("CANCELLED".equals(status)?"ยกเลิกแล้ว":("RETURNED".equals(status)?"คืนสินค้าแล้ว":status)));
            String label=c.getString(0)+" • "+statusLabel+" • "+amountLabel+" "+PosDatabase.money(amount)+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(2)));
            PosUi.button(this,root,label,v->details(id));
        }
        c.close();
        if(billsPage>0)PosUi.button(this,root,"← หน้าก่อน",v->{billsPage--;listBills();});
        if((long)(billsPage+1)*BILLS_PAGE_SIZE<totalBills)PosUi.button(this,root,"หน้าถัดไป →",v->{billsPage++;listBills();});
        PosUi.back(this,root);
    }
    private void details(long id){
        root=PosUi.page(this,"รายละเอียดบิล");
        StringBuilder receipt=new StringBuilder("ใบเสร็จ / MA YAI ON POS\n");
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"bill_code","sale_type","status","subtotal_satang","fee_satang","total_satang","created_at"},"id=?",new String[]{String.valueOf(id)},null,null,null);
        if(c.moveToFirst()){
            String code=c.getString(0),type=c.getString(1),status=c.getString(2);long subtotal=c.getLong(3),fee=c.getLong(4),total=c.getLong(5);boolean waived="CREDIT".equals(type)&&"PAID".equals(status)&&feeWaived(id);
            String date=new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(6)));
            PosUi.line(this,root,"เลขที่บิล: "+code);PosUi.line(this,root,"ประเภท: "+type);PosUi.line(this,root,"สถานะ: "+status);PosUi.line(this,root,"วันที่: "+date);PosUi.line(this,root,"ยอดสินค้า: "+PosDatabase.money(subtotal)+" บาท");
            receipt.append("เลขที่บิล: ").append(code).append("\nวันที่: ").append(date).append("\nประเภท: ").append(type).append("\nสถานะ: ").append(status).append("\nยอดสินค้า: ").append(PosDatabase.money(subtotal)).append(" บาท\n");
            if("CREDIT".equals(type)&&fee>0){String feeLine="ค่าบริการเดิม: "+PosDatabase.money(fee)+" บาท"+(waived?" (ยกเว้นแล้ว)":"");PosUi.line(this,root,feeLine);receipt.append(feeLine).append("\n");}
            PosUi.line(this,root,"ยอดบิลเดิม: "+PosDatabase.money(total)+" บาท");receipt.append("ยอดบิลเดิม: ").append(PosDatabase.money(total)).append(" บาท\n");
            if(waived){PosUi.line(this,root,"ยอดรับเงินจริง: "+PosDatabase.money(subtotal)+" บาท");receipt.append("ยอดรับเงินจริง: ").append(PosDatabase.money(subtotal)).append(" บาท\n");}
        }else{c.close();PosUi.line(this,root,"ไม่พบบิลนี้");PosUi.back(this,root);return;}c.close();
        receipt.append("------------------------------\nรายการสินค้า\n");
        Cursor it=helper.getReadableDatabase().query("bill_items",new String[]{"product_name","quantity","unit_price_satang","line_total_satang"},"bill_id=?",new String[]{String.valueOf(id)},null,null,null);
        while(it.moveToNext()){String line=it.getString(0)+" จำนวน "+it.getInt(1)+" × "+PosDatabase.money(it.getLong(2))+" = "+PosDatabase.money(it.getLong(3))+" บาท";PosUi.line(this,root,line);receipt.append(line).append("\n");}it.close();
        receipt.append("------------------------------\nขอบคุณที่ใช้บริการ");
        PosUi.button(this,root,"แชร์ / ส่งใบเสร็จ",v->{android.content.Intent send=new android.content.Intent(android.content.Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(android.content.Intent.EXTRA_SUBJECT,"ใบเสร็จ "+id);send.putExtra(android.content.Intent.EXTRA_TEXT,receipt.toString());startActivity(android.content.Intent.createChooser(send,"ส่งใบเสร็จ"));});
        PosUi.back(this,root);
    }
    private boolean feeWaived(long billId){
        Cursor c=helper.getReadableDatabase().query("sales_ledger",new String[]{"id"},"bill_id=? AND event_type='CREDIT_FEE_WAIVED'",new String[]{String.valueOf(billId)},null,null,null,"1");
        boolean found=c.moveToFirst();c.close();return found;
    }
    private void customers(){
        root=PosUi.page(this,"บัญชีลูกค้าเชื่อทั้งหมด");
        Cursor c=helper.getReadableDatabase().rawQuery("SELECT c.id,c.full_name,SUM(b.total_satang) due,MIN(b.created_at) oldest FROM customers c JOIN bills b ON b.customer_id=c.id WHERE b.status='UNPAID' GROUP BY c.id ORDER BY due DESC,oldest ASC",null);
        while(c.moveToNext()){long id=c.getLong(0);String customerName=c.getString(1);PosUi.button(this,root,customerName+" — "+PosDatabase.money(c.getLong(2))+" บาท",v->customerBills(id,customerName));}
        c.close();PosUi.back(this,root);
    }
    private void customerBills(long customerId,String name){
        root=PosUi.page(this,name+" • บิลค้างชำระ");
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"bill_code","total_satang","created_at"},"customer_id=? AND status='UNPAID'",new String[]{String.valueOf(customerId)},null,null,"created_at ASC");
        if(!c.moveToFirst())PosUi.line(this,root,"ไม่มีบิลค้างชำระ");
        else do{PosUi.line(this,root,c.getString(0)+" • "+PosDatabase.money(c.getLong(1))+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(2))));}while(c.moveToNext());
        c.close();PosUi.back(this,root);
    }
}