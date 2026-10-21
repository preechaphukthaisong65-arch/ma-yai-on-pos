package com.example.newapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.util.Calendar;

/** Type-specific retention. Unpaid bills and the independent sales ledger are protected. */
final class RetentionManager {
    private RetentionManager(){}
    static void run(Context context){
        PosDatabase helper=PosDatabase.get(context);
        SQLiteDatabase db=helper.getWritableDatabase();
        long now=PosDatabase.now();
        long detailCutoff=now-30L*86400000L;
        long operationsCutoff=now-90L*86400000L;
        Calendar calendar=Calendar.getInstance();calendar.setTimeInMillis(now);calendar.add(Calendar.MONTH,-15);
        long ledgerCutoff=calendar.getTimeInMillis();
        try{
            db.beginTransaction();
            String expired="((status='PAID' AND paid_at IS NOT NULL AND paid_at<?) OR (status IN ('CANCELLED','RETURNED') AND archive_until IS NOT NULL AND archive_until<?))";
            db.delete("payments","bill_id IN (SELECT id FROM bills WHERE "+expired+")",new String[]{String.valueOf(detailCutoff),String.valueOf(now)});
            db.delete("bills",expired,new String[]{String.valueOf(detailCutoff),String.valueOf(now)});
            db.delete("stock_history","created_at<? AND change_type!='COUNT'",new String[]{String.valueOf(operationsCutoff)});
            db.delete("price_history","created_at<?",new String[]{String.valueOf(operationsCutoff)});
            db.delete("sales_ledger_items","created_at<?",new String[]{String.valueOf(ledgerCutoff)});
            db.delete("sales_ledger","created_at<?",new String[]{String.valueOf(ledgerCutoff)});
            db.setTransactionSuccessful();
        } catch (android.database.sqlite.SQLiteException ignored) {
            // Keep the app usable if storage is full; retry on the next home-screen visit.
        } finally { if(db.inTransaction()) db.endTransaction(); }
    }
}