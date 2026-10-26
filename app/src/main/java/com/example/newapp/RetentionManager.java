package com.example.newapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.util.Calendar;

/** Purges only old closed bill details; every report/audit history table is retained permanently. */
final class RetentionManager {
    private RetentionManager(){}
    static void run(Context context){
        PosDatabase helper=PosDatabase.get(context);
        long now=PosDatabase.now();
        long detailCutoff=PosHistoryPolicy.closedBillDetailCutoff(now);
        try{
            SQLiteDatabase db=helper.getWritableDatabase();
            PosTransactions.execute(db,transaction->{
                String expired="((status='PAID' AND paid_at IS NOT NULL AND paid_at<?) OR (status IN ('CANCELLED','RETURNED') AND archive_until IS NOT NULL AND archive_until<?))";
                transaction.delete("payments","bill_id IN (SELECT id FROM bills WHERE "+expired+")",new String[]{String.valueOf(detailCutoff),String.valueOf(now)});
                transaction.delete("bills",expired,new String[]{String.valueOf(detailCutoff),String.valueOf(now)});
                // Never purge stock_history, price_history, payment_history, sales_ledger,
                // sales_ledger_items, or audit_log: reports and audits depend on them.
                return null;
            });
        }catch(Exception ignored){
            // Keep the app usable if storage is full; the shared transaction runner rolls back partial cleanup.
        }
    }
}
