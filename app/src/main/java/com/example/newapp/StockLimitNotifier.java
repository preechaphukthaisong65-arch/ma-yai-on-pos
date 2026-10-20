package com.example.newapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONObject;
import java.util.List;

/** Store-scoped low-stock state machine: notify only on normal -> low transitions. */
public final class StockLimitNotifier {
    private static final String PREFS = "stock_limit_alert_state";
    private static final String CHANNEL = "stock_limit_alerts";
    private static Context app;
    private StockLimitNotifier() { }

    public static synchronized void initialize(Context context) {
        if (context != null) app = context.getApplicationContext();
    }

    public static synchronized boolean isReconciled(Context context, String storeId) {
        if (context == null || storeId == null) return false;
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("reconciled_" + storeId, false);
    }

    /** Scan existing records once after restore, then the home screen uses the small saved count. */
    public static synchronized void reconcileIfNeeded(Context context, String storeId, List<JSONObject> products) {
        initialize(context);
        if (app == null || storeId == null || storeId.trim().isEmpty() || isReconciled(app, storeId)) return;
        if (products != null) {
            for (JSONObject record : products) {
                if (record == null) continue;
                JSONObject p = record.optJSONObject("payload");
                if (p == null || !p.has("stockQuantity") || !p.has("stockLimit")) continue;
                double quantity = p.optDouble("stockQuantity", 0d);
                double limit = p.optDouble("stockLimit", 0d);
                updateState(storeId, record.optString("recordId", ""), quantity <= limit, quantity, limit);
            }
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("reconciled_" + storeId, true).apply();
    }

    public static synchronized boolean hasAnyLowStock(Context context, String storeId) {
        if (context == null || storeId == null || storeId.trim().isEmpty()) return false;
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt("low_count_" + storeId, 0) > 0;
    }

    public static synchronized void updateState(String storeId, String productId, boolean low, double quantity, double limit) {
        if (app == null || storeId == null || storeId.trim().isEmpty()
                || productId == null || productId.trim().isEmpty()) return;
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = "low_" + storeId + "_" + productId;
        boolean wasLow = prefs.getBoolean(key, false);
        if (wasLow == low) return;
        int count = prefs.getInt("low_count_" + storeId, 0);
        if (low) count++; else count = Math.max(0, count - 1);
        prefs.edit().putBoolean(key, low).putInt("low_count_" + storeId, count).apply();
        if (low) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL, "แจ้งเตือนลิมิตสต๊อก", NotificationManager.IMPORTANCE_DEFAULT));
            }
            NotificationManagerCompat manager = NotificationManagerCompat.from(app);
            if (manager.areNotificationsEnabled()) {
                manager.notify((storeId + ":" + productId).hashCode(), new NotificationCompat.Builder(app, CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("แจ้งเตือนสต๊อก")
                        .setContentText("มีสินค้า ต่ำกว่า ลิมิตสต๊อก")
                        .setStyle(new NotificationCompat.BigTextStyle().bigText("มีสินค้า ต่ำกว่า ลิมิตสต๊อก"))
                        .setAutoCancel(true).build());
            }
        }
    }
}
