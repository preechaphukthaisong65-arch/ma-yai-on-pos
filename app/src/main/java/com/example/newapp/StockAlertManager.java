package com.example.newapp;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.content.Context;
import java.util.HashSet;
import java.util.Set;

final class StockAlertManager {
    private static final String CHANNEL="maeyai_stock_alerts";
    private static final int NOTICE_ID=7410;
    private StockAlertManager(){}
    static boolean check(Activity activity){
        PosDatabase helper=PosDatabase.get(activity);
        SQLiteDatabase db;
        try { db=helper.getWritableDatabase(); } catch (android.database.sqlite.SQLiteException e) { return false; }
        try {
        Set<String> low=new HashSet<>();
        Cursor c=db.rawQuery("SELECT id FROM products WHERE active=1 AND stock<=stock_limit",null);
        while(c.moveToNext())low.add(c.getString(0));c.close();
        ContentValues cv=new ContentValues();StringBuilder value=new StringBuilder();for(String id:low){if(value.length()>0)value.append(',');value.append(id);}cv.put("value",value.toString());
        if(db.update("app_meta",cv,"key='low_alert_ids'",null)==0){cv.put("key","low_alert_ids");db.insertWithOnConflict("app_meta",null,cv,SQLiteDatabase.CONFLICT_REPLACE);}
        Cursor notifiedCursor=db.query("app_meta",new String[]{"value"},"key='low_alert_notified_ids'",null,null,null,null);
        Set<String> notified=new HashSet<>();if(notifiedCursor.moveToFirst()){String ids=notifiedCursor.getString(0);if(ids!=null&&!ids.isEmpty())for(String id:ids.split(","))notified.add(id);}notifiedCursor.close();
        boolean pendingNotification=false;for(String id:low)if(!notified.contains(id)){pendingNotification=true;break;}
        if(low.isEmpty()){
            NotificationManager manager=(NotificationManager)activity.getSystemService(Context.NOTIFICATION_SERVICE);if(manager!=null)manager.cancel(NOTICE_ID);
            ContentValues cleared=new ContentValues();cleared.put("key","low_alert_notified_ids");cleared.put("value","");db.insertWithOnConflict("app_meta",null,cleared,SQLiteDatabase.CONFLICT_REPLACE);
        }else if(pendingNotification&&notify(activity)){
            ContentValues sent=new ContentValues();sent.put("key","low_alert_notified_ids");sent.put("value",value.toString());db.insertWithOnConflict("app_meta",null,sent,SQLiteDatabase.CONFLICT_REPLACE);
        }
        return !low.isEmpty();
        } catch (android.database.sqlite.SQLiteException e) { return false; }
    }
    private static boolean notify(Context context){
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return false;
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"แจ้งเตือนลิมิตสต๊อก",NotificationManager.IMPORTANCE_DEFAULT));
        if(Build.VERSION.SDK_INT>=24&&!manager.areNotificationsEnabled())return false;
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("ร้านแม่ใหญ่อ่อน POS").setContentText("มีสินค้า ต่ำกว่า ลิมิตสต๊อก").setStyle(new Notification.BigTextStyle().bigText("มีสินค้า ต่ำกว่า ลิมิตสต๊อก")).setOngoing(true).setAutoCancel(false);
        manager.notify(NOTICE_ID,builder.build());return true;
    }
}