package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;
import com.google.firebase.auth.FirebaseAuth;

public final class StartupActivity extends Activity {
    private EditText storeCode;
    private TextView status;
    private Button action;
    private ProgressBar progress;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showSetup();
        String savedStoreId = StoreIdentity.get(this);
        if (!savedStoreId.isEmpty() && StoreIdentity.isInitialRestoreComplete(this)) {
            openMain();
        } else if (!savedStoreId.isEmpty()) {
            storeCode.setText(savedStoreId);
            beginRestore(savedStoreId);
        }
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
        description.setText("ใส่รหัสประจำร้านเพื่อค้นหาและกู้คืนข้อมูลสำรองบนเครื่องนี้หรือเครื่องใหม่");
        description.setTextSize(16);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = pad / 2;
        root.addView(description, descParams);

        storeCode = new EditText(this);
        storeCode.setSingleLine(true);
        storeCode.setHint("รหัสประจำร้าน");
        storeCode.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, -2);
        inputParams.topMargin = pad;
        root.addView(storeCode, inputParams);

        status = new TextView(this);
        status.setText("การกู้คืนจะไม่เขียนทับรายการที่มีอยู่ในเครื่อง");
        status.setTextSize(14);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = pad / 2;
        root.addView(status, statusParams);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress);

        action = new Button(this);
        action.setText("เชื่อมต่อและกู้คืนข้อมูล");
        action.setOnClickListener(v -> {
            String code = storeCode.getText().toString().trim();
            if (code.isEmpty()) {
                status.setText("กรุณาใส่รหัสประจำร้าน");
                return;
            }
            beginRestore(code);
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = pad / 2;
        root.addView(action, buttonParams);

        setContentView(root);
    }

    private void beginRestore(String storeId) {
        if (busy) return;
        busy = true;
        setBusy(true);
        status.setText("กำลังเชื่อมต่อ Firebase และตรวจสอบข้อมูลสำรอง…");

        final FirebaseAuth auth;
        try {
            auth = FirebaseAuth.getInstance();
        } catch (RuntimeException error) {
            showFailure("ยังไม่ได้ตั้งค่า Firebase ในแอป กรุณาเพิ่ม google-services.json "
                    + "และตรวจสอบ Firebase project ก่อนใช้งานระบบสำรองข้อมูล");
            return;
        }

        if (auth.getCurrentUser() != null) {
            restore(storeId);
        } else {
            auth.signInAnonymously()
                    .addOnSuccessListener(result -> restore(storeId))
                    .addOnFailureListener(error -> showFailure(
                            "เข้าสู่ระบบ Firebase ไม่สำเร็จ: " + safeMessage(error)
                    ));
        }
    }

    private void restore(String storeId) {
        try {
            DataHub.getInstance(getApplicationContext()).restoreMissingRecords(
                    storeId,
                    (restored, skipped, failed, message) -> runOnUiThread(() -> {
                        if (failed > 0) {
                            showFailure(message + " กรุณาตรวจสอบอินเทอร์เน็ตและสิทธิ์ Firebase แล้วลองใหม่");
                            return;
                        }
                        StoreIdentity.save(this, storeId);
                        StoreIdentity.markInitialRestoreComplete(this);
                        status.setText("ตรวจสอบข้อมูลสำรองแล้ว: กู้คืน " + restored
                                + " รายการ, ข้ามรายการเดิม " + skipped + " รายการ");
                        openMain();
                    })
            );
        } catch (RuntimeException error) {
            showFailure("เริ่มระบบฐานข้อมูลกลางไม่ได้ กรุณาตรวจสอบการตั้งค่า Firebase: "
                    + safeMessage(error));
        }
    }

    private void showFailure(String message) {
        runOnUiThread(() -> {
            busy = false;
            setBusy(false);
            status.setText(message == null ? "ดำเนินการไม่สำเร็จ" : message);
        });
    }

    private String safeMessage(Exception error) {
        return error == null || error.getLocalizedMessage() == null
                ? "ข้อผิดพลาดที่ไม่ทราบสาเหตุ" : error.getLocalizedMessage();
    }

    private void setBusy(boolean value) {
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        action.setEnabled(!value);
        storeCode.setEnabled(!value);
        action.setText(value ? "กำลังดำเนินการ…" : "เชื่อมต่อและกู้คืนข้อมูล");
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
