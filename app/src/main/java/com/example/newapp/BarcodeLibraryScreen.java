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
        items = hub.getRecordsByType(storeId, "barcode_item");
        Collections.sort(items, (a,b) -> Long.compare(
                b.optLong("updatedAt", 0L), a.optLong("updatedAt", 0L)));
        search();
    }

    private void search() {
        if (results == null) return;
        results.removeAllViews();
        String q = POSUi.value(query).toLowerCase(Locale.ROOT);
        int count = 0;
        for (JSONObject record : items) {
            JSONObject p = record.optJSONObject("payload");
            if (p == null) continue;
            String name = p.optString("name", "");
            String code = p.optString("barcode", "");
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !code.contains(q)) continue;
            count++;
            results.addView(POSUi.text(this, name + "\nบาร์โค้ด: " + code + "\nราคา: "
                    + String.format(Locale.US, "%.2f", p.optDouble("price", 0d)) + " บาท", 16));
            POSUi.button(this, results, "พิมพ์บาร์โค้ดเดิม", () -> printExisting(name, code, p.optDouble("price", 0d)));
        }
        if (count == 0) results.addView(POSUi.text(this, "ไม่พบรายการที่ตรงกับคำค้นหา", 15));
    }

    private void printExisting(String name, String code, double price) {
        EditText quantityInput = new EditText(this);
        quantityInput.setHint("จำนวนฉลาก 1–500");
        quantityInput.setInputType(2);
        new android.app.AlertDialog.Builder(this).setTitle("พิมพ์ซ้ำ")
                .setView(quantityInput).setNegativeButton("ยกเลิก", null)
                .setPositiveButton("พิมพ์", (d,w) -> {
                    int qty;
                    try { qty = Integer.parseInt(quantityInput.getText().toString().trim()); } catch (Exception e) { qty = 0; }
                    if (qty < 1 || qty > 500) { Toast.makeText(this, "ระบุจำนวน 1–500 ฉลาก", Toast.LENGTH_LONG).show(); return; }
                    android.print.PrintManager manager = (android.print.PrintManager)getSystemService(PRINT_SERVICE);
                    manager.print("บาร์โค้ด-" + code, new BarcodePrintAdapter(this, "บาร์โค้ด-" + code, code, name, price, qty),
                            new android.print.PrintAttributes.Builder()
                                    .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                                    .setResolution(new android.print.PrintAttributes.Resolution("barcode", "Barcode", 300, 300))
                                    .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS).build());
                }).show();
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