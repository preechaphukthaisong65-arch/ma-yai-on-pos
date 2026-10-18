package com.example.newapp;

import android.Manifest;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.database.Cursor;

import com.google.zxing.WriterException;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BarcodeScreen extends Activity {

    private EditText nameInput;
    private EditText priceInput;
    private EditText searchInput;
    private EditText quantityInput;

    private TextView barcodeText;
    private TextView printerStatus;
    private ImageView barcodeImage;

    private Spinner printerSpinner;

    private LocalDatabase database;
    private ProductRepository productRepository;
    private PrinterService printerService;

    private final List<PrinterService.PrinterDevice> printers =
            new ArrayList<>();

    private Bitmap currentBitmap;
    private ProductRepository.Product currentProduct;

    private LinearLayout productList;
    private LinearLayout root;

    private static final int REQUEST_BLUETOOTH_PERMISSION = 501;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        database = LocalDatabase.getInstance(this);
        productRepository = new ProductRepository(this);
        printerService = PrinterService.getInstance(this);

        buildScreen();
        refreshPrinters();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("ระบบบาร์โค้ดสินค้า");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(45, 90, 55));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        addSectionTitle("1. สร้างบาร์โค้ดสินค้า");

        nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("ชื่อสินค้า");
        root.addView(nameInput);

        priceInput = new EditText(this);
        priceInput.setSingleLine(true);
        priceInput.setHint("ราคาขาย (บาท)");
        priceInput.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        root.addView(priceInput);

        Button createButton = new Button(this);
        createButton.setText("สร้างและบันทึกบาร์โค้ดใหม่");
        root.addView(createButton);
        createButton.setOnClickListener(v -> createBarcode());

        Button scanButton = new Button(this);
        scanButton.setText("สแกนบาร์โค้ดด้วยกล้อง");
        root.addView(scanButton);
        scanButton.setOnClickListener(v -> startScan());

        barcodeText = new TextView(this);
        barcodeText.setTextSize(16);
        barcodeText.setTextColor(Color.BLACK);
        barcodeText.setGravity(Gravity.CENTER);
        barcodeText.setPadding(0, 12, 0, 8);
        root.addView(barcodeText);

        barcodeImage = new ImageView(this);
        barcodeImage.setAdjustViewBounds(true);
        barcodeImage.setScaleType(ImageView.ScaleType.FIT_CENTER);

        root.addView(
                barcodeImage,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        180
                )
        );

        addSectionTitle("2. ค้นหาและเรียกบาร์โค้ดเดิม");

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("ค้นหาด้วยชื่อสินค้าหรือเลขบาร์โค้ด");
        root.addView(searchInput);

        Button searchButton = new Button(this);
        searchButton.setText("ค้นหาสินค้า");
        root.addView(searchButton);
        searchButton.setOnClickListener(v -> searchProducts());

        productList = new LinearLayout(this);
        productList.setOrientation(LinearLayout.VERTICAL);
        root.addView(productList);

        addSectionTitle("3. เครื่องพิมพ์บาร์โค้ด Bluetooth");

        printerSpinner = new Spinner(this);
        root.addView(printerSpinner);

        Button refreshPrinterButton = new Button(this);
        refreshPrinterButton.setText("ค้นหาเครื่องพิมพ์ที่จับคู่ไว้");
        root.addView(refreshPrinterButton);
        refreshPrinterButton.setOnClickListener(v -> refreshPrinters());

        Button connectButton = new Button(this);
        connectButton.setText("เชื่อมต่อเครื่องพิมพ์");
        root.addView(connectButton);
        connectButton.setOnClickListener(v -> connectPrinter());

        Button disconnectButton = new Button(this);
        disconnectButton.setText("ยกเลิกการเชื่อมต่อ");
        root.addView(disconnectButton);
        disconnectButton.setOnClickListener(v -> {
            printerService.disconnect();
            updatePrinterStatus("ยกเลิกการเชื่อมต่อแล้ว");
        });

        printerStatus = new TextView(this);
        printerStatus.setTextColor(Color.DKGRAY);
        printerStatus.setTextSize(14);
        printerStatus.setPadding(0, 8, 0, 8);
        root.addView(printerStatus);

        quantityInput = new EditText(this);
        quantityInput.setSingleLine(true);
        quantityInput.setHint("จำนวนดวงที่ต้องการพิมพ์");
        quantityInput.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
        );
        quantityInput.setText("1");
        root.addView(quantityInput);

        Button printButton = new Button(this);
        printButton.setText("พิมพ์บาร์โค้ด");
        root.addView(printButton);
        printButton.setOnClickListener(v -> printCurrentBarcode());

        Button clearButton = new Button(this);
        clearButton.setText("ล้างช่องกรอกข้อมูล");
        root.addView(clearButton);
        clearButton.setOnClickListener(v -> {
            nameInput.setText("");
            priceInput.setText("");
            searchInput.setText("");
            quantityInput.setText("1");
            barcodeText.setText("");
            barcodeImage.setImageDrawable(null);
            currentBitmap = null;
            currentProduct = null;
            productList.removeAllViews();
        });

        scroll.addView(root);
        setContentView(scroll);
    }

    private void addSectionTitle(String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextSize(18);
        heading.setTextColor(Color.rgb(45, 90, 55));
        heading.setPadding(0, 24, 0, 8);
        root.addView(heading);
    }

    private void createBarcode() {
        String name = nameInput.getText().toString().trim();
        String priceText = priceInput.getText().toString().trim();

        if (name.isEmpty() || priceText.isEmpty()) {
            showMessage("กรุณากรอกชื่อสินค้าและราคา");
            return;
        }

        final double price;

        try {
            price = Double.parseDouble(priceText);
        } catch (NumberFormatException e) {
            showMessage("รูปแบบราคาไม่ถูกต้อง");
            return;
        }

        if (!Double.isFinite(price) || price < 0) {
            showMessage("ราคาต้องไม่ติดลบ");
            return;
        }

        String barcode;

        try {
            barcode = database.reserveNextBarcode();

            if (barcode == null || barcode.trim().isEmpty()) {
                showMessage("จองหมายเลขบาร์โค้ดไม่สำเร็จ");
                return;
            }

            long createdId = productRepository.createProduct(
                    barcode,
                    name,
                    price,
                    0.0,
                    "ชิ้น",
                    0
            );

            if (createdId <= 0) {
                showMessage("บันทึกสินค้าไม่สำเร็จ");
                return;
            }

        } catch (RuntimeException e) {
            showMessage("สร้างสินค้าไม่สำเร็จ: " + e.getMessage());
            return;
        }

        ProductRepository.Product product =
                productRepository.findByBarcode(barcode);

        if (product != null) {
            displayProduct(product);
        } else {
            displayBarcode(barcode, name, price);
        }

        showMessage("บันทึกสินค้าและสร้างบาร์โค้ดสำเร็จ");
    }

    private void displayProduct(ProductRepository.Product product) {
        if (product == null) {
            return;
        }

        currentProduct = product;
        nameInput.setText(product.name);
        priceInput.setText(
                String.format(Locale.US, "%.2f", product.price)
        );

        displayBarcode(product.barcode, product.name, product.price);
    }

    private void displayBarcode(
            String barcode,
            String name,
            double price
    ) {
        try {
            currentBitmap = BarcodeGenerator.createCode128Bitmap(
                    barcode,
                    700,
                    200
            );

            barcodeText.setText(
                    "สินค้า: " + name + "\n"
                            + "ราคา: "
                            + String.format(Locale.US, "%.2f", price)
                            + " บาท\n"
                            + "บาร์โค้ด: " + barcode
            );

            barcodeImage.setImageBitmap(currentBitmap);

        } catch (WriterException | IllegalArgumentException e) {
            currentBitmap = null;
            barcodeImage.setImageDrawable(null);
            barcodeText.setText("สร้างภาพบาร์โค้ดไม่สำเร็จ: " + barcode);
            showMessage("สร้างภาพบาร์โค้ดไม่สำเร็จ");
        }
    }

    private void searchProducts() {
        String query = searchInput.getText().toString()
                .trim().toLowerCase(Locale.ROOT);

        productList.removeAllViews();

        if (query.isEmpty()) {
            showMessage("กรุณาพิมพ์ชื่อสินค้าหรือเลขบาร์โค้ด");
            return;
        }

        int found = 0;

        try (Cursor cursor =
                     productRepository.getAllProductsByStockAscending()) {

            int barcodeIndex = cursor.getColumnIndexOrThrow("barcode");
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            int priceIndex = cursor.getColumnIndexOrThrow("price");

            while (cursor.moveToNext()) {
                String barcode = cursor.getString(barcodeIndex);
                String name = cursor.getString(nameIndex);
                double price = cursor.getDouble(priceIndex);

                String safeName = name == null ? "" : name;
                String safeBarcode = barcode == null ? "" : barcode;

                if (!safeName.toLowerCase(Locale.ROOT).contains(query)
                        && !safeBarcode.toLowerCase(Locale.ROOT)
                        .contains(query)) {
                    continue;
                }

                found++;

                Button item = new Button(this);
                item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                item.setText(
                        safeName + "\n"
                                + "บาร์โค้ด: " + safeBarcode + "\n"
                                + String.format(
                                        Locale.US,
                                        "ราคา %.2f บาท",
                                        price
                                )
                                + "\nแตะเพื่อเรียกบาร์โค้ดเดิม"
                );

                final String selectedBarcode = safeBarcode;
                item.setOnClickListener(v ->
                        loadExistingProduct(selectedBarcode));

                productList.addView(item);
            }
        } catch (RuntimeException e) {
            showMessage("ค้นหาสินค้าไม่สำเร็จ: " + e.getMessage());
            return;
        }

        if (found == 0) {
            TextView empty = new TextView(this);
            empty.setText("ไม่พบสินค้าที่ค้นหา");
            empty.setTextColor(Color.DKGRAY);
            empty.setPadding(0, 12, 0, 12);
            productList.addView(empty);
        } else {
            showMessage("พบสินค้า " + found + " รายการ");
        }
    }

    private void loadExistingProduct(String barcode) {
        ProductRepository.Product product =
                productRepository.findByBarcode(barcode);

        if (product == null) {
            showMessage("ไม่พบสินค้ารายการนี้ในฐานข้อมูล");
            return;
        }

        displayProduct(product);
        showMessage("เรียกบาร์โค้ดเดิมแล้ว");
    }

    private void startScan() {
        try {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(
                    IntentIntegrator.ONE_D_CODE_TYPES
            );
            integrator.setPrompt("หันกล้องไปที่บาร์โค้ดสินค้า");
            integrator.setBeepEnabled(false);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();
        } catch (RuntimeException e) {
            showMessage("เปิดกล้องสแกนไม่ได้: " + e.getMessage());
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
                IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result == null || result.getContents() == null) {
            return;
        }

        String scanned = result.getContents().trim();

        searchInput.setText(scanned);

        ProductRepository.Product product =
                productRepository.findByBarcode(scanned);

        if (product != null) {
            displayProduct(product);
            showMessage("พบสินค้าในฐานข้อมูลแล้ว");
        } else {
            showMessage("ยังไม่มีบาร์โค้ดนี้ในฐานข้อมูล");
        }
    }

    private void refreshPrinters() {
        try {
            printers.clear();
            printers.addAll(printerService.getPairedPrinters());

            List<String> labels = new ArrayList<>();

            for (PrinterService.PrinterDevice device : printers) {
                labels.add(device.toString());
            }

            if (labels.isEmpty()) {
                labels.add("ไม่พบเครื่องพิมพ์ที่จับคู่ไว้");
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    labels
            );

            adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
            );

            printerSpinner.setAdapter(adapter);

            if (printers.isEmpty()) {
                updatePrinterStatus(
                        "กรุณาจับคู่เครื่องพิมพ์ใน Bluetooth ของโทรศัพท์ก่อน"
                );
            } else {
                updatePrinterStatus(
                        "พบเครื่อง Bluetooth ที่จับคู่ไว้ "
                                + printers.size() + " เครื่อง"
                );
            }

        } catch (SecurityException e) {
            requestBluetoothPermission();
        } catch (RuntimeException e) {
            updatePrinterStatus("อ่านรายชื่อเครื่องพิมพ์ไม่สำเร็จ");
        }
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT
                        },
                        REQUEST_BLUETOOTH_PERMISSION
                );
                return;
            }
        }

        refreshPrinters();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0]
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                refreshPrinters();
            } else {
                updatePrinterStatus("ต้องอนุญาต Bluetooth เพื่อใช้เครื่องพิมพ์");
            }
        }
    }

    private void connectPrinter() {
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermission();
            return;
        }

        int selected = printerSpinner.getSelectedItemPosition();

        if (printers.isEmpty()
                || selected < 0
                || selected >= printers.size()) {
            showMessage("กรุณาเลือกเครื่องพิมพ์ที่จับคู่ไว้ก่อน");
            return;
        }

        PrinterService.PrinterDevice device = printers.get(selected);
        updatePrinterStatus("กำลังเชื่อมต่อ " + device.name + " ...");

        new Thread(() -> {
            try {
                printerService.connect(device.address);

                runOnUiThread(() ->
                        updatePrinterStatus(
                                "เชื่อมต่อแล้ว: " + device.name
                        )
                );

            } catch (IOException | SecurityException e) {
                runOnUiThread(() ->
                        updatePrinterStatus(
                                "เชื่อมต่อไม่สำเร็จ: " + e.getMessage()
                        )
                );
            }
        }).start();
    }

    private void printCurrentBarcode() {
        if (currentBitmap == null) {
            showMessage("กรุณาสร้างหรือเลือกบาร์โค้ดก่อน");
            return;
        }

        if (!printerService.isConnected()) {
            showMessage("กรุณาเชื่อมต่อเครื่องพิมพ์ก่อน");
            return;
        }

        String quantityText = quantityInput.getText().toString().trim();
        int quantity;

        try {
            quantity = Integer.parseInt(quantityText);
        } catch (NumberFormatException e) {
            showMessage("กรุณาระบุจำนวนดวงที่ถูกต้อง");
            return;
        }

        if (quantity < 1 || quantity > 100) {
            showMessage("จำนวนพิมพ์ต้องอยู่ระหว่าง 1 ถึง 100 ดวง");
            return;
        }

        final Bitmap bitmapToPrint = currentBitmap;
        final String productName = currentProduct != null
                ? currentProduct.name
                : nameInput.getText().toString().trim();

        new Thread(() -> {
            try {
                for (int i = 0; i < quantity; i++) {
                    ByteArrayOutputStream output =
                            new ByteArrayOutputStream();

                    output.write(new byte[]{0x1B, 0x40});

                    output.write(new byte[]{0x1B, 0x61, 0x01});

                    output.write(
                            (productName + "\n").getBytes("UTF-8")
                    );

                    output.write(bitmapToEscPos(bitmapToPrint));

                    output.write(new byte[]{0x0A, 0x0A});

                    printerService.printRaw(output.toByteArray());
                }

                printerService.feed(3);

                runOnUiThread(() ->
                        showMessage("ส่งงานพิมพ์ " + quantity + " ดวงแล้ว")
                );

            } catch (IOException | RuntimeException e) {
                runOnUiThread(() ->
                        showMessage(
                                "พิมพ์ไม่สำเร็จ: " + e.getMessage()
                        )
                );
            }
        }).start();
    }

    /*
     * แปลงภาพเป็นคำสั่ง ESC/POS raster bitmap แบบ GS v 0
     * การรองรับคำสั่งและความกว้างขึ้นอยู่กับเครื่องพิมพ์แต่ละรุ่น
     */
    private byte[] bitmapToEscPos(Bitmap source) throws IOException {
        final int maxWidth = 384;

        int width = Math.min(source.getWidth(), maxWidth);
        int height = Math.max(
                1,
                source.getHeight() * width / source.getWidth()
        );

        Bitmap scaled = Bitmap.createScaledBitmap(
                source,
                width,
                height,
                true
        );

        int widthBytes = (width + 7) / 8;

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        output.write(new byte[]{
                0x1D,
                0x76,
                0x30,
                0x00,
                (byte) (widthBytes & 0xFF),
                (byte) ((widthBytes >> 8) & 0xFF),
                (byte) (height & 0xFF),
                (byte) ((height >> 8) & 0xFF)
        });

        for (int y = 0; y < height; y++) {
            for (int byteX = 0; byteX < widthBytes; byteX++) {
                int value = 0;

                for (int bit = 0; bit < 8; bit++) {
                    int x = byteX * 8 + bit;

                    if (x >= width) {
                        continue;
                    }

                    int pixel = scaled.getPixel(x, y);

                    int red = Color.red(pixel);
                    int green = Color.green(pixel);
                    int blue = Color.blue(pixel);

                    int gray = (red + green + blue) / 3;

                    if (gray < 160) {
                        value |= (0x80 >> bit);
                    }
                }

                output.write(value);
            }
        }

        if (scaled != source) {
            scaled.recycle();
        }

        return output.toByteArray();
    }

    private void updatePrinterStatus(String message) {
        if (printerStatus != null) {
            printerStatus.setText(message);
        }
    }

    private void showMessage(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
}
