package com.example.newapp;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;

/** Shared reporting formulas and customer-debt queries used by multiple POS menus. */
final class ReportingService {
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

    static ArrayList<CustomerDue> unpaidCustomers(PosDatabase helper) {
        SQLiteDatabase db=helper.getReadableDatabase();
        Cursor c=db.rawQuery("SELECT c.id,c.full_name,COALESCE(SUM(b.total_satang),0),MIN(b.created_at) " +
                "FROM customers c JOIN bills b ON b.customer_id=c.id WHERE b.status='UNPAID' " +
                "GROUP BY c.id,c.full_name ORDER BY SUM(b.total_satang) DESC,MIN(b.created_at) ASC",null);
        ArrayList<CustomerDue> result=new ArrayList<>();
        try {
            while(c.moveToNext())result.add(new CustomerDue(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3)));
        } finally { c.close(); }
        return result;
    }

    static ArrayList<BillDue> unpaidBills(PosDatabase helper,long customerId) {
        SQLiteDatabase db=helper.getReadableDatabase();
        Cursor c=db.query("bills",new String[]{"id","bill_code","total_satang","created_at"},
                "customer_id=? AND status='UNPAID'",new String[]{String.valueOf(customerId)},
                null,null,"created_at ASC");
        ArrayList<BillDue> result=new ArrayList<>();
        try {
            while(c.moveToNext())result.add(new BillDue(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3)));
        } finally { c.close(); }
        return result;
    }

    static Summary allTimeSummary(PosDatabase helper) {
        SQLiteDatabase db=helper.getReadableDatabase();
        Cursor c=db.rawQuery("SELECT " +
                "COALESCE(SUM(CASE WHEN event_type='SALE' THEN subtotal_satang ELSE 0 END),0)," +
                "COALESCE(SUM(CASE WHEN event_type='RETURN' THEN -subtotal_satang ELSE 0 END),0)," +
                "COALESCE(SUM(CASE WHEN event_type='CANCEL' THEN -subtotal_satang ELSE 0 END),0)," +
                "COALESCE(SUM(fee_satang),0),COALESCE(SUM(total_satang),0) FROM sales_ledger",null);
        long gross=0,returns=0,cancellations=0,fees=0,net=0;
        try {
            if(c.moveToFirst()){gross=c.getLong(0);returns=c.getLong(1);cancellations=c.getLong(2);fees=c.getLong(3);net=c.getLong(4);}
        } finally { c.close(); }
        Cursor debt=db.rawQuery("SELECT COALESCE(SUM(total_satang),0) FROM bills WHERE status='UNPAID'",null);
        long due=0;
        try { if(debt.moveToFirst())due=debt.getLong(0); }
        finally { debt.close(); }
        return new Summary(gross,returns,cancellations,fees,net,due);
    }
}