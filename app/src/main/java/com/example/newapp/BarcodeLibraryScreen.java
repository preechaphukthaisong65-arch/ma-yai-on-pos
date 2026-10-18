
package com.example.newapp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.WriterException;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class BarcodeLibraryScreen extends Activity {

    private BarcodeRepository repository;
    private PrinterService printerService;
    private BarcodePrintManager printManager;

    private EditText searchInput;
    private EditText quantityInput;

    private LinearLayout resultList;
    private TextView selectedInfo;
    private TextView printerStatus;
    private ImageView barcodeImage;

    private ProductRepository.Product selectedProduct;
    private Bitmap selectedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = new BarcodeRepository(this);
        printerService = PrinterService.getInstance(this);
        printManager = new BarcodePrintManager(printerService);

        buildScreen();
        updatePrinterStatus();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 20, 24, 24);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("คลังบาร์โค้ดสินค้า");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(45, 90, 55));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView description = new TextView(this);
        description.setText(
                "ค้นหาสินค้าและเรียกใช้หมายเลขบาร์โค้ดเดิม"
        );
        description.setTextSize(15);
        description.setTextColor(Color.DKGRAY);
        description.setPadding(0, 12, 0, 12);
        root.addView(description);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("ชื่อสินค้า หรือ หมายเลขบาร์โค้ด");
        root.addView(searchInput);

        Button searchButton = new Button(this);
        searchButton.setText("ค้นหาสินค้า");
        root.addView(searchButton);
        searchButton.setOnClickListener(v -> searchProducts());

        Button scanButton = new Button(this);
        scanButton.setText("สแกนบาร์โค้ดด้วยกล้อง");
        root.addView(scanButton);
        scanButton.setOnClickListener(v -> startScan());

        resultList = new LinearLayout(this);
        resultList.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultList);

        addSectionTitle(root, "บาร์โค้ดที่เลือก");

        selectedInfo = new TextView(this);
        selectedInfo.setText("ยังไม่ได้เลือกสินค้า");
        selectedInfo.setTextSize(16);
        selectedInfo.setTextColor(Color.BLACK);
        selectedInfo.setPadding(0, 8, 0, 8);
        root.addView(selectedInfo);

        barcodeImage = new ImageView(this);
        barcodeImage.setAdjustViewBounds(true);
        barcodeImage.setScaleType(ImageView.ScaleType.FIT_CENTER);

        root.addView(
                barcodeImage,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        170
                )
        );

        addSectionTitle(root, "จำนวนที่ต้องการพิมพ์");

        quantityInput = new EditText(this);
        quantityInput.setSingleLine(true);
        quantityInput.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
        );
        quantityInput.setText("1");
        quantityInput.setHint("จำนวนฉลาก 1-100 ดวง");
        root.addView(quantityInput);

        printerStatus = new TextView(this);
        printerStatus.setTextSize(14);
        printerStatus.setTextColor(Color.DKGRAY);
        printerStatus.setPadding(0, 12, 0, 8);
        root.addView(printerStatus);

        Button printButton = new Button(this);
        printButton.setText("พิมพ์บาร์โค้ดที่เลือก");
        root.addView(printButton);
        printButton.setOnClickListener(v -> printSelected());

        Button backButton = new Button(this);
        backButton.setText("กลับหน้าสร้างบาร์โค้ด");
        root.addView(backButton);
        backButton.setOnClickListener(v -> finish());

        scroll.addView(root);
        setContentView(scroll);
    }

    private void addSectionTitle(
            LinearLayout root,
            String text
    ) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextSize(18);
        heading.setTextColor(Color.rgb(45, 90, 55));
        heading.setPadding(0, 20, 0, 8);
        root.addView(heading);
    }

    private void searchProducts() {
        String query = searchInput.getText().toString().trim();

        resultList.removeAllViews();

        if (query.isEmpty()) {
            showMessage("กรุณาพิมพ์ชื่อสินค้าหรือหมายเลขบาร์โค้ด");
            return;
        }

        final List<ProductRepository.Product> products;

        try {
            products = repository.search(query);
        } catch (RuntimeException e) {
            showMessage("ค้นหาไม่สำเร็จ: " + safeMessage(e));
            return;
        }

        if (products.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("ไม่พบสินค้า");
            empty.setTextColor(Color.DKGRAY);
            empty.setPadding(0, 12, 0, 12);
            resultList.addView(empty);
            return;
        }

        for (ProductRepository.Product product : products) {
            Button item = new Button(this);
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setText(
                    product.name + "\n"
                            + "บาร์โค้ด: " + product.barcode + "\n"
                            + String.format(
                                    Locale.US,
                                    "ราคา %.2f บาท",
                                    product.price
                            )
                            + "\nแตะเพื่อเลือกบาร์โค้ดเดิม"
            );

            item.setOnClickListener(v -> selectProduct(product));
            resultList.addView(item);
        }

        showMessage("พบสินค้า " + products.size() + " รายการ");
    }

    private void selectProduct(
            ProductRepository.Product product
    ) {
        if (product == null) {
            showMessage("ไม่พบข้อมูลสินค้า");
            return;
        }

        try {
            Bitmap newBitmap =
                    BarcodeGenerator.createCode128Bitmap(
                            product.barcode,
                            700,
                            200
                    );

            if (selectedBitmap != null
                    && !selectedBitmap.isRecycled()) {
                selectedBitmap.recycle();
            }

            selectedProduct = product;
            selectedBitmap = newBitmap;

            selectedInfo.setText(
                    "สินค้า: " + product.name + "\n"
                            + "ราคา: "
                            + String.format(
                                    Locale.US,
                                    "%.2f บาท",
                                    product.price
                            )
                            + "\nหมายเลขบาร์โค้ด: "
                            + product.barcode
            );

            barcodeImage.setImageBitmap(selectedBitmap);

            showMessage("เลือกบาร์โค้ดเดิมแล้ว");

        } catch (WriterException | IllegalArgumentException e) {
            showMessage("สร้างภาพบาร์โค้ดไม่สำเร็จ");
        }
    }

    private void startScan() {
        try {
            IntentIntegrator integrator =
                    new IntentIntegrator(this);

            integrator.setDesiredBarcodeFormats(
                    IntentIntegrator.ONE_D_CODE_TYPES
            );
            integrator.setPrompt("หันกล้องไปที่บาร์โค้ดสินค้า");
            integrator.setBeepEnabled(false);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();

        } catch (RuntimeException e) {
            showMessage("เปิดกล้องไม่ได้: " + safeMessage(e));
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            android.content.Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult result =
                IntentIntegrator.parseActivityResult(
                        requestCode,
                        resultCode,
                        data
                );

        if (result == null || result.getContents() == null) {
            return;
        }

        String barcode = result.getContents().trim();
        searchInput.setText(barcode);

        ProductRepository.Product product =
                repository.findByBarcode(barcode);

        if (product == null) {
            resultList.removeAllViews();
            showMessage("ไม่พบหมายเลขนี้ในฐานข้อมูลสินค้า");
            return;
        }

        resultList.removeAllViews();
        selectProduct(product);
    }

    private void printSelected() {
        if (selectedProduct == null || selectedBitmap == null) {
            showMessage("กรุณาค้นหาและเลือกสินค้าก่อน");
            return;
        }

        if (!printerService.isConnected()) {
            updatePrinterStatus();
            showMessage(
                    "ยังไม่ได้เชื่อมต่อเครื่องพิมพ์ "
                            + "กรุณากลับไปเชื่อมต่อที่หน้าสร้างบาร์โค้ด"
            );
            return;
        }

        final int quantity;

        try {
            quantity = Integer.parseInt(
                    quantityInput.getText().toString().trim()
            );
        } catch (NumberFormatException e) {
            showMessage("กรุณาระบุจำนวนพิมพ์");
            return;
        }

        if (quantity < 1 || quantity > 100) {
            showMessage("จำนวนพิมพ์ต้องอยู่ระหว่าง 1-100 ดวง");
            return;
        }

        final ProductRepository.Product product = selectedProduct;
        final Bitmap bitmap = selectedBitmap;

        showMessage("กำลังส่งงานพิมพ์");

        new Thread(() -> {
            try {
                printManager.printBarcode(
                        product.name,
                        product.barcode,
                        product.price,
                        bitmap,
                        quantity
                );

                runOnUiThread(() ->
                        showMessage(
                                "ส่งงานพิมพ์ "
                                        + quantity + " ดวงแล้ว"
                        )
                );

            } catch (IOException | RuntimeException e) {
                runOnUiThread(() ->
                        showMessage(
                                "พิมพ์ไม่สำเร็จ: " + safeMessage(e)
                        )
                );
            }
        }).start();
    }

    private void updatePrinterStatus() {
        if (printerStatus == null) {
            return;
        }

        if (printerService.isConnected()) {
            printerStatus.setText(
                    "สถานะเครื่องพิมพ์: เชื่อมต่อแล้ว (Bluetooth)"
            );
        } else {
            printerStatus.setText(
                    "สถานะเครื่องพิมพ์: ยังไม่ได้เชื่อมต่อ"
            );
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? "เกิดข้อผิดพลาด" : message;
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
        super.onDestroy();

        if (selectedBitmap != null
                && !selectedBitmap.isRecycled()) {
            selectedBitmap.recycle();
            selectedBitmap = null;
        }
    }
}
