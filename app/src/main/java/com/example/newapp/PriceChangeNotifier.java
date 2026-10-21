package com.example.newapp;
import android.app.NotificationChannel;import android.app.NotificationManager;import android.content.Context;import android.os.Build;import androidx.core.app.NotificationCompat;import androidx.core.app.NotificationManagerCompat;
public final class PriceChangeNotifier {
 private static final String CHANNEL="store_price_changes";private PriceChangeNotifier(){}
 public static void notifySaved(Context c,String message){if(c==null||message==null||message.trim().isEmpty())return;NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(m==null)return;
 if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){NotificationChannel ch=new NotificationChannel(CHANNEL,"การเปลี่ยนแปลงราคา",NotificationManager.IMPORTANCE_DEFAULT);ch.setDescription("แจ้งเตือนเมื่อบันทึกการเปลี่ยนแปลงราคาสินค้าสำเร็จ");m.createNotificationChannel(ch);}
 NotificationManagerCompat nm=NotificationManagerCompat.from(c);if(!nm.areNotificationsEnabled())return;
 NotificationCompat.Builder n=new NotificationCompat.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("บันทึกราคาสินค้าสำเร็จ").setContentText(message).setStyle(new NotificationCompat.BigTextStyle().bigText(message)).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true);
 nm.notify((int)(System.currentTimeMillis()&0x7fffffff),n.build());}
}
