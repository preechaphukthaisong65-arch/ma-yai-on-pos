package com.example.newapp;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/** Writes compact, non-sensitive audit events inside the caller's transaction. */
final class AuditService {
    private AuditService() {}

    static final class Event {
        final long id,createdAt;
        final String entityType,entityId,action,details;
        Event(long id,String entityType,String entityId,String action,String details,long createdAt) {
            this.id=id;this.entityType=entityType;this.entityId=entityId;this.action=action;
            this.details=details;this.createdAt=createdAt;
        }
    }

    static java.util.ArrayList<Event> recent(PosDatabase helper,int limit) {
        int safeLimit=Math.max(1,Math.min(500,limit));
        android.database.Cursor c=helper.getReadableDatabase().query("audit_log",
                new String[]{"id","entity_type","entity_id","action","details","created_at"},
                null,null,null,null,"created_at DESC,id DESC",String.valueOf(safeLimit));
        java.util.ArrayList<Event> events=new java.util.ArrayList<>();
        try {
            while(c.moveToNext())events.add(new Event(c.getLong(0),c.getString(1),c.getString(2),
                    c.getString(3),c.getString(4),c.getLong(5)));
        } finally {c.close();}
        return events;
    }

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