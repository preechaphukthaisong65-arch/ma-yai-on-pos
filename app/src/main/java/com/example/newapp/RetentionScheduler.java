package com.example.newapp;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** Schedules an initial retention pass and durable daily follow-up runs. */
public final class RetentionScheduler {
    private RetentionScheduler() { }

    public static void schedule(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        String storeId = com.example.newapp.datahub.StoreIdentity.get(app);
        if (storeId == null || storeId.trim().isEmpty()) return;

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        WorkManager manager = WorkManager.getInstance(app);
        OneTimeWorkRequest initial = new OneTimeWorkRequest.Builder(RetentionWorker.class)
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build();
        manager.enqueueUniqueWork("pos-retention-initial-" + storeId.trim(),
                ExistingWorkPolicy.KEEP, initial);

        PeriodicWorkRequest daily = new PeriodicWorkRequest.Builder(
                RetentionWorker.class, 1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build();
        manager.enqueueUniquePeriodicWork("pos-retention-daily-" + storeId.trim(),
                ExistingPeriodicWorkPolicy.KEEP, daily);
    }
}
