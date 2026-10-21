package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;

public class ReceiptReprintScreen extends Activity {
    private StockSalesRepository repo;
    private LinearLayout root;
    private String focusId;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        repo = new StockSalesRepository(this);
        focusId = getIntent().getStringExtra("billId");
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (repo != null) {
            render();
        }
    }

    private void render() {
        root = POSUi.page(this, "ใบเสร็จ / พิมพ์ซ้ำ");
        List<JSONObject> rows = repo.records("receipt");
        boolean found = false;
        for (JSONObject r : rows) {
            JSONObject p = r.optJSONObject("payload");
            if (p == null) continue;
            String bill = p.optString("billId", "");
            String receiptId = r.optString("recordId");
            if (focusId != null && !focusId.isEmpty()
                    && !focusId.equals(bill) && !focusId.equals(receiptId)) continue;
            found = true;
            double total = p.optDouble("total", 0);
            root.addView(POSUi.text(this, p.optString("receiptType", "ใบเสร็จ")
                    + "\nเลขที่: " + p.optString("receiptId", receiptId)
                    + "\nยอด: " + String.format(Locale.US, "%.2f", total) + " บาท"
                    + "\nสถานะพิมพ์: ยังไม่พัฒนา (พักระบบพิมพ์ไว้ก่อน)", 15));
            POSUi.button(this, root, "พิมพ์ใบเสร็จ / พิมพ์ซ้ำ", this::printerDeferred);
        }
        if (!found) root.addView(POSUi.text(this, "ไม่พบใบเสร็จที่บันทึกไว้", 16));
        POSUi.button(this, root, "กลับ", this::finish);
    }

    private void printerDeferred() {
        Toast.makeText(this, "ระบบพิมพ์ใบเสร็จจะพัฒนาในระยะถัดไป ใบเสร็จเดิมยังคงอยู่", Toast.LENGTH_LONG).show();
    }

}
