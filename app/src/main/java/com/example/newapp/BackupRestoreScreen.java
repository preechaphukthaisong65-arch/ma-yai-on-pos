package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * User-selected, password-encrypted backup and validated restore.
 * Backup format: magic(8) + PBKDF2 salt(16) + AES-GCM nonce(12) + ciphertext/tag.
 */
public final class BackupRestoreScreen extends Activity {
    private static final int REQUEST_EXPORT = 8101;
    private static final int REQUEST_IMPORT = 8102;
    private static final byte[] MAGIC = new byte[]{'M','Y','A','I','P','O','S','1'};
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int PBKDF2_ITERATIONS = 210000;
    private static final int GCM_TAG_BITS = 128;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        OwnerSecurity.requireOwner(this, "สำรองหรือกู้คืนฐานข้อมูลร้านค้า", this::showScreen);
    }

    private void showScreen() {
        LinearLayout root = PosUi.page(this, "สำรอง / กู้คืนข้อมูลร้านค้า");
        TextView warning = new TextView(this);
        warning.setText("ไฟล์สำรองมีข้อมูลการขายและลูกค้า ระบบเข้ารหัสด้วยรหัสผ่านที่คุณตั้งเอง โปรดเก็บรหัสผ่านแยกจากไฟล์ หากลืมรหัสผ่านจะกู้คืนไม่ได้");
        warning.setTextSize(16);
        root.addView(warning, new LinearLayout.LayoutParams(-1, -2));
        PosUi.button(this, root, "สร้างไฟล์สำรองแบบเข้ารหัส", v -> chooseExport());
        PosUi.button(this, root, "กู้คืนจากไฟล์สำรองแบบเข้ารหัส", v -> chooseImport());
        PosUi.back(this, root);
    }

    private void chooseExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "maeyai-pos-backup-" + System.currentTimeMillis() + ".mypbackup");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void chooseImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) askPassword("ตั้งรหัสผ่านสำหรับไฟล์สำรอง", true, password -> exportBackup(uri, password));
        else if (requestCode == REQUEST_IMPORT) askPassword("กรอกรหัสผ่านของไฟล์สำรอง", false, password ->
                new AlertDialog.Builder(this)
                        .setTitle("ยืนยันการกู้คืน")
                        .setMessage("ข้อมูลร้านค้าในเครื่องจะถูกแทนที่ด้วยข้อมูลในไฟล์สำรอง หากไฟล์หรือการย้ายฐานข้อมูลมีปัญหา ระบบจะพยายามคืนฐานข้อมูลเดิม ต้องการดำเนินการต่อหรือไม่?")
                        .setNegativeButton("ยกเลิก", null)
                        .setPositiveButton("กู้คืน", (d, w) -> restoreBackup(uri, password))
                        .show());
    }

    private interface PasswordAction { void run(String password); }

    private void askPassword(String title, boolean create, PasswordAction action) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(create ? "อย่างน้อย 10 ตัวอักษร" : "รหัสผ่าน");
        new AlertDialog.Builder(this).setTitle(title).setView(input)
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("ดำเนินการ", (d, w) -> {
                    String password = input.getText().toString();
                    if (create && password.length() < 10) {
                        message("รหัสผ่านต้องมีอย่างน้อย 10 ตัวอักษร");
                        return;
                    }
                    if (password.isEmpty()) {
                        message("กรุณากรอกรหัสผ่าน");
                        return;
                    }
                    action.run(password);
                }).show();
    }

    private void exportBackup(Uri destination, String password) {
        File snapshot = new File(getCacheDir(), "pos-export-snapshot.db");
        try {
            SQLiteDatabase db = PosDatabase.get(this).getWritableDatabase();
            Cursor checkpoint = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null);
            checkpoint.close();
            PosDatabase.closeInstance();
            File database = getDatabasePath(PosDatabase.databaseFileName());
            if (!database.isFile()) throw new IOException("ไม่พบไฟล์ฐานข้อมูลร้านค้า");
            copy(database, snapshot);
            encryptToUri(snapshot, destination, password);
            message("สร้างไฟล์สำรองแบบเข้ารหัสเรียบร้อยแล้ว");
        } catch (Exception e) {
            message("สำรองข้อมูลไม่สำเร็จ: " + safeMessage(e));
        } finally {
            snapshot.delete();
            try { PosDatabase.get(this).getReadableDatabase(); } catch (Exception ignored) { }
        }
    }

    private void encryptToUri(File source, Uri destination, String password) throws Exception {
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] nonce = randomBytes(NONCE_BYTES);
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, nonce);
        OutputStream raw = getContentResolver().openOutputStream(destination, "wt");
        if (raw == null) throw new IOException("เปิดไฟล์ปลายทางไม่ได้");
        DataOutputStream header = new DataOutputStream(new BufferedOutputStream(raw));
        header.write(MAGIC); header.write(salt); header.write(nonce); header.flush();
        CipherOutputStream encrypted = new CipherOutputStream(header, cipher);
        try (InputStream in = new BufferedInputStream(new FileInputStream(source)); OutputStream out = encrypted) {
            copy(in, out);
        }
    }

    private void restoreBackup(Uri source, String password) {
        File decrypted = new File(getCacheDir(), "pos-restore-candidate.db");
        File rollback = new File(getCacheDir(), "pos-restore-rollback.db");
        boolean oldDatabaseSaved = false;
        try {
            decryptToFile(source, decrypted, password);
            validateDatabase(decrypted, true);

            SQLiteDatabase current = PosDatabase.get(this).getWritableDatabase();
            Cursor checkpoint = current.rawQuery("PRAGMA wal_checkpoint(FULL)", null);
            checkpoint.close();
            PosDatabase.closeInstance();

            File database = getDatabasePath(PosDatabase.databaseFileName());
            if (!database.isFile()) throw new IOException("ไม่พบฐานข้อมูลปัจจุบันสำหรับการป้องกันก่อนกู้คืน");
            copy(database, rollback);
            oldDatabaseSaved = true;
            deleteSidecars(database);
            copy(decrypted, database);

            SQLiteDatabase restored = PosDatabase.get(this).getWritableDatabase();
            validateOpenDatabase(restored);
            Cursor restoredVersion = restored.rawQuery("PRAGMA user_version", null);
            int installedVersion = restoredVersion.moveToFirst() ? restoredVersion.getInt(0) : 0;
            restoredVersion.close();
            if (installedVersion != PosDatabase.DB_VERSION) throw new IOException("เวอร์ชันฐานข้อมูลหลังย้ายไม่ถูกต้อง");
            message("กู้คืนข้อมูลสำเร็จ ตรวจสอบความสมบูรณ์ของฐานข้อมูลแล้ว");
        } catch (Exception e) {
            try {
                PosDatabase.closeInstance();
                File database = getDatabasePath(PosDatabase.databaseFileName());
                if (oldDatabaseSaved && rollback.isFile()) {
                    deleteSidecars(database);
                    copy(rollback, database);
                }
                PosDatabase.get(this).getWritableDatabase();
            } catch (Exception rollbackError) {
                message("กู้คืนไม่สำเร็จ และคืนฐานข้อมูลเดิมอัตโนมัติไม่ได้ กรุณาหยุดใช้งานและเก็บไฟล์สำรองไว้ ติดต่อผู้ดูแลระบบทันที");
                return;
            }
            message("กู้คืนไม่สำเร็จ ระบบคืนฐานข้อมูลเดิมแล้ว: " + safeMessage(e));
        } finally {
            decrypted.delete();
            rollback.delete();
        }
    }

    private void decryptToFile(Uri source, File destination, String password) throws Exception {
        InputStream raw = getContentResolver().openInputStream(source);
        if (raw == null) throw new IOException("เปิดไฟล์สำรองไม่ได้");
        DataInputStream header = new DataInputStream(new BufferedInputStream(raw));
        byte[] magic = new byte[MAGIC.length];
        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        header.readFully(magic); header.readFully(salt); header.readFully(nonce);
        if (!Arrays.equals(magic, MAGIC)) {
            header.close();
            throw new IOException("รูปแบบไฟล์ไม่ถูกต้อง หรือไฟล์นี้ไม่ใช่ไฟล์สำรองแบบเข้ารหัสของ POS");
        }
        CipherInputStream decrypted = new CipherInputStream(header, cipher(Cipher.DECRYPT_MODE, password, salt, nonce));
        try (InputStream in = decrypted; OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            copy(in, out);
        } catch (IOException e) {
            destination.delete();
            throw new IOException("ถอดรหัสไม่สำเร็จ ตรวจสอบรหัสผ่านและความสมบูรณ์ของไฟล์", e);
        }
    }

    private Cipher cipher(int mode, String password, byte[] salt, byte[] nonce) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
        byte[] keyBytes;
        try { keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return cipher;
        } finally { Arrays.fill(keyBytes, (byte) 0); }
    }

    private void validateDatabase(File file, boolean requireCurrentVersion) throws Exception {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            validateOpenDatabase(db);
            Cursor version = db.rawQuery("PRAGMA user_version", null);
            int v = version.moveToFirst() ? version.getInt(0) : 0;
            version.close();
            if (v < 1 || v > PosDatabase.DB_VERSION || (requireCurrentVersion && v != PosDatabase.DB_VERSION))
                throw new IOException("ไม่รองรับเวอร์ชันฐานข้อมูล " + v);
            for (String table : new String[]{"products", "customers", "bills"}) {
                Cursor c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new String[]{table});
                boolean exists = c.moveToFirst(); c.close();
                if (!exists) throw new IOException("ไฟล์สำรองไม่มีตารางสำคัญ: " + table);
            }
        } finally { db.close(); }
    }

    private void validateOpenDatabase(SQLiteDatabase db) throws Exception {
        Cursor integrity = db.rawQuery("PRAGMA integrity_check", null);
        boolean good = integrity.moveToFirst() && "ok".equalsIgnoreCase(integrity.getString(0));
        integrity.close();
        if (!good) throw new IOException("SQLite integrity_check ไม่ผ่าน");
        Cursor foreignKeys = db.rawQuery("PRAGMA foreign_key_check", null);
        boolean violations = foreignKeys.moveToFirst();
        foreignKeys.close();
        if (violations) throw new IOException("พบ foreign-key violation ในฐานข้อมูล");
    }

    private byte[] randomBytes(int count) {
        byte[] result = new byte[count]; new SecureRandom().nextBytes(result); return result;
    }

    private void copy(File from, File to) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(from));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(to))) { copy(in, out); }
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[16384];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        out.flush();
    }

    private void deleteSidecars(File database) {
        new File(database.getPath() + "-wal").delete();
        new File(database.getPath() + "-shm").delete();
        new File(database.getPath() + "-journal").delete();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return e.getClass().getSimpleName();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private void message(String text) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this).setMessage(text).setPositiveButton("ตกลง", null).show();
    }
}
