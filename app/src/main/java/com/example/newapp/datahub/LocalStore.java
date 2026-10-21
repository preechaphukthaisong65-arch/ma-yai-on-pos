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
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // Safe, non-destructive index for store/type queries and large stock ledgers.
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_hub_records_store_type_updated "
                + "ON " + TABLE_RECORDS + " (" + COL_STORE_ID + ", "
                + COL_DATA_TYPE + ", " + COL_UPDATED_AT + " DESC)");
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion
    ) {
        // ห้ามลบข้อมูลอัตโนมัติเมื่อเปลี่ยนเวอร์ชันฐานข้อมูล
    }

    /**
     * บันทึกข้อมูลจาก DataHub
     * เมธอดนี้รับ JSONObject ตามที่ DataHub เรียกใช้
     */
    public synchronized boolean saveRecord(JSONObject record)
            throws JSONException {

        if (record == null
                || !new RecordValidator().isValidRecord(record)) {
            return false;
        }

        String recordId = record.getString("recordId").trim();
        String storeId = record.getString("storeId").trim();
        String dataType = record.getString("dataType").trim();

        if (isBlank(recordId)
                || isBlank(storeId)
                || isBlank(dataType)
                || !record.has("payload")
                || !(record.opt("payload") instanceof JSONObject)) {
            return false;
        }

        int schemaVersion = Math.max(
                1,
                record.optInt("schemaVersion", 1)
        );

        long updatedAt = Math.max(
                1L,
                record.optLong(
                        "updatedAt",
                        System.currentTimeMillis()
                )
        );

        long revision = Math.max(
                1L,
                record.optLong("revision", 1L)
        );

        JSONObject payload = record.getJSONObject("payload");

        ContentValues values = new ContentValues();
        values.put(COL_RECORD_ID, recordId);
        values.put(COL_STORE_ID, storeId);
        values.put(COL_DATA_TYPE, dataType);
        values.put(COL_SCHEMA_VERSION, schemaVersion);
        values.put(COL_UPDATED_AT, updatedAt);
        values.put(COL_REVISION, revision);
        values.put(COL_PAYLOAD, payload.toString());
        values.put(COL_SYNC_STATUS, DataHub.SYNC_PENDING);

        long result = getWritableDatabase().insertWithOnConflict(
                TABLE_RECORDS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );

        return result != -1L;
    }

    /**
     * บันทึกข้อมูลด้วยพารามิเตอร์แบบเดิม
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

        JSONObject payloadObject = new JSONObject(payload);
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        long revision = 1L;

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
                revision = cursor.getLong(0) + 1L;
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
                SQLiteDatabase.CONFLICT_REPLACE
        );

        if (result == -1L) {
            throw new IllegalStateException(
                    "บันทึกข้อมูลลงฐานข้อมูลไม่สำเร็จ"
            );
        }

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
     * อ่านข้อมูลตามรหัสร้านค้าและรหัสรายการ
     */
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
                null,
                null,
                null,
                "1"
        );

        try {
            return cursor.moveToFirst()
                    ? readRecord(cursor)
                    : null;
        } finally {
            cursor.close();
        }
    }

    /**
     * อ่านรายการที่ยังไม่ได้ซิงก์ รวมรายการที่เคยล้มเหลว
     */
    public synchronized List<JSONObject> getRecordsNeedingSync(
            String storeId,
            int limit
    ) throws JSONException {

        if (isBlank(storeId)) {
            throw new IllegalArgumentException(
                    "storeId must not be blank"
            );
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
                Integer.toString(Math.max(1, limit))
        );

        try {
            while (cursor.moveToNext()) {
                records.add(readRecord(cursor));
            }
        } finally {
            cursor.close();
        }

        return records;
    }

    /** Read records of a type for one store, newest first. */
    public synchronized List<JSONObject> getRecordsByType(String storeId, String dataType) throws JSONException {
        if (isBlank(storeId) || isBlank(dataType)) throw new IllegalArgumentException("storeId/dataType must not be blank");
        List<JSONObject> records = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(TABLE_RECORDS, null,
                COL_STORE_ID + " = ? AND " + COL_DATA_TYPE + " = ?",
                new String[]{storeId.trim(), dataType.trim()}, null, null, COL_UPDATED_AT + " DESC");
        try { while (cursor.moveToNext()) records.add(readRecord(cursor)); }
        finally { cursor.close(); }
        return records;
    }

    /** Save related records atomically; any failure rolls the whole transaction back. */
    public synchronized boolean saveRecordsAtomically(List<JSONObject> records) {
        if (records == null || records.isEmpty()) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (JSONObject record : records) {
                if (record == null) return false;
                // Validate expected revisions inside the same SQLite transaction
                // as the writes, so a stale screen cannot overwrite a newer local
                // product or bill after it has read the record.
                if (record.has("expectedRevision")) {
                    String expectedStore = record.optString("storeId", "").trim();
                    String expectedId = record.optString("recordId", "").trim();
                    long expectedRevision = record.optLong("expectedRevision", -1L);
                    if (isBlank(expectedStore) || isBlank(expectedId) || expectedRevision < 0L) {
                        return false;
                    }
                    long actualRevision = 0L;
                    Cursor revisionCursor = db.query(
                            TABLE_RECORDS,
                            new String[]{COL_REVISION},
                            COL_STORE_ID + " = ? AND " + COL_RECORD_ID + " = ?",
                            new String[]{expectedStore, expectedId},
                            null, null, null, "1"
                    );
                    try {
                        if (revisionCursor.moveToFirst()) actualRevision = revisionCursor.getLong(0);
                    } finally {
                        revisionCursor.close();
                    }
                    if (actualRevision != expectedRevision) return false;
                }
                if (!saveRecord(record)) return false;
            }
            db.setTransactionSuccessful();
            return true;
        } catch (Exception error) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Persist snapshots returned by a successful server transaction and mark them
     * synced in the SAME SQLite transaction. They already exist in Firestore;
     * sending them through the generic sync worker could create a second,
     * stale write after the server has committed the sale.
     */
    public synchronized boolean saveServerCommittedRecordsAtomically(List<JSONObject> records) {
        if (records == null || records.isEmpty()) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            java.util.HashSet<String> keys = new java.util.HashSet<>();
            for (JSONObject record : records) {
                if (record == null || !new RecordValidator().isValidRecord(record)) return false;
                String storeId = record.optString("storeId", "").trim();
                String recordId = record.optString("recordId", "").trim();
                if (isBlank(storeId) || isBlank(recordId)
                        || !keys.add(storeId + "::" + recordId)) return false;
                if (record.has("expectedRevision")) {
                    long expected = record.optLong("expectedRevision", -1L);
                    long actual = 0L;
                    Cursor cursor = db.query(TABLE_RECORDS, new String[]{COL_REVISION},
                            COL_STORE_ID + " = ? AND " + COL_RECORD_ID + " = ?",
                            new String[]{storeId, recordId}, null, null, null, "1");
                    try { if (cursor.moveToFirst()) actual = cursor.getLong(0); }
                    finally { cursor.close(); }
                    if (expected < 0L || actual != expected) return false;
                }
                if (!saveRecord(record)) return false;
                ContentValues synced = new ContentValues();
                synced.put(COL_SYNC_STATUS, DataHub.SYNC_SYNCED);
                int changed = db.update(TABLE_RECORDS, synced,
                        COL_STORE_ID + " = ? AND " + COL_RECORD_ID + " = ? AND " + COL_REVISION + " = ?",
                        new String[]{storeId, recordId, Long.toString(Math.max(1L, record.optLong("revision", 1L)))});
                if (changed != 1) return false;
            }
            db.setTransactionSuccessful();
            return true;
        } catch (Exception error) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * นับทุกรายการที่ยังไม่ได้ซิงก์สำเร็จ
     */
    public synchronized int getPendingCount() {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_RECORDS
                        + " WHERE " + COL_SYNC_STATUS + " != ?",
                new String[]{DataHub.SYNC_SYNCED}
        );

        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    /**
     * นับรายการที่ยังไม่ได้ซิงก์ของร้านค้าที่ระบุ
     */
    public synchronized int getPendingCount(String storeId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_RECORDS
                        + " WHERE " + COL_STORE_ID + " = ? AND "
                        + COL_SYNC_STATUS + " != ?",
                new String[]{storeId, DataHub.SYNC_SYNCED}
        );

        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    /**
     * เปลี่ยนสถานะหลังการซิงก์ข้อมูล
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
                new String[]{storeId, recordId}
        ) == 1;
    }

    /**
     * Mark an upload result only if the local record has not changed
     * since the upload started. Prevents an older callback from marking
     * a newer revision as synced.
     */
    public synchronized boolean markSyncStatusIfRevision(
            String storeId,
            String recordId,
            long revision,
            String status
    ) {
        if (isBlank(storeId) || isBlank(recordId)) return false;
        if (!DataHub.SYNC_PENDING.equals(status)
                && !DataHub.SYNC_SYNCED.equals(status)
                && !DataHub.SYNC_FAILED.equals(status)) return false;

        ContentValues values = new ContentValues();
        values.put(COL_SYNC_STATUS, status);
        return getWritableDatabase().update(
                TABLE_RECORDS,
                values,
                COL_STORE_ID + " = ? AND " + COL_RECORD_ID + " = ? AND "
                        + COL_REVISION + " = ?",
                new String[]{storeId, recordId, Long.toString(revision)}
        ) == 1;
    }

    /** Delete only an unchanged revision whose cloud deletion was confirmed. */
    public synchronized boolean deleteRecordIfSyncedAndRevision(String storeId, String recordId, long revision) {
        if (isBlank(storeId) || isBlank(recordId) || revision < 1L) return false;
        return getWritableDatabase().delete(TABLE_RECORDS,
                COL_STORE_ID + " = ? AND " + COL_RECORD_ID + " = ? AND "
                        + COL_REVISION + " = ? AND " + COL_SYNC_STATUS + " = ?",
                new String[]{storeId.trim(), recordId.trim(), Long.toString(revision),
                        DataHub.SYNC_SYNCED}) == 1;
    }

    /**
     * อ่านข้อมูลรายการจากฐานข้อมูลในเครื่อง
     */
    private JSONObject readRecord(
            Cursor cursor
    ) throws JSONException {

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
                cursor.getLong(
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
    }

    private JSONObject buildRecord(
            String recordId,
            String storeId,
            String dataType,
            int schemaVersion,
            long updatedAt,
            long revision,
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
