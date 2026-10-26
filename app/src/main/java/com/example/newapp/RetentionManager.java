package com.example.newapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.util.Calendar;

/** Type-specific retention. Unpaid bills and the independent sales ledger are protected. */
final class RetentionManager {
    private RetentionManager(){}
    static void run(Context context){
        PosDatabase helper=PosDatabase.get(context);
        long now=PosDatabase.now();
        long detailCutoff=now-30L*86400000L;
        Calendar operationsCalendar=AppClock.calendar();operationsCalendar.setTimeInMillis(now);operationsCalendar.add(Calendar.MONTH,-3);long operationsCutoff=operationsCalendar.getTimeInMillis();
        Calendar calendar=AppClock.calendar();calendar.setTimeInMillis(now);calendar.add(Calendar.MONTH,-15);
        long ledgerCutoff=calendar.getTimeInMillis();
        try{
            SQLiteDatabase db=helper.getWritableDatabase();
            PosTransactions.execute(db,transaction->{
                String expired="((status='PAID' AND paid_at IS NOT NULL AND paid_at<?) OR (status IN ('CANCELLED','RETURNED') AND archive_until IS NOT NULL AND archive_until<?))";
                transaction.delete("payments","bill_id IN (SELECT id FROM bills WHERE "+expired+")",new String[]{String.valueOf(detailCutoff),String.valueOf(now)});
                transaction.delete("bills",expired,new String[]{String.valueOf(detailCutoff),String.valueOf(now)});
                transaction.delete("stock_history","created_at<? AND change_type!='COUNT'",new String[]{String.valueOf(operationsCutoff)});
                transaction.delete("price_history","created_at<?",new String[]{String.valueOf(operationsCutoff)});
                transaction.delete("payment_history","created_at<?",new String[]{String.valueOf(operationsCutoff)});
                transaction.delete("sales_ledger_items","created_at<?",new String[]{String.valueOf(ledgerCutoff)});
                transaction.delete("sales_ledger","created_at<?",new String[]{String.valueOf(ledgerCutoff)});
                return null;
            });
        }catch(Exception ignored){
            // Keep the app usable if storage is full; the shared transaction runner rolls back partial cleanup.
        }
    }
}
