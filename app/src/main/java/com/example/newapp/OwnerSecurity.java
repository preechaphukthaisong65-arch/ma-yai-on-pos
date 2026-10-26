package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.Base64;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Best-effort local owner gate for a single-device, offline POS. Not a multi-user identity system. */
public final class OwnerSecurity {
    private static final String PREFS = "pos_owner_security_v1";
    private static final int ITERATIONS = 160000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private OwnerSecurity() {}

    public interface AuthorizedAction { void run(); }

    public static void requireOwner(Activity activity, String reason, AuthorizedAction action) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        SharedPreferences p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("pin_hash")) {
            showFirstSetup(activity, reason, action);
            return;
        }
        long lockUntil = p.getLong("lock_until", 0L);
        long now = System.currentTimeMillis();
        if (now < lockUntil) {
            long seconds = Math.max(1L, (lockUntil - now + 999L) / 1000L);
            new AlertDialog.Builder(activity).setTitle("ล็อกการยืนยันตัวตนชั่วคราว")
                    .setMessage("ลองรหัสผิดหลายครั้ง กรุณารอ " + seconds + " วินาที")
                    .setPositiveButton("ตกลง", (d, w) -> activity.finish()).show();
            return;
        }
        promptPin(activity, reason, action);
    }

    private static void showFirstSetup(Activity a, String reason, AuthorizedAction action) {
        new AlertDialog.Builder(a).setTitle("ตั้งค่า PIN เจ้าของร้าน")
                .setMessage("ยังไม่ได้ตั้ง PIN เจ้าของร้าน ก่อนดำเนินการ: " + reason +
                        "\n\nตั้งค่านี้ต่อหน้าเจ้าของร้านเท่านั้น ผู้ที่ตั้ง PIN ครั้งแรกจะเป็นผู้ควบคุมการเข้าถึงหน้าที่ป้องกันไว้ รหัสนี้เก็บในเครื่องและไม่ใช่ระบบบัญชีพนักงาน")
                .setNegativeButton("ยกเลิก", (d, w) -> a.finish())
                .setPositiveButton("ตั้ง PIN", (d, w) -> setupPin(a, action, null))
                .show();
    }

    private static void promptPin(Activity a, String reason, AuthorizedAction action) {
        EditText pin = passwordInput(a, "PIN 6 หลัก");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(a).setTitle("ยืนยันเจ้าของร้าน")
                .setMessage(reason).setView(pin)
                .setNegativeButton("ยกเลิก", (d, w) -> a.finish())
                .setNeutralButton("กู้คืน PIN", (d, w) -> promptRecovery(a, action))
                .setPositiveButton("ยืนยัน", (d, w) -> {
                    SharedPreferences p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    if (verify(pin.getText().toString(), p.getString("pin_salt", ""), p.getString("pin_hash", ""))) {
                        p.edit().putInt("failures", 0).putLong("lock_until", 0L).apply();
                        PosSession.setOwnerAuthenticated();
                        action.run();
                    } else {
                        int failures = p.getInt("failures", 0) + 1;
                        SharedPreferences.Editor e = p.edit().putInt("failures", failures);
                        if (failures >= 5) e.putLong("lock_until", System.currentTimeMillis() + 5 * 60 * 1000L).putInt("failures", 0);
                        e.apply();
                        new AlertDialog.Builder(a).setTitle("PIN ไม่ถูกต้อง")
                                .setMessage(failures >= 5 ? "ล็อกการยืนยัน 5 นาที" : "PIN ไม่ถูกต้อง กรุณาลองใหม่")
                                .setPositiveButton("ลองอีกครั้ง", (x, y) -> requireOwner(a, reason, action))
                                .setNegativeButton("ยกเลิก", null).show();
                    }
                }).show();
    }

    private static void promptRecovery(Activity a, AuthorizedAction action) {
        EditText recovery = passwordInput(a, "รหัสกู้คืน 12 หลัก");
        recovery.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(a).setTitle("กู้คืน PIN เจ้าของร้าน").setView(recovery)
                .setMessage("ใช้รหัสกู้คืนที่แสดงตอนตั้งค่า PIN ครั้งแรก")
                .setNegativeButton("ยกเลิก", (d, w) -> a.finish())
                .setPositiveButton("ตรวจสอบ", (d, w) -> {
                    SharedPreferences p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    if (verify(recovery.getText().toString(), p.getString("recovery_salt", ""), p.getString("recovery_hash", ""))) {
                        setupPin(a, action, p.getString("recovery_hash", ""));
                    } else {
                        new AlertDialog.Builder(a).setMessage("รหัสกู้คืนไม่ถูกต้อง").setPositiveButton("ลองอีกครั้ง", (x, y) -> promptRecovery(a, action)).show();
                    }
                }).show();
    }

    private static void setupPin(Activity a, AuthorizedAction action, String oldRecoveryHash) {
        EditText first = passwordInput(a, "ตั้ง PIN ใหม่ 6 หลัก");
        first.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        EditText second = passwordInput(a, "ยืนยัน PIN ใหม่");
        second.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout fields = new LinearLayout(a);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(24, 4, 24, 4);
        fields.addView(first); fields.addView(second);
        new AlertDialog.Builder(a).setTitle("ตั้ง PIN เจ้าของร้าน").setView(fields)
                .setMessage("ใช้ตัวเลข 6 หลัก และอย่าใช้วันเกิดหรือรหัสที่เดาง่าย")
                .setNegativeButton("ยกเลิก", (d, w) -> a.finish())
                .setPositiveButton("บันทึก", (d, w) -> {
                    String one = first.getText().toString(), two = second.getText().toString();
                    if (!one.matches("[0-9]{6}") || !one.equals(two)) {
                        new AlertDialog.Builder(a).setMessage("PIN ต้องเป็นตัวเลข 6 หลักและต้องตรงกัน").setPositiveButton("ลองอีกครั้ง", (x, y) -> setupPin(a, action, oldRecoveryHash)).show();
                        return;
                    }
                    byte[] pinSalt = random(SALT_BYTES);
                    SharedPreferences p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    SharedPreferences.Editor edit = p.edit().putString("pin_salt", Base64.encodeToString(pinSalt, Base64.NO_WRAP))
                            .putString("pin_hash", hash(one, pinSalt)).putInt("failures", 0).putLong("lock_until", 0L);
                    if (oldRecoveryHash == null || !p.contains("recovery_hash") || oldRecoveryHash != null) {
                        StringBuilder recoveryBuilder = new StringBuilder(12); SecureRandom recoveryRandom = new SecureRandom(); for (int i = 0; i < 12; i++) recoveryBuilder.append(recoveryRandom.nextInt(10)); String recovery = recoveryBuilder.toString();
                        byte[] recoverySalt = random(SALT_BYTES);
                        edit.putString("recovery_salt", Base64.encodeToString(recoverySalt, Base64.NO_WRAP))
                                .putString("recovery_hash", hash(recovery, recoverySalt));
                        edit.apply();
                        showRecovery(a, recovery, action);
                    } else {
                        edit.apply();
                        new AlertDialog.Builder(a).setMessage("เปลี่ยน PIN เรียบร้อยแล้ว").setPositiveButton("ดำเนินการต่อ", (x, y) -> { PosSession.setOwnerAuthenticated(); action.run(); }).show();
                    }
                }).show();
    }

    private static void showRecovery(Activity a, String recovery, AuthorizedAction action) {
        TextView view = new TextView(a);
        view.setText(recovery);
        view.setTextSize(26);
        view.setPadding(24, 18, 24, 18);
        new AlertDialog.Builder(a).setTitle("บันทึกรหัสกู้คืน").setMessage("จดรหัสนี้และเก็บแยกจากเครื่อง หากลืม PIN จะต้องใช้รหัสนี้เพื่อกู้คืน")
                .setView(view)
                .setNeutralButton("คัดลอกรหัส", (d, w) -> {
                    ClipboardManager clipboard = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("POS recovery code", recovery));
                    showRecovery(a, recovery, action);
                })
                .setPositiveButton("บันทึกแล้ว", (d, w) -> { PosSession.setOwnerAuthenticated(); action.run(); }).show();
    }

    private static EditText passwordInput(Activity a, String hint) {
        EditText e = new EditText(a); e.setSingleLine(true); e.setHint(hint); return e;
    }

    private static boolean verify(String value, String salt64, String expected64) {
        try {
            if (value == null || salt64 == null || expected64 == null || salt64.isEmpty() || expected64.isEmpty()) return false;
            byte[] salt = Base64.decode(salt64, Base64.DEFAULT);
            byte[] expected = Base64.decode(expected64, Base64.DEFAULT);
            byte[] actual = Base64.decode(hash(value, salt), Base64.DEFAULT);
            return MessageDigest.isEqual(actual, expected);
        } catch (Exception e) { return false; }
    }

    private static String hash(String value, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(value.toCharArray(), salt, ITERATIONS, HASH_BITS);
        try { return Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), Base64.NO_WRAP); }
        catch (Exception e) { throw new IllegalStateException("ไม่สามารถสร้างค่า PIN hash", e); }
        finally { spec.clearPassword(); }
    }

    private static byte[] random(int size) { byte[] bytes = new byte[size]; new SecureRandom().nextBytes(bytes); return bytes; }
}
