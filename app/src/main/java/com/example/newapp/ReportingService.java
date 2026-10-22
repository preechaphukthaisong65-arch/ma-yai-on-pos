package com.example.newapp;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;

/** Shared sales, return, fee, and debt formulas used by POS menus and notifications. */
final class ReportingService {
    static final long FEE_WAIVER_WINDOW_MS=86400000L;

    static final class CustomerDue {
        final long id,dueSatang,oldestAt;
        final String name;
        CustomerDue(long id,String name,long dueSatang,long oldestAt) {
            this.id=id;this.name=name;this.dueSatang=dueSatang;this.oldestAt=oldestAt;
        }
    }
    static final class BillDue {
        final long id,amountSatang,createdAt;
        final String code;
        BillDue(long id,String code,long amountSatang,long createdAt) {
            this.id=id;this.code=code;this.amountSatang=amountSatang;this.createdAt=createdAt;
        }
    }
    static final class Summary {
        final long grossSalesSatang,returnsSatang,cancellationsSatang,netFeesSatang,netSalesSatang,outstandingDebtSatang;
        Summary(long gross,long returns,long cancellations,long fees,long net,long debt) {
            this.grossSalesSatang=gross;this.returnsSatang=returns;this.cancellationsSatang=cancellations;
            this.netFeesSatang=fees;this.netSalesSatang=net;this.outstandingDebtSatang=debt;
        }
    }
    private ReportingService() {}

    /** Amount currently payable for an unpaid bill, including the 24-hour fee-waiver rule. */
    static long payableNow(String saleType,long subtotal,long fee,long total,long createdAt,long now) {
        if(subtotal<0||fee<0||total<0||createdAt<0)throw new IllegalStateException("ยอดเงินหรือวันเวลาของบิลไม่ถูกต้อง");
        long expected;
        try{expected=Math.addExact(subtotal,fee);}catch(ArithmeticException e){throw new IllegalStateException("ยอดบิลเกินขอบเขตที่รองรับ");}
        if(expected!=total)throw new IllegalStateException("ยอดรวมบิลไม่ตรงกับยอดสินค้าและค่าบริการ");
        if("CREDIT".equals(saleType)&&now>=createdAt&&now-createdAt<=FEE_WAIVER_WINDOW_MS)return subtotal;
        return total;
    }

