package com.example.newapp;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Runs the retention policy in durable background work, including when the app is not open. */
public final class RetentionWorker extends Worker {
    public RetentionWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        return RetentionManager.runIfDueAndWait(getApplicationContext())
                ? Result.success() : Result.retry();
    }
}
