package com.example.newapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Locale;

public class BarcodeScreen extends BaseScreen {

    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int DARK = Color.rgb(31, 41, 55);
    private static final int GRAY = Color.rgb(107, 114, 128);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int RED = Color.rgb(220, 38, 38);
    private static final int WHITE = Color.WHITE;
    private static final int BG = Color.rgb(245, 247, 251);

    private LocalDatabase localDatabase;
    private SQLiteDatabase database;

    private LinearLayout productList;
    private EditText searchInput;
    private TextView summaryText;
    private TextView printerInfo;

    private WebView activePrintWebView;

    private final ArrayList<ProductRow> products =
            new ArrayList<>();

    private static class ProductRow {
        long id;
        String barcode;
        String name;
        double price;

        ProductRow(
                long id,
                String barcode,
                String name,
                double price
        ) {
            this.id = id;
            this.barcode = barcode;
            this.name = name;
            this.price = price;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createBackground();

        localDatabase = new LocalDatabase(this);
        database = localDatabase.getReadableDatabase();

        buildScreen();
        loadProducts();
    }

    // =========================================================
    // สร้างหน้าจอ
    // =========================================================

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(24)
        );

        scroll.addView(page);

        ViewGroup content = findViewById(android.R.id.content);

        View rootView = content.getChildCount() > 0
                ? content.getChildAt(0)
                : null;

        if (rootView instanceof ViewGroup) {
            ((ViewGroup) rootView).addView(
                    scroll,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        } else {
            content.addView(
                    scroll,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        }

        page.addView(text(
                "บาร์โค้ดสินค้า",
                25,
                DARK,
                true
        ));

        page.addView(text(
                "ค้นหา แสดง และพิมพ์ฉลากบาร์โค้ดสินค้า",
                14,
                GRAY,
                false
        ));

        addSpace(page, 14);

        addPrinterCard(page);

        addSpace(page, 14);

        summaryText = text(
                "กำลังโหลดสินค้า...",
                15,
                BLUE,
                true
        );

        page.addView(summaryText);

        addSpace(page, 10);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(16);
        searchInput.setHint("ค้นหาชื่อสินค้าหรือบาร์โค้ด");
        searchInput.setTextColor(DARK);
        searchInput.setHintTextColor(GRAY);
        searchInput.setPadding(
                dp(12),
                dp(8),
                dp(12),
                dp(8)
        );
        searchInput.setBackgroundColor(WHITE);

        page.addView(
                searchInput,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(50)
                )
        );

        addSpace(page, 12);

        productList = new LinearLayout(this);
        productList.setOrientation(LinearLayout.VERTICAL);

        page.addView(
                productList,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        searchInput.addTextChangedListener(
                new TextWatcher() {
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
                        renderProducts();
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                }
        );
    }

    // =========================================================
    // แผงเครื่องพิมพ์
    // =========================================================

    private void addPrinterCard(LinearLayout page) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(12)
        );
        card.setBackgroundColor(WHITE);

        card.addView(text(
                "🖨 เครื่องพิมพ์ฉลาก",
                17,
                DARK,
                true
        ));

        addSpace(card, 4);

        printerInfo = text(
                "สถานะ: ยังไม่ได้ตรวจสอบเครื่องพิมพ์",
                13,
                GRAY,
                false
        );

        card.addView(printerInfo);

        addSpace(card, 8);

        TextView printerButton = buttonText(
                "ค้นหา / เลือกเครื่องพิมพ์",
                BLUE
        );

