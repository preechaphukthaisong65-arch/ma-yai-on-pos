package com.example.newapp.datahub;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the non-secret identifier used to locate one shop's cloud backup. */
public final class StoreIdentity {
    private static final String PREFS = "ma_yai_on_pos_store";
    private static final String KEY_STORE_ID = "store_id";

    private StoreIdentity() { }

    public static void save(Context context, String storeId) {
        if (context == null || storeId == null || storeId.trim().isEmpty()) {
            throw new IllegalArgumentException("รหัสร้านค้าต้องไม่ว่าง");
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STORE_ID, storeId.trim())
                .apply();
    }

    public static String get(Context context) {
        if (context == null) return "";
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_STORE_ID, "").trim();
    }

    public static boolean isConfigured(Context context) {
        return !get(context).isEmpty();
    }
}
