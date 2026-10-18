
package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
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

public class StockLimitScreen extends Activity {

    private InventoryRepository inventory;

    private EditText barcodeInput;
    private EditText minStockInput;

    private TextView productInfo;

    private String currentBarcode;
    private String currentName;
    private String currentUnit;
    private int currentStock;
    private int currentMinStock;

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
        title.setText("ตั้งค่าลิมิตสต๊อก");
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(45, 90, 55));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(20));
        root.addView(title, fieldParams());

        addLabel(root, "บาร์โค้ดสินค้า");

        barcodeInput = new EditText(this);
        barcodeInput.setSingleLine(true);
        barcodeInput.setHint("กรอกบาร์โค้ดสินค้า");
        barcodeInput.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(barcodeInput, fieldParams());

        Button searchButton = createButton("ค้นหาสินค้า");
        searchButton.setOnClickListener(v -> findProduct());
        root.addView(searchButton, fieldParams());

        productInfo = new TextView(this);
        productInfo.setText("กรุณาค้นหาสินค้าก่อนตั้งค่าลิมิต");
        productInfo.setTextSize(17);
        productInfo.setTextColor(Color.DKGRAY);
        productInfo.setPadding(dp(12), dp(16), dp(12), dp(16));
        root.addView(productInfo, fieldParams());

        addLabel(root, "จำนวนขั้นต่ำที่ต้องการให้แจ้งเตือน");

        minStockInput = new EditText(this);
        minStockInput.setSingleLine(true);
        minStockInput.setHint("กรอกจำนวนขั้นต่ำ เช่น 5");
        minStockInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(minStockInput, fieldParams());

        TextView help = new TextView(this);
        help.setText(
                "เมื่อจำนวนคงเหลือน้อยกว่าหรือเท่ากับลิมิต " +
                "สินค้าจะปรากฏในรายการสินค้าใกล้หมด"
        );
        help.setTextSize(14);
        help.setTextColor(Color.DKGRAY);
        help.setPadding(0, dp(4), 0, dp(12));
        root.addView(help, fieldParams());

        Button saveButton = createButton("บันทึกลิมิตสต๊อก");
        saveButton.setOnClickListener(v -> confirmSave());
        root.addView(saveButton, fieldParams());

        Button lowStockButton = createButton("ดูรายการสินค้าใกล้หมด");
        lowStockButton.setOnClickListener(v -> showLowStockProducts());
        root.addView(lowStockButton, fieldParams());

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

    private void findProduct() {
        String barcode = barcodeInput.getText()
                .toString()
                .trim();

        if (barcode.isEmpty()) {
            barcodeInput.setError("กรุณากรอกบาร์โค้ด");
            return;
        }

        Cursor cursor = null;

        try {
            cursor = inventory.findProductCursor(barcode);

            if (cursor == null || !cursor.moveToFirst()) {
                clearCurrentProduct();
                productInfo.setText("ไม่พบสินค้า: " + barcode);

                Toast.makeText(
                        this,
                        "ไม่พบสินค้า กรุณาตรวจสอบบาร์โค้ด",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            currentBarcode = cursor.getString(
                    cursor.getColumnIndexOrThrow("barcode")
            );
            currentName = cursor.getString(
                    cursor.getColumnIndexOrThrow("name")
            );
            currentUnit = cursor.getString(
                    cursor.getColumnIndexOrThrow("unit")
            );
            currentStock = cursor.getInt(
                    cursor.getColumnIndexOrThrow("stock_qty")
            );
            currentMinStock = cursor.getInt(
                    cursor.getColumnIndexOrThrow("min_stock")
            );

            productInfo.setText(
                    "ชื่อสินค้า: " + currentName
                            + "\nบาร์โค้ด: " + currentBarcode
                            + "\nสต๊อกปัจจุบัน: " + currentStock
                            + " " + currentUnit
                            + "\nลิมิตปัจจุบัน: " + currentMinStock
                            + " " + currentUnit
            );

            minStockInput.setText(
                    String.valueOf(currentMinStock)
            );

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "ค้นหาสินค้าไม่สำเร็จ: " + errorMessage(e),
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void clearCurrentProduct() {
        currentBarcode = null;
        currentName = null;
        currentUnit = null;
        currentStock = 0;
        currentMinStock = 0;
        minStockInput.setText("");
    }

    private void confirmSave() {
        if (currentBarcode == null) {
            Toast.makeText(
                    this,
                    "กรุณาค้นหาสินค้าก่อน",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String value = minStockInput.getText()
                .toString()
                .trim();

        if (value.isEmpty()) {
            minStockInput.setError("กรุณากรอกลิมิตสต๊อก");
            return;
        }

        final int newMinStock;

        try {
            newMinStock = Integer.parseInt(value);

            if (newMinStock < 0) {
                minStockInput.setError("ลิมิตต้องไม่ติดลบ");
                return;
            }

        } catch (NumberFormatException e) {
            minStockInput.setError("จำนวนไม่ถูกต้องหรือมากเกินไป");
            return;
        }

        String message =
                "สินค้า: " + currentName
                        + "\nสต๊อกปัจจุบัน: " + currentStock
                        + " " + currentUnit
                        + "\nลิมิตเดิม: " + currentMinStock
                        + "\nลิมิตใหม่: " + newMinStock
                        + "\n\nยืนยันบันทึกลิมิตสต๊อกหรือไม่?";

        new AlertDialog.Builder(this)
                .setTitle("ยืนยันตั้งค่าลิมิต")
                .setMessage(message)
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton(
                        "ยืนยัน",
                        (dialog, which) -> saveMinStock(newMinStock)
                )
                .show();
    }

    private void saveMinStock(int newMinStock) {
        if (currentBarcode == null) {
            return;
        }

        try {
            boolean success = inventory.setMinStock(
                    currentBarcode,
                    newMinStock
            );

            if (!success) {
                Toast.makeText(
                        this,
                        "บันทึกลิมิตไม่สำเร็จ กรุณาลองใหม่",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            currentMinStock = newMinStock;

            productInfo.setText(
                    "ชื่อสินค้า: " + currentName
                            + "\nบาร์โค้ด: " + currentBarcode
                            + "\nสต๊อกปัจจุบัน: " + currentStock
                            + " " + currentUnit
                            + "\nลิมิตปัจจุบัน: " + currentMinStock
                            + " " + currentUnit
            );

            Toast.makeText(
                    this,
                    "บันทึกลิมิตสต๊อกสำเร็จ",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "บันทึกไม่สำเร็จ: " + errorMessage(e),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void showLowStockProducts() {
        Cursor cursor = null;

        try {
            cursor = inventory.getLowStockProducts();

            StringBuilder text = new StringBuilder();

            if (cursor == null || cursor.getCount() == 0) {
                text.append("ไม่มีสินค้าใกล้หมดในขณะนี้");
            } else {
                while (cursor.moveToNext()) {
                    String barcode = cursor.getString(
                            cursor.getColumnIndexOrThrow("barcode")
                    );
                    String name = cursor.getString(
                            cursor.getColumnIndexOrThrow("name")
                    );
                    int stock = cursor.getInt(
                            cursor.getColumnIndexOrThrow("stock_qty")
                    );
                    int minStock = cursor.getInt(
                            cursor.getColumnIndexOrThrow("min_stock")
                    );
                    String unit = cursor.getString(
                            cursor.getColumnIndexOrThrow("unit")
                    );

                    text.append("สินค้า: ").append(name)
                            .append("\nบาร์โค้ด: ").append(barcode)
                            .append("\nคงเหลือ: ").append(stock)
                            .append(" ").append(unit)
                            .append("\nลิมิต: ").append(minStock)
                            .append(" ").append(unit)
                            .append("\n\n");
                }
            }

            TextView content = new TextView(this);
            content.setText(text.toString().trim());
            content.setTextSize(16);
            content.setTextColor(Color.DKGRAY);
            content.setPadding(dp(16), dp(12), dp(16), dp(12));

            ScrollView scroll = new ScrollView(this);
            scroll.addView(content);

            new AlertDialog.Builder(this)
                    .setTitle("รายการสินค้าใกล้หมด")
                    .setView(scroll)
                    .setPositiveButton("ปิด", null)
                    .show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "อ่านรายการสินค้าใกล้หมดไม่สำเร็จ: "
                            + errorMessage(e),
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String errorMessage(Exception e) {
        return e.getMessage() == null
                ? "เกิดข้อผิดพลาด"
                : e.getMessage();
    }
}
