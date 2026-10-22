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
        Cursor debt=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN sale_type='CREDIT' AND ? >= created_at AND ?-created_at<=? "+
                "THEN subtotal_satang ELSE total_satang END),0) FROM bills WHERE status='UNPAID'",
                new String[]{String.valueOf(PosDatabase.now()),String.valueOf(PosDatabase.now()),String.valueOf(FEE_WAIVER_WINDOW_MS)});
        long due=0;
        try { if(debt.moveToFirst())due=debt.getLong(0); } finally { debt.close(); }
        return new Summary(gross,returns,cancellations,fees,net,due);
    }
}