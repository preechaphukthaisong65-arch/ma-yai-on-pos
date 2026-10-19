package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Locale;

public class StockProductListScreen extends Activity {

    private LocalDatabase database;
    private ProductRepository productRepository;
    private InventoryRepository inventoryRepository;

    private EditText searchInput;
    private ListView productList;
    private TextView emptyMessage;
    private TextView resultCount;

    private ProductCursorAdapter adapter;
    private Cursor productCursor;

    private TextToSpeech textToSpeech;
    private boolean voiceReady = false;

    private int dp(float value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        database = LocalDatabase.getInstance(this);
        productRepository = new ProductRepository(this);
        inventoryRepository = new InventoryRepository(database);

        initializeVoice();
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(8));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("สินค้าทั้งหมดในคลัง");
        title.setTextSize(23);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(45, 90, 55));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, dp(8), 0, dp(12));
        root.addView(title);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(16);
        searchInput.setHint("ค้นหาชื่อสินค้า หรือบาร์โค้ด");
        searchInput.setPadding(
                dp(12), dp(8), dp(12), dp(8)
        );
        root.addView(searchInput, fieldParams());

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        Button searchButton = createButton("ค้นหา");
        searchButton.setOnClickListener(v -> {
            hideKeyboard();
            reloadProducts();
        });

        actionRow.addView(
                searchButton,
                new LinearLayout.LayoutParams(0, dp(50), 1)
        );

        Button scanButton = createButton("สแกนบาร์โค้ด");
        scanButton.setOnClickListener(v -> startScanner());

        LinearLayout.LayoutParams scanParams =
                new LinearLayout.LayoutParams(0, dp(50), 1);
        scanParams.leftMargin = dp(8);
        actionRow.addView(scanButton, scanParams);

        root.addView(actionRow, fieldParams());

        resultCount = new TextView(this);
        resultCount.setTextSize(14);
        resultCount.setTextColor(Color.DKGRAY);
        resultCount.setPadding(dp(2), dp(4), dp(2), dp(8));
        root.addView(resultCount);

        emptyMessage = new TextView(this);
        emptyMessage.setText("ยังไม่มีสินค้าในคลัง");
        emptyMessage.setTextSize(16);
        emptyMessage.setTextColor(Color.DKGRAY);
        emptyMessage.setGravity(Gravity.CENTER);
        emptyMessage.setVisibility(View.GONE);

        root.addView(
                emptyMessage,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(100)
                )
        );

        productList = new ListView(this);
        productList.setDividerHeight(dp(1));
        productList.setBackgroundColor(Color.WHITE);

        adapter = new ProductCursorAdapter();
        productList.setAdapter(adapter);

        productList.setOnItemClickListener(
                (parent, view, position, id) -> {
                    Cursor cursor = productCursor;

                    if (cursor == null || cursor.isClosed()
                            || !cursor.moveToPosition(position)) {
                        showMessage("ไม่สามารถอ่านข้อมูลสินค้าได้");
                        return;
                    }

                    String barcode = cursor.getString(
                            cursor.getColumnIndexOrThrow("barcode")
                    );

                    showProductDetails(barcode);
                }
        );

        root.addView(
                productList,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        Button backButton = createButton("กลับ");
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton, fieldParams());

        setContentView(root);
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(7);
        return params;
    }

    private Button createButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        return button;
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadProducts();
    }

    private void reloadProducts() {
        Cursor newCursor = null;

        try {
            String keyword = searchInput == null
                    ? ""
                    : searchInput.getText().toString().trim();

            SQLiteDatabase db = database.getReadableDatabase();

            String selection = null;
            String[] selectionArgs = null;

            if (!keyword.isEmpty()) {
                selection = "name LIKE ? OR barcode LIKE ?";
                String pattern = "%" + keyword + "%";
                selectionArgs = new String[]{pattern, pattern};
            }

            newCursor = db.query(
                    "products",
                    new String[]{
                            "id",
                            "barcode",
                            "name",
                            "stock_qty",
                            "min_stock",
                            "unit"
                    },
                    selection,
                    selectionArgs,
                    null,
                    null,
                    "stock_qty ASC, name COLLATE NOCASE ASC"
            );

            Cursor oldCursor = productCursor;
            productCursor = newCursor;
            newCursor = null;

            adapter.notifyDataSetChanged();

            if (oldCursor != null && !oldCursor.isClosed()) {
                oldCursor.close();
            }

            int count = productCursor == null
                    ? 0 : productCursor.getCount();

            resultCount.setText(
                    keyword.isEmpty()
                            ? "จำนวนสินค้า: " + count + " รายการ"
                            : "ผลการค้นหา: " + count + " รายการ"
            );

            boolean hasProducts = count > 0;

            emptyMessage.setText(
                    keyword.isEmpty()
                            ? "ยังไม่มีสินค้าในคลัง"
                            : "ไม่พบสินค้าที่ตรงกับคำค้นหา"
            );

            emptyMessage.setVisibility(
                    hasProducts ? View.GONE : View.VISIBLE
            );

            productList.setVisibility(
                    hasProducts ? View.VISIBLE : View.GONE
            );

        } catch (Exception e) {
            if (newCursor != null && !newCursor.isClosed()) {
                newCursor.close();
            }

            emptyMessage.setText("โหลดรายการสินค้าไม่สำเร็จ");
            emptyMessage.setVisibility(View.VISIBLE);
            productList.setVisibility(View.GONE);
            resultCount.setText("");

            showMessage("อ่านรายการสินค้าไม่สำเร็จ");
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

        searchInput.setText(barcode);
        hideKeyboard();

        ProductRepository.Product product =
                productRepository.findByBarcode(barcode);

        if (product == null) {
            speakThai("ไม่พบสินค้าในคลังค่ะ");
            reloadProducts();
            showMessage("ไม่พบสินค้าในคลัง: " + barcode);
            return;
        }

        speakThai(safe(product.name) + "ค่ะ");
        reloadProducts();
        showProductDetails(barcode);
    }

    private void showProductDetails(String barcode) {
        ProductRepository.Product product =
                productRepository.findByBarcode(barcode);

        if (product == null) {
            showMessage("ไม่พบข้อมูลสินค้า");
            return;
        }

        String details =
                "ชื่อสินค้า: " + safe(product.name)
                        + "\nบาร์โค้ด: " + safe(product.barcode)
                        + "\nราคาขาย: " + money(product.price) + " บาท"
                        + "\nต้นทุน: " + money(product.cost) + " บาท"
                        + "\nจำนวนคงเหลือ: " + product.stockQty
                        + " " + safe(product.unit)
                        + "\nลิมิตสต๊อก: " + product.minStock
                        + " " + safe(product.unit)
                        + "\nสถานะ: "
                        + (product.stockQty < product.minStock
                        ? "ต่ำกว่าลิมิตสต๊อก"
                        : "ถึงหรือสูงกว่าลิมิตสต๊อก")
                        + "\nอัปเดตล่าสุด: " + safe(product.updatedAt);

        new AlertDialog.Builder(this)
                .setTitle("รายละเอียดสินค้า")
                .setMessage(details)
                .setNegativeButton("ปิด", null)
                .setPositiveButton(
                        "ประวัติสต๊อก",
                        (dialog, which) -> showStockHistory(barcode)
                )
                .show();
    }

    private void showStockHistory(String barcode) {
        Cursor cursor = null;

        try {
            cursor = inventoryRepository.getStockHistory(barcode);

            StringBuilder text = new StringBuilder();

            while (cursor.moveToNext()) {
                String operation = cursor.getString(
                        cursor.getColumnIndexOrThrow("operation")
                );
                int before = cursor.getInt(
                        cursor.getColumnIndexOrThrow("qty_before")
                );
                int after = cursor.getInt(
                        cursor.getColumnIndexOrThrow("qty_after")
                );
                int difference = cursor.getInt(
                        cursor.getColumnIndexOrThrow("qty_diff")
                );
                String note = cursor.getString(
                        cursor.getColumnIndexOrThrow("note")
                );
                String createdAt = cursor.getString(
                        cursor.getColumnIndexOrThrow("created_at")
                );

                if (text.length() > 0) {
                    text.append("\n\n--------------------\n\n");
                }

                text.append("รายการ: ")
                        .append(operationName(operation))
                        .append("\nก่อนทำรายการ: ")
                        .append(before)
                        .append("\nหลังทำรายการ: ")
                        .append(after)
                        .append("\nผลต่าง: ")
                        .append(difference >= 0 ? "+" : "")
                        .append(difference)
                        .append("\nวันที่: ")
                        .append(safe(createdAt))
                        .append("\nหมายเหตุ: ")
                        .append(safe(note));
            }

            if (text.length() == 0) {
                text.append("ยังไม่มีประวัติสต๊อกของสินค้านี้");
            }

            TextView content = new TextView(this);
            content.setText(text.toString());
            content.setTextSize(15);
            content.setTextColor(Color.DKGRAY);
            content.setPadding(dp(16), dp(12), dp(16), dp(12));

            ScrollView scroll = new ScrollView(this);
            scroll.addView(content);

            new AlertDialog.Builder(this)
                    .setTitle("ประวัติสต๊อกสินค้า")
                    .setView(scroll)
                    .setPositiveButton("ปิด", null)
                    .show();

        } catch (Exception e) {
            showMessage("อ่านประวัติสต๊อกไม่สำเร็จ");

        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    private String operationName(String operation) {
        if ("RECEIVE".equals(operation)) {
            return "รับสินค้าเข้า";
        }

        if ("INITIAL_RECEIPT".equals(operation)) {
            return "รับสินค้าเริ่มต้น";
        }

        if ("STOCKTAKE".equals(operation)) {
            return "ตรวจนับสต๊อก";
        }

        return safe(operation);
    }

    private String money(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty()
                ? "-" : value;
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE
                    );

            if (manager != null && searchInput != null) {
                manager.hideSoftInputFromWindow(
                        searchInput.getWindowToken(),
                        0
                );
            }
        } catch (Exception ignored) {
            // การซ่อนแป้นพิมพ์ไม่ควรขัดขวางการใช้งาน
        }
    }

    private void showMessage(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private void initializeVoice() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS
                    || textToSpeech == null) {
                voiceReady = false;
                return;
            }

            int languageResult = textToSpeech.setLanguage(
                    new Locale("th", "TH")
            );

            voiceReady =
                    languageResult != TextToSpeech.LANG_MISSING_DATA
                    && languageResult != TextToSpeech.LANG_NOT_SUPPORTED;

            if (voiceReady && android.os.Build.VERSION.SDK_INT >= 21) {
                try {
                    android.speech.tts.Voice femaleThaiVoice = null;

                    for (android.speech.tts.Voice voice
                            : textToSpeech.getVoices()) {
                        if (voice == null || voice.getLocale() == null) {
                            continue;
                        }

                        String voiceName = voice.getName() == null
                                ? ""
                                : voice.getName().toLowerCase(Locale.ROOT);

                        if ("th".equals(voice.getLocale().getLanguage())
                                && voiceName.contains("female")) {
                            femaleThaiVoice = voice;
                            break;
                        }
                    }

                    if (femaleThaiVoice != null) {
                        textToSpeech.setVoice(femaleThaiVoice);
                    }

                } catch (Exception ignored) {
                    // ใช้เสียงภาษาไทยเริ่มต้น หากเลือกเสียงผู้หญิงไม่ได้
                }
            }
        });
    }

    private void speakThai(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        if (textToSpeech != null && voiceReady) {
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                textToSpeech.speak(
                        message,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "stock_scan_announcement"
                );
            } else {
                textToSpeech.speak(
                        message,
                        TextToSpeech.QUEUE_FLUSH,
                        null
                );
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }

        if (productCursor != null && !productCursor.isClosed()) {
            productCursor.close();
        }

        productCursor = null;
        super.onDestroy();
    }

    private class ProductCursorAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return productCursor == null || productCursor.isClosed()
                    ? 0 : productCursor.getCount();
        }

        @Override
        public Object getItem(int position) {
            if (productCursor == null
                    || productCursor.isClosed()
                    || !productCursor.moveToPosition(position)) {
                return null;
            }

            return productCursor;
        }

        @Override
        public long getItemId(int position) {
            if (productCursor == null
                    || productCursor.isClosed()
                    || !productCursor.moveToPosition(position)) {
                return 0;
            }

            return productCursor.getLong(
                    productCursor.getColumnIndexOrThrow("id")
            );
        }

        @Override
        public View getView(
                int position,
                View convertView,
                ViewGroup parent
        ) {
            RowHolder holder;

            if (convertView == null) {
                LinearLayout row = new LinearLayout(
                        StockProductListScreen.this
                );
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(
                        dp(8), dp(10), dp(8), dp(10)
                );

                TextView nameView = new TextView(
                        StockProductListScreen.this
                );
                nameView.setTextSize(17);
                nameView.setTypeface(null, Typeface.BOLD);
                nameView.setTextColor(Color.rgb(35, 35, 35));

                TextView quantityView = new TextView(
                        StockProductListScreen.this
                );
                quantityView.setTextSize(15);
                quantityView.setTextColor(Color.rgb(45, 90, 55));

                row.addView(nameView);
                row.addView(quantityView);

                holder = new RowHolder();
                holder.name = nameView;
                holder.quantity = quantityView;

                row.setTag(holder);
                convertView = row;

            } else {
                holder = (RowHolder) convertView.getTag();
            }

            if (productCursor != null
                    && !productCursor.isClosed()
                    && productCursor.moveToPosition(position)) {

                String name = productCursor.getString(
                        productCursor.getColumnIndexOrThrow("name")
                );
                int quantity = productCursor.getInt(
                        productCursor.getColumnIndexOrThrow("stock_qty")
                );
                String unit = productCursor.getString(
                        productCursor.getColumnIndexOrThrow("unit")
                );

                holder.name.setText(safe(name));
                holder.quantity.setText(
                        "คงเหลือ: " + quantity + " " + safe(unit)
                );
            }

            return convertView;
        }
    }

    private static class RowHolder {
        TextView name;
        TextView quantity;
    }
            }
