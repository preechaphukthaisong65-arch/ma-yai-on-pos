
package com.example.newapp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
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

import java.text.DecimalFormat;
import java.util.List;

public class PriceHistoryScreen extends Activity {

    private static final int CAMERA_REQUEST = 821;

    private ProductRepository productRepository;

    private LinearLayout root;
    private LinearLayout scannerHolder;
    private LinearLayout results;

    private EditText barcodeInput;
    private TextView statusText;
    private Button scanButton;

    private DecoratedBarcodeView barcodeView;

    private boolean scanning = false;
    private boolean screenReady = false;

    private final DecimalFormat money =
            new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        productRepository = new ProductRepository(this);

        buildScreen();
        screenReady = true;
    }

    private int dp(float value) {
        return (int) (
                value * getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private TextView text(
            String value,
            int size,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(32, 32, 32));

        if (bold) {
            view.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );
        }

        return view;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 247, 247));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(18));

        root.addView(
                text("ประวัติการแก้ไขราคาขาย", 22, true),
                params()
        );

        root.addView(
                text(
                        "สแกนหรือกรอกบาร์โค้ดเพื่อดูประวัติทั้งหมด "
                                + "เรียงจากรายการล่าสุด",
                        15,
                        false
                ),
                params()
        );

        barcodeInput = new EditText(this);
        barcodeInput.setSingleLine(true);
        barcodeInput.setHint("บาร์โค้ดสินค้า");
        barcodeInput.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        root.addView(barcodeInput, params());

        Button searchButton = new Button(this);
        searchButton.setText("ค้นหาประวัติ");
        searchButton.setAllCaps(false);
        searchButton.setOnClickListener(v ->
                searchHistory(
                        barcodeInput.getText().toString().trim()
                )
        );
        root.addView(searchButton, params());

        scanButton = new Button(this);
        scanButton.setText("เปิดกล้องสแกนบาร์โค้ด");
        scanButton.setAllCaps(false);
        scanButton.setOnClickListener(v ->
                requestCameraAndStart()
        );
        root.addView(scanButton, params());

        scannerHolder = new LinearLayout(this);
        scannerHolder.setOrientation(LinearLayout.VERTICAL);
        root.addView(scannerHolder, params());

        statusText = text(
                "กรุณาสแกนหรือกรอกบาร์โค้ดสินค้า",
                15,
                false
        );

        LinearLayout.LayoutParams statusParams = params();
        statusParams.topMargin = dp(10);
        root.addView(statusText, statusParams);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams resultParams = params();
        resultParams.topMargin = dp(8);
        root.addView(results, resultParams);

        Button clearButton = new Button(this);
        clearButton.setText("ล้างผลการค้นหา");
        clearButton.setAllCaps(false);
        clearButton.setOnClickListener(v -> {
            stopScanner();
            barcodeInput.setText("");
            results.removeAllViews();
            statusText.setText("กรุณาสแกนหรือกรอกบาร์โค้ดสินค้า");
        });
        root.addView(clearButton, params());

        Button backButton = new Button(this);
        backButton.setText("กลับ");
        backButton.setAllCaps(false);
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton, params());

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
        scanButton.setEnabled(false);

        barcodeView = new DecoratedBarcodeView(this);
        barcodeView.setStatusText("สแกนบาร์โค้ดสินค้า");

        scannerHolder.removeAllViews();

        scannerHolder.addView(
                barcodeView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(250)
                )
        );

        Button stopButton = new Button(this);
        stopButton.setText("ปิดกล้องสแกน");
        stopButton.setAllCaps(false);
        stopButton.setOnClickListener(v -> stopScanner());
        scannerHolder.addView(stopButton, params());

        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (!scanning || result == null) {
                    return;
                }

                String barcode = result.getText();

                if (barcode == null || barcode.trim().isEmpty()) {
                    return;
                }

                String exactBarcode = barcode.trim();

                runOnUiThread(() -> {
                    if (!scanning) {
                        return;
                    }

                    stopScanner();
                    barcodeInput.setText(exactBarcode);
                    searchHistory(exactBarcode);
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
        scanButton.setEnabled(true);

        if (barcodeView != null) {
            try {
                barcodeView.pause();
            } catch (Exception e) {
                android.util.Log.w(
                        "PriceHistoryScreen",
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

        if (scannerHolder != null) {
            scannerHolder.removeAllViews();
        }
    }

    private void searchHistory(String barcode) {
        stopScanner();
        results.removeAllViews();

        if (barcode == null || barcode.trim().isEmpty()) {
            statusText.setText("กรุณาระบุบาร์โค้ดสินค้า");
            results.addView(
                    text("กรุณาสแกนหรือกรอกบาร์โค้ดก่อนค้นหา", 15, false),
                    params()
            );
            return;
        }

        final String exactBarcode = barcode.trim();
        int count = 0;

        try (Cursor cursor =
                     productRepository.getPriceHistory(exactBarcode)) {

            while (cursor.moveToNext()) {
                count++;

                String savedBarcode =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow("barcode")
                        );

                // ป้องกันไม่ให้แสดงประวัติของบาร์โค้ดอื่น
                if (!exactBarcode.equals(savedBarcode)) {
                    continue;
                }

                String productName =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow("product_name")
                        );

                double oldPrice =
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow("old_price")
                        );

                double newPrice =
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow("new_price")
                        );

                String changedAt =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow("changed_at")
                        );

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(12), dp(10), dp(12), dp(10));
                card.setBackgroundColor(Color.WHITE);

                LinearLayout.LayoutParams cardParams =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        );

                cardParams.topMargin = dp(5);
                cardParams.bottomMargin = dp(5);

                card.addView(
                        text("ชื่อสินค้า: " + safe(productName), 16, true),
                        params()
                );

                card.addView(
                        text("บาร์โค้ด: " + safe(savedBarcode), 14, false),
                        params()
                );

                card.addView(
                        text(
                                "ราคาเดิม: " + money.format(oldPrice) + " บาท",
                                15,
                                false
                        ),
                        params()
                );

                card.addView(
                        text(
                                "ราคาใหม่: " + money.format(newPrice) + " บาท",
                                16,
                                true
                        ),
                        params()
                );

                card.addView(
                        text(
                                "เวลาแก้ไข: " + safe(changedAt),
                                14,
                                false
                        ),
                        params()
                );

                results.addView(card, cardParams);
            }

        } catch (Exception e) {
            android.util.Log.e(
                    "PriceHistoryScreen",
                    "อ่านประวัติการแก้ไขราคาไม่สำเร็จ",
                    e
            );

            statusText.setText("ค้นหาประวัติไม่สำเร็จ");
            results.removeAllViews();
            results.addView(
                    text(
                            "อ่านประวัติไม่ได้ กรุณาลองใหม่",
                            15,
                            false
                    ),
                    params()
            );
            return;
        }

        if (count == 0) {
            statusText.setText("ไม่พบประวัติของบาร์โค้ดนี้");
            results.addView(
                    text("ไม่พบประวัติการแก้ไขราคา", 16, true),
                    params()
            );
        } else {
            statusText.setText(
                    "พบประวัติ " + count + " รายการ"
                            + "\nบาร์โค้ด: " + exactBarcode
            );
        }
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty()
                ? "-"
                : value;
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

        if (requestCode == CAMERA_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScanner();
        } else if (requestCode == CAMERA_REQUEST) {
            Toast.makeText(
                    this,
                    "ต้องอนุญาตกล้องเพื่อสแกนบาร์โค้ด",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (screenReady && scanning && barcodeView != null) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        if (barcodeView != null) {
            try {
                barcodeView.pause();
            } catch (Exception e) {
                android.util.Log.w(
                        "PriceHistoryScreen",
                        "พักกล้องไม่สำเร็จ",
                        e
                );
            }
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopScanner();
        super.onDestroy();
    }
}
