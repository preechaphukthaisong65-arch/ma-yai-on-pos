package com.example.newapp.datahub;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONException;
import org.json.JSONObject;

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
        String sql = "CREATE TABLE " + TABLE_RECORDS + " ("
                + COL_RECORD_ID + " TEXT NOT NULL, "
                + COL_STORE_ID + " TEXT NOT NULL, "
                + COL_DATA_TYPE + " TEXT NOT NULL, "
                + COL_SCHEMA_VERSION + " INTEGER NOT NULL DEFAULT 1, "
                + COL_UPDATED_AT + " INTEGER NOT NULL, "
                + COL_REVISION + " INTEGER NOT NULL DEFAULT 1, "
                + COL_PAYLOAD + " TEXT NOT NULL, "
                + COL_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'pending', "
                + "PRIMARY KEY (" + COL_STORE_ID + ", "
                + COL_RECORD_ID + ")"
                + ")";

        db.execSQL(sql);

        db.execSQL(
                "CREATE INDEX idx_hub_records_sync "
                        + "ON " + TABLE_RECORDS
                        + " (" + COL_SYNC_STATUS + ")"
        );
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion
    ) {
        // ห้ามลบข้อมูลเดิมโดยอัตโนมัติ
        // เพิ่มคำสั่ง migration เมื่อมี schema รุ่นใหม่
    }

    /**
     * บันทึกข้อมูลในเครื่อง
     * หากมี recordId และ storeId เดิม จะปรับปรุงรายการนั้น
     */
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
                    "Record fields must not be empty"
            );
        }

        // ตรวจสอบว่า payload เป็น JSON ที่ถูกต้อง
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
                null,
                null,
                null
        );

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

        db.insertWithOnConflict(
                TABLE_RECORDS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );

        return buildRecord(
                recordId,
                storeId,
                dataType,
                1,
                now,
                revision,
                payloadObject,
                DataHub.SYNC_PENDING
        );
    }

    /**
     * อ่านข้อมูลโดยระบุทั้ง storeId และ recordId
     */
    public synchronized JSONObject getRecord(
            String storeId,
            String recordId
    ) throws JSONException {

        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_RECORDS,
                null,
                COL_STORE_ID + " = ? AND "
                        + COL_RECORD_ID + " = ?",
                new String[]{storeId, recordId},
                null,
                null,
                null,
                "1"
        );

        try {
            if (!cursor.moveToFirst()) {
                return null;
            }

            return buildRecord(
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(COL_RECORD_ID)
                    ),
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(COL_STORE_ID)
                    ),
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(COL_DATA_TYPE)
                    ),
                    cursor.getInt(
                            cursor.getColumnIndexOrThrow(COL_SCHEMA_VERSION)
                    ),
                    cursor.getLong(
                            cursor.getColumnIndexOrThrow(COL_UPDATED_AT)
                    ),
                    cursor.getInt(
                            cursor.getColumnIndexOrThrow(COL_REVISION)
                    ),
                    new JSONObject(
                            cursor.getString(
                                    cursor.getColumnIndexOrThrow(COL_PAYLOAD)
                            )
                    ),
                    cursor.getString(
                            cursor.getColumnIndexOrThrow(COL_SYNC_STATUS)
                    )
            );
        } finally {
            cursor.close();
        }
    }

    /**
     * นับจำนวนรายการที่ยังรอสำรอง
     */
    public synchronized int getPendingCount() {
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_RECORDS
                        + " WHERE " + COL_SYNC_STATUS + " = ?",
                new String[]{DataHub.SYNC_PENDING}
        );

        try {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }

            return 0;
        } finally {
            cursor.close();
        }
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
