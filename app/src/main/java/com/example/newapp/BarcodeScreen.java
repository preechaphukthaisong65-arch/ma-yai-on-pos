package com.example.newapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.print.PrintManager;
import android.provider.Settings;
import android.view.View;
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
    private EditText nameInput, priceInput;
    private LinearLayout root;
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
            try {
                List<JSONObject> records = hub.getRecordsByType(storeId, "barcode_item");
                long max = 0;
                for (JSONObject record : records) {
                    JSONObject p = record.optJSONObject("payload");
                    if (p == null) continue;
                    String value = p.optString("barcode", "");
                    try { max = Math.max(max, Long.parseLong(value)); } catch (NumberFormatException ignored) { }
                }
                String code = String.format(Locale.US, "%012d", max + 1);
                JSONObject payload = new JSONObject();
                payload.put("barcode", code);
                payload.put("name", name);
                payload.put("price", price);
                payload.put("createdAt", System.currentTimeMillis());
                payload.put("barcodeFormat", "CODE_128");
                boolean saved = hub.saveRecord(storeId, "barcode_item:" + code, "barcode_item", 1, 1, payload);
                if (!saved) {
                    Toast.makeText(this, "บันทึกบาร์โค้ดไม่สำเร็จ กรุณาลองอีกครั้ง", Toast.LENGTH_LONG).show();
                    return;
                }
                lastBarcode = code;
                lastName = name;
                lastPrice = price;
                showCreated();
            } catch (Exception error) {
                Toast.makeText(this, "สร้างบาร์โค้ดไม่สำเร็จ", Toast.LENGTH_LONG).show();
            } finally {
                saving = false;
            }
        }
    }

    private void showCreated() {
        barcodeText = POSUi.text(this, "บาร์โค้ด: " + lastBarcode, 18);
        root.addView(barcodeText);
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(lastBarcode, BarcodeFormat.CODE_128, 700, 180);
            Bitmap bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
            for (int y=0;y<matrix.getHeight();y++) for(int x=0;x<matrix.getWidth();x++)
                bitmap.setPixel(x,y,matrix.get(x,y)? Color.BLACK:Color.WHITE);
            preview.setImageBitmap(bitmap);
            root.addView(preview, new LinearLayout.LayoutParams(-1, POSUi.dp(this, 100)));
        } catch (Exception ignored) { }
        root.addView(POSUi.text(this, lastName + " — " + String.format(Locale.US, "%.2f", lastPrice) + " บาท", 16));
        POSUi.button(this, root, "พิมพ์บาร์โค้ด", this::askPrintQuantity);
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