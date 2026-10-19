package com.example.newapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.WriterException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BarcodeScreen extends Activity {

    private static final int REQUEST_BLUETOOTH_PERMISSION = 7001;

    private BarcodeRepository barcodeRepository;
    private PrinterService printerService;
    private BarcodePrintManager printManager;

    private EditText nameInput;
    private EditText priceInput;
    private EditText quantityInput;
    private Spinner printerSpinner;
    private TextView printerStatus;
    private TextView barcodeInfo;
    private ImageView barcodeImage;

    private Button createButton;
    private Button connectButton;
    private Button printButton;

    private final List<PrinterService.PrinterDevice> printerDevices =
            new ArrayList<>();

    private ArrayAdapter<PrinterService.PrinterDevice> printerAdapter;
    private ProductRepository.Product currentProduct;
    private Bitmap currentBitmap;

    private boolean creatingBarcode = false;
    private boolean connecting = false;
    private boolean printing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        barcodeRepository = new BarcodeRepository(this);
        printerService = PrinterService.getInstance(this);
        printManager = new BarcodePrintManager(printerService);

        buildScreen();
        refreshPrinterStatus();
        refreshPrinters();
    }

    private void buildScreen() {
        int pad = dp(16);

        ScrollView scrollView = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFFF5F6F8);

        scrollView.addView(root);
        setContentView(scrollView);

        TextView title = new TextView(this);
        title.setText("สร้างบาร์โค้ดสินค้า");
        title.setTextSize(24);
        title.setTextColor(0xFF172033);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("มาตรฐานบาร์โค้ด: Code 128");
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(0xFF586174);

        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.bottomMargin = dp(16);
        root.addView(subtitle, subtitleParams);

        addLabel(root, "ชื่อสินค้า");

        nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("กรอกชื่อสินค้า");
        nameInput.setTextSize(16);
        nameInput.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
        );
        root.addView(nameInput, matchWrap());

        addLabel(root, "ราคาขาย (บาท)");

        priceInput = new EditText(this);
        priceInput.setSingleLine(true);
        priceInput.setHint("เช่น 25 หรือ 25.50");
        priceInput.setTextSize(16);
        priceInput.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        root.addView(priceInput, matchWrap());

        createButton = new Button(this);
        createButton.setText("สร้างและบันทึกบาร์โค้ด");

        LinearLayout.LayoutParams createParams = matchWrap();
        createParams.topMargin = dp(12);

        root.addView(createButton, createParams);
        createButton.setOnClickListener(v -> createBarcode());

        barcodeInfo = new TextView(this);
        barcodeInfo.setText("ยังไม่ได้สร้างบาร์โค้ด");
        barcodeInfo.setTextSize(16);
        barcodeInfo.setTextColor(0xFF172033);
        barcodeInfo.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams infoParams = matchWrap();
        infoParams.topMargin = dp(12);

        root.addView(barcodeInfo, infoParams);

        barcodeImage = new ImageView(this);
        barcodeImage.setAdjustViewBounds(true);
        barcodeImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        barcodeImage.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams imageParams = matchWrap();
        imageParams.height = dp(145);
        imageParams.topMargin = dp(8);
        imageParams.bottomMargin = dp(12);

        root.addView(barcodeImage, imageParams);

        addSectionTitle(root, "เครื่องพิมพ์บาร์โค้ด");

        printerStatus = new TextView(this);
        printerStatus.setTextSize(14);
        printerStatus.setTextColor(0xFF374151);
        root.addView(printerStatus, matchWrap());

        printerSpinner = new Spinner(this);

        printerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                printerDevices
        );

        printerAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        printerSpinner.setAdapter(printerAdapter);

        LinearLayout.LayoutParams spinnerParams = matchWrap();
        spinnerParams.topMargin = dp(8);

        root.addView(printerSpinner, spinnerParams);

        Button refreshButton = new Button(this);
        refreshButton.setText("ค้นหาเครื่องพิมพ์ที่จับคู่ไว้");
        root.addView(refreshButton, matchWrap());
        refreshButton.setOnClickListener(v -> refreshPrinters());

        connectButton = new Button(this);
        connectButton.setText("เชื่อมต่อเครื่องพิมพ์");
        root.addView(connectButton, matchWrap());
        connectButton.setOnClickListener(v -> connectPrinter());

        quantityInput = new EditText(this);
        quantityInput.setSingleLine(true);
        quantityInput.setHint("จำนวนสำเนา (1–100)");
        quantityInput.setText("1");
        quantityInput.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
        );

        LinearLayout.LayoutParams quantityParams = matchWrap();
        quantityParams.topMargin = dp(8);

        root.addView(quantityInput, quantityParams);

        printButton = new Button(this);
        printButton.setText("พิมพ์บาร์โค้ด");
        root.addView(printButton, matchWrap());
        printButton.setOnClickListener(v -> printCurrentBarcode());

        Button libraryButton = new Button(this);
        libraryButton.setText(
                "เปิดคลังบาร์โค้ด / ค้นหา / พิมพ์ซ้ำ"
        );

        LinearLayout.LayoutParams libraryParams = matchWrap();
        libraryParams.topMargin = dp(8);

        root.addView(libraryButton, libraryParams);

        libraryButton.setOnClickListener(v -> {
            startActivity(new Intent(
                    BarcodeScreen.this,
                    BarcodeLibraryScreen.class
            ));
        });

        Button backButton = new Button(this);
        backButton.setText("กลับหน้าหลัก");
        root.addView(backButton, matchWrap());
        backButton.setOnClickListener(v -> finish());
    }

    private void createBarcode() {
        if (creatingBarcode) {
            return;
        }

        String name = nameInput.getText().toString().trim();
        String priceText = priceInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("กรุณากรอกชื่อสินค้า");
            nameInput.requestFocus();
            return;
        }

        if (priceText.isEmpty()) {
            priceInput.setError("กรุณากรอกราคาขาย");
            priceInput.requestFocus();
            return;
        }

        final double price;

        try {
            price = Double.parseDouble(priceText);

            if (Double.isNaN(price)
                    || Double.isInfinite(price)
                    || price < 0) {
                priceInput.setError(
                        "ราคาต้องเป็นเลขตั้งแต่ 0 ขึ้นไป"
                );
                return;
            }

        } catch (NumberFormatException e) {
            priceInput.setError("กรุณากรอกราคาให้ถูกต้อง");
            return;
        }

        creatingBarcode = true;
        createButton.setEnabled(false);

        ProductRepository.Product createdProduct = null;

        try {
            // บันทึกสินค้าและจองเลขบาร์โค้ดใน transaction เดียว
            createdProduct = barcodeRepository.createProduct(
                    name,
                    price
            );

            /*
             * สินค้าถูกบันทึกแล้ว
             * ล้างช่องกรอกทันทีเพื่อป้องกันการกดซ้ำ
             * แล้วค่อยสร้างภาพบาร์โค้ด
             */
            nameInput.setText("");
            priceInput.setText("");

            Bitmap newBitmap = BarcodeGenerator.createCode128Bitmap(
                    createdProduct.barcode,
                    700,
                    200
            );

            showProduct(createdProduct, newBitmap);

            Toast.makeText(
                    this,
                    "บันทึกสินค้าและสร้างบาร์โค้ดแล้ว",
                    Toast.LENGTH_LONG
            ).show();

        } catch (WriterException e) {
            if (createdProduct != null) {
                showProduct(createdProduct, null);

                showError(
                        "บันทึกสินค้าและเลขบาร์โค้ดแล้ว "
                                + "แต่สร้างภาพบาร์โค้ดไม่สำเร็จ: "
                                + safeMessage(e)
                                + "\nค้นหาสินค้านี้ได้จากคลังบาร์โค้ด"
                );
            } else {
                showError(
                        "สร้างภาพบาร์โค้ดไม่สำเร็จ: "
                                + safeMessage(e)
                );
            }

        } catch (Exception e) {
            if (createdProduct != null) {
                showProduct(createdProduct, null);

                showError(
                        "บันทึกสินค้าแล้ว แต่มีปัญหาระหว่างสร้างภาพ: "
                                + safeMessage(e)
                                + "\nค้นหาสินค้านี้ได้จากคลังบาร์โค้ด"
                );
            } else {
                showError(
                        "บันทึกหรือสร้างบาร์โค้ดไม่สำเร็จ: "
                                + safeMessage(e)
                );
            }

        } finally {
            creatingBarcode = false;

            if (createButton != null) {
                createButton.setEnabled(true);
            }
        }
    }

    private void showProduct(
            ProductRepository.Product product,
            Bitmap bitmap
    ) {
        if (currentBitmap != null
                && currentBitmap != bitmap
                && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }

        currentProduct = product;
        currentBitmap = bitmap;

        barcodeInfo.setText(
                "ชื่อสินค้า: " + product.name
                        + "\nราคา: " + String.format(
                                Locale.US,
                                "%.2f",
                                product.price
                        ) + " บาท"
                        + "\nบาร์โค้ด: " + product.barcode
                        + "\nมาตรฐาน: Code 128"
        );

        if (bitmap != null && !bitmap.isRecycled()) {
            barcodeImage.setImageBitmap(bitmap);
        } else {
            barcodeImage.setImageDrawable(null);
        }
    }

    private void refreshPrinters() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission();
            return;
        }

        try {
            List<PrinterService.PrinterDevice> found =
                    printerService.getPairedPrinters();

            printerDevices.clear();

            if (found != null) {
                printerDevices.addAll(found);
            }

            if (printerAdapter != null) {
                printerAdapter.notifyDataSetChanged();
            }

            refreshPrinterStatus();

            if (printerDevices.isEmpty()) {
                Toast.makeText(
                        this,
                        "ยังไม่พบเครื่องพิมพ์ที่จับคู่ไว้ "
                                + "กรุณาจับคู่เครื่องพิมพ์ใน Bluetooth "
                                + "ของโทรศัพท์ก่อน",
                        Toast.LENGTH_LONG
                ).show();
            }

        } catch (SecurityException e) {
            requestBluetoothPermission();

        } catch (Exception e) {
            showError(
                    "ค้นหาเครื่องพิมพ์ไม่สำเร็จ: "
                            + safeMessage(e)
            );
        }
    }

    private void connectPrinter() {
        if (connecting) {
            return;
        }

        if (!hasBluetoothPermission()) {
            requestBluetoothPermission();
            return;
        }

        int position = printerSpinner.getSelectedItemPosition();

        if (position < 0 || position >= printerDevices.size()) {
            showError("กรุณาค้นหาและเลือกเครื่องพิมพ์ก่อน");
            return;
        }

        PrinterService.PrinterDevice device =
                printerDevices.get(position);

        connecting = true;
        connectButton.setEnabled(false);
        printerStatus.setText("กำลังเชื่อมต่อ: " + device.name);

        Thread thread = new Thread(() -> {
            String error = null;

            try {
                printerService.connect(device.address);

            } catch (Exception e) {
                error = safeMessage(e);
            }

            final String resultError = error;

            runOnUiThread(() -> {
                connecting = false;
                connectButton.setEnabled(true);

                if (resultError == null) {
                    printerStatus.setText(
                            "เชื่อมต่อแล้ว: " + device.name
                    );

                    Toast.makeText(
                            this,
                            "เชื่อมต่อเครื่องพิมพ์สำเร็จ",
                            Toast.LENGTH_SHORT
                    ).show();

                } else {
                    printerStatus.setText(
                            "เชื่อมต่อไม่สำเร็จ: " + resultError
                    );

                    showError(
                            "เชื่อมต่อเครื่องพิมพ์ไม่สำเร็จ: "
                                    + resultError
                    );
                }

                refreshPrinterStatus();
            });
        });

        thread.start();
    }

    private void printCurrentBarcode() {
        if (printing) {
            return;
        }

        if (currentProduct == null) {
            showError(
                    "กรุณาสร้างบาร์โค้ดหรือเลือกสินค้าจากคลังก่อน"
            );
            return;
        }

        if (currentBitmap == null || currentBitmap.isRecycled()) {
            showError(
                    "ไม่มีภาพบาร์โค้ดสำหรับพิมพ์ "
                            + "กรุณาเลือกสินค้าจากคลังบาร์โค้ดใหม่"
            );
            return;
        }

        if (!printerService.isConnected()) {
            showError("กรุณาเชื่อมต่อเครื่องพิมพ์ก่อน");
            refreshPrinterStatus();
            return;
        }

        final int quantity;

        try {
            quantity = Integer.parseInt(
                    quantityInput.getText().toString().trim()
            );

            if (quantity < 1 || quantity > 100) {
                showError(
                        "จำนวนสำเนาต้องอยู่ระหว่าง 1 ถึง 100"
                );
                return;
            }

        } catch (NumberFormatException e) {
            showError(
                    "กรุณาระบุจำนวนสำเนาเป็นตัวเลข 1–100"
            );
            return;
        }

        final ProductRepository.Product product = currentProduct;
        final Bitmap printBitmap;

        try {
            printBitmap = currentBitmap.copy(
                    Bitmap.Config.ARGB_8888,
                    false
            );

            if (printBitmap == null) {
                showError("เตรียมภาพสำหรับพิมพ์ไม่สำเร็จ");
                return;
            }

        } catch (Exception e) {
            showError("เตรียมภาพสำหรับพิมพ์ไม่สำเร็จ");
            return;
        }

        printing = true;
        printButton.setEnabled(false);

        printerStatus.setText(
                "กำลังพิมพ์ " + quantity + " สำเนา..."
        );

        new Thread(() -> {
            String error = null;

            try {
                printManager.printBarcode(
                        product.name,
                        product.barcode,
                        product.price,
                        printBitmap,
                        quantity
                );

            } catch (Exception e) {
                error = safeMessage(e);

            } finally {
                if (!printBitmap.isRecycled()) {
                    printBitmap.recycle();
                }
            }

            final String resultError = error;

            runOnUiThread(() -> {
                printing = false;
                printButton.setEnabled(true);

                refreshPrinterStatus();

                if (resultError == null) {
                    Toast.makeText(
                            this,
                            "ส่งงานพิมพ์แล้ว "
                                    + quantity
                                    + " สำเนา",
                            Toast.LENGTH_LONG
                    ).show();

                } else {
                    showError(
                            "พิมพ์ไม่สำเร็จ: " + resultError
                    );
                }
            });
        }).start();
    }

    private void refreshPrinterStatus() {
        if (printerStatus == null) {
            return;
        }

        try {
            if (printerService != null
                    && printerService.isConnected()) {
                String address =
                        printerService.getConnectedAddress();

                printerStatus.setText(
                        "สถานะ: เชื่อมต่อแล้ว"
                                + (address == null
                                ? ""
                                : "\n" + address)
                );

            } else if (!connecting && !printing) {
                printerStatus.setText(
                        "สถานะ: ยังไม่เชื่อมต่อเครื่องพิมพ์"
                );
            }

        } catch (Exception e) {
            printerStatus.setText(
                    "สถานะ: ตรวจสอบเครื่องพิมพ์ไม่ได้"
            );
        }
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(
                        Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT
                    },
                    REQUEST_BLUETOOTH_PERMISSION
            );

        } else {
            showError("ไม่สามารถเข้าถึง Bluetooth ได้");
        }
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
                    == PackageManager.PERMISSION_GRANTED) {
                refreshPrinters();

            } else {
                showError(
                        "ต้องอนุญาต Bluetooth "
                                + "เพื่อค้นหาและเชื่อมต่อเครื่องพิมพ์"
                );
            }
        }
    }

    private void addLabel(
            LinearLayout root,
            String text
    ) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(15);
        label.setTextColor(0xFF172033);
        label.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);

        root.addView(label, params);
    }

    private void addSectionTitle(
            LinearLayout root,
            String text
    ) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(19);
        label.setTextColor(0xFF172033);
        label.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(12);
        params.bottomMargin = dp(6);

        root.addView(label, params);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density + 0.5f
        );
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();

        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("แจ้งเตือน")
                .setMessage(message)
                .setPositiveButton("ตกลง", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (barcodeImage != null) {
            barcodeImage.setImageDrawable(null);
        }

        if (currentBitmap != null
                && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }

        super.onDestroy();
    }
            }
