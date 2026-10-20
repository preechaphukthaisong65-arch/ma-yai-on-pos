package com.example.newapp.datahub;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class LocalStore extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "data_hub.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_RECORDS = "hub_records";

    private static final String COL_RECORD_ID = "record_id";
    private static final String COL_STORE_ID = "store_id";
    private static final String COL_DATA_TYPE = "data_type";
    private static final String COL_SCHEMA_VERSION = "schema_version";
    private static final String COL_UPDATED_AT = "updated_at";
    private static final String COL_REVISION = "revision";
    private static final String COL_PAYLOAD = "payload";
    private static final String COL_SYNC_STATUS = "sync_status";

    public LocalStore(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_RECORDS + " ("
                + COL_RECORD_ID + " TEXT NOT NULL, "
                + COL_STORE_ID + " TEXT NOT NULL, "
                + COL_DATA_TYPE + " TEXT NOT NULL, "
                + COL_SCHEMA_VERSION + " INTEGER NOT NULL DEFAULT 1, "
                + COL_UPDATED_AT + " INTEGER NOT NULL, "
                + COL_REVISION + " INTEGER NOT NULL DEFAULT 1, "
                + COL_PAYLOAD + " TEXT NOT NULL, "
                + COL_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'pending', "
                + "PRIMARY KEY (" + COL_STORE_ID + ", "
                + COL_RECORD_ID + "))");

        db.execSQL("CREATE INDEX idx_hub_records_sync ON "
                + TABLE_RECORDS + " (" + COL_SYNC_STATUS + ")");
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion
    ) {
        // ห้ามลบข้อมูลอัตโนมัติเมื่อเปลี่ยนเวอร์ชันฐานข้อมูล
    }

    public synchronized JSONObject saveRecord(
            String recordId,
            String storeId,
            String dataType,
            String payload
    ) throws JSONException {

        if (isBlank(recordId)
                || isBlank(storeId)
                || isBlank(dataType)
                || payload == null) {
            throw new IllegalArgumentException(
                    "Record fields must not be empty");
        }

        JSONObject payloadObject = new JSONObject(payload);
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        int revision = 1;

        Cursor cursor = db.query(
                TABLE_RECORDS,
                new String[]{COL_REVISION},
                COL_STORE_ID + " = ? AND "
                        + COL_RECORD_ID + " = ?",
                new String[]{storeId, recordId},
                null, null, null);

        try {
            if (cursor.moveToFirst()) {
                revision = cursor.getInt(0) + 1;
            }
        } finally {
            cursor.close();
        }

        ContentValues values = new ContentValues();
        values.put(COL_RECORD_ID, recordId);
        values.put(COL_STORE_ID, storeId);
        values.put(COL_DATA_TYPE, dataType);
        values.put(COL_SCHEMA_VERSION, 1);
        values.put(COL_UPDATED_AT, now);
        values.put(COL_REVISION, revision);
        values.put(COL_PAYLOAD, payloadObject.toString());
        values.put(COL_SYNC_STATUS, DataHub.SYNC_PENDING);

        long result = db.insertWithOnConflict(
                TABLE_RECORDS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE);

        if (result == -1L) {
            throw new IllegalStateException(
                    "บันทึกข้อมูลลงฐานข้อมูลไม่สำเร็จ");
        }

        return buildRecord(
                recordId,
                storeId,
                dataType,
                1,
                now,
                revision,
                payloadObject,
                DataHub.SYNC_PENDING);
    }

    public synchronized JSONObject getRecord(
            String storeId,
            String recordId
    ) throws JSONException {

        Cursor cursor = getReadableDatabase().query(
                TABLE_RECORDS,
                null,
                COL_STORE_ID + " = ? AND "
                        + COL_RECORD_ID + " = ?",
                new String[]{storeId, recordId},
                null, null, null, "1");

        try {
            return cursor.moveToFirst()
                    ? readRecord(cursor)
                    : null;
        } finally {
            cursor.close();
        }
    }

    /**
     * อ่านรายการที่ยังไม่ได้สำรอง รวมรายการที่เคยล้มเหลว
     * เพื่อให้ลองสำรองใหม่ได้
     */
    public synchronized List<JSONObject> getRecordsNeedingBackup(
            String storeId,
            int limit
    ) throws JSONException {

        if (isBlank(storeId)) {
            throw new IllegalArgumentException(
                    "storeId must not be blank");
        }

        List<JSONObject> records = new ArrayList<>();

        Cursor cursor = getReadableDatabase().query(
                TABLE_RECORDS,
                null,
                COL_STORE_ID + " = ? AND "
                        + COL_SYNC_STATUS + " != ?",
                new String[]{storeId, DataHub.SYNC_SYNCED},
                null,
                null,
                COL_UPDATED_AT + " ASC",
                Integer.toString(Math.max(1, limit)));

        try {
            while (cursor.moveToNext()) {
                records.add(readRecord(cursor));
            }
        } finally {
            cursor.close();
        }

        return records;
    }

    /**
     * นับทุกรายการที่ยังไม่ได้สำรองสำเร็จ
     */
    public synchronized int getPendingCount() {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_RECORDS
                        + " WHERE " + COL_SYNC_STATUS + " != ?",
                new String[]{DataHub.SYNC_SYNCED});

        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public synchronized int getPendingCount(String storeId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_RECORDS
                        + " WHERE " + COL_STORE_ID + " = ? AND "
                        + COL_SYNC_STATUS + " != ?",
                new String[]{storeId, DataHub.SYNC_SYNCED});

        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    /**
     * เปลี่ยนสถานะของรายการหลังผลการสำรอง
     */
    public synchronized boolean markSyncStatus(
            String storeId,
            String recordId,
            String status
    ) {
        if (isBlank(storeId) || isBlank(recordId)) {
            return false;
        }

        if (!DataHub.SYNC_PENDING.equals(status)
                && !DataHub.SYNC_SYNCED.equals(status)
                && !DataHub.SYNC_FAILED.equals(status)) {
            return false;
        }

        ContentValues values = new ContentValues();
        values.put(COL_SYNC_STATUS, status);

        return getWritableDatabase().update(
                TABLE_RECORDS,
                values,
                COL_STORE_ID + " = ? AND "
                        + COL_RECORD_ID + " = ?",
                new String[]{storeId, recordId}) == 1;
    }

    /**
     * กู้คืนเฉพาะรายการที่ไม่มีอยู่ในเครื่อง
     * ไม่เขียนทับข้อมูลท้องถิ่นที่มีอยู่แล้ว
     *
     * คืน true เมื่อเพิ่มรายการใหม่สำเร็จ
     */
    public synchronized boolean insertRestoredRecordIfAbsent(
            JSONObject record
    ) throws JSONException {

        if (!new BackupManager().isValidRecord(record)) {
            throw new IllegalArgumentException(
                    "Invalid backup record");
        }

        ContentValues values = new ContentValues();
        values.put(COL_RECORD_ID, record.getString("recordId"));
        values.put(COL_STORE_ID, record.getString("storeId"));
        values.put(COL_DATA_TYPE, record.getString("dataType"));
        values.put(COL_SCHEMA_VERSION,
                record.getInt("schemaVersion"));
        values.put(COL_UPDATED_AT,
                record.optLong("updatedAt",
                        System.currentTimeMillis()));
        values.put(COL_REVISION, record.getInt("revision"));
        values.put(COL_PAYLOAD,
                record.getJSONObject("payload").toString());

        // ข้อมูลมาจากคลาวด์แล้ว จึงถือว่ามีสำเนาบนคลาวด์
        values.put(COL_SYNC_STATUS, DataHub.SYNC_SYNCED);

        long result = getWritableDatabase().insertWithOnConflict(
                TABLE_RECORDS,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE);

        return result != -1L;
    }

    private JSONObject readRecord(
            Cursor cursor
    ) throws JSONException {

        return buildRecord(
                cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_RECORD_ID)),
                cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_STORE_ID)),
                cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_DATA_TYPE)),
                cursor.getInt(
                        cursor.getColumnIndexOrThrow(COL_SCHEMA_VERSION)),
                cursor.getLong(
                        cursor.getColumnIndexOrThrow(COL_UPDATED_AT)),
                cursor.getInt(
                        cursor.getColumnIndexOrThrow(COL_REVISION)),
                new JSONObject(cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_PAYLOAD))),
                cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_SYNC_STATUS)));
    }

    private JSONObject buildRecord(
            String recordId,
            String storeId,
            String dataType,
            int schemaVersion,
            long updatedAt,
            int revision,
            JSONObject payload,
            String syncStatus
    ) throws JSONException {

        JSONObject record = new JSONObject();
        record.put("recordId", recordId);
        record.put("storeId", storeId);
        record.put("dataType", dataType);
        record.put("schemaVersion", schemaVersion);
        record.put("updatedAt", updatedAt);
        record.put("revision", revision);
        record.put("payload", payload);
        record.put("syncStatus", syncStatus);
        return record;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
                    }
