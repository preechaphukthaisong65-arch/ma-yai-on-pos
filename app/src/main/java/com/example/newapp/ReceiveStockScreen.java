package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Locale;

public class ReceiveStockScreen extends Activity {

    private InventoryRepository inventory;
    private AndroidNotificationService notificationService;

    private EditText barcodeInput;
    private EditText quantityInput;
    private EditText noteInput;

    private TextView productInfo;
    private TextView stockInfo;
    private Button addNewProductButton;

    private ProductInfo currentProduct;

    private TextToSpeech textToSpeech;
    private boolean voiceReady = false;
    private String pendingSpeech;

    private static class ProductInfo {
        String barcode;
        String name;
        String unit;
        int stockQty;
    }

    private int dp(float value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        inventory = new InventoryRepository(
                LocalDatabase.getInstance(this)
        );
        notificationService = new AndroidNotificationService(this);

        initializeVoice();
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // เมื่อกลับจากหน้าสร้างสินค้า ให้ค้นหาบาร์โค้ดเดิมอีกครั้ง
        // เพื่อเลือกสินค้าที่เพิ่งสร้างและรับสินค้าต่อได้
        if (barcodeInput != null && currentProduct == null) {
            String barcode = barcodeInput.getText()
                    .toString()
                    .trim();

            if (!barcode.isEmpty()) {
                findProduct();
            }
        }
    }

    private void initializeVoice() {
        textToSpeech = new TextToSpeech(
                getApplicationContext(),
                status -> {
                    if (status != TextToSpeech.SUCCESS
                            || textToSpeech == null) {
                        voiceReady = false;
                        return;
                    }

                    int languageResult = textToSpeech.setLanguage(
                            new Locale("th", "TH")
                    );

                    voiceReady =
                            languageResult != TextToSpeech.LANG_MISSING_DATA
                                    && languageResult
                                    != TextToSpeech.LANG_NOT_SUPPORTED;

                    if (voiceReady && pendingSpeech != null) {
                        String message = pendingSpeech;
                        pendingSpeech = null;
                        speakThai(message);
                    }
                }
        );
    }

    private void speakThai(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        if (textToSpeech == null) {
            return;
        }

        if (!voiceReady) {
            pendingSpeech = message;
            return;
        }

        int result = textToSpeech.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "receive_stock_voice"
        );

