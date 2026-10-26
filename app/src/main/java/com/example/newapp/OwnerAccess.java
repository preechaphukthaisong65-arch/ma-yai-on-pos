package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local owner gate for the single-device POS. PINs and recovery codes are stored only as salted hashes. */
final class OwnerAccess {
    private static final String PREFS = "maeyai_owner_access";
    private static final String ITERATIONS = "120000";
    private static volatile boolean unlockedForProcess;
    private static final SecureRandom RANDOM = new SecureRandom();

    private OwnerAccess() {}

    static void ensureUnlocked(Activity activity, Runnable onUnlocked) {
        if (activity == null || activity.isFinishing()) return;
        if (unlockedForProcess) { onUnlocked.run(); return; }
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        String pendingRecovery = prefs.getString("recovery_pending", null);
        if (prefs.getString("pin_hash", null) == null) {
            showPinSetup(activity, prefs, onUnlocked, true);
        } else if (pendingRecovery != null) {
            showRecoveryCode(activity, prefs, pendingRecovery, () -> showUnlock(activity, prefs, onUnlocked));
        } else {
            showUnlock(activity, prefs, onUnlocked);
        }
    }

    static String actorLabel() {
        return unlockedForProcess ? "เจ้าของร้าน" : "ยังไม่ระบุผู้ใช้";
    }

    static void changePin(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        confirmOwnerAction(activity, "เปลี่ยน PIN เจ้าของร้าน", () -> showPinSetup(activity, prefs, () -> {}, false));
    }

