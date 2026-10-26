package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.LinearLayout;

public class HistoryScreen extends Activity {
    private PosDatabase helper; private LinearLayout root;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);OwnerSecurity.requireOwner(this,"ดูประวัติการขายและข้อมูลลูกค้า",this::home);} 
    private void home(){root=PosUi.page(this,"ประวัติการขาย");
        PosUi.button(this,root,"รายการขายทั้งหมด",v->listBills());
        PosUi.back(this,root);
    }
    private void listBills(){
        root=PosUi.page(this,"รายการขายทั้งหมด");
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"bill_code","total_satang","created_at","id","sale_type","subtotal_satang","status"},null,null,null,null,"created_at DESC","200");
        while(c.moveToNext()){
            final long id=c.getLong(3);
            long amount=c.getLong(1);
            String type=c.getString(4),status=c.getString(6);
            String amountLabel="ยอดบิล";
            if("PAID".equals(status)&&"CREDIT".equals(type)&&feeWaived(id)){amount=c.getLong(5);amountLabel="รับจริง";}
            String statusLabel="PAID".equals(status)?"ชำระแล้ว":"UNPAID".equals(status)?"ค้างชำระ":"CANCELLED".equals(status)?"ยกเลิกแล้ว":"RETURNED".equals(status)?"คืนสินค้าแล้ว":status;
            String label=c.getString(0)+" • "+statusLabel+" • "+amountLabel+" "+PosDatabase.money(amount)+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(2)));
            PosUi.button(this,root,label,v->details(id));
        }
        if(c.getCount()>=200)PosUi.line(this,root,"แสดง 200 บิลล่าสุด เพื่อป้องกันหน้าจอค้าง");
        c.close();PosUi.back(this,root);
    }
    private void details(long id){
        root=PosUi.page(this,"รายละเอียดบิล");
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"bill_code","sale_type","status","subtotal_satang","fee_satang","total_satang","created_at"},"id=?",new String[]{String.valueOf(id)},null,null,null);
        if(c.moveToFirst()){
            String code=c.getString(0),type=c.getString(1),status=c.getString(2);long subtotal=c.getLong(3),fee=c.getLong(4),total=c.getLong(5);boolean waived="CREDIT".equals(type)&&"PAID".equals(status)&&feeWaived(id);
            PosUi.line(this,root,"เลขที่บิล: "+code);PosUi.line(this,root,"ประเภท: "+type);PosUi.line(this,root,"สถานะ: "+status);PosUi.line(this,root,"ยอดสินค้า: "+PosDatabase.money(subtotal)+" บาท");
            if("CREDIT".equals(type)&&fee>0)PosUi.line(this,root,"ค่าบริการเดิม: "+PosDatabase.money(fee)+" บาท"+(waived?" (เจ้าของร้านอนุมัติยกเว้นแล้ว)":""));
            PosUi.line(this,root,"ยอดบิลเดิม: "+PosDatabase.money(total)+" บาท");
            if(waived)PosUi.line(this,root,"ยอดรับเงินจริง: "+PosDatabase.money(subtotal)+" บาท");
        }c.close();
        PosUi.button(this,root,"แชร์ใบเสร็จ",v->PosReceiptOutput.share(this,receiptText(id)));
        PosUi.button(this,root,"พิมพ์ใบเสร็จ",v->{try{PosReceiptOutput.print(this,"ใบเสร็จ POS",receiptText(id));}catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()==null?"เปิดระบบพิมพ์ไม่สำเร็จ":e.getMessage()).setPositiveButton("ตกลง",null).show();}});
        Cursor it=helper.getReadableDatabase().query("bill_items",new String[]{"product_name","quantity","unit_price_satang","line_total_satang"},"bill_id=?",new String[]{String.valueOf(id)},null,null,null);
        while(it.moveToNext())PosUi.line(this,root,it.getString(0)+" จำนวน "+it.getInt(1)+" × "+PosDatabase.money(it.getLong(2))+" = "+PosDatabase.money(it.getLong(3)));it.close();PosUi.back(this,root);
    }

    private String receiptText(long id){
        StringBuilder out=new StringBuilder("ร้านแม่ใหญ่อ่อน POS\nใบเสร็จ / รายการขาย\n");
        Cursor b=helper.getReadableDatabase().rawQuery("SELECT b.bill_code,b.sale_type,b.status,b.subtotal_satang,b.fee_satang,b.total_satang,b.created_at,c.full_name FROM bills b LEFT JOIN customers c ON c.id=b.customer_id WHERE b.id=?",new String[]{String.valueOf(id)});
        if(b.moveToFirst()){
            String code=b.getString(0),type=b.getString(1),status=b.getString(2);long subtotal=b.getLong(3),fee=b.getLong(4),total=b.getLong(5),created=b.getLong(6);String customer=b.isNull(7)?"":b.getString(7);
            out.append("เลขที่บิล: ").append(code).append("\nวันที่: ").append(new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(created))).append("\nประเภท: ").append(type).append("\nสถานะ: ").append(status).append("\n");
            if(!customer.isEmpty())out.append("ลูกค้า: ").append(customer).append("\n");
            out.append("------------------------------\n");
            Cursor items=helper.getReadableDatabase().query("bill_items",new String[]{"product_name","quantity","unit_price_satang","line_total_satang"},"bill_id=?",new String[]{String.valueOf(id)},null,null,null);
            while(items.moveToNext())out.append(items.getString(0)).append(" x").append(items.getInt(1)).append(" @ ").append(PosDatabase.money(items.getLong(2))).append(" = ").append(PosDatabase.money(items.getLong(3))).append(" บาท\n");
            items.close();
            out.append("------------------------------\nยอดสินค้า: ").append(PosDatabase.money(subtotal)).append(" บาท\n");
            if("CREDIT".equals(type)&&fee>0){out.append("ค่าบริการเดิม 5%: ").append(PosDatabase.money(fee)).append(" บาท\n");if("PAID".equals(status)&&feeWaived(id))out.append("หมายเหตุ: เจ้าของร้านอนุมัติยกเว้นค่าบริการ\n");}
            out.append("ยอดบิลเดิม: ").append(PosDatabase.money(total)).append(" บาท\n");
            if("PAID".equals(status)&&"CREDIT".equals(type)&&feeWaived(id))out.append("ยอดรับจริง: ").append(PosDatabase.money(subtotal)).append(" บาท\n");
            out.append("ขอบคุณที่ใช้บริการ");
        }
        b.close();return out.toString();
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