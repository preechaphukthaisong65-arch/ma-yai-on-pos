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

/** User-controlled offline database export and schema-checked restore. */
public final class BackupRestoreScreen extends Activity {
    private static final int REQUEST_EXPORT = 7401;
    private static final int REQUEST_IMPORT = 7402;
    private static final long MAX_BACKUP_BYTES = 100L * 1024L * 1024L;
    private LinearLayout root;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        home();
    }

    private void home() {
        root = PosUi.page(this, "สำรองและกู้คืนข้อมูลร้าน");
        PosUi.line(this, root, "ไฟล์สำรองมีข้อมูลสินค้า ลูกค้า ยอดหนี้ และประวัติการขาย ควรเก็บในพื้นที่ส่วนตัวและไม่ส่งต่อให้บุคคลอื่น");
        PosUi.button(this, root, "ส่งออกไฟล์สำรองฐานข้อมูล", v -> exportPicker());
        PosUi.button(this, root, "กู้คืนจากไฟล์สำรอง", v -> importPicker());
        PosUi.back(this, root);
    }

    private void exportPicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "maeyai-pos-backup-" + System.currentTimeMillis() + ".db");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void importPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) exportDatabase(uri);
        else if (requestCode == REQUEST_IMPORT) stageAndConfirmRestore(uri);
    }

    private void exportDatabase(Uri destination) {
        File source = getDatabasePath("maeyai_on_pos.db");
        try {
            SQLiteDatabase db = PosDatabase.get(this).getWritableDatabase();
            Cursor checkpoint = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null);
            boolean busy = checkpoint.moveToFirst() && checkpoint.getInt(0) != 0;
            checkpoint.close();
            if (busy) throw new IllegalStateException("ฐานข้อมูลกำลังถูกใช้งาน กรุณาลองสำรองอีกครั้ง");
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (out == null) throw new IllegalStateException("เปิดไฟล์ปลายทางไม่ได้");
                copy(in, out, MAX_BACKUP_BYTES);
                out.flush();
            }
            PosUi.result(this, true, "ส่งออกไฟล์สำรองแล้ว กรุณาเก็บไฟล์นี้ในพื้นที่ปลอดภัย", null);
        } catch (Exception e) {
            PosUi.result(this, false, "สำรองข้อมูลไม่สำเร็จ: " + safeMessage(e), null);
        }
    }

    private void stageAndConfirmRestore(Uri source) {
        File staged = new File(getCacheDir(), "maeyai-restore-staged.db");
        try {
            try (InputStream in = getContentResolver().openInputStream(source);
                 OutputStream out = new FileOutputStream(staged, false)) {
                if (in == null) throw new IllegalStateException("เปิดไฟล์สำรองไม่ได้");
                copy(in, out, MAX_BACKUP_BYTES);
                out.flush();
            }
            validateBackup(staged);
            EditText confirmation = PosUi.input(this, root, "พิมพ์ RESTORE เพื่อยืนยันการแทนที่ข้อมูลปัจจุบัน");
            new AlertDialog.Builder(this)
                    .setTitle("ยืนยันกู้คืนฐานข้อมูล")
                    .setMessage("การกู้คืนจะแทนที่ข้อมูลปัจจุบันทั้งหมด ระบบจะเก็บฐานข้อมูลเดิมเป็นไฟล์สำรองภายในแอปก่อนเปลี่ยนไฟล์ แนะนำให้ส่งออกข้อมูลปัจจุบันก่อนดำเนินการ")
                    .setView(confirmation)
                    .setNegativeButton("ยกเลิก", (d, w) -> staged.delete())
                    .setPositiveButton("กู้คืนข้อมูล", (d, w) -> {
                        if (!"RESTORE".equals(confirmation.getText().toString().trim())) {
                            staged.delete();
                            PosUi.result(this, false, "คำยืนยันไม่ถูกต้อง ยกเลิกการกู้คืนแล้ว", null);
                            return;
                        }
                        restoreVerified(staged);
                    }).show();
        } catch (Exception e) {
            staged.delete();
            PosUi.result(this, false, "ไฟล์สำรองไม่ผ่านการตรวจสอบ: " + safeMessage(e), null);
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
            if (!"ok".equalsIgnoreCase(result)) throw new IllegalArgumentException("ตรวจสอบความสมบูรณ์ของ SQLite ไม่ผ่าน");
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

    private void restoreVerified(File staged) {
        File live = getDatabasePath("maeyai_on_pos.db");
        File replacement = new File(live.getAbsolutePath() + ".restore");
        File previous = new File(live.getAbsolutePath() + ".pre-restore-" + System.currentTimeMillis());
        try {
            validateBackup(staged);
            SQLiteDatabase db = PosDatabase.get(this).getWritableDatabase();
            Cursor checkpoint = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null);
            boolean busy = checkpoint.moveToFirst() && checkpoint.getInt(0) != 0;
            checkpoint.close();
            if (busy) throw new IllegalStateException("ฐานข้อมูลยังมีการเขียนอยู่ กรุณาลองใหม่");
            PosDatabase.closeInstanceForRestore();
            copyFile(staged, replacement);
            if (live.exists() && !live.renameTo(previous))
                throw new IllegalStateException("เก็บฐานข้อมูลเดิมไว้เป็นไฟล์สำรองไม่สำเร็จ");
            if (!replacement.renameTo(live)) {
                if (previous.exists()) previous.renameTo(live);
                throw new IllegalStateException("แทนที่ฐานข้อมูลไม่สำเร็จ");
            }
            new File(live.getAbsolutePath() + "-wal").delete();
            new File(live.getAbsolutePath() + "-shm").delete();
            staged.delete();
            new AlertDialog.Builder(this)
                    .setTitle("กู้คืนข้อมูลสำเร็จ")
                    .setMessage("ข้อมูลถูกกู้คืนแล้ว ระบบจะกลับไปหน้าแรก กรุณาตรวจสอบสินค้า ลูกหนี้ และประวัติยอดขายก่อนเริ่มขาย")
                    .setPositiveButton("กลับหน้าแรก", (d, w) -> {
                        Intent home = new Intent(this, MainActivity.class);
                        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(home);
                        finish();
                    }).setCancelable(false).show();
        } catch (Exception e) {
            replacement.delete();
            staged.delete();
            PosDatabase.closeInstanceForRestore();
            PosUi.result(this, false, "กู้คืนข้อมูลไม่สำเร็จ: " + safeMessage(e) + " กรุณาอย่าถอนการติดตั้งแอป และตรวจสอบฐานข้อมูลเดิมก่อนลองใหม่", null);
        }
    }

    private static void copyFile(File source, File destination) throws Exception {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(destination, false)) {
            copy(in, out, MAX_BACKUP_BYTES);
            out.flush();
        }
    }

    private static void copy(InputStream in, OutputStream out, long limit) throws Exception {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IllegalArgumentException("ไฟล์ใหญ่เกินขนาดที่รองรับ");
            out.write(buffer, 0, read);
        }
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? "ข้อผิดพลาดที่ไม่ทราบสาเหตุ" : e.getMessage();
    }
}
