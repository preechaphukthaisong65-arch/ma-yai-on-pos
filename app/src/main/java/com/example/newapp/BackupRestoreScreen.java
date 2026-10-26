package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** User-controlled local database export and validated restore. */
public class BackupRestoreScreen extends Activity {
    private static final int EXPORT_REQUEST = 8101;
    private static final int IMPORT_REQUEST = 8102;
    private PosDatabase helper;
    private LinearLayout root;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        helper = PosDatabase.get(this);
        home();
    }

    private void home() {
        root = PosUi.page(this, "สำรอง / กู้คืนข้อมูลร้าน");
        PosUi.line(this, root, "ส่งออกฐานข้อมูลทั้งหมดเป็นไฟล์สำรอง เก็บไว้ในพื้นที่ปลอดภัย");
        PosUi.button(this, root, "ส่งออกไฟล์สำรอง", v -> exportPicker());
        PosUi.line(this, root, "กู้คืนจะแทนที่ข้อมูลปัจจุบันทั้งหมด ควรส่งออกข้อมูลปัจจุบันก่อนทุกครั้ง");
        PosUi.button(this, root, "กู้คืนจากไฟล์สำรอง", v -> new AlertDialog.Builder(this)
                .setTitle("ยืนยันกู้คืนข้อมูล")
                .setMessage("การกู้คืนจะแทนที่ข้อมูลสินค้า บิล ลูกค้า หนี้ และประวัติในเครื่องนี้ทั้งหมด ดำเนินการต่อหรือไม่?")
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("เลือกไฟล์สำรอง", (d, w) -> importPicker()).show());
        PosUi.back(this, root);
    }

    private void exportPicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "maeyai-on-pos-backup-" +
                new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                        .format(new java.util.Date()) + ".db");
        startActivityForResult(intent, EXPORT_REQUEST);
    }

    private void importPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, IMPORT_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == EXPORT_REQUEST) exportTo(uri);
        else if (requestCode == IMPORT_REQUEST) restoreFrom(uri);
    }

    private void exportTo(Uri destination) {
        File database = getDatabasePath("maeyai_on_pos.db");
        FileInputStream input = null;
        OutputStream output = null;
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            Cursor checkpoint = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null);
            checkpoint.moveToFirst();
            checkpoint.close();
            input = new FileInputStream(database);
            output = getContentResolver().openOutputStream(destination, "w");
            if (output == null) throw new IllegalStateException("เปิดไฟล์ปลายทางไม่ได้");
            copy(input, output);
            output.flush();
            PosUi.result(this, true, "ส่งออกไฟล์สำรองเรียบร้อยแล้ว เก็บไฟล์นี้ไว้ในที่ปลอดภัย", this::home);
        } catch (Exception e) {
            PosUi.result(this, false, "ส่งออกข้อมูลไม่สำเร็จ: " + safeMessage(e), null);
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    private void restoreFrom(Uri source) {
        File database = getDatabasePath("maeyai_on_pos.db");
        File temp = new File(database.getAbsolutePath() + ".import.tmp");
        File previous = new File(database.getAbsolutePath() + ".before-restore-" + System.currentTimeMillis());
        InputStream input = null;
        OutputStream output = null;
        boolean movedOriginal = false;
        boolean installedBackup = false;
        try {
            input = getContentResolver().openInputStream(source);
            if (input == null) throw new IllegalStateException("อ่านไฟล์สำรองไม่ได้");
            output = new FileOutputStream(temp, false);
            copy(input, output);
            output.flush();
            closeQuietly(input); input = null;
            closeQuietly(output); output = null;
            validateBackup(temp);

            helper.close();
            if (database.exists()) {
                if (!database.renameTo(previous)) throw new IllegalStateException("ย้ายฐานข้อมูลเดิมเพื่อความปลอดภัยไม่สำเร็จ");
                movedOriginal = true;
            }
            if (!temp.renameTo(database)) throw new IllegalStateException("ติดตั้งไฟล์สำรองไม่สำเร็จ");
            installedBackup = true;
            // Opening through SQLiteOpenHelper applies any required schema migrations.
            helper.getWritableDatabase();
            PosUi.result(this, true, "กู้คืนข้อมูลเรียบร้อยแล้ว", this::home);
        } catch (Exception e) {
            try {
                helper.close();
                if (installedBackup && database.exists()) database.delete();
                if (movedOriginal && previous.exists()) previous.renameTo(database);
                helper.getWritableDatabase();
            } catch (Exception ignored) { }
            temp.delete();
            PosUi.result(this, false, "กู้คืนไม่สำเร็จ ระบบพยายามคืนฐานข้อมูลเดิมให้แล้ว: " + safeMessage(e), null);
        } finally {
            closeQuietly(input);
            closeQuietly(output);
            if (temp.exists()) temp.delete();
        }
    }

    private void validateBackup(File file) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            Cursor integrity = db.rawQuery("PRAGMA integrity_check", null);
            boolean valid = integrity.moveToFirst() && "ok".equalsIgnoreCase(integrity.getString(0));
            integrity.close();
            if (!valid) throw new IllegalArgumentException("ไฟล์สำรองเสียหาย");
            String[] required = {"products", "bills", "customers", "bill_items", "app_meta"};
            for (String table : required) {
                Cursor c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new String[]{table});
                boolean found = c.moveToFirst();
                c.close();
                if (!found) throw new IllegalArgumentException("ไฟล์นี้ไม่ใช่ฐานข้อมูล POS ที่รองรับ");
            }
        } finally {
            db.close();
        }
    }

    private static void copy(InputStream input, OutputStream output) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) try { closeable.close(); } catch (Exception ignored) { }
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? "ข้อผิดพลาดของไฟล์หรือฐานข้อมูล" : e.getMessage();
    }
}
