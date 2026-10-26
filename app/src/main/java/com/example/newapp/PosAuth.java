package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import android.util.Base64;

/** Local offline owner-PIN gate for sensitive POS screens. */
final class PosAuth {
    private static final String PREFS = "maeyai_pos_security";
    private static final String SALT = "pin_salt";
    private static final String HASH = "pin_hash";
    private static final String FAILS = "pin_fails";
    private static final String LOCK_UNTIL = "pin_lock_until";
    private static final int ITERATIONS = 150000;
    private static final int KEY_BITS = 256;
    private static volatile String actor = "ยังไม่ระบุผู้ใช้";

    private PosAuth() {}

    static String actorLabel() { return actor; }

    static void requireOwner(Activity activity, Runnable unlocked) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains(SALT) || !prefs.contains(HASH)) {
            showSetup(activity, prefs, unlocked);
        } else {
            showLogin(activity, prefs, unlocked);
        }
    }

    private static void showSetup(Activity activity, SharedPreferences prefs, Runnable unlocked) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = PosUi.dp(activity, 16);
        box.setPadding(pad, pad, pad, 0);
        TextView message = new TextView(activity);
        message.setText("ตั้ง PIN เจ้าของร้านครั้งแรก ใช้ตัวเลขอย่างน้อย 6 หลัก ห้ามใช้วันเกิดหรือเลขที่เดาง่าย และเก็บ PIN ไว้ในที่ปลอดภัย");
        message.setTextColor(Color.DKGRAY);
        box.addView(message, new LinearLayout.LayoutParams(-1, -2));
        EditText pin = new EditText(activity);
        pin.setSingleLine(true);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setHint("PIN อย่างน้อย 6 หลัก");
        box.addView(pin, new LinearLayout.LayoutParams(-1, -2));
        EditText confirm = new EditText(activity);
        confirm.setSingleLine(true);
        confirm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        confirm.setHint("ยืนยัน PIN");
        box.addView(confirm, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("ตั้งค่าความปลอดภัยเจ้าของร้าน")
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("ออกจากหน้านี้", (d, w) -> activity.finish())
                .setPositiveButton("ตั้ง PIN", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = pin.getText().toString();
            if (!value.matches("[0-9]{6,12}")) { pin.setError("PIN ต้องเป็นตัวเลข 6–12 หลัก"); return; }
            if (!value.equals(confirm.getText().toString())) { confirm.setError("PIN ไม่ตรงกัน"); return; }
            try {
                byte[] salt = new byte[16];
                new SecureRandom().nextBytes(salt);
                String hash = derive(value, salt);
                boolean saved = prefs.edit().putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                        .putString(HASH, hash).putInt(FAILS, 0).putLong(LOCK_UNTIL, 0L).commit();
                if (!saved) { pin.setError("บันทึก PIN ไม่สำเร็จ กรุณาลองใหม่"); return; }
                actor = "เจ้าของร้าน";
                dialog.dismiss();
                new AlertDialog.Builder(activity).setTitle("ตั้ง PIN สำเร็จ")
                        .setMessage("เก็บ PIN ไว้ให้ปลอดภัย หากลืม PIN ระบบนี้ยังไม่มีช่องทางรีเซ็ตโดยไม่เสี่ยงต่อข้อมูลร้าน")
                        .setPositiveButton("ดำเนินการต่อ", (d, w) -> { if (!activity.isFinishing()) unlocked.run(); })
                        .setCancelable(false).show();
            } catch (Exception e) {
                pin.setError("ตั้ง PIN ไม่สำเร็จ กรุณาลองใหม่");
            }
        }));
        dialog.show();
    }

    private static void showLogin(Activity activity, SharedPreferences prefs, Runnable unlocked) {
        long lockUntil = prefs.getLong(LOCK_UNTIL, 0L);
        long now = System.currentTimeMillis();
        if (lockUntil > now) {
            new AlertDialog.Builder(activity).setTitle("พักการเข้าสู่ระบบ")
                    .setMessage("กรอก PIN ผิดหลายครั้ง กรุณารอ " + Math.max(1L, (lockUntil - now + 999L) / 1000L) + " วินาที")
                    .setPositiveButton("ตกลง", (d, w) -> activity.finish()).setCancelable(false).show();
            return;
        }
        EditText pin = new EditText(activity);
        pin.setSingleLine(true);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setHint("PIN เจ้าของร้าน");
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("ยืนยันตัวตน")
                .setMessage("กรอก PIN เพื่อเปิดหน้าจอที่มีข้อมูลหรือการดำเนินการสำคัญ")
                .setView(pin).setCancelable(false)
                .setNegativeButton("ออก", (d, w) -> activity.finish())
                .setPositiveButton("ยืนยัน", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                byte[] salt = Base64.decode(prefs.getString(SALT, ""), Base64.NO_WRAP);
                String expected = prefs.getString(HASH, "");
                String actual = derive(pin.getText().toString(), salt);
                if (MessageDigest.isEqual(expected.getBytes("UTF-8"), actual.getBytes("UTF-8"))) {
                    prefs.edit().putInt(FAILS, 0).putLong(LOCK_UNTIL, 0L).apply();
                    actor = "เจ้าของร้าน";
                    dialog.dismiss();
                    if (!activity.isFinishing()) unlocked.run();
                } else {
                    int fails = prefs.getInt(FAILS, 0) + 1;
                    SharedPreferences.Editor edit = prefs.edit().putInt(FAILS, fails);
                    if (fails >= 5) edit.putInt(FAILS, 0).putLong(LOCK_UNTIL, System.currentTimeMillis() + 30000L);
                    edit.apply();
                    pin.setText("");
                    pin.setError(fails >= 5 ? "PIN ผิด 5 ครั้ง ระบบพัก 30 วินาที" : "PIN ไม่ถูกต้อง");
                }
            } catch (Exception e) {
                pin.setError("ตรวจสอบ PIN ไม่สำเร็จ");
            }
        }));
        dialog.show();
    }

    private static String derive(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            byte[] result = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
            return Base64.encodeToString(result, Base64.NO_WRAP);
        } finally {
            spec.clearPassword();
        }
    }
}
