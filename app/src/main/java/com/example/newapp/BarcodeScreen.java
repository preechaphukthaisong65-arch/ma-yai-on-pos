package com.example.newapp;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Locale;

public class BarcodeScreen extends BaseScreen {

    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int DARK = Color.rgb(31, 41, 55);
    private static final int GRAY = Color.rgb(107, 114, 128);
    private static final int WHITE = Color.WHITE;

    private LocalDatabase localDatabase;
    private SQLiteDatabase database;

    private LinearLayout productList;
    private EditText searchInput;
    private TextView summaryText;

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

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(14), dp(16), dp(24));

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
                "ค้นหาและตรวจสอบบาร์โค้ดของสินค้า",
                14,
                GRAY,
                false
        ));

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
        searchInput.setPadding(dp(12), dp(8), dp(12), dp(8));
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

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {}

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
            public void afterTextChanged(Editable s) {}
        });
    }

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
                products.add(new ProductRow(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getDouble(3)
                ));
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

    private void renderProducts() {
        if (productList == null) {
            return;
        }

        productList.removeAllViews();

        String keyword = searchInput == null
                ? ""
                : searchInput.getText().toString()
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
                    && !name.toLowerCase(Locale.ROOT).contains(keyword)
                    && !barcode.toLowerCase(Locale.ROOT).contains(keyword)) {
                continue;
            }

            addProductCard(product);
            shown++;
        }

        summaryText.setText(
                "พบสินค้า " + shown + " รายการ"
        );

        if (shown == 0) {
            productList.addView(text(
                    "ไม่พบสินค้า",
                    15,
                    GRAY,
                    false
            ));
        }
    }

    private void addProductCard(ProductRow product) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
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

        TextView showButton = text(
                "แสดงบาร์โค้ดขนาดใหญ่  ›",
                14,
                BLUE,
                true
        );
        showButton.setGravity(Gravity.END);
        card.addView(showButton);

        card.setOnClickListener(v ->
                showBarcode(product)
        );
    }

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
                    "สร้างภาพบาร์โค้ดไม่สำเร็จ",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        android.widget.ImageView imageView =
                new android.widget.ImageView(this);

        imageView.setImageBitmap(bitmap);
        imageView.setAdjustViewBounds(true);
        imageView.setBackgroundColor(WHITE);
        imageView.setPadding(dp(8), dp(12), dp(8), dp(12));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(8), dp(16), dp(8));

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

        new android.app.AlertDialog.Builder(this)
                .setTitle(product.name)
                .setView(container)
                .setPositiveButton("ปิด", null)
                .show();
    }

    /*
     * สร้างภาพบาร์โค้ด Code 128 แบบพื้นฐาน
     * รองรับตัวเลขและตัวอักษร ASCII ที่ Code 128 B ใช้ได้
     */
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

        codes.add(104); // Start Code B

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
        codes.add(106); // Stop

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
            throw new IllegalArgumentException("Invalid Code 128 value");
        }

        return patterns[code];
    }

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
        view.setPadding(0, dp(3), 0, dp(3));

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        return view;
    }

    private void addSpace(LinearLayout parent, int height) {
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
        return String.format(Locale.US, "%,.2f", value);
    }

    @Override
    protected void onDestroy() {
        if (localDatabase != null) {
            localDatabase.close();
        }

        super.onDestroy();
    }
                    }
