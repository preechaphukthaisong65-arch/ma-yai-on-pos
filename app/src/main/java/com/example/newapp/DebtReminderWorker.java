package com.example.newapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class DebtReminderWorker extends Worker {
    private static final String CHANNEL_ID = "overdue_debt_reminders";

    public DebtReminderWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override public Result doWork() {
        // Reminder timing, wording, and individual-versus-aggregate content require
        // owner confirmation. Do not send the previous unapproved notification.
        return Result.success();
    }

    private void postNotification(Context context, Map<String, Double> debts) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "แจ้งเตือนหนี้ค้างชำระ", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
        StringBuilder text = new StringBuilder();
        int shown = 0;
        double total = 0d;
        for (Map.Entry<String, Double> entry : debts.entrySet()) {
            total += entry.getValue();
            if (shown < 5) {
                if (shown > 0) text.append(" • ");
                text.append(entry.getKey()).append(" ").append(String.format(java.util.Locale.US, "%.2f", entry.getValue())).append(" บาท");
                shown++;
            }
        }
        if (debts.size() > shown) text.append(" • และอีก ").append(debts.size() - shown).append(" ราย");
        Intent intent = new Intent(context, HistoryScreen.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 9031, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("แจ้งเตือนลูกหนี้ค้างเกิน 30 วัน")
                .setContentText(text.toString())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text + "\nยอดค้างรวม " + String.format(java.util.Locale.US, "%.2f", total) + " บาท"))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        manager.notify(9031, notification.build());
    }
}