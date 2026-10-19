
package com.example.newapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();

        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        try {
            AndroidNotificationService notificationService =
                    new AndroidNotificationService(
                            context.getApplicationContext()
                    );

            notificationService.syncLowStockNotification();

        } catch (Exception e) {
            Log.e(
                    TAG,
                    "ซิงก์แจ้งเตือนสต๊อกหลังเปิดเครื่องไม่สำเร็จ",
                    e
            );
        }
    }
}
