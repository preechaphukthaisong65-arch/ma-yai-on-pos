package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;

import org.json.JSONObject;

import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

public class OverdueDebtScreen extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String storeId = StoreIdentity.get(this);
        LinearLayout root = POSUi.page(this, "หนี้ค้างเกิน 30 วัน");
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        Map<String, Double> debt = new LinkedHashMap<>();
        for (JSONObject bill : DataHub.getInstance(getApplicationContext()).getRecordsByType(storeId, "sale_bill")) {
            JSONObject p = bill.optJSONObject("payload");
            if (p == null || !"credit".equals(p.optString("paymentType"))
                    || !"ค้างชำระ".equals(p.optString("paymentStatus"))) continue;
            long created = p.optLong("createdAt", 0L);
            if (created <= 0 || created > cutoff) continue;
            String name = p.optString("customerName", "ไม่ระบุชื่อลูกค้า");
            double amount = Math.max(0d, p.optDouble("remainingBalance", p.optDouble("total", 0d)));
            debt.put(name, debt.getOrDefault(name, 0d) + amount);
        }
        if (debt.isEmpty()) root.addView(POSUi.text(this, "ไม่มีหนี้ค้างเกิน 30 วัน", 16));
        else for (Map.Entry<String, Double> entry : debt.entrySet())
            root.addView(POSUi.text(this, entry.getKey() + " — " + String.format(Locale.US, "%.2f", entry.getValue()) + " บาท", 16));
        POSUi.button(this, root, "กลับ", this::finish);
    }
}