        card.addView(
                printerButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(46)
                )
        );

        printerButton.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(
                        BarcodeScreen.this,
                        PrinterScreen.class
                );

                startActivity(intent);

            } catch (Exception e) {
                Toast.makeText(
                        BarcodeScreen.this,
                        "เปิดหน้าจัดการเครื่องพิมพ์ไม่สำเร็จ",
                        Toast.LENGTH_LONG
                ).show();
            }
        });

        addSpace(card, 5);

        card.addView(text(
                "ขนาดฉลากที่ร้องขอ: 40 × 30 มม. " +
                "การรองรับขึ้นอยู่กับเครื่องพิมพ์และบริการพิมพ์",
                12,
                GRAY,
                false
        ));

        page.addView(
                card,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
    }

    // =========================================================
    // โหลดสินค้า
    // =========================================================

    private void loadProducts() {
        products.clear();

        Cursor cursor = null;

        try {
            cursor = database.rawQuery(
                    "SELECT id, barcode, name, price " +
                    "FROM products " +
                    "WHERE is_archived = 0 " +
                    "ORDER BY name COLLATE NOCASE ASC",
                    null
            );

            while (cursor.moveToNext()) {
                products.add(
                        new ProductRow(
                                cursor.getLong(0),
                                cursor.getString(1),
                                cursor.getString(2),
                                cursor.getDouble(3)
                        )
                );
            }

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "โหลดข้อมูลบาร์โค้ดไม่สำเร็จ",
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        renderProducts();
    }

    // =========================================================
    // แสดงรายการสินค้าและค้นหา
    // =========================================================

    private void renderProducts() {
        if (productList == null || summaryText == null) {
            return;
        }

        productList.removeAllViews();

        String keyword = searchInput == null
                ? ""
                : searchInput.getText()
                        .toString()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        int shown = 0;

        for (ProductRow product : products) {
            String name = product.name == null
                    ? ""
                    : product.name;

            String barcode = product.barcode == null
                    ? ""
                    : product.barcode;

            if (!keyword.isEmpty()
                    && !name.toLowerCase(Locale.ROOT)
                            .contains(keyword)
                    && !barcode.toLowerCase(Locale.ROOT)
                            .contains(keyword)) {
                continue;
            }

            addProductCard(product);
            shown++;
        }

        summaryText.setText(
                "พบสินค้า " + shown + " รายการ"
        );

        if (shown == 0) {
            TextView empty = text(
                    "ไม่พบสินค้า",
                    15,
                    GRAY,
                    false
            );

            empty.setGravity(Gravity.CENTER);
            empty.setPadding(
                    dp(10),
                    dp(24),
                    dp(10),
                    dp(24)
            );

            productList.addView(empty);
        }
    }

    private void addProductCard(ProductRow product) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(12)
        );
        card.setBackgroundColor(WHITE);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(10);

        productList.addView(card, params);

        card.addView(text(
                product.name == null ? "-" : product.name,
                17,
                DARK,
                true
        ));

        card.addView(text(
                "รหัสสินค้า: " + product.id,
                13,
                GRAY,
                false
        ));

        card.addView(text(
                "บาร์โค้ด: " + (
                        product.barcode == null
                                ? "-"
                                : product.barcode
                ),
                16,
                BLUE,
                true
        ));

        card.addView(text(
                "ราคา: " + money(product.price) + " บาท",
                14,
                DARK,
                false
        ));

        addSpace(card, 8);

        TextView showButton = buttonText(
                "แสดงบาร์โค้ดและพิมพ์ฉลาก  ›",
                BLUE
        );

        card.addView(
                showButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(44)
                )
        );

        card.setOnClickListener(v -> showBarcode(product));
        showButton.setOnClickListener(v -> showBarcode(product));
    }

    // =========================================================
    // แสดงบาร์โค้ดสินค้า
    // =========================================================

    private void showBarcode(ProductRow product) {
        if (product.barcode == null
                || product.barcode.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "สินค้านี้ไม่มีบาร์โค้ด",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Bitmap bitmap = createBarcodeBitmap(product.barcode);

        if (bitmap == null) {
            Toast.makeText(
                    this,
                    "สร้างภาพบาร์โค้ดไม่สำเร็จ " +
                    "โปรดตรวจสอบว่ารหัสเป็นตัวอักษร ASCII",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        imageView.setAdjustViewBounds(true);
        imageView.setBackgroundColor(WHITE);
        imageView.setPadding(
                dp(8),
                dp(12),
                dp(8),
                dp(12)
        );

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(
                dp(16),
                dp(8),
                dp(16),
                dp(8)
        );
        container.setBackgroundColor(WHITE);

        container.addView(
                imageView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(130)
                )
        );

        TextView barcodeText = text(
                product.barcode,
                18,
                DARK,
                true
        );

        barcodeText.setGravity(Gravity.CENTER);

        container.addView(barcodeText);

        container.addView(text(
                "ราคา " + money(product.price) + " บาท",
                15,
                DARK,
                false
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(
                        product.name == null
                                ? "บาร์โค้ดสินค้า"
                                : product.name
                )
                .setView(container)
                .setNegativeButton("ปิด", null)
                .setPositiveButton(
                        "พิมพ์ฉลาก",
                        (d, which) -> printBarcodeLabel(
                                product,
                                bitmap
                        )
                )
                .create();

        dialog.setOnDismissListener(d -> {
            // เก็บ Bitmap ไว้ เผื่อระบบพิมพ์กำลังใช้งาน
        });

        dialog.show();
    }

    // =========================================================
    // พิมพ์ฉลากบาร์โค้ดผ่านระบบพิมพ์ Android
    // =========================================================

    private void printBarcodeLabel(
            ProductRow product,
            Bitmap barcodeBitmap
    ) {
        if (barcodeBitmap == null || barcodeBitmap.isRecycled()) {
            Toast.makeText(
                    this,
                    "ไม่มีภาพบาร์โค้ดสำหรับพิมพ์",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        boolean compressed = barcodeBitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                output
        );

        if (!compressed) {
            Toast.makeText(
                    this,
                    "เตรียมภาพบาร์โค้ดไม่สำเร็จ",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String imageBase64 = Base64.encodeToString(
                output.toByteArray(),
                Base64.NO_WRAP
        );

        String safeName = escapeHtml(
                product.name == null ? "" : product.name
        );

        String safeBarcode = escapeHtml(product.barcode);

        String safePrice = escapeHtml(
                money(product.price)
        );

        String html =
                "<!DOCTYPE html>" +
                "<html><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width'>" +
                "<style>" +
                "@page { size: 40mm 30mm; margin: 1mm; }" +
                "html,body {" +
                "margin:0;padding:0;width:100%;" +
                "font-family:sans-serif;text-align:center;" +
                "color:#000;background:#fff;" +
                "}" +
                ".name {" +
                "font-size:9pt;font-weight:bold;" +
                "overflow-wrap:anywhere;" +
                "margin-bottom:1mm;" +
                "}" +
                "img {" +
                "width:100%;height:13mm;" +
                "object-fit:fill;" +
                "}" +
                ".code {font-size:8pt;margin-top:1mm;}" +
                ".price {font-size:9pt;font-weight:bold;}" +
                "</style></head><body>" +
                "<div class='name'>" + safeName + "</div>" +
                "<img src='data:image/png;base64," +
                imageBase64 + "'/>" +
                "<div class='code'>" + safeBarcode + "</div>" +
                "<div class='price'>" + safePrice +
                " บาท</div>" +
                "</body></html>";

        PrintManager printManager =
                (PrintManager) getSystemService(PRINT_SERVICE);

        if (printManager == null) {
            Toast.makeText(
                    this,
                    "ระบบพิมพ์ Android ไม่พร้อมใช้งาน",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        WebView printWebView = new WebView(this);
        activePrintWebView = printWebView;

        printWebView.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {
                        view.post(() -> {
                            try {
                                PrintDocumentAdapter adapter =
                                        view.createPrintDocumentAdapter(
                                                "Barcode_" + product.id
                                        );

                                PrintAttributes attributes =
                                        new PrintAttributes.Builder()
                                                .setMediaSize(
                                                        new PrintAttributes.MediaSize(
                                                                "LABEL_40X30",
                                                                "Label 40 x 30 mm",
                                                                1575,
                                                                1181
                                                        )
                                                )
                                                .setMinMargins(
                                                        PrintAttributes.Margins.NO_MARGINS
                                                )
                                                .build();

                                printManager.print(
                                        "ฉลาก_" + product.id,
                                        adapter,
                                        attributes
                                );

                                if (printerInfo != null) {
                                    printerInfo.setText(
                                            "สถานะ: ส่งคำขอไปยังระบบพิมพ์แล้ว " +
                                            "(ยังไม่ยืนยันว่าพิมพ์สำเร็จ)"
                                    );
                                }

                            } catch (Exception e) {
                                Toast.makeText(
                                        BarcodeScreen.this,
                                        "เปิดหน้าพิมพ์ไม่สำเร็จ",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        });
                    }
                }
        );

        printWebView.loadDataWithBaseURL(
                null,
                html,
                "text/html",
                "UTF-8",
                null
        );
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

    // =========================================================
    // สร้างภาพบาร์โค้ด Code 128 B
    // =========================================================

    private Bitmap createBarcodeBitmap(String value) {
        try {
            int[] codes = encodeCode128B(value);

            if (codes == null) {
                return null;
            }

            int moduleWidth = 3;
            int quietZone = 20;
            int height = 180;

            int modules = quietZone * 2;

            for (int code : codes) {
                modules += code128Pattern(code).length();
            }

            int width = modules * moduleWidth;

            Bitmap bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
            );

            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.FILL);

            int x = quietZone * moduleWidth;

            for (int code : codes) {
                String pattern = code128Pattern(code);
                boolean black = true;

                for (int i = 0; i < pattern.length(); i++) {
                    int barWidth =
                            (pattern.charAt(i) - '0') * moduleWidth;

                    if (black) {
                        canvas.drawRect(
                                x,
                                8,
                                x + barWidth,
                                height - 8,
                                paint
                        );
                    }

                    x += barWidth;
                    black = !black;
                }
            }

            return bitmap;

        } catch (Exception e) {
            return null;
        }
    }

    private int[] encodeCode128B(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        ArrayList<Integer> codes = new ArrayList<>();

        codes.add(104);

        int checksum = 104;

        for (int i = 0; i < value.length(); i++) {
            int ascii = value.charAt(i);

            if (ascii < 32 || ascii > 126) {
                return null;
            }

            int code = ascii - 32;

            codes.add(code);
            checksum += code * (i + 1);
        }

        codes.add(checksum % 103);
        codes.add(106);

        int[] result = new int[codes.size()];

        for (int i = 0; i < codes.size(); i++) {
            result[i] = codes.get(i);
        }

        return result;
    }

    private String code128Pattern(int code) {
        String[] patterns = {
                "212222","222122","222221","121223","121322",
                "131222","122213","122312","132212","221213",
                "221312","231212","112232","122132","122231",
                "113222","123122","123221","223211","221132",
                "221231","213212","223112","312131","311222",
                "321122","321221","312212","322112","322211",
                "212123","212321","232121","111323","131123",
                "131321","112313","132113","132311","211313",
                "231113","231311","112133","112331","132131",
                "113123","113321","133121","313121","211331",
                "231131","213113","213311","213131","311123",
                "311321","331121","312113","312311","332111",
                "314111","221411","431111","111224","111422",
                "121124","121421","141122","141221","112214",
                "112412","122114","122411","142112","142211",
                "241211","221114","413111","241112","134111",
                "111242","121142","121241","114212","124112",
                "124211","411212","421112","421211","212141",
                "214121","412121","111143","111341","131141",
                "114113","114311","411113","411311","113141",
                "114131","311141","411131","211412","211214",
                "211232","2331112"
        };

        if (code < 0 || code >= patterns.length) {
            throw new IllegalArgumentException(
                    "Invalid Code 128 value"
            );
        }

        return patterns[code];
    }

    // =========================================================
    // เครื่องมือสร้าง UI
    // =========================================================

    private TextView text(
            String value,
            int size,
            int color,
            boolean bold
    ) {
        TextView view = new TextView(this);

        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(
                0,
                dp(3),
                0,
                dp(3)
        );

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        return view;
    }

    private TextView buttonText(
            String value,
            int backgroundColor
    ) {
        TextView view = new TextView(this);

        view.setText(value);
        view.setTextSize(14);
        view.setTextColor(backgroundColor);
        view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(8)
        );

        view.setBackgroundColor(WHITE);
        view.setClickable(true);
        view.setFocusable(true);

        return view;
    }

    private void addSpace(
            LinearLayout parent,
            int height
    ) {
        View space = new View(this);

        parent.addView(
                space,
                new LinearLayout.LayoutParams(
                        1,
                        dp(height)
                )
        );
    }

    private String money(double value) {
        return String.format(
                Locale.US,
                "%,.2f",
                value
        );
    }

    // =========================================================
    // ปิดฐานข้อมูล
    // =========================================================

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

        if (localDatabase != null) {
            localDatabase.close();
        }

        super.onDestroy();
    }
            }
