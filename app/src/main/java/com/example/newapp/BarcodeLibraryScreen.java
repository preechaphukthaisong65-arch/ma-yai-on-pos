package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BarcodeLibraryScreen extends Activity {
    private DataHub hub;
    private String storeId;
    private LinearLayout root, results;
    private EditText query;
    private List<JSONObject> items = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        hub = DataHub.getInstance(getApplicationContext());
        storeId = StoreIdentity.get(this);
        render();
        loadItems();
    }

    private void render() {
        root = POSUi.page(this, "คลังบาร์โค้ด");
        query = POSUi.input(this, root, "ค้นหาชื่อสินค้าหรือเลขบาร์โค้ด");
        POSUi.button(this, root, "ค้นหา", this::search);
        POSUi.button(this, root, "สแกนด้วยกล้อง", () ->
                new IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
                        .setPrompt("สแกนบาร์โค้ดสินค้า").setBeepEnabled(false).initiateScan());
        POSUi.button(this, root, "กลับ", this::finish);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);
    }

    private void loadItems() {
        // Barcode-library records are canonical. Product records are read only as a
        // compatibility fallback for factory barcodes created before the library existed.
        java.util.LinkedHashMap<String, JSONObject> byBarcode = new java.util.LinkedHashMap<>();
        for (JSONObject record : hub.getRecordsByType(storeId, "product")) {
            JSONObject p = record.optJSONObject("payload");
            if (p == null) continue;
            String code = p.optString("barcode", "").trim();
            if (!code.isEmpty()) byBarcode.put(code, record);
        }
        for (JSONObject record : hub.getRecordsByType(storeId, "barcode_item")) {
            JSONObject p = record.optJSONObject("payload");
            if (p == null) continue;
            String code = p.optString("barcode", "").trim();
            if (!code.isEmpty()) byBarcode.put(code, record);
        }
        items = new ArrayList<>(byBarcode.values());
        Collections.sort(items, (a,b) -> Long.compare(
                b.optLong("updatedAt", 0L), a.optLong("updatedAt", 0L)));
        search();
    }

    private void search() {
        if (results == null) return;
        results.removeAllViews();
        String rawQuery = POSUi.value(query).trim();
        String q = rawQuery.toLowerCase(Locale.ROOT);
        boolean exactBarcodeQuery = false;
        for (JSONObject record : items) {
            JSONObject p = record.optJSONObject("payload");
            if (p != null && rawQuery.equals(p.optString("barcode", "").trim())) {
                exactBarcodeQuery = true;
                break;
            }
        }
        int count = 0;
        for (JSONObject record : items) {
            JSONObject p = record.optJSONObject("payload");
            if (p == null) continue;
            String name = p.optString("name", "");
            String code = p.optString("barcode", "").trim();
            if (!q.isEmpty()) {
                if (exactBarcodeQuery) {
                    if (!rawQuery.equals(code)) continue;
                } else if (!name.toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
            }
            count++;
            results.addView(POSUi.text(this, name + "\nบาร์โค้ด: " + code + "\nราคา: "
                    + String.format(Locale.US, "%.2f", p.optDouble("price", 0d)) + " บาท", 16));
            POSUi.button(this, results, "พิมพ์บาร์โค้ดเดิม", this::printerDeferred);
        }
        if (count == 0) results.addView(POSUi.text(this, "ไม่พบรายการที่ตรงกับคำค้นหา", 15));
    }

    private void printerDeferred() {
        Toast.makeText(this, "ระบบพิมพ์บาร์โค้ดจะพัฒนาในระยะถัดไป", Toast.LENGTH_LONG).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                query.setText(result.getContents());
                search();
                if (items.stream().noneMatch(r -> {
                    JSONObject p = r.optJSONObject("payload");
                    return p != null && result.getContents().equals(p.optString("barcode"));
                })) Toast.makeText(this, "ไม่พบเลขบาร์โค้ดนี้ในคลัง", Toast.LENGTH_LONG).show();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}