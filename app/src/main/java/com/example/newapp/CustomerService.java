package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;

/** Shared customer identity and lookup rules. */
final class CustomerService {
    private CustomerService() {}

    /** Caller must include this operation in its existing transaction. */
    static long findOrCreate(SQLiteDatabase db,String fullName,long timestamp) {
        if(db==null||fullName==null||fullName.trim().isEmpty())
            throw new IllegalArgumentException("กรอกชื่อเต็มลูกค้า");
        String name=fullName.trim();
        Cursor c=db.query("customers",new String[]{"id"},"full_name=?",
                new String[]{name},null,null,null,"1");
        try { if(c.moveToFirst())return c.getLong(0); }
        finally { c.close(); }
        ContentValues values=new ContentValues();values.put("full_name",name);values.put("created_at",timestamp);
        long id=db.insertOrThrow("customers",null,values);
        AuditService.record(db,"CUSTOMER",String.valueOf(id),"CUSTOMER_CREATED","source=SALE",timestamp);
        return id;
    }

    static ArrayList<String> listNames(PosDatabase helper) {
        Cursor c=helper.getReadableDatabase().query("customers",new String[]{"full_name"},
                null,null,null,null,"full_name COLLATE NOCASE ASC");
        ArrayList<String> names=new ArrayList<>();
        try { while(c.moveToNext())names.add(c.getString(0)); }
        finally { c.close(); }
        return names;
    }
}