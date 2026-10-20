package com.example.newapp.datahub;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * DataHub
 *
 * ศูนย์กลางข้อมูลของแอป
 * สร้างใหม่ทั้งหมด และยังไม่เชื่อมกับระบบขาย สต๊อก หนี้ หรือรายงาน
 */
public final class DataHub {

    public static final String SYNC_PENDING = "pending";
    public static final String SYNC_SYNCED = "synced";
    public static final String SYNC_FAILED = "failed";

    private static volatile DataHub instance;

    private final LocalStore localStore;

    private DataHub(Context context) {
        localStore = new LocalStore(
                context.getApplicationContext()
        );
    }

    /**
     * เรียกใช้งาน DataHub แบบ Singleton
     */
    public static DataHub getInstance(Context context) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "Context must not be null"
            );
        }

        if (instance == null) {
            synchronized (DataHub.class) {
                if (instance == null) {
                    instance = new DataHub(context);
                }
            }
        }

        return instance;
    }

    /**
     * บันทึกข้อมูลผ่านศูนย์กลาง
     *
     * ขณะนี้บันทึกลงเครื่องและกำหนดสถานะรอสำรอง
     * ยังไม่ได้ส่งข้อมูลขึ้นคลาวด์
     */
    public JSONObject saveRecord(
            String storeId,
            String dataType,
            String recordId,
            JSONObject payload
    ) throws JSONException {

        requireText(storeId, "storeId");
        requireText(dataType, "dataType");

        if (payload == null) {
            throw new IllegalArgumentException(
                    "payload must not be null"
            );
        }

        String normalizedId;

        if (recordId == null || recordId.trim().isEmpty()) {
            normalizedId = UUID.randomUUID().toString();
        } else {
            normalizedId = recordId.trim();
        }

        return localStore.saveRecord(
                normalizedId,
                storeId.trim(),
                dataType.trim(),
                payload.toString()
        );
    }

    /**
     * อ่านข้อมูลจากเครื่อง
     * คืนค่า null หากไม่พบรายการ
     */
    public JSONObject getRecord(
            String storeId,
            String recordId
    ) throws JSONException {

        requireText(storeId, "storeId");
        requireText(recordId, "recordId");

        return localStore.getRecord(
                storeId.trim(),
                recordId.trim()
        );
    }

    /**
     * จำนวนรายการที่รอสำรองข้อมูล
     */
    public int getPendingBackupCount() {
        return localStore.getPendingCount();
    }

    private static void requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }
    }
}
