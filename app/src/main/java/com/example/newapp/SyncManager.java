package com.example.newapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SyncManager {

    private static final String TAG = "SyncManager";
    private final Context context;
    private final LocalDatabase dbHelper;

    // URL สำหรับ Endpoint ของ Cloud Server (สามารถปรับเปลี่ยนตามจริงได้)
    private static final String SYNC_API_URL = "https://your-api-endpoint.com/api/sync";
    private static final String RESTORE_API_URL = "https://your-api-endpoint.com/api/restore";

    public SyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new LocalDatabase(this.context);
    }

    public boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network state", e);
        }
        return false;
    }

    /**
     * ฟังก์ชันดึงข้อมูลจาก Cloud ลงมาติดตั้งที่เครื่องใหม่ (Auto-Restore)
     */
    public void restoreDataFromCloud(RestoreCallback callback) {
        if (!isOnline()) {
            if (callback != null) callback.onRestoreFinished(false, "ไม่มีการเชื่อมต่ออินเทอร์เน็ต ไม่สามารถกู้คืนข้อมูลได้");
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL(RESTORE_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(7000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // แปลงข้อมูล JSON ที่ได้จาก Cloud แล้วบันทึกลง SQLite
                    boolean saved = parseAndSaveRestoredData(response.toString());

                    if (saved) {
                        if (callback != null) callback.onRestoreFinished(true, "กู้คืนข้อมูลสำเร็จเรียบร้อย");
                    } else {
                        if (callback != null) callback.onRestoreFinished(false, "รูปแบบข้อมูลคลาวด์ไม่ถูกต้อง");
                    }
                } else {
                    if (callback != null) callback.onRestoreFinished(false, "ไม่สามารถเชื่อมต่อเซิร์ฟเวอร์คลาวด์ได้ (Code: " + responseCode + ")");
                }

            } catch (Exception e) {
                Log.e(TAG, "Restore error", e);
                if (callback != null) callback.onRestoreFinished(false, "เกิดข้อผิดพลาดในการกู้คืนข้อมูล: " + e.getMessage());
            }
        }).start();
    }

    /**
     * นำข้อมูล JSON ที่ดึงมาจาก Cloud มาบันทึกลง Local SQLite Database
     */
    private boolean parseAndSaveRestoredData(String jsonStr) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            JSONArray productsArray = jsonObject.optJSONArray("products");

            if (productsArray != null) {
                db.beginTransaction();
                for (int i = 0; i < productsArray.length(); i++) {
                    JSONObject p = productsArray.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    values.put("name", p.optString("name"));
                    values.put("price", p.optDouble("price", 0.0));
                    values.put("cost", p.optInt("cost", 0));
                    values.put("stock", p.optInt("stock", 0));
                    values.put("min_stock", p.optInt("min_stock", 5));
                    values.put("barcode", p.optString("barcode"));
                    values.put("unit", p.optString("unit", "ชิ้น"));

                    // ป้องกันข้อมูลซ้ำโดยใช้ REPLACE หรือ INSERT
                    db.insertWithOnConflict("products", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
                db.setTransactionSuccessful();
                db.endTransaction();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Parse restore data error", e);
            return false;
        } finally {
            db.close();
        }
    }

    public interface RestoreCallback {
        void onRestoreFinished(boolean success, String message);
    }
}
