package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.InputType;
import android.util.Base64;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local staff PIN login for the offline single-device POS. */
public final class StaffSecurity {
    private static final int ITERATIONS = 160000;
    private StaffSecurity() {}

    public static void requireCashier(Activity activity, OwnerSecurity.AuthorizedAction action) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        Cursor count = PosDatabase.get(activity).getReadableDatabase().rawQuery("SELECT COUNT(*) FROM staff_users WHERE active=1", null);
        int active = count.moveToFirst() ? count.getInt(0) : 0;
        count.close();
        if (active == 0) {
            OwnerSecurity.requireOwner(activity, "ตั้งค่า PIN เจ้าของร้านก่อนเริ่มใช้งานการขาย", action::run);
            return;
        }
        EditText username = new EditText(activity); username.setSingleLine(true); username.setHint("ชื่อผู้ใช้พนักงาน");
        EditText pin = new EditText(activity); pin.setSingleLine(true); pin.setHint("PIN 6 หลัก");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout fields = new LinearLayout(activity); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24,4,24,4);
        fields.addView(username); fields.addView(pin);
        new AlertDialog.Builder(activity).setTitle("เข้าสู่ระบบพนักงาน").setView(fields)
                .setMessage("กรอกชื่อผู้ใช้และ PIN เพื่อบันทึกชื่อผู้ทำรายการ")
                .setNegativeButton("ยกเลิก", (d,w) -> activity.finish())
                .setNeutralButton("เจ้าของร้าน", (d,w) -> OwnerSecurity.requireOwner(activity, "เข้าสู่ระบบเจ้าของร้าน", action::run))
                .setPositiveButton("เข้าสู่ระบบ", (d,w) -> {
                    String user = username.getText().toString().trim();
                    String enteredPin = pin.getText().toString();
                    SQLiteDatabase db = PosDatabase.get(activity).getReadableDatabase();
                    Cursor c = db.query("staff_users", new String[]{"id","display_name","pin_salt","pin_hash"},
                            "username=? COLLATE NOCASE AND active=1", new String[]{user}, null, null, null, "1");
                    boolean ok = false; long id = -1L; String name = "";
                    if (c.moveToFirst()) {
                        id = c.getLong(0); name = c.getString(1);
                        ok = verify(enteredPin, c.getString(2), c.getString(3));
                    }
                    c.close();
                    if (ok) {
                        PosSession.setStaffAuthenticated(id, name);
                        action.run();
                    } else {
                        new AlertDialog.Builder(activity).setTitle("เข้าสู่ระบบไม่สำเร็จ")
                                .setMessage("ชื่อผู้ใช้หรือ PIN ไม่ถูกต้อง")
                                .setPositiveButton("ลองอีกครั้ง", (x,y) -> requireCashier(activity, action))
                                .setNegativeButton("ยกเลิก", (x,y) -> activity.finish()).show();
                    }
                }).show();
    }

    static String newSalt() {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    static String hash(String pin, String salt64) {
        byte[] salt = Base64.decode(salt64, Base64.DEFAULT);
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
        try { return Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), Base64.NO_WRAP); }
        catch (Exception e) { throw new IllegalStateException("ไม่สามารถสร้างค่า PIN hash", e); }
        finally { spec.clearPassword(); }
    }

    private static boolean verify(String pin, String salt, String expected) {
        try {
            if (pin == null || !pin.matches("[0-9]{6}") || salt == null || expected == null) return false;
            byte[] actual = Base64.decode(hash(pin, salt), Base64.DEFAULT);
            byte[] wanted = Base64.decode(expected, Base64.DEFAULT);
            return MessageDigest.isEqual(actual, wanted);
        } catch (Exception e) { return false; }
    }
}
