package com.example.newapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.print.PrintManager;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public class BarcodeScreen extends Activity {
    private static final Object CREATE_LOCK = new Object();
    private static final long FIRST_BARCODE = 2000001L;
    private EditText nameInput, priceInput;
    private LinearLayout root, resultContainer;
    private TextView status, barcodeText;
    private ImageView preview;
    private String lastBarcode = "", lastName = "";
    private double lastPrice = 0d;
    private DataHub hub;
    private String storeId;
    private boolean saving = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        hub = DataHub.getInstance(getApplicationContext());
        storeId = StoreIdentity.get(this);
        render();
    }

    private void render() {
        root = POSUi.page(this, "สร้างบาร์โค้ด");
        if (storeId == null || storeId.trim().isEmpty()) {
            root.addView(POSUi.text(this, "ยังไม่พบรหัสร้านค้า กรุณาตั้งค่าร้านค้าก่อน", 16));
            POSUi.button(this, root, "กลับ", this::finish);
            return;
        }
        root.addView(POSUi.text(this, "ระบบกำหนดเลขบาร์โค้ดให้อัตโนมัติ ห้ามเลือกเลขเอง", 14));
        nameInput = POSUi.input(this, root, "ชื่อสินค้า");
        priceInput = POSUi.input(this, root, "ราคาสินค้า (บาท)");
        priceInput.setInputType(8194);
        POSUi.button(this, root, "สร้างบาร์โค้ด", this::createBarcode);
        status = POSUi.text(this, "สถานะเครื่องพิมพ์: ใช้บริการพิมพ์ของ Android (ยังไม่ทราบรุ่นเครื่องพิมพ์)", 13);
        root.addView(status);
        POSUi.button(this, root, "ค้นหาเครื่องพิมพ์", this::openPrintSettings);
        POSUi.button(this, root, "เชื่อมต่อเครื่องพิมพ์", this::openPrintSettings);
        POSUi.button(this, root, "คลังบาร์โค้ด", () -> startActivity(new Intent(this, BarcodeLibraryScreen.class)));
        POSUi.button(this, root, "กลับหน้าหลัก", this::finish);
        resultContainer = new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultContainer);
    }

    private void createBarcode() {
        if (saving) return;
        String name = POSUi.value(nameInput);
        double price = POSUi.number(priceInput);
        if (name.isEmpty() || price < 0 || !Double.isFinite(price)) {
            Toast.makeText(this, "กรุณากรอกชื่อสินค้าและราคาที่ถูกต้อง", Toast.LENGTH_LONG).show();
            return;
        }
        saving = true;
        synchronized (CREATE_LOCK) {
            reserveNextAvailable(name, price, FIRST_BARCODE);
        }
    }

    /**
     * Barcode creation only writes a barcode_item record. It must never create
     * a product, stock-history entry, or otherwise mutate inventory.
     */
    private void reserveNextAvailable(String name, double price, long candidate) {
        if (isFinishing()) {
            saving = false;
            return;
        }
        if (candidate < FIRST_BARCODE || candidate == Long.MAX_VALUE) {
            saving = false;
            Toast.makeText(this, "ไม่พบเลขบาร์โค้ดว่างที่ใช้งานได้", Toast.LENGTH_LONG).show();
            return;
        }

        List<JSONObject> products = hub.getRecordsByType(storeId, "product");
        List<JSONObject> barcodeItems = hub.getRecordsByType(storeId, "barcode_item");
        long next = candidate;
        while (next < Long.MAX_VALUE) {
            String code = Long.toString(next);
            if (!barcodeExists(products, code) && !barcodeExists(barcodeItems, code)
                    && hub.getRecord(storeId, "barcode_item_" + code) == null) {
                break;
            }
            next++;
        }
        if (next >= Long.MAX_VALUE) {
            saving = false;
            Toast.makeText(this, "ไม่พบเลขบาร์โค้ดว่างที่ใช้งานได้", Toast.LENGTH_LONG).show();
            return;
        }

        final long selectedNumber = next;
        final String selectedCode = Long.toString(selectedNumber);
        hub.reserveBarcode(storeId, selectedCode, (succeeded, failed, message) -> runOnUiThread(() -> {
            if (failed > 0) {
                String detail = message == null ? "" : message;
                String lower = detail.toLowerCase(Locale.ROOT);
                if (detail.contains("ถูกจองแล้ว") || lower.contains("already reserved")
                        || lower.contains("already exists")) {
                    reserveNextAvailable(name, price, selectedNumber + 1L);
                    return;
                }
                saving = false;
                Toast.makeText(BarcodeScreen.this,
                        "จองเลขบาร์โค้ดไม่สำเร็จ จึงยังไม่ได้สร้างรายการ: " + detail,
                        Toast.LENGTH_LONG).show();
                return;
            }

            try {
                JSONObject payload = new JSONObject();
                payload.put("name", name.trim());
                payload.put("barcode", selectedCode);
                payload.put("price", price);
                payload.put("source", "shop");
                payload.put("createdAt", System.currentTimeMillis());
                boolean saved = hub.saveRecord(storeId, "barcode_item_" + selectedCode,
                        "barcode_item", 1, 1L, payload);
                saving = false;
                if (!saved) {
                    Toast.makeText(BarcodeScreen.this,
                            "จองเลขบาร์โค้ดแล้ว แต่บันทึกคลังบาร์โค้ดไม่สำเร็จ เลขนี้จะไม่ถูกนำกลับมาใช้ซ้ำ",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                lastBarcode = selectedCode;
                lastName = name.trim();
                lastPrice = price;
                showCreated();
            } catch (Exception error) {
                saving = false;
                Toast.makeText(BarcodeScreen.this, "บันทึกคลังบาร์โค้ดไม่สำเร็จ", Toast.LENGTH_LONG).show();
            }
        }));
    }

    private boolean barcodeExists(List<JSONObject> records, String code) {
        for (JSONObject record : records) {
            JSONObject payload = record.optJSONObject("payload");
            if (payload != null && code.equals(payload.optString("barcode", "").trim())) return true;
        }
        return false;
    }

    private void showCreated() {
        resultContainer.removeAllViews();
        barcodeText = POSUi.text(this, "บาร์โค้ด: " + lastBarcode, 18);
        resultContainer.addView(barcodeText);
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(lastBarcode, BarcodeFormat.CODE_128, 700, 180);
            Bitmap bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
            for (int y = 0; y < matrix.getHeight(); y++) for (int x = 0; x < matrix.getWidth(); x++)
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            preview.setImageBitmap(bitmap);
            resultContainer.addView(preview, new LinearLayout.LayoutParams(-1, POSUi.dp(this, 100)));
        } catch (Exception ignored) { }
        resultContainer.addView(POSUi.text(this, lastName + " — " + String.format(Locale.US, "%.2f", lastPrice) + " บาท", 16));
        POSUi.button(this, resultContainer, "พิมพ์บาร์โค้ด", this::askPrintQuantity);
    }

    private void askPrintQuantity() {
        EditText quantityInput = new EditText(this);
        quantityInput.setHint("จำนวนฉลากที่ต้องการพิมพ์");
        quantityInput.setInputType(2);
        new android.app.AlertDialog.Builder(this)
                .setTitle("พิมพ์บาร์โค้ดเดิม")
                .setMessage("จำนวนฉลากไม่ทำให้เกิดเลขบาร์โค้ดใหม่")
                .setView(quantityInput)
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("พิมพ์", (dialog, which) -> {
                    int quantity;
                    try { quantity = Integer.parseInt(quantityInput.getText().toString().trim()); }
                    catch (Exception error) { quantity = 0; }
                    if (quantity < 1 || quantity > 500) {
                        Toast.makeText(this, "ระบุจำนวน 1–500 ฉลาก", Toast.LENGTH_LONG).show();
                        return;
                    }
                    PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    printManager.print("บาร์โค้ด-" + lastBarcode,
                            new BarcodePrintAdapter(this, "บาร์โค้ด-" + lastBarcode, lastBarcode,
                                    lastName, lastPrice, quantity),
                            new android.print.PrintAttributes.Builder()
                                    .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                                    .setResolution(new android.print.PrintAttributes.Resolution("barcode", "Barcode", 300, 300))
                                    .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                                    .build());
                }).show();
    }

    private void openPrintSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS));
        } catch (Exception error) {
            Toast.makeText(this, "อุปกรณ์นี้ไม่มีหน้าตั้งค่าบริการพิมพ์ของ Android", Toast.LENGTH_LONG).show();
        }
    }
}