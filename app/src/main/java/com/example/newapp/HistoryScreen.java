package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.LinearLayout;

public class HistoryScreen extends Activity {
    private PosDatabase helper; private LinearLayout root;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);home();}
    private void home(){PosUi.resetNavigation(this);root=PosUi.page(this,"ประวัติการขาย");
        PosUi.button(this,root,"บิลที่ชำระแล้ว",v->listBills("PAID"));
        PosUi.button(this,root,"บิลค้างชำระ",v->listBills("UNPAID"));
        PosUi.button(this,root,"บัญชีลูกค้าเชื่อทั้งหมด",v->customers());
        PosUi.button(this,root,"สรุปยอดขายสุทธิ / ยอดคืน / ยอดหนี้",v->summary());
        PosUi.back(this,root);
    }
    private void listBills(String status){
        root=PosUi.page(this,status.equals("PAID")?"บิลที่ชำระแล้ว":"บิลค้างชำระ");
        Cursor c=helper.getReadableDatabase().query("bills",new String[]{"bill_code","total_satang","created_at","id","sale_type","subtotal_satang"},"status=?",new String[]{status},null,null,"created_at DESC");
        while(c.moveToNext()){
            final long id=c.getLong(3);long amount=c.getLong(1);String type=c.getString(4);String amountLabel="ยอดบิล";
            if("PAID".equals(status)&&"CREDIT".equals(type)&&ReceiptService.isFeeWaived(helper,id)){amount=c.getLong(5);amountLabel="รับจริง";}
            PosUi.button(this,root,c.getString(0)+" • "+amountLabel+" "+PosDatabase.money(amount)+" บาท • "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(2))),v->details(id));
        }
        c.close();PosUi.back(this,root);
    }
    private void details(long id){
        root=PosUi.page(this,"รายละเอียดบิล");
        try{
            ReceiptService.BillReceipt receipt=ReceiptService.load(helper,id);
            PosUi.line(this,root,ReceiptService.formatBill(receipt));
        }catch(Exception e){
            PosUi.line(this,root,"เปิดรายละเอียดบิลไม่สำเร็จ: "+
                    (e.getMessage()==null?"ข้อมูลบิลไม่ถูกต้อง":e.getMessage()));
        }
        PosUi.back(this,root);
    }
    private void customers(){
        root=PosUi.page(this,"บัญชีลูกค้าเชื่อทั้งหมด");
        java.util.ArrayList<ReportingService.CustomerDue> customers=ReportingService.unpaidCustomers(helper);
        if(customers.isEmpty())PosUi.line(this,root,"ไม่มีลูกค้าที่มียอดค้างชำระ");
        for(ReportingService.CustomerDue customer:customers){
            PosUi.button(this,root,customer.name+" — "+PosDatabase.money(customer.dueSatang)+" บาท",
                    v->customerBills(customer.id,customer.name));
        }
        PosUi.back(this,root);
    }

    private void customerBills(long customerId,String name){
        root=PosUi.page(this,name+" • บิลค้างชำระ");
        java.util.ArrayList<ReportingService.BillDue> bills=ReportingService.unpaidBills(helper,customerId);
        if(bills.isEmpty())PosUi.line(this,root,"ไม่มีบิลค้างชำระ");
        for(ReportingService.BillDue bill:bills){
            PosUi.line(this,root,bill.code+" • "+PosDatabase.money(bill.amountSatang)+" บาท • "+
                    new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault())
                            .format(new java.util.Date(bill.createdAt)));
        }
        PosUi.back(this,root);
    }

    private void summary(){
        ReportingService.Summary s=ReportingService.retainedSummary(helper);
        root=PosUi.page(this,"สรุปยอดขายและหนี้สิน");
        PosUi.line(this,root,"หมายเหตุ: ยอดขายรวมใช้ข้อมูลบัญชีที่ยังเก็บไว้ตามนโยบายสำรองประวัติ");
        PosUi.line(this,root,"ยอดขายสินค้าเดิม: "+PosDatabase.money(s.grossSalesSatang)+" บาท");
        PosUi.line(this,root,"ยอดคืนสินค้า: "+PosDatabase.money(s.returnsSatang)+" บาท");
        PosUi.line(this,root,"ยอดยกเลิกบิล: "+PosDatabase.money(s.cancellationsSatang)+" บาท");
        PosUi.line(this,root,"ยอดค่าบริการสุทธิ: "+PosDatabase.money(s.netFeesSatang)+" บาท");
        PosUi.line(this,root,"ยอดขายสุทธิจากข้อมูลที่เก็บไว้: "+PosDatabase.money(s.netSalesSatang)+" บาท");
        PosUi.line(this,root,"ยอดหนี้คงค้างปัจจุบัน: "+PosDatabase.money(s.outstandingDebtSatang)+" บาท");
        PosUi.back(this,root);
    }

}