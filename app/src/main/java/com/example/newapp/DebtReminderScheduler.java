package com.example.newapp;

import android.content.Context;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

final class DebtReminderScheduler {
    private static final String UNIQUE_WORK = "ma-yai-on-pos-weekly-debt-reminder";
    private DebtReminderScheduler() { }

    static void scheduleNext(Context context) {
        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        next.set(Calendar.HOUR_OF_DAY, 9);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 7);
        long delay = Math.max(0L, next.getTimeInMillis() - now.getTimeInMillis());
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DebtReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request);
    }
}