package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.newapp.datahub.StoreIdentity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public final class StartupActivity extends Activity {
    private EditText emailInput;
    private EditText passwordInput;
    private EditText storeCode;
    private TextView status;
    private Button action;
    private Button continueOffline;
    private ProgressBar progress;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showSetup();
        String savedStoreId = StoreIdentity.get(this);
        if (!savedStoreId.isEmpty()) {
            storeCode.setText(savedStoreId);
        }
        status.setText("เข้าสู่ระบบด้วยบัญชีเจ้าของร้านที่ยืนยันอีเมลแล้ว "
                + "เพื่อยืนยันสิทธิ์ร้าน โดยอนุญาตให้ใช้งานได้ครั้งละหนึ่งเครื่อง");
    }

    private void showSetup() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("ร้านแม่ใหญ่อ่อน POS");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView description = new TextView(this);
        description.setText("ใช้งานได้ครั้งละ 1 เครื่องเท่านั้น ต้องยืนยันสิทธิ์เมื่อย้ายเครื่อง");
        description.setTextSize(16);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = pad / 2;
        root.addView(description, descParams);

        emailInput = new EditText(this);
        emailInput.setSingleLine(true);
        emailInput.setHint("อีเมลเจ้าของร้าน");
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(254)});
        addField(root, emailInput, pad / 2);

        passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setHint("รหัสผ่าน (อย่างน้อย 6 ตัวอักษร)");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128)});
        addField(root, passwordInput, pad / 2);

        storeCode = new EditText(this);
        storeCode.setSingleLine(true);
        storeCode.setHint("รหัสร้านที่ได้รับการผูกสิทธิ์แล้ว");
        storeCode.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        addField(root, storeCode, pad / 2);

        status = new TextView(this);
        status.setText("ข้อมูลในเครื่องจะไม่ถูกเขียนทับโดยการเข้าสู่ระบบ");
        status.setTextSize(14);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = pad / 2;
        root.addView(status, statusParams);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress);

        action = new Button(this);
        action.setText("เข้าสู่ระบบและยืนยันสิทธิ์ร้าน");
        action.setOnClickListener(v -> signInOwner());
        addField(root, action, pad / 2);

        continueOffline = new Button(this);
        continueOffline.setText("ใช้งานข้อมูลในเครื่องแบบออฟไลน์");
        continueOffline.setVisibility(View.GONE);
        continueOffline.setOnClickListener(v -> continueOfflineSafely());
        addField(root, continueOffline, pad / 3);

        setContentView(root);
    }

    private void addField(LinearLayout root, View view, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = topMargin;
        root.addView(view, params);
    }

    private String normalizedEmail() {
        return emailInput.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String enteredPassword() {
        return passwordInput.getText().toString();
    }

    private String enteredStoreId() {
        return storeCode.getText().toString().trim();
    }

    private boolean validateInputs(boolean requireStore) {
        if (normalizedEmail().isEmpty() || enteredPassword().isEmpty()) {
            status.setText("กรุณากรอกอีเมลและรหัสผ่าน");
            return false;
        }
        if (enteredPassword().length() < 6) {
            status.setText("รหัสผ่านต้องมีอย่างน้อย 6 ตัวอักษร");
            return false;
        }
        if (requireStore && enteredStoreId().isEmpty()) {
            status.setText("กรุณากรอกรหัสร้านที่ผู้ดูแลระบบผูกกับบัญชีนี้แล้ว");
            return false;
        }
        return true;
    }

    private FirebaseAuth getAuthOrShowError() {
        try {
            return FirebaseAuth.getInstance();
        } catch (RuntimeException error) {
            showFailure("ยังไม่ได้ตั้งค่า Firebase Auth กรุณาตรวจ google-services.json "
                    + "และ Firebase project ก่อนใช้งาน");
            return null;
        }
    }

    private void signInOwner() {
        if (busy || !validateInputs(true)) return;
        FirebaseAuth auth = getAuthOrShowError();
        if (auth == null) return;

        busy = true;
        setBusy(true);
        status.setText("กำลังตรวจสอบบัญชีเจ้าของร้าน…");
        auth.signInWithEmailAndPassword(normalizedEmail(), enteredPassword())
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        showFailure("Firebase ไม่ส่งกลับบัญชีผู้ใช้ กรุณาลองใหม่");
                        return;
                    }
                    if (!user.isEmailVerified()) {
                        user.sendEmailVerification()
                                .addOnCompleteListener(task -> {
                                    auth.signOut();
                                    showFailure(task.isSuccessful()
                                            ? "กรุณายืนยันอีเมลจากกล่องจดหมาย แล้วกลับมาเข้าสู่ระบบอีกครั้ง"
                                            : "อีเมลยังไม่ได้ยืนยัน และส่งอีเมลยืนยันไม่สำเร็จ กรุณาตรวจการตั้งค่า Firebase");
                                });
                        return;
                    }
                    continueForOwner(auth, user, enteredStoreId());
                })
                .addOnFailureListener(error -> showFailure(
                        "เข้าสู่ระบบไม่สำเร็จ กรุณาตรวจอีเมล รหัสผ่าน และการตั้งค่า Firebase: "
                                + safeMessage(error)));
    }

    private void continueForOwner(FirebaseAuth auth, FirebaseUser user, String storeId) {
        status.setText("กำลังตรวจสอบสิทธิ์เครื่องที่ใช้งาน…");
        String deviceId = StoreIdentity.getOrCreateDeviceId(getApplicationContext());
        new com.example.newapp.datahub.DeviceSessionManager().claim(storeId, deviceId, (claimed, message) -> runOnUiThread(() -> {
            if (!claimed) {
                showFailure("ไม่สามารถรับสิทธิ์ใช้งานร้านได้: " + message);
                return;
            }
            finishOwnerSetup(auth, user, storeId);
        }));
    }

    private void finishOwnerSetup(FirebaseAuth auth, FirebaseUser user, String storeId) {
        try {
            StoreIdentity.save(this, storeId);
            StoreIdentity.saveOwnerUid(this, user.getUid());
            StoreIdentity.markInitialSetupComplete(this);
            status.setText("ยืนยันบัญชีและสิทธิ์ร้านสำเร็จ");
            openMain();
        } catch (RuntimeException error) {
            showFailure("ยืนยันสิทธิ์ร้านไม่สำเร็จ: " + safeMessage(error));
        }
    }

    private void continueOfflineSafely() {
        FirebaseAuth auth = getAuthOrShowError();
        if (auth == null) return;
        FirebaseUser user = auth.getCurrentUser();
        String savedStore = StoreIdentity.get(this);
        String savedOwnerUid = StoreIdentity.getOwnerUid(this);
        if (user == null || !user.isEmailVerified()
                || savedStore.isEmpty()
                || !savedStore.equals(enteredStoreId())
                || savedOwnerUid.isEmpty()
                || !savedOwnerUid.equals(user.getUid())
                || !StoreIdentity.isInitialSetupComplete(this)) {
            status.setText("ใช้ออฟไลน์ได้เฉพาะเครื่องที่เคยยืนยันสิทธิ์ร้านสำเร็จ "
                    + "และบัญชีเจ้าของร้านเดิมยังเข้าสู่ระบบอยู่");
            return;
        }
        openMain();
    }

    private void showFailure(String message) {
        runOnUiThread(() -> {
            busy = false;
            setBusy(false);
            status.setText(message == null ? "ดำเนินการไม่สำเร็จ" : message);
            continueOffline.setVisibility(View.VISIBLE);
        });
    }

    private String safeMessage(Exception error) {
        return error == null || error.getLocalizedMessage() == null
                ? "ข้อผิดพลาดที่ไม่ทราบสาเหตุ" : error.getLocalizedMessage();
    }

    private void setBusy(boolean value) {
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        action.setEnabled(!value);
        emailInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        storeCode.setEnabled(!value);
        continueOffline.setEnabled(!value);
        if (value) continueOffline.setVisibility(View.GONE);
        action.setText(value ? "กำลังดำเนินการ…" : "เข้าสู่ระบบและยืนยันสิทธิ์ร้าน");
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
