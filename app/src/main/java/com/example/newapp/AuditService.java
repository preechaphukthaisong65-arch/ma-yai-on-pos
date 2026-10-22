package com.example.newapp;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/** Writes compact, non-sensitive audit events inside the caller's transaction. */
final class AuditService {
    private AuditService() {}

    static void record(SQLiteDatabase db,String entityType,String entityId,String action,String details,long timestamp) {
        if(db==null||entityType==null||entityType.trim().isEmpty()||
                entityId==null||entityId.trim().isEmpty()||action==null||action.trim().isEmpty()||timestamp<0)
            throw new IllegalArgumentException("ข้อมูลประวัติการทำรายการไม่ถูกต้อง");
        ContentValues values=new ContentValues();
        values.put("entity_type",entityType.trim());
        values.put("entity_id",entityId.trim());
        values.put("action",action.trim());
        values.put("details",details==null?"":details);
        values.put("created_at",timestamp);
        db.insertOrThrow("audit_log",null,values);
    }
}