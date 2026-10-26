package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Offline database backup and schema-validated restore using Android's document picker. */
public final class BackupRestoreScreen extends Activity {
    private static final int REQUEST_EXPORT = 7401;
    private static final int REQUEST_IMPORT = 7402;
    private static final long MAX_BACKUP_BYTES = 100L * 1024L * 1024L;
    private static final String DB_NAME = "maeyai_on_pos.db";
    private LinearLayout root;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = PosUi.page(this, "สำรองและกู้คืนข้อมูลร้าน");
        PosUi.line(this, root, "ไฟล์นี้มีข้อมูลสินค้า ลูกค้า หนี้ และประวัติการขาย โปรดเก็บในพื้นที่ส่วนตัว ห้ามส่งต่อให้บุคคลอื่น");
        PosUi.button(this, root, "ส่งออกไฟล์สำรอง", v -> exportPicker());
        PosUi.button(this, root, "กู้คืนจากไฟล์สำรอง", v -> importPicker());
        PosUi.back(this, root);
    }

    private void exportPicker() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, "maeyai-pos-backup-" + System.currentTimeMillis() + ".db");
        startActivityForResult(i, REQUEST_EXPORT);
    }

    private void importPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQUEST_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_EXPORT) exportDatabase(data.getData());
        else if (requestCode == REQUEST_IMPORT) stageImport(data.getData());
    }

    private void exportDatabase(Uri destination) {
        File source = getDatabasePath(DB_NAME);
        try {
            SQLiteDatabase db = PosDatabase.get(this).getWritableDatabase();
            Cursor checkpoint = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null);
            boolean busy = checkpoint.moveToFirst() && checkpoint.getInt(0) != 0;
            checkpoint.close();
            if (busy) throw new IllegalStateException("ฐานข้อมูลกำลังถูกใช้งาน กรุณาลองสำรองอีกครั้ง");
            if (!source.isFile() || source.length() <= 0L) throw new IllegalStateException("ไม่พบไฟล์ฐานข้อมูล");
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (out == null) throw new IllegalStateException("เปิดไฟล์ปลายทางไม่ได้");
                copyLimited(in, out, MAX_BACKUP_BYTES);
                out.flush();
            }
            PosUi.result(this, true, "ส่งออกไฟล์สำรองแล้ว โปรดเก็บไฟล์ไว้ในพื้นที่ปลอดภัย", null);
        } catch (Exception e) {
            PosUi.result(this, false, "สำรองข้อมูลไม่สำเร็จ: " + message(e), null);
        }
    }

    private void stageImport(Uri source) {
        File staged = new File(getCacheDir(), "maeyai-restore-staged.db");
        try {
            try (InputStream in = getContentResolver().openInputStream(source);
                 OutputStream out = new FileOutputStream(staged, false)) {
                if (in == null) throw new IllegalStateException("เปิดไฟล์สำรองไม่ได้");
                copyLimited(in, out, MAX_BACKUP_BYTES);
                out.flush();
            }
            validateBackup(staged);
            EditText confirm = new EditText(this);
            confirm.setSingleLine(true);
            confirm.setHint("พิมพ์ RESTORE เพื่อยืนยัน");
            new AlertDialog.Builder(this)
                    .setTitle("ยืนยันกู้คืนข้อมูลร้าน")
                    .setMessage("การกู้คืนจะแทนที่ข้อมูลปัจจุบันทั้งหมด ระบบจะเก็บฐานข้อมูลเดิมไว้ก่อนเปลี่ยนไฟล์ ควรส่งออกข้อมูลปัจจุบันก่อนดำเนินการ")
                    .setView(confirm)
                    .setNegativeButton("ยกเลิก", (d, w) -> staged.delete())
                    .setPositiveButton("กู้คืน", (d, w) -> {
                        if (!"RESTORE".equals(confirm.getText().toString().trim())) {
                            staged.delete();
                            PosUi.result(this, false, "คำยืนยันไม่ถูกต้อง ยกเลิกการกู้คืนแล้ว", null);
                            return;
                        }
                        restore(staged);
                    }).show();
        } catch (Exception e) {
            staged.delete();
            PosUi.result(this, false, "ไฟล์สำรองไม่ผ่านการตรวจสอบ: " + message(e), null);
        }
    }

    private void validateBackup(File file) throws Exception {
        if (!file.isFile() || file.length() <= 0L || file.length() > MAX_BACKUP_BYTES)
            throw new IllegalArgumentException("ขนาดไฟล์ไม่ถูกต้อง");
        SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            Cursor integrity = db.rawQuery("PRAGMA integrity_check", null);
            String result = integrity.moveToFirst() ? integrity.getString(0) : "";
            integrity.close();
            if (!"ok".equalsIgnoreCase(result)) throw new IllegalArgumentException("ตรวจสอบความสมบูรณ์ SQLite ไม่ผ่าน");
            Cursor version = db.rawQuery("PRAGMA user_version", null);
            int savedVersion = version.moveToFirst() ? version.getInt(0) : -1;
            version.close();
            if (savedVersion != PosDatabase.currentVersion())
                throw new IllegalArgumentException("เวอร์ชันฐานข้อมูลไม่ตรงกับแอปนี้ (ไฟล์ " + savedVersion + ", แอป " + PosDatabase.currentVersion() + ")");
            String[] required = {"products", "customers", "bills", "bill_items", "sales_ledger", "audit_log", "app_meta"};
            for (String table : required) {
                Cursor found = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new String[]{table});
                boolean exists = found.moveToFirst();
                found.close();
                if (!exists) throw new IllegalArgumentException("ไม่พบตารางสำคัญ: " + table);
            }
        } finally {
            db.close();
        }
    }

    private void restore(File staged) {
        File live = getDatabasePath(DB_NAME);
        File old = new File(live.getAbsolutePath() + ".pre-restore-" + System.currentTimeMillis());
        File replacement = new File(live.getAbsolutePath() + ".restore");
        boolean originalMoved = false;
        try {
            validateBackup(staged);
            SQLiteDatabase current = PosDatabase.get(this).getWritableDatabase();
            Cursor checkpoint = current.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null);
            boolean busy = checkpoint.moveToFirst() && checkpoint.getInt(0) != 0;
            checkpoint.close();
            if (busy) throw new IllegalStateException("ฐานข้อมูลกำลังถูกใช้งาน กรุณาลองใหม่");
            PosDatabase.closeInstanceForRestore();
            deleteSidecars(live);
            copyFile(staged, replacement);
            if (live.exists()) {
                if (!live.renameTo(old)) throw new IllegalStateException("ไม่สามารถเก็บฐานข้อมูลเดิมไว้ได้");
                originalMoved = true;
            }
            if (!replacement.renameTo(live)) throw new IllegalStateException("ไม่สามารถแทนที่ฐานข้อมูลได้");
            deleteSidecars(live);
            SQLiteDatabase restored = PosDatabase.get(this).getWritableDatabase();
            Cursor integrity = restored.rawQuery("PRAGMA integrity_check", null);
            String result = integrity.moveToFirst() ? integrity.getString(0) : "";
            integrity.close();
            if (!"ok".equalsIgnoreCase(result)) throw new IllegalStateException("ตรวจสอบฐานข้อมูลหลังติดตั้งไม่ผ่าน");
            staged.delete();
            new AlertDialog.Builder(this).setTitle("กู้คืนข้อมูลสำเร็จ")
                    .setMessage("กู้คืนแล้ว กรุณาตรวจสอบสินค้า ลูกหนี้ และประวัติขายก่อนเริ่มขาย ข้อมูลเดิมถูกเก็บไว้ในพื้นที่ภายในแอป")
                    .setPositiveButton("กลับหน้าแรก", (d, w) -> {
                        Intent i = new Intent(this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    }).setCancelable(false).show();
        } catch (Exception e) {
            PosDatabase.closeInstanceForRestore();
            replacement.delete();
            if (originalMoved) {
                live.delete();
                deleteSidecars(live);
                if (!old.renameTo(live)) {
                    PosUi.result(this, false, "กู้คืนล้มเหลว และกู้ฐานข้อมูลเดิมกลับไม่สำเร็จ ไฟล์เดิมยังอยู่ที่ " + old.getAbsolutePath() + " กรุณาห้ามถอนการติดตั้งแอป", null);
                    return;
                }
            }
            staged.delete();
            PosUi.result(this, false, "กู้คืนไม่สำเร็จ ระบบพยายามคืนฐานข้อมูลเดิมแล้ว: " + message(e), null);
        }
    }

    private static void deleteSidecars(File db) {
        new File(db.getAbsolutePath() + "-wal").delete();
        new File(db.getAbsolutePath() + "-shm").delete();
        new File(db.getAbsolutePath() + "-journal").delete();
    }

    private static void copyFile(File source, File destination) throws Exception {
        try (InputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination, false)) {
            copyLimited(in, out, MAX_BACKUP_BYTES);
            out.flush();
            out.getFD().sync();
        }
    }

    private static void copyLimited(InputStream in, OutputStream out, long limit) throws Exception {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IllegalArgumentException("ไฟล์ใหญ่เกินขนาดที่รองรับ");
            out.write(buffer, 0, read);
        }
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? "ข้อผิดพลาดที่ไม่ทราบสาเหตุ" : e.getMessage();
    }
}
