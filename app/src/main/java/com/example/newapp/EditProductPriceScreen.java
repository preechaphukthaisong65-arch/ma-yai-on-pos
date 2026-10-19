
package com.example.newapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class EditProductPriceScreen extends Activity {

    private static final int CAMERA_REQUEST = 820;
    private static final int NOTIFICATION_REQUEST = 822;

    private ProductRepository productRepository;
    private NotificationRepository notificationRepository;
    private AndroidNotificationService notificationService;

    private LinearLayout root;
    private LinearLayout content;
    private DecoratedBarcodeView barcodeView;

    private TextView statusText;
    private TextView productText;
    private EditText barcodeInput;
    private EditText priceInput;

    private TextToSpeech tts;

    private boolean scanning = false;
    private boolean waitingForVoice = false;
    private boolean screenReady = false;

    private ProductRepository.Product selectedProduct;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        productRepository = new ProductRepository(this);

        LocalDatabase database =
                LocalDatabase.getInstance(this);

        notificationRepository =
                new NotificationRepository(database);

        notificationService =
                new AndroidNotificationService(this);

        initializeVoice();
        buildScreen();
        screenReady = true;

        requestNotificationPermissionIfNeeded();
    }

    private int dp(float value) {
        return (int) (
                value * getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    NOTIFICATION_REQUEST
            );
        }
    }

    private void initializeVoice() {
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && tts != null) {
                int languageResult =
                        tts.setLanguage(new Locale("th", "TH"));

                if (languageResult == TextToSpeech.LANG_MISSING_DATA
                        || languageResult
                        == TextToSpeech.LANG_NOT_SUPPORTED) {
                    runOnUiThread(() ->
                            toast("เครื่องยังไม่รองรับเสียงภาษาไทย")
                    );
                }
            }
        });
    }

    private TextView makeText(
            String value,
            int size,
            boolean bold
    ) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.rgb(35, 45, 55));

        if (bold) {
            text.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );
        }

        return text;
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));

        root.addView(
                makeText("แก้ไขราคาสินค้า", 24, true),
                params()
        );

        root.addView(
                makeText(
                        "สแกนบาร์โค้ดเพื่อค้นหาสินค้า",
                        16,
                        false
                ),
                params()
        );

        barcodeInput = new EditText(this);
        barcodeInput.setSingleLine(true);
        barcodeInput.setHint("บาร์โค้ดสินค้า");
        barcodeInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        root.addView(barcodeInput, params());

        Button searchButton = new Button(this);
        searchButton.setText("ค้นหาจากบาร์โค้ดที่กรอก");
        searchButton.setAllCaps(false);
        searchButton.setOnClickListener(v ->
                searchByBarcode(
                        barcodeInput.getText().toString().trim()
                )
        );
        root.addView(searchButton, params());

        Button scanButton = new Button(this);
        scanButton.setText("เปิดกล้องสแกนบาร์โค้ด");
        scanButton.setAllCaps(false);
        scanButton.setOnClickListener(v ->
                requestCameraAndStart()
        );
        root.addView(scanButton, params());

        statusText = makeText(
                "ยังไม่ได้เลือกสินค้า",
                15,
                false
        );

        LinearLayout.LayoutParams statusParams = params();
        statusParams.topMargin = dp(12);
        root.addView(statusText, statusParams);

        productText = makeText(
                "ข้อมูลสินค้าจะแสดงที่นี่",
                17,
                true
        );

        LinearLayout.LayoutParams productParams = params();
        productParams.topMargin = dp(8);
        root.addView(productText, productParams);

        root.addView(
                makeText("ราคาขายใหม่ (บาท)", 16, true),
                params()
        );

        priceInput = new EditText(this);
        priceInput.setSingleLine(true);
        priceInput.setHint("กรอกราคาขายใหม่");
        priceInput.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED
        );
        priceInput.setEnabled(false);
        root.addView(priceInput, params());

        Button saveButton = new Button(this);
        saveButton.setText("ตรวจสอบและยืนยันบันทึกราคา");
        saveButton.setAllCaps(false);
        saveButton.setOnClickListener(v -> confirmPriceChange());
        root.addView(saveButton, params());

        Button clearButton = new Button(this);
        clearButton.setText("ล้างสินค้าและค้นหาใหม่");
        clearButton.setAllCaps(false);
        clearButton.setOnClickListener(v -> clearSelection());
        root.addView(clearButton, params());

        Button backButton = new Button(this);
        backButton.setText("กลับ");
        backButton.setAllCaps(false);
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton, params());

        content = root;

        scroll.addView(root);
        setContentView(scroll);
    }

    private void requestCameraAndStart() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_REQUEST
            );
            return;
        }

        startScanner();
    }

    private void startScanner() {
        if (scanning) {
            return;
        }

        scanning = true;
        waitingForVoice = false;

        barcodeView = new DecoratedBarcodeView(this);
        barcodeView.setStatusText("สแกนบาร์โค้ดสินค้า");

        LinearLayout.LayoutParams scannerParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(250)
                );

        int insertAt = 3;
        root.addView(barcodeView, insertAt, scannerParams);

        Button stopButton = new Button(this);
        stopButton.setText("ปิดกล้องสแกน");
        stopButton.setAllCaps(false);
        stopButton.setOnClickListener(v -> stopScanner());

        root.addView(stopButton, insertAt + 1, params());

        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (!scanning || waitingForVoice || result == null) {
                    return;
                }

                String barcode = result.getText();

                if (barcode == null || barcode.trim().isEmpty()) {
                    return;
                }

                waitingForVoice = true;

                runOnUiThread(() -> {
                    if (barcodeView != null) {
                        barcodeView.pause();
                    }

                    searchByBarcode(barcode.trim());
                });
            }

            @Override
            public void possibleResultPoints(
                    List<ResultPoint> points
            ) {
                // ไม่ต้องดำเนินการกับจุดสแกน
            }
        });

        barcodeView.resume();
        statusText.setText("กำลังสแกนบาร์โค้ด...");
    }

    private void stopScanner() {
        scanning = false;
        waitingForVoice = false;

        if (barcodeView != null) {
            try {
                barcodeView.pause();
            } catch (Exception e) {
                android.util.Log.w(
                        "EditProductPrice",
                        "หยุดกล้องไม่สำเร็จ",
                        e
                );
            }

            ViewGroup parent =
                    (ViewGroup) barcodeView.getParent();

            if (parent != null) {
                parent.removeView(barcodeView);
            }

            barcodeView = null;
        }

        if (root != null) {
            for (int i = root.getChildCount() - 1; i >= 0; i--) {
                View child = root.getChildAt(i);

                if (child instanceof Button) {
                    Button button = (Button) child;

                    if ("ปิดกล้องสแกน".contentEquals(
                            button.getText()
                    )) {
                        root.removeViewAt(i);
                    }
                }
            }
        }
    }

    private void searchByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            selectedProduct = null;
            priceInput.setText("");
            priceInput.setEnabled(false);
            productText.setText("ยังไม่ได้เลือกสินค้า");
            statusText.setText("กรุณาสแกนหรือกรอกบาร์โค้ด");
            toast("กรุณาระบุบาร์โค้ดสินค้า");
            return;
        }

        stopScanner();

        String exactBarcode = barcode.trim();

        ProductRepository.Product product;

        try {
            product = productRepository.findByBarcode(exactBarcode);
        } catch (Exception e) {
            android.util.Log.e(
                    "EditProductPrice",
                    "ค้นหาสินค้าไม่สำเร็จ",
                    e
            );

            selectedProduct = null;
            priceInput.setText("");
            priceInput.setEnabled(false);
            productText.setText("ไม่สามารถอ่านข้อมูลสินค้าได้");
            statusText.setText("ค้นหาสินค้าไม่สำเร็จ");
            toast("ค้นหาสินค้าไม่สำเร็จ");
            return;
        }

        if (product == null
                || !exactBarcode.equals(product.barcode)) {
            selectedProduct = null;
            priceInput.setText("");
            priceInput.setEnabled(false);

            productText.setText("ไม่พบสินค้าบาร์โค้ดนี้");
            statusText.setText("ไม่พบสินค้า: " + exactBarcode);

            speak("ไม่พบสินค้าในระบบ");
            return;
        }

        selectedProduct = product;

        productText.setText(
                "ชื่อสินค้า: " + product.name
                        + "\nบาร์โค้ด: " + product.barcode
                        + "\nราคาขายปัจจุบัน: "
                        + money(product.price) + " บาท"
        );

        statusText.setText("พบสินค้าแล้ว ตรวจสอบข้อมูลก่อนแก้ราคา");

        priceInput.setText(money(product.price));
        priceInput.setEnabled(true);

        speak(product.name);
    }

    private void confirmPriceChange() {
        if (selectedProduct == null) {
            toast("กรุณาค้นหาและเลือกสินค้าก่อน");
            return;
        }

        final double newPrice;

        try {
            String value = priceInput.getText()
                    .toString()
                    .trim()
                    .replace(",", "");

            if (value.isEmpty()) {
                throw new NumberFormatException();
            }

            newPrice = Double.parseDouble(value);

            if (!Double.isFinite(newPrice) || newPrice < 0) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException e) {
            toast("กรุณากรอกราคาที่ถูกต้องและไม่ติดลบ");
            return;
        }

        final String barcode = selectedProduct.barcode;
        final String productName = selectedProduct.name;
        final double displayedOldPrice = selectedProduct.price;

        new AlertDialog.Builder(this)
                .setTitle("ยืนยันการเปลี่ยนราคา")
                .setMessage(
                        "สินค้า: " + productName
                                + "\nบาร์โค้ด: " + barcode
                                + "\nราคาเดิม: "
                                + money(displayedOldPrice) + " บาท"
                                + "\nราคาใหม่: "
                                + money(newPrice) + " บาท"
                                + "\n\nต้องการบันทึกราคานี้หรือไม่?"
                )
                .setNegativeButton("ยกเลิก", (dialog, which) -> {
                    // ไม่เปลี่ยนข้อมูล
                })
                .setPositiveButton("ยืนยันบันทึก", (dialog, which) ->
                        savePrice(
                                barcode,
                                displayedOldPrice,
                                newPrice
                        )
                )
                .show();
    }

    private void savePrice(
            String barcode,
            double displayedOldPrice,
            double newPrice
    ) {
        final ProductRepository.Product saved;
        final double oldPrice;

        try {
            // ตรวจข้อมูลล่าสุดอีกครั้งก่อนบันทึก
            ProductRepository.Product current =
                    productRepository.findByBarcode(barcode);

            if (current == null
                    || !barcode.equals(current.barcode)) {
                selectedProduct = null;
                priceInput.setText("");
                priceInput.setEnabled(false);
                productText.setText("ไม่พบสินค้าแล้ว");
                statusText.setText("ยกเลิกการบันทึก");
                toast("ไม่พบสินค้านี้ จึงไม่ได้บันทึกราคา");
                return;
            }

            if (Double.compare(
                    current.price,
                    displayedOldPrice
            ) != 0) {
                selectedProduct = current;

                productText.setText(
                        "ชื่อสินค้า: " + current.name
                                + "\nบาร์โค้ด: " + current.barcode
                                + "\nราคาปัจจุบันเปลี่ยนเป็น: "
                                + money(current.price) + " บาท"
                );

                priceInput.setText(money(current.price));
                statusText.setText(
                        "ราคาเปลี่ยนระหว่างเปิดหน้า กรุณาตรวจสอบและยืนยันใหม่"
                );

                toast("ราคาปัจจุบันเปลี่ยนไปแล้ว กรุณาตรวจสอบอีกครั้ง");
                return;
            }

            if (Double.compare(current.price, newPrice) == 0) {
                toast("ราคาใหม่เท่ากับราคาเดิม ไม่มีการบันทึกประวัติ");
                return;
            }

            oldPrice = current.price;

            // Repository บันทึกราคากับประวัติใน transaction เดียวกัน
            productRepository.changeSalePrice(barcode, newPrice);

            // ยืนยันว่าราคาใหม่อยู่ในฐานข้อมูลแล้ว
            saved = productRepository.findByBarcode(barcode);

            if (saved == null
                    || !barcode.equals(saved.barcode)
                    || Double.compare(saved.price, newPrice) != 0) {
                throw new IllegalStateException(
                        "ไม่สามารถยืนยันราคาที่บันทึกได้"
                );
            }

        } catch (Exception e) {
            android.util.Log.e(
                    "EditProductPrice",
                    "เปลี่ยนราคาไม่สำเร็จ",
                    e
            );

            statusText.setText("บันทึกราคาไม่สำเร็จ");
            toast("บันทึกราคาไม่สำเร็จ กรุณาตรวจสอบราคาแล้วลองใหม่");
            return;
        }

        // ตั้งสถานะสำเร็จทันทีหลังยืนยันการบันทึกราคา
        selectedProduct = saved;

        productText.setText(
                "ชื่อสินค้า: " + saved.name
                        + "\nบาร์โค้ด: " + saved.barcode
                        + "\nราคาใหม่: " + money(saved.price) + " บาท"
        );

        priceInput.setText(money(saved.price));
        statusText.setText("บันทึกราคาและประวัติสำเร็จ");

        String message = "เปลี่ยนราคาสินค้า "
                + saved.name
                + " จาก " + money(oldPrice)
                + " บาท เป็น " + money(saved.price)
                + " บาท";

        // การแจ้งเตือนเป็นงานแยกจากธุรกรรมราคา
        // หากแจ้งเตือนผิดพลาด จะไม่รายงานว่าราคาบันทึกล้มเหลว
        try {
            boolean messageSaved = notificationRepository.addMessage(
                    "PRICE_CHANGED",
                    message
            );

            if (!messageSaved) {
                android.util.Log.e(
                        "EditProductPrice",
                        "บันทึกข้อความแจ้งเตือนในแอปไม่สำเร็จ"
                );
            }
        } catch (Exception e) {
            android.util.Log.e(
                    "EditProductPrice",
                    "บันทึกข้อความแจ้งเตือนในแอปไม่สำเร็จ",
                    e
            );
        }

        try {
            int notificationId = (int) (
                    System.currentTimeMillis() & 0x7fffffff
            );

            boolean shown = notificationService.show(
                    notificationId,
                    "แก้ไขราคาสินค้าสำเร็จ",
                    message
            );

            if (!shown) {
                android.util.Log.i(
                        "EditProductPrice",
                        "แสดง Android notification ไม่สำเร็จ "
                                + "อาจไม่ได้รับอนุญาตแจ้งเตือน"
                );
            }
        } catch (Exception e) {
            android.util.Log.e(
                    "EditProductPrice",
                    "แสดง Android notification ไม่สำเร็จ",
                    e
            );
        }

        toast("บันทึกราคาใหม่สำเร็จ");
    }

    private void clearSelection() {
        stopScanner();

        selectedProduct = null;

        barcodeInput.setText("");
        priceInput.setText("");
        priceInput.setEnabled(false);

        productText.setText("ข้อมูลสินค้าจะแสดงที่นี่");
        statusText.setText("ยังไม่ได้เลือกสินค้า");
    }

    private String money(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    private void speak(String message) {
        if (tts == null) {
            waitingForVoice = false;
            return;
        }

        int result = tts.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "price_" + UUID.randomUUID()
        );

        if (result == TextToSpeech.ERROR) {
            waitingForVoice = false;
        }
    }

    private void toast(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
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

        if (requestCode == CAMERA_REQUEST) {
            if (grantResults.length > 0
                    && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED) {
                startScanner();
            } else {
                toast("ต้องอนุญาตกล้องเพื่อสแกนบาร์โค้ด");
            }

        } else if (requestCode == NOTIFICATION_REQUEST) {
            if (grantResults.length > 0
                    && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED) {
                toast("อนุญาตการแจ้งเตือนแล้ว");
            } else {
                toast(
                        "ยังปิดการแจ้งเตือนระบบไว้ "
                                + "แต่ข้อความในแอปจะแสดงได้หากบันทึกสำเร็จ"
                );
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (screenReady
                && scanning
                && barcodeView != null
                && !waitingForVoice) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        if (barcodeView != null) {
            barcodeView.pause();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopScanner();

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        super.onDestroy();
    }
}
