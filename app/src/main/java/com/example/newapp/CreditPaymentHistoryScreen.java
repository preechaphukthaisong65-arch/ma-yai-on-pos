package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CreditPaymentHistoryScreen extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    private void render() {
        LinearLayout root = POSUi.page(this, "ประวัติการชำระหนี้");
        POSUi.button(this, root, "กลับ", this::finish);
        String storeId = StoreIdentity.get(this);
        if (storeId.isEmpty()) {
            root.addView(POSUi.text(this, "ยังไม่ได้ยืนยันสิทธิ์ร้าน", 15));
            return;
        }

        List<JSONObject> history = new ArrayList<>(
                DataHub.getInstance(getApplicationContext())
                        .getRecordsByType(storeId, "credit_payment_history"));
        history.sort((a, b) -> Long.compare(paidAt(b), paidAt(a)));

        if (history.isEmpty()) {
            root.addView(POSUi.text(this, "ยังไม่มีประวัติการชำระหนี้", 15));
            return;
        }

        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        for (JSONObject record : history) {
            JSONObject p = record.optJSONObject("payload");
            if (p == null) continue;
            long timestamp = paidAt(record);
            String date = timestamp > 0L ? format.format(new Date(timestamp)) : "ไม่ระบุวันเวลา";
            JSONArray billIds = p.optJSONArray("billIds");
            StringBuilder bills = new StringBuilder();
            if (billIds != null) {
                for (int i = 0; i < billIds.length(); i++) {
                    if (bills.length() > 0) bills.append(", ");
                    bills.append(billIds.optString(i, ""));
                }
            }
            root.addView(POSUi.text(this,
                    "วันเวลา: " + date
                            + "\nรหัสรายการ: " + record.optString("recordId", "ไม่ระบุ")
                            + "\nบิลที่ชำระ: " + (bills.length() == 0 ? "ไม่ระบุ" : bills)
                            + "\nยอดชำระ: " + String.format(Locale.US, "%.2f", p.optDouble("amount", 0d))
                            + " บาท", 15));
        }
    }

    private long paidAt(JSONObject record) {
        JSONObject p = record == null ? null : record.optJSONObject("payload");
        return p == null ? 0L : p.optLong("paidAt", record.optLong("updatedAt", 0L));
    }
}