    static void confirmOwnerAction(Activity activity, String action, Runnable authorized) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        EditText pin = pinField(activity, "PIN เจ้าของร้าน 6 หลัก");
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("ยืนยันสิทธิ์เจ้าของร้าน")
                .setMessage("กรอก PIN เพื่อยืนยัน: " + action)
                .setView(pin)
                .setPositiveButton("ยืนยัน", null)
                .setNegativeButton("ยกเลิก", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = pin.getText().toString();
            if (!value.matches("[0-9]{6}")) { pin.setError("กรอก PIN 6 หลัก"); return; }
            if (!verify(prefs, "pin", value)) { pin.setError("PIN ไม่ถูกต้อง"); pin.setText(""); return; }
            dialog.dismiss();
            authorized.run();
        }));
        dialog.show();
    }

    private static void showUnlock(Activity activity, SharedPreferences prefs, Runnable onUnlocked) {
        EditText pin = pinField(activity, "PIN 6 หลัก");
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("ยืนยันตัวตนเจ้าของร้าน")
                .setMessage("กรอก PIN เพื่อเข้าถึงข้อมูลร้านค้า")
                .setView(pin)
                .setPositiveButton("ปลดล็อก", null)
                .setNegativeButton("ใช้รหัสกู้คืน", (d, w) -> showRecoveryEntry(activity, prefs, onUnlocked))
                .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = pin.getText().toString();
            if (!value.matches("[0-9]{6}")) { pin.setError("กรอก PIN 6 หลัก"); return; }
            if (!verify(prefs, "pin", value)) { pin.setError("PIN ไม่ถูกต้อง"); pin.setText(""); return; }
            unlockedForProcess = true;
            dialog.dismiss();
            onUnlocked.run();
        }));
        dialog.show();
    }

    private static void showPinSetup(Activity activity, SharedPreferences prefs, Runnable onUnlocked, boolean firstSetup) {
        EditText pin = pinField(activity, "ตั้ง PIN ใหม่ 6 หลัก");
        EditText confirm = pinField(activity, "ยืนยัน PIN");
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(16 * activity.getResources().getDisplayMetrics().density + 0.5f);
        fields.setPadding(padding, 0, padding, 0);
        fields.addView(pin, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fields.addView(confirm, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(firstSetup ? "ตั้งรหัสเจ้าของร้านครั้งแรก" : "เปลี่ยน PIN เจ้าของร้าน")
                .setMessage("ใช้ PIN 6 หลักที่ผู้อื่นเดาได้ยาก และเก็บรหัสกู้คืนที่ระบบจะแสดงไว้ให้ปลอดภัย")
                .setView(fields)
                .setPositiveButton(firstSetup ? "ตั้ง PIN" : "บันทึก PIN ใหม่", null)
                .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = pin.getText().toString();
            if (!value.matches("[0-9]{6}")) { pin.setError("PIN ต้องมีตัวเลข 6 หลัก"); return; }
            if (!value.equals(confirm.getText().toString())) { confirm.setError("PIN ไม่ตรงกัน"); return; }
            saveCredential(prefs, "pin", value);
            String recovery = generateRecoveryCode();
            saveCredential(prefs, "recovery", recovery);
            prefs.edit().putString("recovery_pending", recovery).apply();
            dialog.dismiss();
            showRecoveryCode(activity, prefs, recovery, () -> {
                unlockedForProcess = true;
                onUnlocked.run();
            });
        }));
        dialog.show();
    }

    private static void showRecoveryEntry(Activity activity, SharedPreferences prefs, Runnable onUnlocked) {
        EditText recovery = new EditText(activity);
        recovery.setSingleLine(true);
        recovery.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        recovery.setHint("รหัสกู้คืน 12 ตัวอักษร");
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("กู้คืนการเข้าถึง")
                .setMessage("กรอกรหัสกู้คืนที่บันทึกไว้ ระบบจะให้ตั้ง PIN ใหม่")
                .setView(recovery)
                .setPositiveButton("ตรวจสอบรหัส", null)
                .setNegativeButton("กลับ", (d, w) -> showUnlock(activity, prefs, onUnlocked))
                .create();
        dialog.setCancelable(false);
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = recovery.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (!verify(prefs, "recovery", value)) { recovery.setError("รหัสกู้คืนไม่ถูกต้อง"); return; }
            dialog.dismiss();
            showPinSetup(activity, prefs, onUnlocked, false);
        }));
        dialog.show();
    }

    private static void showRecoveryCode(Activity activity, SharedPreferences prefs, String recovery, Runnable after) {
        TextView code = new TextView(activity);
        code.setText(recovery);
        code.setTextSize(24);
        code.setTextIsSelectable(true);
        code.setGravity(android.view.Gravity.CENTER);
        int padding = (int)(20 * activity.getResources().getDisplayMetrics().density + 0.5f);
        code.setPadding(padding, padding, padding, padding);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("บันทึกรหัสกู้คืน")
                .setMessage("จดหรือเก็บรหัสนี้ไว้นอกโทรศัพท์อย่างปลอดภัย หากลืม PIN จะใช้รหัสนี้ตั้ง PIN ใหม่ได้")
                .setView(code)
                .setPositiveButton("บันทึกรหัสแล้ว", (d, w) -> {
                    prefs.edit().remove("recovery_pending").apply();
                    after.run();
                })
                .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private static EditText pinField(Activity activity, String hint) {
        EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setHint(hint);
        field.setMaxEms(12);
        return field;
    }

    private static String generateRecoveryCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder out = new StringBuilder(12);
        for (int i = 0; i < 12; i++) out.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return out.toString();
    }

    private static void saveCredential(SharedPreferences prefs, String prefix, String value) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltText = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP);
        String hashText = android.util.Base64.encodeToString(hash(value, salt), android.util.Base64.NO_WRAP);
        prefs.edit().putString(prefix + "_salt", saltText).putString(prefix + "_hash", hashText).apply();
    }

    private static boolean verify(SharedPreferences prefs, String prefix, String value) {
        String saltText = prefs.getString(prefix + "_salt", null);
        String expectedText = prefs.getString(prefix + "_hash", null);
        if (saltText == null || expectedText == null || value == null) return false;
        try {
            byte[] salt = android.util.Base64.decode(saltText, android.util.Base64.NO_WRAP);
            byte[] expected = android.util.Base64.decode(expectedText, android.util.Base64.NO_WRAP);
            return MessageDigest.isEqual(expected, hash(value, salt));
        } catch (Exception ignored) { return false; }
    }

    private static byte[] hash(String value, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(value.toCharArray(), salt, Integer.parseInt(ITERATIONS), 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("ไม่สามารถตรวจสอบ PIN ได้", e);
        } finally {
            spec.clearPassword();
        }
    }
}
