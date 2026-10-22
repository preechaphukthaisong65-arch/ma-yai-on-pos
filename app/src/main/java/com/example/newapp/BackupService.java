package com.example.newapp;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Manual database backup and validated restore using Android's document picker. */
final class BackupService {
    private BackupService() {}

    static void exportTo(Context context,Uri destination) throws Exception {
        if(destination==null)throw new IllegalArgumentException("ไม่ได้เลือกตำแหน่งบันทึกไฟล์");
        PosDatabase helper=PosDatabase.get(context);
        SQLiteDatabase db=helper.getWritableDatabase();
        Cursor checkpoint=db.rawQuery("PRAGMA wal_checkpoint(FULL)",null);
        try { if(checkpoint.moveToFirst()&&checkpoint.getInt(0)!=0)throw new IllegalStateException("ฐานข้อมูลกำลังใช้งาน กรุณาลองใหม่"); }
        finally {checkpoint.close();}
        helper.close();
        File database=context.getDatabasePath(helper.getDatabaseName());
        if(!database.isFile())throw new IllegalStateException("ไม่พบไฟล์ฐานข้อมูล");
        try(InputStream in=new FileInputStream(database);
            OutputStream out=context.getContentResolver().openOutputStream(destination,"w")) {
            if(out==null)throw new IllegalStateException("เปิดไฟล์ปลายทางไม่ได้");
            copy(in,out);
            out.flush();
        }
    }

    static void restoreFrom(Context context,Uri source) throws Exception {
        if(source==null)throw new IllegalArgumentException("ไม่ได้เลือกไฟล์สำรอง");
        File incoming=new File(context.getCacheDir(),"pos-restore-incoming.db");
        File rollback=new File(context.getCacheDir(),"pos-restore-rollback.db");
        try {
            try(InputStream in=context.getContentResolver().openInputStream(source);
                OutputStream out=new FileOutputStream(incoming,false)) {
                if(in==null)throw new IllegalStateException("เปิดไฟล์สำรองไม่ได้");
                copy(in,out);out.flush();
            }
            validateBackup(incoming);

            PosDatabase helper=PosDatabase.get(context);
            helper.close();
            File live=context.getDatabasePath(helper.getDatabaseName());
            if(live.isFile())copyFile(live,rollback);
            deleteSidecars(live);
            try {
                copyFile(incoming,live);
                SQLiteDatabase restored=helper.getWritableDatabase();
                if(restored.getVersion()!=PosDatabase.currentVersion())
                    throw new IllegalStateException("อัปเกรดฐานข้อมูลจากไฟล์สำรองไม่สำเร็จ");
                Cursor check=restored.rawQuery("PRAGMA integrity_check",null);
                try {
                    if(!check.moveToFirst()||!"ok".equalsIgnoreCase(check.getString(0)))
                        throw new IllegalStateException("ฐานข้อมูลหลังนำเข้าไม่ผ่านการตรวจสอบ");
                } finally {check.close();}
            } catch(Exception failure) {
                helper.close();
                deleteSidecars(live);
                if(rollback.isFile())copyFile(rollback,live);
                helper.getWritableDatabase();
                throw new IllegalStateException("กู้คืนไฟล์สำรองไม่สำเร็จ ระบบคืนฐานข้อมูลเดิมแล้ว",failure);
            }
        } finally {
            incoming.delete();
            rollback.delete();
        }
    }

    private static void validateBackup(File file) throws Exception {
        if(!file.isFile()||file.length()<1024L)throw new IllegalArgumentException("ไฟล์สำรองว่างหรือไม่ถูกต้อง");
        SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
        try {
            Cursor integrity=db.rawQuery("PRAGMA integrity_check",null);
            try {
                if(!integrity.moveToFirst()||!"ok".equalsIgnoreCase(integrity.getString(0)))
                    throw new IllegalArgumentException("ไฟล์สำรองเสียหาย");
            } finally {integrity.close();}
            int version=db.getVersion();
            if(version<1||version>PosDatabase.currentVersion())
                throw new IllegalArgumentException("เวอร์ชันฐานข้อมูลในไฟล์สำรองไม่รองรับ");
            String[] required={"products","customers","bills","bill_items","stock_history","price_history",
                    "barcode_registry","payments","payment_history","sales_ledger","sales_ledger_items","app_meta"};
            for(String table:required) {
                Cursor exists=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                        new String[]{table});
                try {if(!exists.moveToFirst())throw new IllegalArgumentException("ไฟล์สำรองไม่มีตาราง "+table);}
                finally {exists.close();}
            }
        } finally {db.close();}
    }

    private static void deleteSidecars(File database) {
        new File(database.getPath()+"-wal").delete();
        new File(database.getPath()+"-shm").delete();
        new File(database.getPath()+"-journal").delete();
    }
    private static void copyFile(File source,File destination) throws Exception {
        try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(destination,false)) {
            copy(in,out);out.flush();
        }
    }
    private static void copy(InputStream in,OutputStream out) throws Exception {
        byte[] buffer=new byte[32768];int read;
        while((read=in.read(buffer))!=-1)out.write(buffer,0,read);
    }
}