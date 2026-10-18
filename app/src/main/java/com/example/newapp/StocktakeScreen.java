
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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class StocktakeScreen extends Activity {

    private InventoryRepository inventory;

    private EditText barcodeInput;
    private EditText countedInput;
    private EditText noteInput;

    private TextView productInfo;
    private TextView currentStockInfo;

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
        title.setText("ตรวจนับสต๊อก");
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(45, 90, 55));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(20));
        root.addView(title);

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
                        0,
                        dp(54),
                        1
                )
        );

        Button scanButton = new Button(this);
        scanButton.setText("สแกน");
        scanButton.setAllCaps(false);
        scanButton.setOnClickListener(v -> startScanner());

        barcodeRow.addView(scanButton);
        root.addView(barcodeRow);

        Button searchButton = createButton("ค้นหาสินค้า");
        searchButton.setOnClickListener(v -> findProduct());
        root.addView(searchButton);

        productInfo = new TextView(this);
        productInfo.setText("ยังไม่ได้เลือกสินค้า");
        productInfo.setTextSize(17);
        productInfo.setTextColor(Color.DKGRAY);
        productInfo.setPadding(
                dp(12), dp(16), dp(12), dp(12)
        );
        root.addView(productInfo);

        currentStockInfo = new TextView(this);
        currentStockInfo.setText("สต๊อกปัจจุบัน: -");
        currentStockInfo.setTextSize(18);
        currentStockInfo.setTypeface(null, Typeface.BOLD);
        currentStockInfo.setTextColor(Color.rgb(45, 90, 55));
        currentStockInfo.setPadding(
                dp(12), dp(4), dp(12), dp(20)
        );
        root.addView(currentStockInfo);

        addLabel(root, "จำนวนที่นับได้");

        countedInput = new EditText(this);
        countedInput.setSingleLine(true);
        countedInput.setHint("กรอกจำนวนจริงที่นับได้");
        countedInput.setInputType(
                InputType.TYPE_CLASS_NUMBER
        );
        root.addView(countedInput, fieldLayoutParams());

        addLabel(root, "หมายเหตุ (ถ้ามี)");

        noteInput = new EditText(this);
        noteInput.setHint("เช่น สินค้าเสียหาย หรือปรับยอด");
        noteInput.setMinLines(2);
        noteInput.setGravity(Gravity.TOP | Gravity.START);
        noteInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );
        root.addView(noteInput, fieldLayoutParams());

        Button saveButton = createButton("ยืนยันและบันทึกยอดตรวจนับ");
        saveButton.setOnClickListener(v -> confirmStocktake());
        root.addView(saveButton);

        Button historyButton = createButton("ดูประวัติสต๊อกสินค้านี้");
        historyButton.setOnClickListener(v -> showHistory());
        root.addView(historyButton);

        Button backButton = createButton("กลับ");
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton);

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

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.topMargin = dp(8);
        params.bottomMargin = dp(4);

        button.setTag(params);
        return button;
    }

    private LinearLayout.LayoutParams fieldLayoutParams() {
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
        super.onActivityResult(
                requestCode, resultCode, data
        );

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
            productInfo.setText("ไม่พบสินค้าบาร์โค้ด: " + barcode);
            currentStockInfo.setText("สต๊อกปัจจุบัน: -");

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

        currentStockInfo.setText(
                "สต๊อกปัจจุบัน: " + product.stockQty
                        + " " + product.unit
        );

        countedInput.setText("");
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

            product.stockQty = cursor.getInt(
                    cursor.getColumnIndexOrThrow("stock_qty")
            );

            product.unit = cursor.getString(
                    cursor.getColumnIndexOrThrow("unit")
            );

            return product;

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "อ่านข้อมูลสินค้าไม่สำเร็จ: "
                            + safeMessage(e),
                    Toast.LENGTH_LONG
            ).show();

            return null;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void confirmStocktake() {
        if (currentProduct == null) {
            Toast.makeText(
                    this,
                    "กรุณาค้นหาสินค้าก่อน",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String qtyText = countedInput.getText()
                .toString()
                .trim();

        if (qtyText.isEmpty()) {
            countedInput.setError("กรุณากรอกจำนวนที่นับได้");
            return;
        }

        final int countedQty;

        try {
            countedQty = Integer.parseInt(qtyText);

            if (countedQty < 0) {
                countedInput.setError(
                        "จำนวนต้องไม่ติดลบ"
                );
                return;
            }

        } catch (NumberFormatException e) {
            countedInput.setError(
                    "จำนวนไม่ถูกต้องหรือมีค่ามากเกินไป"
            );
            return;
        }

        String message =
                "สินค้า: " + currentProduct.name
                        + "\nสต๊อกเดิม: " + currentProduct.stockQty
                        + "\nยอดที่นับได้: " + countedQty
                        + "\nผลต่าง: "
                        + (countedQty - currentProduct.stockQty)
                        + "\n\nยืนยันบันทึกยอดตรวจนับหรือไม่?";

        new AlertDialog.Builder(this)
                .setTitle("ยืนยันการตรวจนับ")
                .setMessage(message)
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton(
                        "ยืนยัน",
                        (dialog, which) ->
                                saveStocktake(countedQty)
                )
                .show();
    }

    private void saveStocktake(int countedQty) {
        if (currentProduct == null) {
            return;
        }

        String barcode = currentProduct.barcode;
        String note = noteInput.getText()
                .toString()
                .trim();

        try {
            inventory.stocktake(
                    barcode,
                    countedQty,
                    note
            );

            ProductInfo updated = readProduct(barcode);

            if (updated != null) {
                currentProduct = updated;

                productInfo.setText(
                        "ชื่อสินค้า: " + updated.name
                                + "\nบาร์โค้ด: " + updated.barcode
                                + "\nหน่วย: " + updated.unit
                );

                currentStockInfo.setText(
                        "สต๊อกปัจจุบัน: " + updated.stockQty
                                + " " + updated.unit
                );
            }

            countedInput.setText("");

            Toast.makeText(
                    this,
                    "บันทึกการตรวจนับสต๊อกสำเร็จ",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "บันทึกไม่สำเร็จ: " + safeMessage(e),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void showHistory() {
        if (currentProduct == null) {
            Toast.makeText(
                    this,
                    "กรุณาค้นหาสินค้าก่อนดูประวัติ",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Cursor cursor = null;

        try {
            cursor = inventory.getStockHistory(
                    currentProduct.barcode
            );

            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(
                    dp(16), dp(8), dp(16), dp(8)
            );

            if (cursor == null || cursor.getCount() == 0) {
                TextView empty = new TextView(this);
                empty.setText("ยังไม่มีประวัติสต๊อก");
                empty.setTextSize(16);
                list.addView(empty);

            } else {
                while (cursor.moveToNext()) {
                    String operation = cursor.getString(
                            cursor.getColumnIndexOrThrow("operation")
                    );

                    int before = cursor.getInt(
                            cursor.getColumnIndexOrThrow("qty_before")
                    );

                    int after = cursor.getInt(
                            cursor.getColumnIndexOrThrow("qty_after")
                    );

                    int diff = cursor.getInt(
                            cursor.getColumnIndexOrThrow("qty_diff")
                    );

                    String note = cursor.getString(
                            cursor.getColumnIndexOrThrow("note")
                    );

                    String createdAt = cursor.getString(
                            cursor.getColumnIndexOrThrow("created_at")
                    );

                    TextView item = new TextView(this);
                    item.setTextSize(15);
                    item.setTextColor(Color.DKGRAY);
                    item.setPadding(
                            0, dp(10), 0, dp(10)
                    );

                    item.setText(
                            "รายการ: " + operationName(operation)
                                    + "\nก่อนทำรายการ: " + before
                                    + "\nหลังทำรายการ: " + after
                                    + "\nผลต่าง: "
                                    + (diff > 0 ? "+" : "")
                                    + diff
                                    + "\nวันที่: " + safeText(createdAt)
                                    + "\nหมายเหตุ: " + safeText(note)
                    );

                    list.addView(item);

                    View divider = new View(this);
                    divider.setBackgroundColor(
                            Color.LTGRAY
                    );

                    list.addView(
                            divider,
                            new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    dp(1)
                            )
                    );
                }
            }

            ScrollView historyScroll = new ScrollView(this);
            historyScroll.addView(list);

            new AlertDialog.Builder(this)
                    .setTitle(
                            "ประวัติสต๊อก: "
                                    + currentProduct.name
                    )
                    .setView(historyScroll)
                    .setPositiveButton("ปิด", null)
                    .show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "อ่านประวัติไม่สำเร็จ: " + safeMessage(e),
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String operationName(String operation) {
        if (operation == null) {
            return "ไม่ระบุ";
        }

        switch (operation) {
            case "STOCKTAKE":
                return "ตรวจนับสต๊อก";

            case "RECEIVE":
                return "รับสินค้าเข้า";

            case "INITIAL_RECEIPT":
                return "รับสินค้าเริ่มต้น";

            default:
                return operation;
        }
    }

    private String safeText(String value) {
        return value == null || value.trim().isEmpty()
                ? "-"
                : value;
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null
                ? "เกิดข้อผิดพลาด"
                : e.getMessage();
    }
}
