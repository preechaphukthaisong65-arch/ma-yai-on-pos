
package com.example.newapp;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public final class AndroidNotificationService {

    private static final String CHANNEL_ID = "maeyai_on_pos_alerts";
    private static final String CHANNEL_NAME = "แจ้งเตือนร้านแม่ใหญ่อ่อน";

    private final Context context;

    public AndroidNotificationService(Context context) {
        this.context = context.getApplicationContext();
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE
                );

        if (manager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );

        channel.setDescription(
                "แจ้งเตือนสต๊อกต่ำและบิลเครดิตค้างชำระ"
        );

        manager.createNotificationChannel(channel);
    }

    /**
     * ใช้ notificationId เดิมสำหรับเหตุการณ์เดิม
     * หากต้องการให้แต่ละเหตุการณ์คงอยู่แยกกัน ให้ใช้ ID ที่ไม่ซ้ำ
     */
    public boolean show(int notificationId, String title, String message) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        Notification notification = new NotificationCompat.Builder(
                context,
                CHANNEL_ID
        )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title == null ? "แจ้งเตือน" : title)
                .setContentText(message == null ? "" : message)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message == null ? "" : message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .build();

        try {
            NotificationManagerCompat.from(context)
                    .notify(notificationId, notification);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public boolean showLowStock(int notificationId, String productName) {
        return show(
                notificationId,
                "แจ้งเตือนสต๊อกสินค้า",
                "มีสินค้า ต่ำกว่า ลิมิตสต๊อก: " + productName
        );
    }

    public boolean showOverdueDebt(int notificationId, int billCount) {
        return show(
                notificationId,
                "แจ้งเตือนบิลเครดิต",
                "มีบิลเครดิตค้างเกินหนึ่งเดือน " + billCount + " บิล"
        );
    }
}
