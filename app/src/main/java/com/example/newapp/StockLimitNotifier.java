package com.example.newapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/** Low-stock state machine: notify only on normal -> low transition. */
public final class StockLimitNotifier {
    private static final String PREFS = "stock_limit_alert_state";
    private static final String CHANNEL = "stock_limit_alerts";
    private static Context app;
    private StockLimitNotifier() { }

    public static synchronized void initialize(Context context) {
        if (context != null) app = context.getApplicationContext();
    }

    public static synchronized void updateState(String productId, boolean low, double quantity, double limit) {
        if (app == null || productId == null || productId.trim().isEmpty()) return;
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = "low_" + productId;
        boolean wasLow = prefs.getBoolean(key, false);
        prefs.edit().putBoolean(key, low).apply();
        if (low && !wasLow) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL, "แจ้งเตือนลิมิตสต๊อก", NotificationManager.IMPORTANCE_DEFAULT));
            }
            NotificationManagerCompat manager = NotificationManagerCompat.from(app);
            if (manager.areNotificationsEnabled()) {
                manager.notify(productId.hashCode(), new NotificationCompat.Builder(app, CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("แจ้งเตือนสต๊อก")
                        .setContentText("มีสินค้า ต่ำกว่า ลิมิตสต๊อก")
                        .setStyle(new NotificationCompat.BigTextStyle().bigText("มีสินค้า ต่ำกว่า ลิมิตสต๊อก"))
                        .setAutoCancel(true).build());
            }
        }
    }
}
