package com.example.newapp;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintJob;
import android.print.PrintManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BarcodeScreen extends BaseScreen {

    private SQLiteDatabase database;

    private LinearLayout mainLayout;
    private LinearLayout productContainer;
    private TextView printerStatusText;
    private EditText searchInput;

    private WebView activePrintWebView;

    private final List<ProductItem> products = new ArrayList<>();

    private static final int BLUE = Color.rgb(53, 99, 233);
    private static final int DARK = Color.rgb(24, 34, 48);
    private static final int GRAY = Color.rgb(100, 116, 139);
    private static final int BACKGROUND = Color.rgb(245, 247, 251);
    private static final int GREEN = Color.rgb(21, 155, 114);
    private static final int RED = Color.rgb(224, 82, 96);

    private static final int LABEL_WIDTH_MILS = 1575;
    private static final int LABEL_HEIGHT_MILS = 1181;

    private static class ProductItem {
        long id;
        String name;
        String barcode;
        double price;
        int stock;

        ProductItem(
                long id,
                String name,
                String barcode,
                double price,
                int stock
        ) {
            this.id = id;
            this.name = name;
            this.barcode = barcode;
            this.price = price;
            this.stock = stock;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            LocalDatabase helper = new LocalDatabase(this);
            database = helper.getReadableDatabase();

            buildScreen();
            loadProducts("");

        } catch (Exception e) {
            e.printStackTrace();

            new AlertDialog.Builder(this)
                    .setTitle("เปิดหน้าบาร์โค้ดไม่ได้")
                    .setMessage(
                            "ไม่สามารถเปิดฐานข้อมูลสินค้าได้\n\n"
                                    + e.getMessage()
                    )
                    .setPositiveButton("ตกลง", (dialog, which) -> finish())
                    .show();
        }
    }

    private void buildScreen() {
        createBackground();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.TRANSPARENT);

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(dp(16), dp(12), dp(16), dp(24));

        scrollView.addView(mainLayout);

        setContentView(scrollView);

        TextView title = new TextView(this);
        title.setText("จัดการบาร์โค้ด");
        title.setTextColor(DARK);
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);

        mainLayout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("ค้นหาสินค้า ดูบาร์โค้ด และพิมพ์ฉลาก");
        subtitle.setTextColor(GRAY);
        subtitle.setTextSize(14);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.topMargin = dp(4);
        subtitleParams.bottomMargin = dp(16);

        mainLayout.addView(subtitle, subtitleParams);

        buildPrinterCard();

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(16);
        searchInput.setHint("ค้นหาชื่อสินค้า หรือบาร์โค้ด");
        searchInput.setPadding(dp(14), dp(8), dp(14), dp(8));
        searchInput.setBackground(makeRoundedBackground(
                Color.WHITE,
                12,
                Color.rgb(220, 226, 235),
                1
        ));

        LinearLayout.LayoutParams searchParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(52)
                );

        searchParams.topMargin = dp(16);

        mainLayout.addView(searchInput, searchParams);

        TextView listTitle = new TextView(this);
        listTitle.setText("รายการสินค้า");
        listTitle.setTextSize(18);
        listTitle.setTextColor(DARK);
        listTitle.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams listTitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        listTitleParams.topMargin = dp(18);
        listTitleParams.bottomMargin = dp(8);

        mainLayout.addView(listTitle, listTitleParams);

        productContainer = new LinearLayout(this);
        productContainer.setOrientation(LinearLayout.VERTICAL);

        mainLayout.addView(
                productContainer,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                loadProducts(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void buildPrinterCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(makeRoundedBackground(
                Color.WHITE,
                14,
                Color.rgb(225, 231, 240),
                1
        ));

        TextView heading = new TextView(this);
        heading.setText("ระบบพิมพ์ฉลาก");
        heading.setTextColor(DARK);
        heading.setTextSize(17);
        heading.setTypeface(null, Typeface.BOLD);

        card.addView(heading);

        printerStatusText = new TextView(this);
        printerStatusText.setTextColor(GRAY);
        printerStatusText.setTextSize(13);
        printerStatusText.setPadding(0, dp(8), 0, dp(10));

        card.addView(printerStatusText);

        Button searchPrinterButton = new Button(this);
        searchPrinterButton.setText("ค้นหาเครื่องพิมพ์ / เปิดหน้าพิมพ์");
        searchPrinterButton.setTextColor(Color.WHITE);
        searchPrinterButton.setAllCaps(false);
        searchPrinterButton.setBackground(makeRoundedBackground(
                BLUE,
                10,
                BLUE,
                0
        ));

        searchPrinterButton.setOnClickListener(v -> openPrinterSearch());

        card.addView(
                searchPrinterButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(48)
                )
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        mainLayout.addView(card, cardParams);

        refreshPrinterStatus();
    }

    private void refreshPrinterStatus() {
        if (printerStatusText == null) {
            return;
        }

        try {
            boolean available = PrinterManager.isAvailable(this);

            if (!available) {
                printerStatusText.setText(
                        "ไม่พบระบบพิมพ์ของ Android ในขณะนี้"
                );
                printerStatusText.setTextColor(RED);
                return;
            }

            List<PrintJob> jobs = PrinterManager.getPrintJobs(this);

            int activeJobs = 0;
            int failedJobs = 0;

            if (jobs != null) {
                for (PrintJob job : jobs) {
                    if (job == null) {
                        continue;
                    }

                    if (job.isQueued() || job.isStarted() || job.isBlocked()) {
                        activeJobs++;
                    }

                    if (job.isFailed()) {
                        failedJobs++;
                    }
                }
            }

            printerStatusText.setText(
                    "ระบบพิมพ์ Android: พร้อมใช้งาน\n"
                            + "งานพิมพ์ที่กำลังดำเนินการ: "
                            + activeJobs
                            + "\nงานพิมพ์ที่ล้มเหลว: "
                            + failedJobs
                            + "\nหมายเหตุ: สถานะนี้ยังยืนยันไม่ได้ว่า "
                            + "เครื่องพิมพ์จริงเชื่อมต่ออยู่"
            );

            printerStatusText.setTextColor(
                    failedJobs > 0 ? RED : GREEN
            );

        } catch (Exception e) {
            printerStatusText.setText(
                    "ไม่สามารถตรวจสอบสถานะระบบพิมพ์ได้"
            );
            printerStatusText.setTextColor(RED);
        }
    }

    private void openPrinterSearch() {
        try {
            String html =
                    "<!DOCTYPE html><html><head>"
                            + "<meta charset='UTF-8'>"
                            + "<style>"
                            + "@page { size: A4; margin: 15mm; }"
                            + "body { font-family: sans-serif; "
                            + "text-align: center; padding-top: 40px; }"
                            + "</style></head><body>"
                            + "<h2>ทดสอบระบบพิมพ์</h2>"
                            + "<p>ร้านแม่ใหญ่อ่อน POS</p>"
                            + "<p>เลือกเครื่องพิมพ์จากหน้าต่างพิมพ์ของ Android</p>"
                            + "</body></html>";

            printHtml(
                    "ค้นหาเครื่องพิมพ์",
                    html,
                    false
            );

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "เปิดหน้าค้นหาเครื่องพิมพ์ไม่สำเร็จ",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void loadProducts(String keyword) {
        if (database == null || productContainer == null) {
            return;
        }

        products.clear();
        productContainer.removeAllViews();

        Cursor cursor = null;

        try {
            String query =
                    "SELECT id, name, barcode, price, stock "
                            + "FROM products "
                            + "WHERE is_archived = 0 "
                            + "AND (name LIKE ? OR barcode LIKE ?) "
                            + "ORDER BY name COLLATE NOCASE ASC";

            String search = "%" + keyword.trim() + "%";

            cursor = database.rawQuery(
                    query,
                    new String[]{search, search}
            );

            while (cursor.moveToNext()) {
                ProductItem item = new ProductItem(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("barcode")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("price")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("stock"))
                );

                products.add(item);
            }

        } catch (Exception e) {
            e.printStackTrace();

            showEmptyMessage(
                    "อ่านข้อมูลสินค้าไม่สำเร็จ\n"
                            + e.getMessage()
            );

            return;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (products.isEmpty()) {
            showEmptyMessage("ไม่พบสินค้า");
            return;
        }

        for (ProductItem item : products) {
            addProductCard(item);
        }
    }

    private void addProductCard(ProductItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        card.setBackground(makeRoundedBackground(
                Color.WHITE,
                12,
                Color.rgb(225, 231, 240),
                1
        ));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin = dp(10);

        productContainer.addView(card, cardParams);

        TextView name = new TextView(this);
        name.setText(item.name == null ? "ไม่มีชื่อสินค้า" : item.name);
        name.setTextColor(DARK);
        name.setTextSize(16);
        name.setTypeface(null, Typeface.BOLD);

        card.addView(name);

        TextView barcode = new TextView(this);
        barcode.setText("บาร์โค้ด: " + safeText(item.barcode));
        barcode.setTextColor(GRAY);
        barcode.setTextSize(14);

        LinearLayout.LayoutParams barcodeParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        barcodeParams.topMargin = dp(5);

        card.addView(barcode, barcodeParams);

        TextView price = new TextView(this);
        price.setText(
                "ราคา: "
                        + String.format(
                                Locale.US,
                                "%,.2f บาท",
                                item.price
                        )
                        + "    คงเหลือ: "
                        + item.stock
        );

        price.setTextColor(BLUE);
        price.setTextSize(14);

        LinearLayout.LayoutParams priceParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        priceParams.topMargin = dp(5);

        card.addView(price, priceParams);

        Button viewButton = new Button(this);
        viewButton.setText("ดูบาร์โค้ด / พิมพ์ฉลาก");
        viewButton.setAllCaps(false);
        viewButton.setTextColor(Color.WHITE);
        viewButton.setBackground(makeRoundedBackground(
                BLUE,
                9,
                BLUE,
                0
        ));

        viewButton.setOnClickListener(v -> showBarcode(item));

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(46)
                );

        buttonParams.topMargin = dp(10);

        card.addView(viewButton, buttonParams);
    }

    private void showEmptyMessage(String message) {
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(GRAY);
        empty.setTextSize(15);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(12), dp(24), dp(12), dp(24));

        productContainer.addView(
                empty,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
    }

    private void showBarcode(ProductItem item) {
        if (item.barcode == null || item.barcode.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "สินค้านี้ยังไม่มีบาร์โค้ด",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        Bitmap barcodeBitmap = createBarcodeBitmap(item.barcode);

        if (barcodeBitmap == null) {
            Toast.makeText(
                    this,
                    "สร้างบาร์โค้ดไม่สำเร็จ กรุณาตรวจสอบรหัสสินค้า",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView productName = new TextView(this);
        productName.setText(safeText(item.name));
        productName.setTextColor(DARK);
        productName.setTextSize(17);
        productName.setTypeface(null, Typeface.BOLD);
        productName.setGravity(Gravity.CENTER);

        content.addView(productName);

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(barcodeBitmap);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(120)
                );

        imageParams.topMargin = dp(10);

        content.addView(imageView, imageParams);

        TextView codeText = new TextView(this);
        codeText.setText(item.barcode);
        codeText.setTextColor(DARK);
        codeText.setTextSize(15);
        codeText.setGravity(Gravity.CENTER);

        content.addView(codeText);

        TextView priceText = new TextView(this);
        priceText.setText(
                String.format(
                        Locale.US,
                        "%.2f บาท",
                        item.price
                )
        );
        priceText.setTextColor(BLUE);
        priceText.setTextSize(18);
        priceText.setTypeface(null, Typeface.BOLD);
        priceText.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams priceParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        priceParams.topMargin = dp(8);

        content.addView(priceText, priceParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("บาร์โค้ดสินค้า")
                .setView(content)
                .setNegativeButton("ปิด", null)
                .setPositiveButton("พิมพ์ฉลาก", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button printButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );

            printButton.setOnClickListener(v -> {
                dialog.dismiss();
                printBarcodeLabel(item);
            });
        });

        dialog.show();
    }

    /**
     * สร้างบาร์โค้ด Code 128 ด้วย ZXing
     * คำนวณความกว้างจาก BitMatrix โดยตรง
     * จึงไม่เกิดปัญหาความกว้างไม่พอจากการนับตัวเลขใน pattern
     */
    private Bitmap createBarcodeBitmap(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 0);

            BitMatrix matrix = new Code128Writer().encode(
                    value,
                    BarcodeFormat.CODE_128,
                    1,
                    120,
                    hints
            );

            int barcodeWidth = matrix.getWidth();
            int barcodeHeight = matrix.getHeight();

            int quietZone = 20;
            int moduleWidth = 3;

            int width =
                    (barcodeWidth + quietZone * 2) * moduleWidth;

            int height = barcodeHeight + 16;

            Bitmap bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
            );

            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.FILL);
            paint.setAntiAlias(false);

            for (int x = 0; x < barcodeWidth; x++) {
                for (int y = 0; y < barcodeHeight; y++) {
                    if (matrix.get(x, y)) {
                        int left =
                                quietZone * moduleWidth
                                        + x * moduleWidth;

                        canvas.drawRect(
                                left,
                                8 + y,
                                left + moduleWidth,
                                8 + y + 1,
                                paint
                        );
                    }
                }
            }

            return bitmap;

        } catch (WriterException | IllegalArgumentException e) {
            e.printStackTrace();
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void printBarcodeLabel(ProductItem item) {
        if (item == null || item.barcode == null) {
            Toast.makeText(
                    this,
                    "ข้อมูลบาร์โค้ดไม่ถูกต้อง",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String html = createLabelHtml(item);

        printHtml(
                "ฉลาก " + safeText(item.name),
                html,
                true
        );
    }

    private String createLabelHtml(ProductItem item) {
        String name = escapeHtml(safeText(item.name));
        String barcode = escapeHtml(safeText(item.barcode));

        String price = String.format(
                Locale.US,
                "%.2f",
                item.price
        );

        return "<!DOCTYPE html>"
                + "<html><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "@page { size: 40mm 30mm; margin: 1mm; }"
                + "* { box-sizing: border-box; }"
                + "html, body { margin: 0; padding: 0; width: 38mm; "
                + "height: 28mm; background: #fff; color: #000; }"
                + "body { font-family: sans-serif; text-align: center; "
                + "overflow: hidden; }"
                + ".name { font-size: 9pt; font-weight: bold; "
                + "height: 4mm; overflow: hidden; white-space: nowrap; }"
                + ".barcode { width: 100%; height: 14mm; "
                + "object-fit: fill; display: block; }"
                + ".code { font-size: 8pt; margin-top: 0.5mm; }"
                + ".price { font-size: 10pt; font-weight: bold; "
                + "margin-top: 0.5mm; }"
                + "</style></head><body>"
                + "<div class='name'>" + name + "</div>"
                + "<img class='barcode' src='data:image/png;base64,"
                + bitmapToBase64(item.barcode)
                + "'>"
                + "<div class='code'>" + barcode + "</div>"
                + "<div class='price'>" + price + " บาท</div>"
                + "</body></html>";
    }

    private String bitmapToBase64(String value) {
        Bitmap bitmap = createBarcodeBitmap(value);

        if (bitmap == null) {
            return "";
        }

        java.io.ByteArrayOutputStream output =
                new java.io.ByteArrayOutputStream();

        bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                output
        );

        bitmap.recycle();

        return android.util.Base64.encodeToString(
                output.toByteArray(),
                android.util.Base64.NO_WRAP
        );
    }

    private void printHtml(
            String jobName,
            String html,
            boolean labelSize
    ) {
        try {
            PrintManager printManager =
                    (PrintManager) getSystemService(
                            Context.PRINT_SERVICE
                    );

            if (printManager == null) {
                Toast.makeText(
                        this,
                        "ระบบพิมพ์ Android ไม่พร้อมใช้งาน",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            if (activePrintWebView != null) {
                try {
                    activePrintWebView.stopLoading();
                    activePrintWebView.destroy();
                } catch (Exception ignored) {
                }

                activePrintWebView = null;
            }

            WebView webView = new WebView(this);
            activePrintWebView = webView;

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(
                        WebView view,
                        String url
                ) {
                    if (view != activePrintWebView) {
                        return;
                    }

                    try {
                        PrintAttributes.MediaSize mediaSize;

                        if (labelSize) {
                            mediaSize = new PrintAttributes.MediaSize(
                                    "LABEL_40X30",
                                    "Label 40 x 30 mm",
                                    LABEL_WIDTH_MILS,
                                    LABEL_HEIGHT_MILS
                            );
                        } else {
                            mediaSize =
                                    PrintAttributes.MediaSize.ISO_A4;
                        }

                        PrintAttributes attributes =
                                new PrintAttributes.Builder()
                                        .setMediaSize(mediaSize)
                                        .setColorMode(
                                                PrintAttributes.COLOR_MODE_MONOCHROME
                                        )
                                        .setResolution(
                                                new PrintAttributes.Resolution(
                                                        "default",
                                                        "Default",
                                                        300,
                                                        300
                                                )
                                        )
                                        .setMinMargins(
                                                PrintAttributes.Margins.NO_MARGINS
                                        )
                                        .build();

                        android.print.PrintDocumentAdapter adapter =
                                view.createPrintDocumentAdapter(jobName);

                        PrintJob job = PrinterManager.print(
                                BarcodeScreen.this,
                                jobName,
                                adapter,
                                attributes
                        );

                        if (job == null) {
                            Toast.makeText(
                                    BarcodeScreen.this,
                                    "ส่งงานพิมพ์ไม่ได้ "
                                            + "กรุณาตรวจสอบระบบพิมพ์ Android",
                                    Toast.LENGTH_LONG
                            ).show();
                        } else {
                            Toast.makeText(
                                    BarcodeScreen.this,
                                    "ส่งงานพิมพ์เข้าสู่ระบบแล้ว "
                                            + "โปรดตรวจสอบสถานะในหน้าพิมพ์",
                                    Toast.LENGTH_LONG
                            ).show();
                        }

                        refreshPrinterStatus();

                    } catch (Exception e) {
                        e.printStackTrace();

                        Toast.makeText(
                                BarcodeScreen.this,
                                "สร้างงานพิมพ์ไม่สำเร็จ",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
            });

            webView.loadDataWithBaseURL(
                    null,
                    html,
                    "text/html",
                    "UTF-8",
                    null
            );

        } catch (Exception e) {
            e.printStackTrace();

            Toast.makeText(
                    this,
                    "เปิดระบบพิมพ์ไม่สำเร็จ",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private GradientDrawable makeRoundedBackground(
            int color,
            int radiusDp,
            int strokeColor,
            int strokeWidth
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));

        if (strokeWidth > 0) {
            drawable.setStroke(dp(strokeWidth), strokeColor);
        }

        return drawable;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPrinterStatus();
    }

    @Override
    protected void onDestroy() {
        if (activePrintWebView != null) {
            try {
                activePrintWebView.stopLoading();
                activePrintWebView.destroy();
            } catch (Exception ignored) {
            }

            activePrintWebView = null;
        }

        if (database != null && database.isOpen()) {
            database.close();
            database = null;
        }

        super.onDestroy();
    }
    }
