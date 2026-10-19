
package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class ReceiveStockScreen extends Activity {

    private InventoryRepository inventory;
    private AndroidNotificationService notificationService;

    private EditText barcodeInput;
    private EditText quantityInput;
    private EditText noteInput;

    private TextView productInfo;
    private TextView stockInfo;

    private ProductInfo currentProduct;

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

        buildScreen();
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
        IntentIntegrator integrator =
                new IntentIntegrator(this);

        integrator.setPrompt("สแกนบาร์โค้ดสินค้า");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.setDesiredBarcodeFormats(
                IntentIntegrator.ONE_D_CODE_TYPES
        );
        integrator.initiateScan();
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
                barcodeInput.setText(result.getContents());
                findProduct();
            } else {
                Toast.makeText(
                        this,
                        "ยกเลิกการสแกน",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private void findProduct() {
        String barcode = barcodeInput.getText()
                .toString()
                .trim();

        if (barcode.isEmpty()) {
            barcodeInput.setError("กรุณากรอกบาร์โค้ด");
            return;
        }

        ProductInfo product = readProduct(barcode);

        if (product == null) {
            currentProduct = null;
            productInfo.setText("ไม่พบสินค้า: " + barcode);
            stockInfo.setText("สต๊อกปัจจุบัน: -");

            Toast.makeText(
                    this,
                    "ไม่พบสินค้า กรุณาตรวจสอบบาร์โค้ด",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        currentProduct = product;

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

        Toast.makeText(
                this,
                "ค้นหาสินค้าสำเร็จ",
                Toast.LENGTH_SHORT
        ).show();
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
            Toast.makeText(
                    this,
                    "อ่านข้อมูลสินค้าไม่สำเร็จ: " + errorMessage(e),
                    Toast.LENGTH_LONG
            ).show();
            return null;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void confirmReceipt() {
        if (currentProduct == null) {
            Toast.makeText(
                    this,
                    "กรุณาค้นหาสินค้าก่อน",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String qtyText = quantityInput.getText()
                .toString()
                .trim();

        if (qtyText.isEmpty()) {
            quantityInput.setError("กรุณากรอกจำนวนรับเข้า");
            return;
        }

        final int receivedQty;

        try {
            receivedQty = Integer.parseInt(qtyText);

            if (receivedQty <= 0) {
                quantityInput.setError("จำนวนรับเข้าต้องมากกว่า 0");
                return;
            }

            if ((long) currentProduct.stockQty + receivedQty
                    > Integer.MAX_VALUE) {
                quantityInput.setError("จำนวนรวมมากเกินไป");
                return;
            }

        } catch (NumberFormatException e) {
            quantityInput.setError("จำนวนไม่ถูกต้อง");
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
                Toast.makeText(
                        this,
                        "รับสินค้าไม่สำเร็จ กรุณาตรวจสอบข้อมูลแล้วลองใหม่",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            // ซิงก์สถานะเตือนหลังธุรกรรมรับสินค้าเสร็จสมบูรณ์แล้ว
            notificationService.syncLowStockNotification();

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

            Toast.makeText(
                    this,
                    "รับสินค้าเข้าและบันทึกประวัติสำเร็จ",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "รับสินค้าไม่สำเร็จ: " + errorMessage(e),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private String errorMessage(Exception e) {
        return e.getMessage() == null
                ? "เกิดข้อผิดพลาด"
                : e.getMessage();
    }
}
