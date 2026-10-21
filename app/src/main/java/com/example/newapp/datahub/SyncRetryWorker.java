package com.example.newapp.datahub;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Retries pending SQLite-backed records when the network becomes available. */
public final class SyncRetryWorker extends Worker {
    private static final String KEY_STORE_ID = "store_id";

    public SyncRetryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Prefer the store explicitly captured by the scheduler. Falling back
        // preserves compatibility with work enqueued by older app versions.
        String storeId = getInputData().getString(KEY_STORE_ID);
        if (storeId == null || storeId.trim().isEmpty()) {
            storeId = StoreIdentity.get(getApplicationContext());
        }
        if (storeId == null || storeId.trim().isEmpty()) return Result.success();
        final String checkedStoreId = storeId.trim();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger failed = new AtomicInteger(1);

        DataHub.getInstance(getApplicationContext()).syncPendingRecords(
                checkedStoreId,
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

        // DataHub processes a bounded batch per run. If more than one batch
        // remains, retry so large stores are drained instead of leaving the
        // records after the first 500 pending forever.
        if (DataHub.getInstance(getApplicationContext())
                .getPendingSyncCount(checkedStoreId) > 0) {
            return Result.retry();
        }
        return Result.success();
    }
}
