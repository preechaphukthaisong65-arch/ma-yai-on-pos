package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class NotificationScreen extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    private void render() {
        LinearLayout root = POSUi.page(this, "การแจ้งเตือน");
        POSUi.button(this, root, "กลับ", this::finish);
        String storeId = StoreIdentity.get(this);
        if (storeId.isEmpty()) {
            root.addView(POSUi.text(this, "ยังไม่ได้ยืนยันสิทธิ์ร้าน", 15));
            return;
        }

        List<JSONObject> notices = new ArrayList<>(
                DataHub.getInstance(getApplicationContext())
                        .getRecordsByType(storeId, "app_notification"));
        notices.sort((a, b) -> Long.compare(timestamp(b), timestamp(a)));
        if (notices.isEmpty()) {
            root.addView(POSUi.text(this, "ไม่มีรายการแจ้งเตือนที่บันทึกไว้", 15));
            return;
        }

        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        for (JSONObject record : notices) {
            JSONObject p = record.optJSONObject("payload");
            if (p == null) continue;
            long time = timestamp(record);
            String date = time > 0L ? format.format(new Date(time)) : "ไม่ระบุวันเวลา";
            String message = p.optString("message", "").trim();
            if (message.isEmpty()) continue;
            root.addView(POSUi.text(this, date + "\n" + message, 15));
        }
    }

    private long timestamp(JSONObject record) {
        JSONObject p = record == null ? null : record.optJSONObject("payload");
        if (p == null) return record == null ? 0L : record.optLong("updatedAt", 0L);
        return p.optLong("changedAt", p.optLong("createdAt",
                record.optLong("updatedAt", 0L)));
    }
}
