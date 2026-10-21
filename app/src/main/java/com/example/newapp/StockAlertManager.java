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
        SQLiteDatabase db=helper.getWritableDatabase();
        Set<String> low=new HashSet<>();
        Cursor c=db.rawQuery("SELECT id FROM products WHERE active=1 AND stock<=stock_limit",null);
        while(c.moveToNext())low.add(c.getString(0));c.close();
        Cursor state=db.query("app_meta",new String[]{"value"},"key='low_alert_ids'",null,null,null,null);
        Set<String> previous=new HashSet<>();if(state.moveToFirst()){String s=state.getString(0);if(s!=null&&!s.isEmpty())for(String id:s.split(","))previous.add(id);}state.close();
        boolean newLow=false;for(String id:low)if(!previous.contains(id)){newLow=true;break;}
        ContentValues cv=new ContentValues();StringBuilder value=new StringBuilder();for(String id:low){if(value.length()>0)value.append(',');value.append(id);}cv.put("value",value.toString());
        if(db.update("app_meta",cv,"key='low_alert_ids'",null)==0){cv.put("key","low_alert_ids");db.insertWithOnConflict("app_meta",null,cv,SQLiteDatabase.CONFLICT_REPLACE);}
        if(newLow&&!low.isEmpty())notify(activity);
        return !low.isEmpty();
    }
    private static void notify(Context context){
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"แจ้งเตือนลิมิตสต๊อก",NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("ร้านแม่ใหญ่อ่อน POS").setContentText("มีสินค้า ต่ำกว่า ลิมิตสต๊อก").setStyle(new Notification.BigTextStyle().bigText("มีสินค้า ต่ำกว่า ลิมิตสต๊อก")).setAutoCancel(true);
        manager.notify(NOTICE_ID,builder.build());
    }
}