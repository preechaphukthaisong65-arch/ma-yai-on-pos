
package com.example.newapp;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public final class AndroidNotificationService {

    private static final String CHANNEL_ID =
            "maeyai_on_pos_alerts";

    private static final String CHANNEL_NAME =
            "แจ้งเตือนร้านแม่ใหญ่อ่อน";

    /*
     * ใช้ ID คงที่สำหรับสถานะสต๊อกต่ำทั้งหมด
     * การอัปเดตจะใช้รายการแจ้งเตือนเดิม ไม่สร้างรายการใหม่ทุกครั้ง
     */
    private static final int LOW_STOCK_NOTIFICATION_ID = 4000;

    private static final String LOW_STOCK_TITLE =
            "แจ้งเตือนสต๊อกสินค้า";

    private static final String LOW_STOCK_MESSAGE =
            "มีสินค้า ต่ำกว่า ลิมิตสต๊อก";

    private final Context context;

    public AndroidNotificationService(Context context) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "Context ห้ามเป็น null"
            );
        }

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

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }

        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * แสดงหรือยกเลิกสถานะเตือนสต๊อกต่ำ
     *
     * มีสินค้า stock_qty < min_stock อย่างน้อยหนึ่งรายการ:
     * แสดง Notification ID เดิม พร้อมข้อความทั่วไปเท่านั้น
     *
     * ไม่มีสินค้าเข้าเงื่อนไข:
     * ยกเลิก Notification นี้
     *
     * ไม่แสดงชื่อสินค้า บาร์โค้ด หรือจำนวนสินค้าใน Notification
     */
    public boolean syncLowStockNotification() {
        boolean hasLowStock = false;

        try {
            LocalDatabase database =
                    LocalDatabase.getInstance(context);

            SQLiteDatabase db =
                    database.getReadableDatabase();

            try (Cursor cursor = db.rawQuery(
                    "SELECT EXISTS (" +
                            "SELECT 1 FROM products " +
                            "WHERE stock_qty < min_stock" +
                            ")",
                    null
            )) {
                hasLowStock =
                        cursor.moveToFirst()
                                && cursor.getInt(0) == 1;
            }

        } catch (Exception e) {
            /*
             * หากอ่านฐานข้อมูลไม่ได้ อย่ายกเลิก Notification
             * เพราะยังไม่ทราบแน่ชัดว่าสต๊อกกลับสู่เกณฑ์หรือไม่
             */
            return false;
        }

        NotificationManagerCompat manager =
                NotificationManagerCompat.from(context);

        if (!hasLowStock) {
            manager.cancel(LOW_STOCK_NOTIFICATION_ID);
            return true;
        }

        if (!hasNotificationPermission()) {
            return false;
        }

        Notification notification =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable.ic_dialog_info
                        )
                        .setContentTitle(LOW_STOCK_TITLE)
                        .setContentText(LOW_STOCK_MESSAGE)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(LOW_STOCK_MESSAGE)
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH
                        )
                        .setCategory(
                                NotificationCompat.CATEGORY_STATUS
                        )
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setOnlyAlertOnce(true)
                        .build();

        try {
            manager.notify(
                    LOW_STOCK_NOTIFICATION_ID,
                    notification
            );
            return true;

        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * ใช้สำหรับการแจ้งเตือนทั่วไป เช่น บิลเครดิต
     */
    public boolean show(
            int notificationId,
            String title,
            String message
    ) {
        if (!hasNotificationPermission()) {
            return false;
        }

        Notification notification =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable.ic_dialog_info
                        )
                        .setContentTitle(
                                title == null ? "แจ้งเตือน" : title
                        )
                        .setContentText(
                                message == null ? "" : message
                        )
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(
                                                message == null
                                                        ? ""
                                                        : message
                                        )
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH
                        )
                        .setCategory(
                                NotificationCompat.CATEGORY_STATUS
                        )
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

    /**
     * คงชื่อเมธอดเดิมไว้เพื่อไม่ให้คลาสที่เรียกใช้เดิมคอมไพล์พัง
     * แต่ไม่แสดงชื่อสินค้าใน Notification อีกต่อไป
     */
    public boolean showLowStock(
            int notificationId,
            String productName
    ) {
        return syncLowStockNotification();
    }

    public boolean showOverdueDebt(
            int notificationId,
            int billCount
    ) {
        return show(
                notificationId,
                "แจ้งเตือนบิลเครดิต",
                "มีบิลเครดิตค้างเกินหนึ่งเดือน "
                        + billCount + " บิล"
        );
    }
}
