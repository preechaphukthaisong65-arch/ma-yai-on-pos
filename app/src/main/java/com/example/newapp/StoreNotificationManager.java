package com.example.newapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

final class StoreNotificationManager {
    private static final String CHANNEL="maeyai_store_updates";
    private static final int PRICE_NOTICE_ID=7411;
    private StoreNotificationManager(){}
    static void priceChanged(Context context,String productName){
        String message="บันทึกราคาใหม่ของ "+productName+" แล้ว";
        SQLiteDatabase db=PosDatabase.get(context).getWritableDatabase();
        putMeta(db,"home_notice",message);putMeta(db,"home_notice_time",String.valueOf(PosDatabase.now()));
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"แจ้งเตือนการจัดการร้านค้า",NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("แก้ไขราคาสินค้าสำเร็จ").setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setAutoCancel(true);
        manager.notify(PRICE_NOTICE_ID,builder.build());
    }
    static String recentHomeNotice(Context context){
        SQLiteDatabase db=PosDatabase.get(context).getReadableDatabase();
        Cursor c=db.query("app_meta",new String[]{"value"},"key='home_notice'",null,null,null,null);
        String message=c.moveToFirst()?c.getString(0):"";c.close();
        Cursor t=db.query("app_meta",new String[]{"value"},"key='home_notice_time'",null,null,null,null);
        long timestamp=t.moveToFirst()?Long.parseLong(t.getString(0)):0;t.close();
        return PosDatabase.now()-timestamp<=86400000L?message:"";
    }
    private static void putMeta(SQLiteDatabase db,String key,String value){
        ContentValues cv=new ContentValues();cv.put("key",key);cv.put("value",value);db.insertWithOnConflict("app_meta",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }
}