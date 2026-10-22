package com.example.newapp;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Shared bill/receipt data and formatting rules for sales, history, and credit-payment screens. */
final class ReceiptService {
    static final class Item {
        final String barcode,name,unit;
        final int quantity;
        final long unitPriceSatang,lineTotalSatang;
        Item(String barcode,String name,String unit,int quantity,long unitPriceSatang,long lineTotalSatang) {
            this.barcode=barcode;this.name=name;this.unit=unit;this.quantity=quantity;
            this.unitPriceSatang=unitPriceSatang;this.lineTotalSatang=lineTotalSatang;
        }
    }
    static final class BillReceipt {
        final long id,subtotalSatang,feeSatang,totalSatang,createdAt,cashReceivedSatang,changeSatang;
        final String code,saleType,status,customerName;
        final boolean feeWaived;
        final ArrayList<Item> items;
        BillReceipt(long id,String code,String saleType,String status,String customerName,long subtotal,long fee,
                    long total,long createdAt,long cashReceived,long change,boolean feeWaived,ArrayList<Item> items) {
            this.id=id;this.code=code;this.saleType=saleType;this.status=status;this.customerName=customerName;
            this.subtotalSatang=subtotal;this.feeSatang=fee;this.totalSatang=total;this.createdAt=createdAt;
            this.cashReceivedSatang=cashReceived;this.changeSatang=change;this.feeWaived=feeWaived;this.items=items;
        }
        long actualPayableSatang(){return feeWaived?subtotalSatang:totalSatang;}
    }
    private ReceiptService() {}

    static boolean isFeeWaived(PosDatabase helper,long billId) {
        Cursor c=helper.getReadableDatabase().query("sales_ledger",new String[]{"id"},
                "bill_id=? AND event_type='CREDIT_FEE_WAIVED'",new String[]{String.valueOf(billId)},
                null,null,null,"1");
        try{return c.moveToFirst();}finally{c.close();}
    }

    static BillReceipt load(PosDatabase helper,long billId) {
        SQLiteDatabase db=helper.getReadableDatabase();
        Cursor c=db.rawQuery("SELECT b.id,b.bill_code,b.sale_type,b.status,COALESCE(c.full_name,''),"+
                "b.subtotal_satang,b.fee_satang,b.total_satang,b.created_at,b.cash_received_satang,b.change_satang "+
                "FROM bills b LEFT JOIN customers c ON c.id=b.customer_id WHERE b.id=?",
                new String[]{String.valueOf(billId)});
        String code,type,status,customer;long subtotal,fee,total,created,cash,change;boolean waived;
        try {
            if(!c.moveToFirst())throw new IllegalStateException("ไม่พบบิลนี้");
            code=c.getString(1);type=c.getString(2);status=c.getString(3);customer=c.getString(4);
            subtotal=c.getLong(5);fee=c.getLong(6);total=c.getLong(7);created=c.getLong(8);
            cash=c.getLong(9);change=c.getLong(10);
            waived="CREDIT".equals(type)&&"PAID".equals(status)&&isFeeWaived(helper,billId);
        } finally {c.close();}
        Cursor items=db.query("bill_items",new String[]{"barcode","product_name","unit","quantity","unit_price_satang","line_total_satang"},
                "bill_id=?",new String[]{String.valueOf(billId)},null,null,"id ASC");
        ArrayList<Item> lines=new ArrayList<>();
        try {
            while(items.moveToNext())lines.add(new Item(items.getString(0),items.getString(1),items.getString(2),
                    items.getInt(3),items.getLong(4),items.getLong(5)));
        } finally {items.close();}
        return new BillReceipt(billId,code,type,status,customer,subtotal,fee,total,created,cash,change,waived,lines);
    }

    static String formatBill(BillReceipt bill) {
        StringBuilder out=new StringBuilder("ใบเสร็จ / รายละเอียดบิล\n");
        out.append("เลขที่บิล: ").append(bill.code).append('\n');
        if(!bill.customerName.isEmpty())out.append("ลูกค้า: ").append(bill.customerName).append('\n');
        out.append("ประเภท: ").append("CREDIT".equals(bill.saleType)?"ขายเชื่อ":"เงินสด").append('\n');
        out.append("สถานะ: ").append(statusLabel(bill.status)).append('\n');
        out.append("วันที่: ").append(new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault())
                .format(new Date(bill.createdAt))).append("\n\n");
        for(Item item:bill.items) {
            out.append(item.name).append(" ").append(item.quantity).append(" ").append(item.unit)
                    .append(" × ").append(PosDatabase.money(item.unitPriceSatang)).append(" = ")
                    .append(PosDatabase.money(item.lineTotalSatang)).append(" บาท\n");
        }
        out.append("\nยอดสินค้า: ").append(PosDatabase.money(bill.subtotalSatang)).append(" บาท\n");
        if("CREDIT".equals(bill.saleType)&&bill.feeSatang>0) {
            out.append("ค่าบริการเดิม 5%: ").append(PosDatabase.money(bill.feeSatang)).append(" บาท");
            if(bill.feeWaived)out.append(" (ยกเว้นแล้ว)");
            out.append('\n');
        }
        out.append("ยอดบิลเดิม: ").append(PosDatabase.money(bill.totalSatang)).append(" บาท\n");
        if(bill.feeWaived)out.append("ยอดรับจริงหลังยกเว้นค่าบริการ: ").append(PosDatabase.money(bill.actualPayableSatang())).append(" บาท\n");
        if("CASH".equals(bill.saleType)) {
            out.append("รับเงิน: ").append(PosDatabase.money(bill.cashReceivedSatang)).append(" บาท\n");
            out.append("เงินทอน: ").append(PosDatabase.money(bill.changeSatang)).append(" บาท\n");
        }
        return out.toString();
    }

    static String formatPayment(CreditPaymentService.Result result) {
        StringBuilder out=new StringBuilder("ใบรับชำระหนี้\n");
        for(CreditPaymentService.BillQuote bill:result.bills) {
            out.append("บิล ").append(bill.billCode).append(": รับชำระ ")
                    .append(PosDatabase.money(bill.payableSatang)).append(" บาท\n");
            if(bill.feeWaived&&bill.feeSatang>0)
                out.append("ยกเว้นค่าบริการ ").append(PosDatabase.money(bill.feeSatang)).append(" บาท\n");
        }
        out.append("รวมรับชำระ ").append(PosDatabase.money(result.totalSatang)).append(" บาท");
        return out.toString();
    }

    private static String statusLabel(String status) {
        if("PAID".equals(status))return "ชำระแล้ว";
        if("UNPAID".equals(status))return "ค้างชำระ";
        if("RETURNED".equals(status))return "คืนสินค้า";
        if("CANCELLED".equals(status))return "ยกเลิก";
        return status;
    }
}