        if (result == TextToSpeech.ERROR) {
            android.util.Log.w(
                    "ReceiveStockScreen",
                    "อ่านข้อความภาษาไทยไม่สำเร็จ"
            );
        }
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(24));

        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("รับสินค้าเข้าคลัง");
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(45, 90, 55));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(20));
        root.addView(title, fieldParams());

        addLabel(root, "บาร์โค้ดสินค้า");

        LinearLayout barcodeRow = new LinearLayout(this);
        barcodeRow.setOrientation(LinearLayout.HORIZONTAL);
        barcodeRow.setGravity(Gravity.CENTER_VERTICAL);

        barcodeInput = new EditText(this);
        barcodeInput.setSingleLine(true);
        barcodeInput.setHint("กรอกหรือสแกนบาร์โค้ด");
        barcodeInput.setInputType(InputType.TYPE_CLASS_TEXT);

        barcodeRow.addView(
                barcodeInput,
                new LinearLayout.LayoutParams(
                        0, dp(54), 1
                )
        );

        Button scanButton = createButton("สแกน");
        scanButton.setOnClickListener(v -> startScanner());
        barcodeRow.addView(scanButton);

        root.addView(barcodeRow, fieldParams());

        Button searchButton = createButton("ค้นหาสินค้า");
        searchButton.setOnClickListener(v -> findProduct());
        root.addView(searchButton, fieldParams());

        addNewProductButton = createButton(
                "เพิ่มสินค้าใหม่ด้วยบาร์โค้ดนี้"
        );
        addNewProductButton.setVisibility(View.GONE);
        addNewProductButton.setOnClickListener(v -> openCreateProduct());
        root.addView(addNewProductButton, fieldParams());

        productInfo = new TextView(this);
        productInfo.setText("ยังไม่ได้เลือกสินค้า");
        productInfo.setTextSize(17);
        productInfo.setTextColor(Color.DKGRAY);
        productInfo.setPadding(dp(12), dp(16), dp(12), dp(12));
        root.addView(productInfo, fieldParams());

        stockInfo = new TextView(this);
        stockInfo.setText("สต๊อกปัจจุบัน: -");
        stockInfo.setTextSize(18);
        stockInfo.setTypeface(null, Typeface.BOLD);
        stockInfo.setTextColor(Color.rgb(45, 90, 55));
        stockInfo.setPadding(dp(12), dp(4), dp(12), dp(16));
        root.addView(stockInfo, fieldParams());

        addLabel(root, "จำนวนสินค้าที่รับเข้า");

        quantityInput = new EditText(this);
        quantityInput.setSingleLine(true);
        quantityInput.setHint("กรอกจำนวนที่รับจริง");
        quantityInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(quantityInput, fieldParams());

        addLabel(root, "หมายเหตุ (ถ้ามี)");

        noteInput = new EditText(this);
        noteInput.setHint("เช่น รับจากผู้จำหน่าย");
        noteInput.setMinLines(2);
        noteInput.setGravity(Gravity.TOP | Gravity.START);
        noteInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );
        root.addView(noteInput, fieldParams());

        Button saveButton = createButton("ยืนยันรับสินค้าเข้า");
        saveButton.setOnClickListener(v -> confirmReceipt());
        root.addView(saveButton, fieldParams());

        Button backButton = createButton("กลับ");
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton, fieldParams());

        setContentView(scroll);
    }

    private void addLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(16);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(Color.DKGRAY);
        label.setPadding(0, dp(12), 0, dp(6));
        root.addView(label);
    }

    private Button createButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        return button;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(8);
        return params;
    }

    private void startScanner() {
        try {
            IntentIntegrator integrator =
                    new IntentIntegrator(this);

            integrator.setPrompt("สแกนบาร์โค้ดสินค้า");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(false);
            integrator.setDesiredBarcodeFormats(
                    IntentIntegrator.ONE_D_CODE_TYPES
            );
            integrator.initiateScan();

        } catch (RuntimeException e) {
            showMessage("เปิดกล้องสแกนบาร์โค้ดไม่สำเร็จ");
            speakThai("เปิดกล้องสแกนบาร์โค้ดไม่สำเร็จ");
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult result =
                IntentIntegrator.parseActivityResult(
                        requestCode, resultCode, data
                );

        if (result != null) {
            if (result.getContents() != null) {
                String barcode = result.getContents().trim();
                barcodeInput.setText(barcode);
                findProduct();
            } else {
                showMessage("ยกเลิกการสแกน");
            }
        }
    }

    private void findProduct() {
        String barcode = barcodeInput.getText()
                .toString()
                .trim();

        if (barcode.isEmpty()) {
            barcodeInput.setError("กรุณากรอกบาร์โค้ด");
            speakThai("กรุณากรอกหรือสแกนบาร์โค้ดสินค้า");
            return;
        }

        ProductInfo product = readProduct(barcode);

        if (product == null) {
            currentProduct = null;
            productInfo.setText("ไม่พบสินค้า: " + barcode);
            stockInfo.setText("สต๊อกปัจจุบัน: -");
            addNewProductButton.setVisibility(View.VISIBLE);

            showMessage(
                    "ไม่พบสินค้า สามารถเพิ่มสินค้าใหม่ด้วยบาร์โค้ดนี้ได้"
            );

            // ข้อความเสียงตามข้อกำหนดของขั้นตอนเพิ่มสินค้าใหม่
            speakThai("สินค้าใหม่ค่ะ");
            return;
        }

        currentProduct = product;
        addNewProductButton.setVisibility(View.GONE);

        productInfo.setText(
                "ชื่อสินค้า: " + product.name
                        + "\nบาร์โค้ด: " + product.barcode
                        + "\nหน่วย: " + product.unit
        );

        stockInfo.setText(
                "สต๊อกปัจจุบัน: " + product.stockQty
                        + " " + product.unit
        );

        quantityInput.setText("");
        noteInput.setText("");

        showMessage("ค้นหาสินค้าสำเร็จ");
        speakThai("พบสินค้า " + product.name);
    }

    private void openCreateProduct() {
        String barcode = barcodeInput.getText()
                .toString()
                .trim();

        if (barcode.isEmpty()) {
            barcodeInput.setError("กรุณากรอกบาร์โค้ด");
            return;
        }

        try (Cursor cursor = inventory.findProductCursor(barcode)) {
            if (cursor != null && cursor.moveToFirst()) {
                showMessage(
                        "บาร์โค้ดนี้มีสินค้าอยู่แล้ว กรุณาค้นหาสินค้า"
                );
                speakThai(
                        "บาร์โค้ดนี้มีสินค้าอยู่แล้ว กรุณาค้นหาสินค้า"
                );
                findProduct();
                return;
            }
        } catch (Exception e) {
            showMessage("ตรวจสอบบาร์โค้ดไม่สำเร็จ กรุณาลองใหม่");
            return;
        }

        Intent intent = new Intent(
                ReceiveStockScreen.this,
                CreateStockProductScreen.class
        );
        intent.putExtra("prefill_barcode", barcode);
        startActivity(intent);
    }

    private ProductInfo readProduct(String barcode) {
        Cursor cursor = null;

        try {
            cursor = inventory.findProductCursor(barcode);

            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            ProductInfo product = new ProductInfo();

            product.barcode = cursor.getString(
                    cursor.getColumnIndexOrThrow("barcode")
            );
            product.name = cursor.getString(
                    cursor.getColumnIndexOrThrow("name")
            );
            product.unit = cursor.getString(
                    cursor.getColumnIndexOrThrow("unit")
            );
            product.stockQty = cursor.getInt(
                    cursor.getColumnIndexOrThrow("stock_qty")
            );

            return product;

        } catch (Exception e) {
            showMessage(
                    "อ่านข้อมูลสินค้าไม่สำเร็จ: " + errorMessage(e)
            );
            return null;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void confirmReceipt() {
        if (currentProduct == null) {
            showMessage("กรุณาค้นหาสินค้าก่อน");
            speakThai("กรุณาค้นหาสินค้าก่อน");
            return;
        }

        String qtyText = quantityInput.getText()
                .toString()
                .trim();

        if (qtyText.isEmpty()) {
            quantityInput.setError("กรุณากรอกจำนวนรับเข้า");
            speakThai("กรุณากรอกจำนวนสินค้าที่รับเข้า");
            return;
        }

        final int receivedQty;

        try {
            receivedQty = Integer.parseInt(qtyText);

            if (receivedQty <= 0) {
                quantityInput.setError("จำนวนรับเข้าต้องมากกว่า 0");
                speakThai("จำนวนรับเข้าต้องมากกว่าศูนย์");
                return;
            }

            if ((long) currentProduct.stockQty + receivedQty
                    > Integer.MAX_VALUE) {
                quantityInput.setError("จำนวนรวมมากเกินไป");
                speakThai("จำนวนสินค้ารวมมากเกินไป");
                return;
            }

        } catch (NumberFormatException e) {
            quantityInput.setError("จำนวนไม่ถูกต้อง");
            speakThai("จำนวนสินค้าไม่ถูกต้อง");
            return;
        }

        long newQty = (long) currentProduct.stockQty + receivedQty;

        String message =
                "สินค้า: " + currentProduct.name
                        + "\nสต๊อกเดิม: " + currentProduct.stockQty
                        + "\nจำนวนรับเข้า: " + receivedQty
                        + "\nยอดหลังรับเข้า: " + newQty
                        + "\n\nยืนยันรับสินค้าเข้าคลังหรือไม่?";

        new AlertDialog.Builder(this)
                .setTitle("ยืนยันรับสินค้า")
                .setMessage(message)
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton(
                        "ยืนยัน",
                        (dialog, which) -> saveReceipt(receivedQty)
                )
                .show();
    }

    private void saveReceipt(int receivedQty) {
        if (currentProduct == null) {
            return;
        }

        String barcode = currentProduct.barcode;
        String note = noteInput.getText()
                .toString()
                .trim();

        try {
            boolean success = inventory.receiveStock(
                    barcode,
                    receivedQty,
                    note
            );

            if (!success) {
                showMessage(
                        "รับสินค้าไม่สำเร็จ กรุณาตรวจสอบข้อมูลแล้วลองใหม่"
                );
                speakThai("รับสินค้าไม่สำเร็จ กรุณาตรวจสอบข้อมูล");
                return;
            }

            // ซิงก์แจ้งเตือนหลังธุรกรรมรับสินค้าเสร็จสมบูรณ์แล้ว
            try {
                notificationService.syncLowStockNotification();
            } catch (Exception notificationError) {
                android.util.Log.w(
                        "ReceiveStockScreen",
                        "ซิงก์แจ้งเตือนสต๊อกไม่สำเร็จ",
                        notificationError
                );
            }

            ProductInfo updated = readProduct(barcode);

            if (updated != null) {
                currentProduct = updated;

                productInfo.setText(
                        "ชื่อสินค้า: " + updated.name
                                + "\nบาร์โค้ด: " + updated.barcode
                                + "\nหน่วย: " + updated.unit
                );

                stockInfo.setText(
                        "สต๊อกปัจจุบัน: " + updated.stockQty
                                + " " + updated.unit
                );
            }

            quantityInput.setText("");
            noteInput.setText("");

            showMessage("รับสินค้าเข้าและบันทึกประวัติสำเร็จ");
            speakThai("รับสินค้าเข้าเรียบร้อยแล้ว");

        } catch (Exception e) {
            showMessage(
                    "รับสินค้าไม่สำเร็จ: " + errorMessage(e)
            );
            speakThai("รับสินค้าไม่สำเร็จ กรุณาลองใหม่");
        }
    }

    private String errorMessage(Exception e) {
        return e.getMessage() == null
                ? "เกิดข้อผิดพลาด"
                : e.getMessage();
    }

    private void showMessage(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }

        super.onDestroy();
    }
                              }
