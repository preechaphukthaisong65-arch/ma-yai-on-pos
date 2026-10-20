package com.example.newapp.datahub;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Schedules durable retries; the SQLite records remain the source of truth. */
public final class BackupRetryScheduler {
    private BackupRetryScheduler() { }

    public static void schedule(Context context, String storeId) {
        if (context == null || storeId == null || storeId.trim().isEmpty()) return;

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackupRetryWorker.class)
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(
                        "datahub-backup-" + storeId.trim(),
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        request
                );
    }
}