    static ArrayList<CustomerDue> unpaidCustomers(PosDatabase helper) {
        SQLiteDatabase db=helper.getReadableDatabase();
        long now=PosDatabase.now();
        Cursor c=db.rawQuery("SELECT c.id,c.full_name,"+
                "COALESCE(SUM(CASE WHEN b.sale_type='CREDIT' AND ? >= b.created_at AND ?-b.created_at<=? "+
                "THEN b.subtotal_satang ELSE b.total_satang END),0),MIN(b.created_at) "+
                "FROM customers c JOIN bills b ON b.customer_id=c.id WHERE b.status='UNPAID' "+
                "GROUP BY c.id,c.full_name ORDER BY 3 DESC,MIN(b.created_at) ASC",
                new String[]{String.valueOf(now),String.valueOf(now),String.valueOf(FEE_WAIVER_WINDOW_MS)});
        ArrayList<CustomerDue> result=new ArrayList<>();
        try {
            while(c.moveToNext())result.add(new CustomerDue(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3)));
        } finally { c.close(); }
        return result;
    }

    static ArrayList<BillDue> unpaidBills(PosDatabase helper,long customerId) {
        SQLiteDatabase db=helper.getReadableDatabase();
        long now=PosDatabase.now();
        Cursor c=db.query("bills",new String[]{"id","bill_code","sale_type","subtotal_satang","fee_satang","total_satang","created_at"},
                "customer_id=? AND status='UNPAID'",new String[]{String.valueOf(customerId)},
                null,null,"created_at ASC");
        ArrayList<BillDue> result=new ArrayList<>();
        try {
            while(c.moveToNext()) {
                long amount=payableNow(c.getString(2),c.getLong(3),c.getLong(4),c.getLong(5),c.getLong(6),now);
                result.add(new BillDue(c.getLong(0),c.getString(1),amount,c.getLong(6)));
            }
        } finally { c.close(); }
        return result;
    }

    /** Same overdue rule and balance formula as the credit-account menu. */
    static ArrayList<CustomerDue> overdueCustomers(PosDatabase helper,long cutoffMillis) {
        SQLiteDatabase db=helper.getReadableDatabase();
        long now=PosDatabase.now();
        Cursor c=db.rawQuery("SELECT c.id,c.full_name,"+
                "COALESCE(SUM(CASE WHEN b.sale_type='CREDIT' AND ? >= b.created_at AND ?-b.created_at<=? "+
                "THEN b.subtotal_satang ELSE b.total_satang END),0),MIN(b.created_at) "+
                "FROM customers c JOIN bills b ON b.customer_id=c.id WHERE b.status='UNPAID' "+
                "GROUP BY c.id,c.full_name HAVING MIN(b.created_at)<? ORDER BY 3 DESC,MIN(b.created_at) ASC",
                new String[]{String.valueOf(now),String.valueOf(now),String.valueOf(FEE_WAIVER_WINDOW_MS),String.valueOf(cutoffMillis)});
        ArrayList<CustomerDue> result=new ArrayList<>();
        try {
            while(c.moveToNext())result.add(new CustomerDue(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3)));
        } finally { c.close(); }
        return result;
    }

    /** Net sales recorded within [startInclusive, endExclusive), from the same ledger used by reports. */
    static long netSalesBetween(PosDatabase helper,long startInclusive,long endExclusive) {
        if(startInclusive<0||endExclusive<startInclusive)throw new IllegalArgumentException("ช่วงเวลารายงานไม่ถูกต้อง");
        Cursor c=helper.getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(total_satang),0) FROM sales_ledger "+
                "WHERE created_at>=? AND created_at<? AND event_type IN ('SALE','CANCEL','RETURN','CREDIT_FEE_WAIVED')",
                new String[]{String.valueOf(startInclusive),String.valueOf(endExclusive)});
        try { return c.moveToFirst()?c.getLong(0):0L; } finally { c.close(); }
    }

    static final class PeriodSummary {
        final long grossSalesSatang,netSubtotalSatang,netFeesSatang,netSalesSatang,soldQuantity,netQuantity,cashGrossSalesSatang;
        final int ledgerEntries,creditEntries;
        PeriodSummary(long gross,long subtotal,long fees,long net,int entries,int credits,long sold,long netQty,long cash) {
            grossSalesSatang=gross;netSubtotalSatang=subtotal;netFeesSatang=fees;netSalesSatang=net;
            ledgerEntries=entries;creditEntries=credits;soldQuantity=sold;netQuantity=netQty;cashGrossSalesSatang=cash;
        }
    }
    static final class ReturnCancel {
        final String billCode,eventType,saleType;
        final long subtotalSatang,feeSatang,totalSatang,createdAt,displayFeeSatang;
        ReturnCancel(String code,String event,String type,long subtotal,long fee,long total,long created,long displayFee) {
            billCode=code;eventType=event;saleType=type;subtotalSatang=subtotal;feeSatang=fee;
            totalSatang=total;createdAt=created;displayFeeSatang=displayFee;
        }
    }
    static final class ProductQuantity {
        final String name;
        final long quantity;
        ProductQuantity(String name,long quantity){this.name=name;this.quantity=quantity;}
    }

    static PeriodSummary periodSummary(PosDatabase helper,long fromInclusive,long toExclusive) {
        if(fromInclusive<0||toExclusive<fromInclusive)throw new IllegalArgumentException("ช่วงเวลารายงานไม่ถูกต้อง");
        SQLiteDatabase db=helper.getReadableDatabase();
        String[] range={String.valueOf(fromInclusive),String.valueOf(toExclusive)};
        Cursor c=db.rawQuery("SELECT "+
                "COALESCE(SUM(CASE WHEN event_type='SALE' THEN total_satang ELSE 0 END),0),"+
                "COALESCE(SUM(subtotal_satang),0),COALESCE(SUM(fee_satang),0),COALESCE(SUM(total_satang),0),"+
                "COUNT(*),COALESCE(SUM(CASE WHEN sale_type='CREDIT' THEN 1 ELSE 0 END),0) "+
                "FROM sales_ledger WHERE created_at>=? AND created_at<? "+
                "AND event_type IN ('SALE','CANCEL','RETURN','CREDIT_FEE_WAIVED')",range);
        long gross=0,subtotal=0,fees=0,net=0;int entries=0,credits=0;
        try{if(c.moveToFirst()){gross=c.getLong(0);subtotal=c.getLong(1);fees=c.getLong(2);net=c.getLong(3);entries=c.getInt(4);credits=c.getInt(5);}}
        finally{c.close();}
        Cursor sold=db.rawQuery("SELECT COALESCE(SUM(quantity),0) FROM sales_ledger_items WHERE created_at>=? AND created_at<? AND event_type='SALE'",range);
        long soldQty=0;try{if(sold.moveToFirst())soldQty=sold.getLong(0);}finally{sold.close();}
        Cursor netQtyCursor=db.rawQuery("SELECT COALESCE(SUM(quantity),0) FROM sales_ledger_items WHERE created_at>=? AND created_at<? AND event_type IN ('SALE','CANCEL','RETURN')",range);
        long netQty=0;try{if(netQtyCursor.moveToFirst())netQty=netQtyCursor.getLong(0);}finally{netQtyCursor.close();}
        Cursor cash=db.rawQuery("SELECT COALESCE(SUM(total_satang),0) FROM sales_ledger WHERE created_at>=? AND created_at<? AND sale_type='CASH' AND event_type='SALE'",range);
        long cashGross=0;try{if(cash.moveToFirst())cashGross=cash.getLong(0);}finally{cash.close();}
        return new PeriodSummary(gross,subtotal,fees,net,entries,credits,soldQty,netQty,cashGross);
    }

    static ArrayList<ReturnCancel> returnCancelEvents(PosDatabase helper) {
        SQLiteDatabase db=helper.getReadableDatabase();
        Cursor c=db.rawQuery("SELECT l.bill_code,l.event_type,l.subtotal_satang,l.fee_satang,l.total_satang,l.created_at,l.sale_type,"+
                "CASE WHEN l.sale_type='CREDIT' AND l.fee_satang=0 AND NOT EXISTS "+
                "(SELECT 1 FROM sales_ledger w WHERE w.bill_id=l.bill_id AND w.event_type='CREDIT_FEE_WAIVED') "+
                "THEN COALESCE((SELECT s.fee_satang FROM sales_ledger s WHERE s.bill_id=l.bill_id AND s.event_type='SALE'),0) "+
                "ELSE l.fee_satang END FROM sales_ledger l WHERE l.event_type IN ('RETURN','CANCEL') ORDER BY l.created_at DESC",null);
        ArrayList<ReturnCancel> result=new ArrayList<>();
        try{while(c.moveToNext())result.add(new ReturnCancel(c.getString(0),c.getString(1),c.getString(6),
                c.getLong(2),c.getLong(3),c.getLong(4),c.getLong(5),c.getLong(7)));}
        finally{c.close();}
        return result;
    }

    static ArrayList<ProductQuantity> topProducts(PosDatabase helper,boolean best,long now) {
        SQLiteDatabase db=helper.getReadableDatabase();long from=now-30L*86400000L;
        String eligible="WITH eligible AS (SELECT DISTINCT bill_id FROM sales_ledger_items WHERE event_type='SALE' AND created_at>=? AND created_at<?), "+
                "qty AS (SELECT barcode,SUM(quantity) AS qty FROM sales_ledger_items WHERE bill_id IN (SELECT bill_id FROM eligible) "+
                "AND event_type IN ('SALE','CANCEL','RETURN') GROUP BY barcode) ";
        String sql=best
                ?eligible+"SELECT i.product_name,SUM(i.quantity) AS qty FROM sales_ledger_items i "+
                "WHERE i.bill_id IN (SELECT bill_id FROM eligible) AND i.event_type IN ('SALE','CANCEL','RETURN') "+
                "GROUP BY i.barcode,i.product_name HAVING SUM(i.quantity)>0 ORDER BY qty DESC,i.product_name COLLATE NOCASE ASC LIMIT 20"
                :eligible+"SELECT p.name,MAX(0,COALESCE(q.qty,0)) AS qty FROM products p LEFT JOIN qty q ON q.barcode=p.barcode "+
                "WHERE p.active=1 ORDER BY qty ASC,p.name COLLATE NOCASE ASC LIMIT 20";
        Cursor c=db.rawQuery(sql,new String[]{String.valueOf(from),String.valueOf(now)});
        ArrayList<ProductQuantity> result=new ArrayList<>();
        try{while(c.moveToNext())result.add(new ProductQuantity(c.getString(0),c.getLong(1)));}
        finally{c.close();}
        return result;
    }

    /** Summarizes retained ledger data; older ledger rows are removed by the retention policy. */
    static Summary retainedSummary(PosDatabase helper) {
        SQLiteDatabase db=helper.getReadableDatabase();
        Cursor c=db.rawQuery("SELECT "+
                "COALESCE(SUM(CASE WHEN event_type='SALE' THEN subtotal_satang ELSE 0 END),0),"+
                "COALESCE(SUM(CASE WHEN event_type='RETURN' THEN -subtotal_satang ELSE 0 END),0),"+
                "COALESCE(SUM(CASE WHEN event_type='CANCEL' THEN -subtotal_satang ELSE 0 END),0),"+
                "COALESCE(SUM(fee_satang),0),COALESCE(SUM(total_satang),0) FROM sales_ledger",null);
        long gross=0,returns=0,cancellations=0,fees=0,net=0;
        try {
            if(c.moveToFirst()){gross=c.getLong(0);returns=c.getLong(1);cancellations=c.getLong(2);fees=c.getLong(3);net=c.getLong(4);}
        } finally { c.close(); }
        long now=PosDatabase.now();
        Cursor debt=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN sale_type='CREDIT' AND ? >= created_at AND ? - created_at <= ? "+
                "THEN subtotal_satang ELSE total_satang END),0) FROM bills WHERE status='UNPAID'",
                new String[]{String.valueOf(now),String.valueOf(now),String.valueOf(FEE_WAIVER_WINDOW_MS)});
        long due=0;
        try { if(debt.moveToFirst())due=debt.getLong(0); } finally { debt.close(); }
        return new Summary(gross,returns,cancellations,fees,net,due);
    }
}