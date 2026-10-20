package com.example.newapp.datahub;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Retries pending SQLite-backed records when the network becomes available. */
public final class BackupRetryWorker extends Worker {
    public BackupRetryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String storeId = StoreIdentity.get(getApplicationContext());
        if (storeId.isEmpty()) return Result.success();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger failed = new AtomicInteger(1);

        DataHub.getInstance(getApplicationContext()).backupPendingRecords(
                storeId,
                (succeeded, failedCount, message) -> {
                    failed.set(failedCount);
                    latch.countDown();
                }
        );

        try {
            if (!latch.await(60, TimeUnit.SECONDS)) return Result.retry();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        if (failed.get() > 0) return Result.retry();
        return Result.success();
    }
}
