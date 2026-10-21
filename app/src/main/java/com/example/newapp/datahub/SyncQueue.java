
package com.example.newapp.datahub;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * คิวรายการที่รอซิงก์
 *
 * เวอร์ชันเริ่มต้นเก็บคิวในหน่วยความจำเท่านั้น
 */
public final class SyncQueue {

    private final List<JSONObject> queue =
            new ArrayList<>();

    /**
     * เพิ่มรายการเข้าคิว
     */
    public synchronized boolean enqueue(
            JSONObject record
    ) {
        if (record == null
                || !new RecordValidator().isValidRecord(record)) {
            return false;
        }

        String recordId =
                record.optString("recordId", "");
        String storeId =
                record.optString("storeId", "");

        // ป้องกันรายการซ้ำในคิวของร้านเดียวกัน
        for (JSONObject queued : queue) {
            if (recordId.equals(
                    queued.optString("recordId", ""))
                    && storeId.equals(
                    queued.optString("storeId", ""))) {
                return false;
            }
        }

        try {
            queue.add(new JSONObject(record.toString()));
            return true;
        } catch (JSONException error) {
            // ไม่เพิ่มรายการ หากคัดลอก JSON ไม่สำเร็จ
            return false;
        }
    }

    /**
     * อ่านรายการแรกในคิว
     */
    public synchronized JSONObject peek() {
        if (queue.isEmpty()) {
            return null;
        }

        JSONObject record = queue.get(0);

        try {
            return new JSONObject(record.toString());
        } catch (JSONException error) {
            return null;
        }
    }

    /**
     * นำรายการแรกออกจากคิว
     */
    public synchronized JSONObject dequeue() {
        if (queue.isEmpty()) {
            return null;
        }

        JSONObject record = queue.remove(0);

        try {
            return new JSONObject(record.toString());
        } catch (JSONException error) {
            return null;
        }
    }

    /**
     * จำนวนรายการที่รออยู่
     */
    public synchronized int size() {
        return queue.size();
    }

    /**
     * ตรวจสอบว่าคิวว่างหรือไม่
     */
    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * ล้างคิวในหน่วยความจำ
     */
    public synchronized void clear() {
        queue.clear();
    }

    /**
     * อ่านรายการทั้งหมดเป็นสำเนา
     */
    public synchronized List<JSONObject> snapshot() {
        List<JSONObject> result = new ArrayList<>();

        for (JSONObject record : queue) {
            try {
                result.add(
                        new JSONObject(record.toString())
                );
            } catch (JSONException ignored) {
                // ข้ามรายการที่คัดลอกไม่ได้
            }
        }

        return Collections.unmodifiableList(result);
    }
}
