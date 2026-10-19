package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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

import android.database.Cursor;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class CreateStockProductScreen extends Activity {

    private InventoryRepository inventory;
    private LocalDatabase database;
    private AndroidNotificationService notificationService;

    private EditText barcodeInput;
    private EditText nameInput;
    private EditText salePriceInput;
    private EditText costPriceInput;
    private EditText unitInput;
    private EditText minStockInput;
    private EditText initialQtyInput;

    private int dp(float value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        database = LocalDatabase.getInstance(this);
        inventory = new InventoryRepository(database);
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
        title.setText("เพิ่มสินค้าใหม่เข้าคลัง");
        title.setTextSize(25);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(45, 90, 55));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(20));
        root.addView(title, fieldParams());

        addLabel(root, "บาร์โค้ดสินค้า");

        barcodeInput = createInput(
                "กรอกหรือสร้างบาร์โค้ด",
                InputType.TYPE_CLASS_TEXT
        );
        barcodeInput.setSingleLine(true);
        root.addView(barcodeInput, fieldParams());

        LinearLayout barcodeRow = new LinearLayout(this);
        barcodeRow.setOrientation(LinearLayout.HORIZONTAL);
        barcodeRow.setGravity(Gravity.CENTER_VERTICAL);

        Button generateButton = createButton("สร้างเลขอัตโนมัติ");
        barcodeRow.addView(
                generateButton,
                new LinearLayout.LayoutParams(0, dp(50), 1)
        );
        generateButton.setOnClickListener(v -> generateBarcode());

        Button scanButton = createButton("สแกน");
        LinearLayout.LayoutParams scanParams =
                new LinearLayout.LayoutParams(0, dp(50), 1);
        scanParams.leftMargin = dp(8);
        barcodeRow.addView(scanButton, scanParams);
        scanButton.setOnClickListener(v -> startScanner());

        root.addView(barcodeRow, fieldParams());

        addLabel(root, "ชื่อสินค้า");
        nameInput = createInput(
                "กรอกชื่อสินค้า",
                InputType.TYPE_CLASS_TEXT
        );
        root.addView(nameInput, fieldParams());

        addLabel(root, "ราคาขาย (บาท)");
        salePriceInput = createInput(
                "เช่น 25.00",
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        root.addView(salePriceInput, fieldParams());

        addLabel(root, "ต้นทุนต่อหน่วย (บาท)");
        costPriceInput = createInput(
                "เช่น 15.00",
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        root.addView(costPriceInput, fieldParams());

        addLabel(root, "หน่วยสินค้า");
        unitInput = createInput(
                "เช่น ชิ้น, ขวด, แพ็ค, กิโลกรัม",
                InputType.TYPE_CLASS_TEXT
        );
        unitInput.setText("ชิ้น");
        root.addView(unitInput, fieldParams());

        addLabel(root, "ลิมิตสต๊อก");
        minStockInput = createInput(
                "จำนวนขั้นต่ำสำหรับแจ้งเตือน",
                InputType.TYPE_CLASS_NUMBER
        );
        minStockInput.setText("0");
        root.addView(minStockInput, fieldParams());

        addLabel(root, "จำนวนเริ่มต้นในคลัง");
        initialQtyInput = createInput(
                "กรอกจำนวนสินค้าเริ่มต้น (ใส่ 0 ได้)",
                InputType.TYPE_CLASS_NUMBER
        );
        initialQtyInput.setText("0");
        root.addView(initialQtyInput, fieldParams());

        TextView help = new TextView(this);
        help.setText(
                "สร้างเลขอัตโนมัติได้จากปุ่มด้านบน " +
                "ระบบจะตรวจสอบบาร์โค้ดที่มีอยู่ก่อนใช้เลขนั้น " +
                "และบันทึกสินค้าโดยเก็บประวัติรับเข้าครั้งแรก " +
                "ในธุรกรรมเดียวกัน"
        );
        help.setTextSize(14);
        help.setTextColor(Color.DKGRAY);
        help.setPadding(0, dp(4), 0, dp(12));
        root.addView(help, fieldParams());

        Button saveButton = createButton("ตรวจสอบและบันทึกสินค้า");
        saveButton.setOnClickListener(v -> confirmSave());
        root.addView(saveButton, fieldParams());

        Button backButton = createButton("กลับ");
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton, fieldParams());

        setContentView(scroll);
    }

    private EditText createInput(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setTextSize(16);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        return input;
    }

    private void addLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(16);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(Color.DKGRAY);
        label.setPadding(0, dp(10), 0, dp(5));
        root.addView(label);
    }

    private Button createButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinHeight(dp(50));
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

    /**
     * จองเลขจากฐานข้อมูล แล้วข้ามเลขที่มีสินค้าใช้อยู่แล้ว
     * การจองแต่ละครั้งบันทึกเลขถัดไปลงฐานข้อมูลทันที
     */
    private void generateBarcode() {
        try {
            for (int attempt = 0; attempt < 1000; attempt++) {
                String barcode = database.reserveNextBarcode();

                if (barcode == null || barcode.trim().isEmpty()) {
                    showMessage("สร้างเลขบาร์โค้ดไม่สำเร็จ");
                    return;
                }

                boolean exists = false;

                try (Cursor cursor =
                             database.findProductByBarcode(barcode)) {
                    exists = cursor.moveToFirst();
                }

                if (!exists) {
                    barcodeInput.setText(barcode);
                    barcodeInput.setError(null);
                    showMessage("สร้างบาร์โค้ดแล้ว: " + barcode);
                    return;
                }
            }

            showMessage(
                    "หาเลขบาร์โค้ดว่างไม่สำเร็จ กรุณาลองใหม่"
            );

        } catch (Exception e) {
            showMessage(
                    "สร้างบาร์โค้ดไม่สำเร็จ กรุณาตรวจสอบฐานข้อมูล"
            );
        }
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
                        requestCode,
                        resultCode,
                        data
                );

        if (result == null) {
            return;
        }

        if (result.getContents() == null) {
            showMessage("ยกเลิกการสแกน");
            return;
        }

        String barcode = result.getContents().trim();

        if (barcode.isEmpty()) {
            showMessage("ไม่พบบาร์โค้ดจากการสแกน");
            return;
        }

        barcodeInput.setText(barcode);
    }

    private void confirmSave() {
        String barcode = textOf(barcodeInput);
        String name = textOf(nameInput);
        String salePriceText = textOf(salePriceInput);
        String costPriceText = textOf(costPriceInput);
        String unit = textOf(unitInput);
        String minStockText = textOf(minStockInput);
        String initialQtyText = textOf(initialQtyInput);

        if (barcode.isEmpty()) {
            barcodeInput.setError("กรุณากรอกหรือสร้างบาร์โค้ด");
            return;
        }

        if (name.isEmpty()) {
            nameInput.setError("กรุณากรอกชื่อสินค้า");
            return;
        }

        if (salePriceText.isEmpty()) {
            salePriceInput.setError("กรุณากรอกราคาขาย");
            return;
        }

        if (costPriceText.isEmpty()) {
            costPriceInput.setError("กรุณากรอกต้นทุน");
            return;
        }

        if (unit.isEmpty()) {
            unitInput.setError("กรุณาระบุหน่วยสินค้า");
            return;
        }

        if (minStockText.isEmpty()) {
            minStockInput.setError("กรุณากรอกลิมิตสต๊อก");
            return;
        }

        if (initialQtyText.isEmpty()) {
            initialQtyInput.setError("กรุณากรอกจำนวนเริ่มต้น");
            return;
        }

        final double salePrice;
        final double costPrice;
        final int minStock;
        final int initialQty;

        try {
            salePrice = Double.parseDouble(salePriceText);
            costPrice = Double.parseDouble(costPriceText);

            if (!Double.isFinite(salePrice) || salePrice < 0) {
                salePriceInput.setError("ราคาขายต้องไม่ติดลบ");
                return;
            }

            if (!Double.isFinite(costPrice) || costPrice < 0) {
                costPriceInput.setError("ต้นทุนต้องไม่ติดลบ");
                return;
            }

        } catch (NumberFormatException e) {
            salePriceInput.setError("ราคาหรือต้นทุนไม่ถูกต้อง");
            return;
        }

        try {
            minStock = Integer.parseInt(minStockText);
            initialQty = Integer.parseInt(initialQtyText);

            if (minStock < 0) {
                minStockInput.setError("ลิมิตต้องไม่ติดลบ");
                return;
            }

            if (initialQty < 0) {
                initialQtyInput.setError("จำนวนเริ่มต้นต้องไม่ติดลบ");
                return;
            }

        } catch (NumberFormatException e) {
            minStockInput.setError("จำนวนไม่ถูกต้องหรือมากเกินไป");
            return;
        }

        String summary =
                "บาร์โค้ด: " + barcode
                        + "\nชื่อสินค้า: " + name
                        + "\nราคาขาย: " + salePrice + " บาท"
                        + "\nต้นทุน: " + costPrice + " บาท"
                        + "\nหน่วย: " + unit
                        + "\nลิมิตสต๊อก: " + minStock
                        + "\nจำนวนเริ่มต้น: " + initialQty
                        + "\n\nยืนยันบันทึกสินค้าใหม่หรือไม่?";

        new AlertDialog.Builder(this)
                .setTitle("ตรวจสอบข้อมูลสินค้า")
                .setMessage(summary)
                .setNegativeButton("กลับไปแก้ไข", null)
                .setPositiveButton(
                        "ยืนยันบันทึก",
                        (dialog, which) -> saveProduct(
                                barcode,
                                name,
                                salePrice,
                                costPrice,
                                unit,
                                minStock,
                                initialQty
                        )
                )
                .show();
    }

    private void saveProduct(
            String barcode,
            String name,
            double salePrice,
            double costPrice,
            String unit,
            int minStock,
            int initialQty
    ) {
        try {
            boolean success = inventory.createProductWithInitialStock(
                    barcode,
                    name,
                    salePrice,
                    costPrice,
                    unit,
                    minStock,
                    initialQty
            );

            if (!success) {
                showMessage(
                        "บันทึกไม่สำเร็จ กรุณาตรวจสอบว่าบาร์โค้ดซ้ำ " +
                        "หรือข้อมูลไม่ถูกต้อง"
                );
                return;
            }

            // ซิงก์แจ้งเตือนหลังสร้างสินค้าและบันทึกสต๊อกเริ่มต้นสำเร็จเท่านั้น
            try {
                notificationService.syncLowStockNotification();
            } catch (Exception notificationError) {
                android.util.Log.w(
                        "CreateStockProduct",
                        "ซิงก์แจ้งเตือนสต๊อกไม่สำเร็จ",
                        notificationError
                );
            }

            new AlertDialog.Builder(this)
                    .setTitle("บันทึกสินค้าเรียบร้อย")
                    .setMessage(
                            "เพิ่มสินค้า \"" + name + "\" สำเร็จ"
                                    + "\nบาร์โค้ด: " + barcode
                                    + "\nจำนวนเริ่มต้น: " + initialQty
                                    + "\nสามารถกลับไปดูสินค้าในคลังได้"
                    )
                    .setCancelable(false)
                    .setPositiveButton(
                            "กลับไปคลังสินค้า",
                            (dialog, which) -> finish()
                    )
                    .show();

        } catch (Exception e) {
            showMessage(
                    "บันทึกสินค้าไม่สำเร็จ: "
                            + (e.getMessage() == null
                            ? "เกิดข้อผิดพลาด" : e.getMessage())
            );
        }
    }

    private String textOf(EditText input) {
        return input.getText().toString().trim();
    }

    private void showMessage(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
}
