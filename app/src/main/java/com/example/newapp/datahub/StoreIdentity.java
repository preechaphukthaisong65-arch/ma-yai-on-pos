package com.example.newapp.datahub;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the non-secret identifier used to locate one shop's cloud data. */
public final class StoreIdentity {
    private static final String PREFS = "ma_yai_on_pos_store";
    private static final String KEY_STORE_ID = "store_id";
    private static final String KEY_INITIAL_SETUP = "initial_restore_complete";
    private static final String KEY_OWNER_UID = "owner_uid";
    private static final String KEY_DEVICE_ID = "device_id";

    private StoreIdentity() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void save(Context context, String storeId) {
        if (context == null || storeId == null || storeId.trim().isEmpty()) {
            throw new IllegalArgumentException("รหัสร้านค้าต้องไม่ว่าง");
        }
        prefs(context).edit().putString(KEY_STORE_ID, storeId.trim()).apply();
    }

    public static String get(Context context) {
        if (context == null) return "";
        return prefs(context).getString(KEY_STORE_ID, "").trim();
    }

    public static void saveOwnerUid(Context context, String ownerUid) {
        if (context == null || ownerUid == null || ownerUid.trim().isEmpty()) {
            throw new IllegalArgumentException("รหัสบัญชีเจ้าของร้านต้องไม่ว่าง");
        }
        prefs(context).edit().putString(KEY_OWNER_UID, ownerUid.trim()).apply();
    }

    public static String getOwnerUid(Context context) {
        if (context == null) return "";
        return prefs(context).getString(KEY_OWNER_UID, "").trim();
    }

    /** Stable per-install identifier; generated once and preserved across app restarts. */
    public static synchronized String getOrCreateDeviceId(Context context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        SharedPreferences preferences = prefs(context);
        String id = preferences.getString(KEY_DEVICE_ID, "").trim();
        if (!id.isEmpty()) return id;
        id = java.util.UUID.randomUUID().toString();
        preferences.edit().putString(KEY_DEVICE_ID, id).commit();
        return id;
    }

    public static boolean isConfigured(Context context) {
        return !get(context).isEmpty();
    }

    public static boolean isInitialSetupComplete(Context context) {
        return prefs(context).getBoolean(KEY_INITIAL_SETUP, false);
    }

    public static void markInitialSetupComplete(Context context) {
        prefs(context).edit().putBoolean(KEY_INITIAL_SETUP, true).apply();
    }
}
