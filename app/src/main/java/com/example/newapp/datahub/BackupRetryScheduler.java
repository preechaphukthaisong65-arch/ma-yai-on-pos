package com.example.newapp.datahub;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Schedules durable retries; the SQLite records remain the source of truth. */
public final class BackupRetryScheduler {
    private static final String KEY_STORE_ID = "store_id";

    private BackupRetryScheduler() { }

    public static void schedule(Context context, String storeId) {
        if (context == null || storeId == null || storeId.trim().isEmpty()) return;

        String checkedStoreId = storeId.trim();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Bind each retry to the shop that queued it. StoreIdentity is a
        // single active-shop preference and may change before this work runs.
        Data input = new Data.Builder()
                .putString(KEY_STORE_ID, checkedStoreId)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackupRetryWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(
                        "datahub-backup-" + checkedStoreId,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        request
                );
    }
}
