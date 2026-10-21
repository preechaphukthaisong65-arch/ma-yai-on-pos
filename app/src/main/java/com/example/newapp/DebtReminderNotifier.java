package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class DebtReminderNotifier {
    private static final String CHANNEL="maeyai_debt_alerts";
    private DebtReminderNotifier(){}
    private static final class Debt {
        long id,total,oldest;String name,message;
        Debt(long id,String name,long total,long oldest){this.id=id;this.name=name;this.total=total;this.oldest=oldest;
            String date=new SimpleDateFormat("dd/MM/yy",Locale.getDefault()).format(new java.util.Date(oldest));
            this.message=name+" ค้างชำระ "+amount(total)+" บาท ตั้งแต่วันที่ "+date;
        }
    }
    private static String amount(long satang){
        if(satang%100L==0L)return String.format(Locale.US,"%,d",satang/100L);
        return String.format(Locale.US,"%,.2f",satang/100.0d);
    }
    private static ArrayList<Debt> overdue(Context context){
        Calendar cutoff=Calendar.getInstance();cutoff.add(Calendar.MONTH,-1);
        ArrayList<Debt> list=new ArrayList<>();
        Cursor c=PosDatabase.get(context).getReadableDatabase().rawQuery(
                "SELECT c.id,c.full_name,SUM(b.total_satang),MIN(b.created_at) FROM customers c JOIN bills b ON b.customer_id=c.id WHERE b.status='UNPAID' GROUP BY c.id HAVING MIN(b.created_at)<? ORDER BY SUM(b.total_satang) DESC,MIN(b.created_at) ASC",
                new String[]{String.valueOf(cutoff.getTimeInMillis())});
        while(c.moveToNext())list.add(new Debt(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3)));
        c.close();return list;
    }
    static void sendWeekly(Context context){
        ArrayList<Debt> debts=overdue(context);
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        Set<String> currentIds=new HashSet<>();
        for(Debt debt:debts){
            currentIds.add(String.valueOf(debt.id));
            if(manager==null||(Build.VERSION.SDK_INT>=24&&!manager.areNotificationsEnabled()))continue;
            if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"แจ้งเตือนลูกหนี้ค้างชำระ",NotificationManager.IMPORTANCE_DEFAULT));
            Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
            builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("แจ้งเตือนลูกหนี้").setContentText(debt.message).setStyle(new Notification.BigTextStyle().bigText(debt.message)).setAutoCancel(true);
            manager.notify(200000+(int)(debt.id%1000000L),builder.build());
        }
        SQLiteDatabase db=PosDatabase.get(context).getWritableDatabase();
        Cursor old=db.query("app_meta",new String[]{"value"},"key='debt_notification_ids'",null,null,null,null);
        if(old.moveToFirst()){String v=old.getString(0);if(manager!=null&&v!=null&&!v.isEmpty())for(String id:v.split(",")){if(!currentIds.contains(id)){try{manager.cancel(200000+(int)(Long.parseLong(id)%1000000L));}catch(NumberFormatException ignored){}}}}old.close();
        StringBuilder ids=new StringBuilder();for(String id:currentIds){if(ids.length()>0)ids.append(',');ids.append(id);}
        ContentValues cv=new ContentValues();cv.put("key","debt_notification_ids");cv.put("value",ids.toString());db.insertWithOnConflict("app_meta",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }
    static boolean hasOverdue(Context context){return !overdue(context).isEmpty();}
    static String homeSummary(Context context){StringBuilder out=new StringBuilder();for(Debt debt:overdue(context)){if(out.length()>0)out.append("     •     ");out.append(debt.message);}return out.toString();}
    static void showOverdue(Activity activity){
        ArrayList<Debt> debts=overdue(activity);
        if(debts.isEmpty()){new AlertDialog.Builder(activity).setMessage("ไม่มีลูกหนี้ค้างชำระเกิน 1 เดือน").setPositiveButton("ตกลง",null).show();return;}
        StringBuilder message=new StringBuilder();for(Debt d:debts)message.append(d.message).append('\n');
        new AlertDialog.Builder(activity).setTitle("ลูกหนี้ค้างชำระเกิน 1 เดือน").setMessage(message.toString()).setPositiveButton("ตกลง",null).show();
    }
}