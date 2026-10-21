package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.DataHubStatus;
import com.example.newapp.datahub.StoreIdentity;

public final class CloudSyncStatusScreen extends Activity {
    private LinearLayout root;
    private DataHub hub;
    private String storeId;
    private boolean syncing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        hub = DataHub.getInstance(getApplicationContext());
        storeId = StoreIdentity.get(this);
        render();
    }

    private void render() {
        root = POSUi.page(this, "สถานะซิงก์ข้อมูล");
        POSUi.button(this, root, "ตรวจสอบสถานะอีกครั้ง", this::render);
        DataHubStatus status = hub.getStatus();
        root.addView(POSUi.text(this, "รหัสร้าน: "
                + (storeId.isEmpty() ? "ยังไม่ได้ยืนยันสิทธิ์ร้าน" : storeId), 15));
        root.addView(POSUi.text(this, "สถานะล่าสุดในแอป: " + status.getStatus(), 16));
        root.addView(POSUi.text(this, "รายละเอียด: " + status.getMessage(), 15));
        root.addView(POSUi.text(this, "รายการที่ยังรอซิงก์: "
                + hub.getPendingSyncCount(storeId) + " รายการ", 16));
        POSUi.button(this, root, "ลองซิงก์ข้อมูลที่รออยู่", this::syncNow);
        POSUi.button(this, root, "กลับ", this::finish);
    }

    private void syncNow() {
        if (syncing) return;
        if (storeId.isEmpty()) {
            Toast.makeText(this, "ยังไม่ได้ยืนยันสิทธิ์ร้าน", Toast.LENGTH_LONG).show();
            return;
        }
        syncing = true;
        Toast.makeText(this, "กำลังซิงก์ข้อมูลที่รออยู่…", Toast.LENGTH_SHORT).show();
        hub.syncPendingRecords(storeId, (succeeded, failed, message) -> runOnUiThread(() -> {
            syncing = false;
            Toast.makeText(this, message + " (สำเร็จ " + succeeded
                    + ", ไม่สำเร็จ " + failed + ")", Toast.LENGTH_LONG).show();
            render();
        }));
    }
}
