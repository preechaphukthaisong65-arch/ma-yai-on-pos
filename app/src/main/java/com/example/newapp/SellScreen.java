package com.example.newapp;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import android.content.Intent;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class SellScreen extends BaseScreen {

    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int DARK = Color.rgb(31, 41, 55);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int RED = Color.rgb(220, 38, 38);
    private static final int BG = Color.rgb(245, 247, 251);

    private LocalDatabase localDatabase;
    private SQLiteDatabase database;
    private TextToSpeech textToSpeech;

    private EditText barcodeInput;
    private EditText receivedInput;
    private TextView cartContainer;
    private LinearLayout cartLayout;
    private TextView subtotalText;
    private TextView surchargeText;
    private TextView totalText;
    private TextView changeText;
    private TextView paymentStatusText;
    private RadioGroup paymentGroup;
    private RadioButton cashRadio;
    private RadioButton creditRadio;
    private Button checkoutButton;

    private final ArrayList<CartItem> cart = new ArrayList<>();

    private boolean saving = false;
    private String pendingSaleId;

    private static class CartItem {
        long productId;
        String barcode;
        String name;
        double price;
        int stock;
        int quantity;

        CartItem(
                long productId,
                String barcode,
                String name,
                double price,
                int stock
        ) {
            this.productId = productId;
            this.barcode = barcode;
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.quantity = 1;
        }

        double getLineTotal() {
            return roundMoney(price * quantity);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createBackground();

        localDatabase = new LocalDatabase(this);
        database = localDatabase.getWritableDatabase();

        initSpeech();
        buildScreen();
    }

    private void initSpeech() {
        textToSpeech = new TextToSpeech(
                this,
                status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        int result = textToSpeech.setLanguage(
                                new Locale("th", "TH")
                        );

                        if (result == TextToSpeech.LANG_MISSING_DATA
                                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            textToSpeech.setLanguage(Locale.getDefault());
                        }
                    }
                }
        );
    }

    private void speak(String message) {
        if (textToSpeech != null) {
            textToSpeech.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "maeyai-pos-speech"
            );
        }
    }

    private void buildScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(14), dp(16), dp(24));

        scrollView.addView(page);

        ViewGroup content = findViewById(android.R.id.content);
        View backgroundRoot = content.getChildCount() > 0
                ? content.getChildAt(0)
                : null;

        if (backgroundRoot instanceof ViewGroup) {
            ((ViewGroup) backgroundRoot).addView(
                    scrollView,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        } else {
            content.addView(
                    scrollView,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        }

        TextView title = text(
                "ขายสินค้า",
                25,
                DARK,
                true
        );
        page.addView(title);

        TextView subtitle = text(
                "สแกนบาร์โค้ดเพื่อเพิ่มสินค้าเข้าตะกร้า",
                14,
                Color.DKGRAY,
                false
        );
        page.addView(subtitle);

        addSpace(page, 16);

        addSectionTitle(page, "สแกนหรือกรอกบาร์โค้ด");

        LinearLayout barcodeRow = new LinearLayout(this);
        barcodeRow.setOrientation(LinearLayout.HORIZONTAL);
        barcodeRow.setGravity(Gravity.CENTER_VERTICAL);

        barcodeInput = new EditText(this);
        barcodeInput.setSingleLine(true);
        barcodeInput.setHint("กรอกบาร์โค้ดสินค้า");
        barcodeInput.setTextSize(16);
        barcodeInput.setInputType(InputType.TYPE_CLASS_TEXT);
        barcodeInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        barcodeInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        barcodeInput.setBackgroundColor(Color.WHITE);

        barcodeRow.addView(
                barcodeInput,
                new LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        1
                )
        );

        Button addButton = button("เพิ่ม", BLUE);
        barcodeRow.addView(
                addButton,
                new LinearLayout.LayoutParams(
                        dp(78),
                        dp(52)
                )
        );

        page.addView(barcodeRow);

        addButton.setOnClickListener(v -> addBarcodeFromInput());

        barcodeInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO) {
                addBarcodeFromInput();
                return true;
            }
            return false;
        });

        Button scanButton = button("เปิดกล้องสแกนบาร์โค้ด", DARK);
        page.addView(
                scanButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(48)
                )
        );

        scanButton.setOnClickListener(v -> startScanner());

        addSpace(page, 18);
        addSectionTitle(page, "รายการสินค้า");

        cartLayout = new LinearLayout(this);
        cartLayout.setOrientation(LinearLayout.VERTICAL);
        cartLayout.setPadding(dp(12), dp(10), dp(12), dp(10));
        cartLayout.setBackgroundColor(Color.WHITE);

        page.addView(
                cartLayout,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        addSpace(page, 18);
        addSectionTitle(page, "วิธีชำระเงิน");

        paymentGroup = new RadioGroup(this);
        paymentGroup.setOrientation(RadioGroup.VERTICAL);

        cashRadio = new RadioButton(this);
        cashRadio.setText("เงินสด");
        cashRadio.setTextSize(16);
        cashRadio.setId(View.generateViewId());

        creditRadio = new RadioButton(this);
        creditRadio.setText("เครดิต (+5%)");
        creditRadio.setTextSize(16);
        creditRadio.setId(View.generateViewId());

        paymentGroup.addView(cashRadio);
        paymentGroup.addView(creditRadio);
        paymentGroup.check(cashRadio.getId());

        page.addView(paymentGroup);

        paymentGroup.setOnCheckedChangeListener(
                (group, checkedId) -> updateTotals()
        );

        TextView receivedLabel = text(
                "จำนวนเงินที่รับ",
                15,
                DARK,
                true
        );
        page.addView(receivedLabel);

        receivedInput = new EditText(this);
        receivedInput.setSingleLine(true);
        receivedInput.setHint("กรอกจำนวนเงินที่ลูกค้าจ่าย");
        receivedInput.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        receivedInput.setTextSize(18);
        receivedInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        receivedInput.setBackgroundColor(Color.WHITE);

        page.addView(
                receivedInput,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(52)
                )
        );

        receivedInput.addTextChangedListener(
                new android.text.TextWatcher() {
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
                        updateTotals();
                    }

                    @Override
                    public void afterTextChanged(
                            android.text.Editable s
                    ) {}
                }
        );

        addSpace(page, 16);

        subtotalText = text("ยอดสินค้า: 0.00 บาท", 16, DARK, false);
        surchargeText = text("ค่าธรรมเนียมเครดิต: 0.00 บาท", 15, DARK, false);
        totalText = text("ยอดสุทธิ: 0.00 บาท", 22, BLUE, true);
        changeText = text("เงินทอน: 0.00 บาท", 18, GREEN, true);
        paymentStatusText = text("", 14, DARK, false);

        page.addView(subtotalText);
        page.addView(surchargeText);
        page.addView(totalText);
        page.addView(changeText);
        page.addView(paymentStatusText);

        addSpace(page, 18);

        checkoutButton = button("คิดเงิน", GREEN);
        checkoutButton.setTextSize(19);

        page.addView(
                checkoutButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(58)
                )
        );

        checkoutButton.setOnClickListener(v -> checkout());

        renderCart();
        updateTotals();
    }

    private void startScanner() {
        try {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(
                    IntentIntegrator.ALL_CODE_TYPES
            );
            integrator.setPrompt("วางบาร์โค้ดให้อยู่ในกรอบ");
            integrator.setBeepEnabled(false);
            integrator.setBarcodeImageEnabled(false);
            integrator.setOrientationLocked(true);
            integrator.initiateScan();
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "เปิดกล้องสแกนไม่สำเร็จ",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult result = IntentIntegrator.parseActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (result != null && result.getContents() != null) {
            barcodeInput.setText(result.getContents());
            addProductByBarcode(result.getContents());
        }
    }

    private void addBarcodeFromInput() {
        String barcode = barcodeInput.getText()
                .toString()
                .trim();

        if (barcode.isEmpty()) {
            Toast.makeText(
                    this,
                    "กรุณากรอกหรือสแกนบาร์โค้ด",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        addProductByBarcode(barcode);
    }

    private void addProductByBarcode(String barcode) {
        Cursor cursor = null;

        try {
            cursor = database.rawQuery(
                    "SELECT id, barcode, name, price, stock " +
                    "FROM products " +
                    "WHERE barcode = ? AND is_archived = 0 " +
                    "LIMIT 1",
                    new String[]{barcode}
            );

            if (!cursor.moveToFirst()) {
                speak("ไม่พบสินค้าค่ะ");
                Toast.makeText(
                        this,
                        "ไม่พบสินค้า",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            long productId = cursor.getLong(0);
            String productBarcode = cursor.getString(1);
            String productName = cursor.getString(2);
            double price = cursor.getDouble(3);
            int stock = cursor.getInt(4);

            if (stock <= 0) {
                speak("สินค้าหมดค่ะ");
                Toast.makeText(
                        this,
                        "สินค้าหมด",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            CartItem existing = findCartItem(productId);

            if (existing != null) {
                if (existing.quantity >= stock) {
                    Toast.makeText(
                            this,
                            "สินค้าในสต็อกไม่เพียงพอ",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                existing.quantity++;
                existing.stock = stock;
            } else {
                CartItem item = new CartItem(
                        productId,
                        productBarcode,
                        productName,
                        price,
                        stock
                );
                cart.add(item);
            }

            if (pendingSaleId == null) {
                pendingSaleId = UUID.randomUUID().toString();
            }

            barcodeInput.setText("");
            renderCart();
            updateTotals();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "อ่านข้อมูลสินค้าไม่สำเร็จ",
                    Toast.LENGTH_LONG
            ).show();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private CartItem findCartItem(long productId) {
        for (CartItem item : cart) {
            if (item.productId == productId) {
                return item;
            }
        }
        return null;
    }

    private void renderCart() {
        cartLayout.removeAllViews();

        if (cart.isEmpty()) {
            TextView empty = text(
                    "ยังไม่มีสินค้าในตะกร้า",
                    15,
                    Color.GRAY,
                    false
            );
            cartLayout.addView(empty);
            return;
        }

        for (int i = 0; i < cart.size(); i++) {
            final CartItem item = cart.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            TextView name = text(
                    item.name,
                    16,
                    DARK,
                    true
            );
            row.addView(name);

            TextView details = text(
                    money(item.price) + " บาท × "
                            + item.quantity
                            + " = "
                            + money(item.getLineTotal())
                            + " บาท",
                    14,
                    Color.DKGRAY,
                    false
            );
            row.addView(details);

            LinearLayout controls = new LinearLayout(this);
            controls.setGravity(Gravity.CENTER_VERTICAL);

            Button minus = button("−", DARK);
            Button plus = button("+", BLUE);
            Button remove = button("ลบ", RED);

            controls.addView(
                    minus,
                    new LinearLayout.LayoutParams(dp(52), dp(40))
            );

            TextView quantity = text(
                    String.valueOf(item.quantity),
                    17,
                    DARK,
                    true
            );
            quantity.setGravity(Gravity.CENTER);

            controls.addView(
                    quantity,
                    new LinearLayout.LayoutParams(dp(48), dp(40))
            );

            controls.addView(
                    plus,
                    new LinearLayout.LayoutParams(dp(52), dp(40))
            );

            controls.addView(
                    remove,
                    new LinearLayout.LayoutParams(dp(64), dp(40))
            );

            row.addView(controls);

            minus.setOnClickListener(v -> {
                if (item.quantity > 1) {
                    item.quantity--;
                } else {
                    cart.remove(item);
                }

                if (cart.isEmpty()) {
                    pendingSaleId = null;
                }

                renderCart();
                updateTotals();
            });

            plus.setOnClickListener(v -> {
                if (item.quantity >= item.stock) {
                    Toast.makeText(
                            this,
                            "สินค้าในสต็อกไม่เพียงพอ",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                item.quantity++;
                renderCart();
                updateTotals();
            });

            remove.setOnClickListener(v -> {
                cart.remove(item);

                if (cart.isEmpty()) {
                    pendingSaleId = null;
                }

                renderCart();
                updateTotals();
            });

            cartLayout.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(
                    Color.rgb(229, 231, 235)
            );

            cartLayout.addView(
                    divider,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(1)
                    )
            );
        }
    }

    private boolean isCredit() {
        return creditRadio != null
                && creditRadio.isChecked();
    }

    private double getSubtotal() {
        double subtotal = 0;

        for (CartItem item : cart) {
            subtotal += item.getLineTotal();
        }

        return roundMoney(subtotal);
    }

    private double getSurcharge() {
        if (isCredit()) {
            return roundMoney(getSubtotal() * 0.05);
        }
        return 0;
    }

    private double getTotal() {
        return roundMoney(getSubtotal() + getSurcharge());
    }

    private double getAmountReceived() {
        if (isCredit()) {
            return 0;
        }

        try {
            String value = receivedInput.getText()
                    .toString()
                    .trim();

            if (value.isEmpty()) {
                return 0;
            }

            return roundMoney(Double.parseDouble(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private void updateTotals() {
        if (subtotalText == null) {
            return;
        }

        double subtotal = getSubtotal();
        double surcharge = getSurcharge();
        double total = getTotal();

        subtotalText.setText(
                "ยอดสินค้า: " + money(subtotal) + " บาท"
        );

        surchargeText.setText(
                "ค่าธรรมเนียมเครดิต 5%: "
                        + money(surcharge) + " บาท"
        );

        totalText.setText(
                "ยอดสุทธิ: " + money(total) + " บาท"
        );

        if (isCredit()) {
            receivedInput.setEnabled(false);
            receivedInput.setText("");
            changeText.setText("เงินทอน: 0.00 บาท");
            paymentStatusText.setText("สถานะ: ค้างชำระ");
        } else {
            receivedInput.setEnabled(true);

            double received = getAmountReceived();
            double change = Math.max(0, received - total);
            double shortage = Math.max(0, total - received);

            changeText.setText(
                    "เงินทอน: " + money(change) + " บาท"
            );

            if (shortage > 0) {
                paymentStatusText.setText(
                        "เงินยังขาด: " + money(shortage) + " บาท"
                );
            } else {
                paymentStatusText.setText("สถานะ: รับเงินครบ");
            }
        }
    }

    private void checkout() {
        if (saving) {
            return;
        }

        if (cart.isEmpty()) {
            Toast.makeText(
                    this,
                    "กรุณาเพิ่มสินค้าก่อนคิดเงิน",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        final double subtotal = getSubtotal();
        final double surcharge = getSurcharge();
        final double total = getTotal();
        final boolean credit = isCredit();
        final double received = credit ? 0 : getAmountReceived();

        if (!credit && received < total) {
            Toast.makeText(
                    this,
                    "จำนวนเงินที่รับยังไม่ครบ",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("ยืนยันการคิดเงิน")
                .setMessage(
                        "ยอดสินค้า: " + money(subtotal) + " บาท\n"
                                + "ค่าธรรมเนียม: " + money(surcharge)
                                + " บาท\n"
                                + "ยอดสุทธิ: " + money(total) + " บาท\n"
                                + "ประเภท: " + (credit ? "เครดิต" : "เงินสด")
                )
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("ยืนยัน", (dialog, which) -> {
                    performCheckout(
                            subtotal,
                            surcharge,
                            total,
                            credit,
                            received
                    );
                })
                .show();
    }

    private void performCheckout(
            double subtotal,
            double surcharge,
            double total,
            boolean credit,
            double received
    ) {
        if (saving) {
            return;
        }

        saving = true;
        checkoutButton.setEnabled(false);

        if (pendingSaleId == null) {
            pendingSaleId = UUID.randomUUID().toString();
        }

        String saleId = pendingSaleId;
        String now = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss",
                Locale.US
        ).format(new Date());

        String receiptNumber = "MYO-"
                + new SimpleDateFormat(
                        "yyyyMMddHHmmss",
                        Locale.US
                ).format(new Date())
                + "-"
                + saleId.substring(0, 6).toUpperCase(Locale.US);

        SQLiteDatabase db = null;
        boolean success = false;
        String errorMessage = "บันทึกการขายไม่สำเร็จ";

        try {
            db = localDatabase.getWritableDatabase();
            db.beginTransaction();

            // ป้องกันการบันทึกซ้ำด้วย idempotency_key
            Cursor existing = db.rawQuery(
                    "SELECT id FROM sales WHERE idempotency_key = ?",
                    new String[]{saleId}
            );

            boolean alreadySaved;

            try {
                alreadySaved = existing.moveToFirst();
            } finally {
                existing.close();
            }

            if (alreadySaved) {
                db.setTransactionSuccessful();
                success = true;
            } else {
                // ตรวจสอบสต็อกและราคาจากฐานข้อมูลจริงก่อนบันทึก
                double currentSubtotal = 0;

                for (CartItem item : cart) {
                    Cursor product = db.rawQuery(
                            "SELECT name, barcode, price, stock " +
                            "FROM products " +
                            "WHERE id = ? AND is_archived = 0",
                            new String[]{String.valueOf(item.productId)}
                    );

                    try {
                        if (!product.moveToFirst()) {
                            throw new Exception(
                                    "ไม่พบสินค้า: " + item.name
                            );
                        }

                        String currentName = product.getString(0);
                        String currentBarcode = product.getString(1);
                        double currentPrice = product.getDouble(2);
                        int currentStock = product.getInt(3);

                        if (currentStock < item.quantity) {
                            throw new Exception(
                                    "สต็อกไม่เพียงพอ: " + currentName
                            );
                        }

                        if (Math.abs(currentPrice - item.price) > 0.001) {
                            throw new Exception(
                                    "ราคาสินค้าเปลี่ยน กรุณานำสินค้า "
                                            + currentName
                                            + " ออกจากตะกร้าแล้วเพิ่มใหม่"
                            );
                        }

                        if (!item.barcode.equals(currentBarcode)) {
                            throw new Exception(
                                    "ข้อมูลบาร์โค้ดสินค้าเปลี่ยนแปลง"
                            );
                        }

                        currentSubtotal += item.price * item.quantity;

                    } finally {
                        product.close();
                    }
                }

                currentSubtotal = roundMoney(currentSubtotal);

                double currentSurcharge = credit
                        ? roundMoney(currentSubtotal * 0.05)
                        : 0;

                double currentTotal = roundMoney(
                        currentSubtotal + currentSurcharge
                );

                if (Math.abs(currentTotal - total) > 0.001) {
                    throw new Exception(
                            "ยอดขายเปลี่ยน กรุณาตรวจสอบรายการอีกครั้ง"
                    );
                }

                if (!credit && received < currentTotal) {
                    throw new Exception(
                            "จำนวนเงินที่รับไม่ครบ"
                    );
                }

                double change = credit
                        ? 0
                        : roundMoney(received - currentTotal);

                ContentValues sale = new ContentValues();
                sale.put("id", saleId);
                sale.put("receipt_number", receiptNumber);
                sale.put("sold_at", now);
                sale.put("subtotal", currentSubtotal);
                sale.put("discount", 0);
                sale.put("surcharge", currentSurcharge);
                sale.put("total", currentTotal);
                sale.put("payment_method", credit ? "credit" : "cash");
                sale.put("payment_status", credit ? "unpaid" : "paid");
                sale.put("amount_received", received);
                sale.put("change_amount", change);
                sale.put("paid_at", credit ? null : now);
                sale.put("print_status", "not_printed");
                sale.put("idempotency_key", saleId);

                long inserted = db.insertOrThrow(
                        "sales",
                        null,
                        sale
                );

                if (inserted == -1) {
                    throw new Exception("สร้างรายการขายไม่สำเร็จ");
                }

                for (CartItem item : cart) {
                    Cursor product = db.rawQuery(
                            "SELECT name, barcode, price, stock " +
                            "FROM products " +
                            "WHERE id = ? AND is_archived = 0",
                            new String[]{String.valueOf(item.productId)}
                    );

                    String productName;
                    String barcode;
                    double price;
                    int stockBefore;

                    try {
                        if (!product.moveToFirst()) {
                            throw new Exception(
                                    "ไม่พบสินค้า: " + item.name
                            );
                        }

                        productName = product.getString(0);
                        barcode = product.getString(1);
                        price = product.getDouble(2);
                        stockBefore = product.getInt(3);
                    } finally {
                        product.close();
                    }

                    ContentValues saleItem = new ContentValues();
                    saleItem.put("sale_id", saleId);
                    saleItem.put("product_id", item.productId);
                    saleItem.put("product_name", productName);
                    saleItem.put("barcode", barcode);
                    saleItem.put("unit_price", price);
                    saleItem.put("quantity", item.quantity);
                    saleItem.put(
                            "line_total",
                            roundMoney(price * item.quantity)
                    );

                    db.insertOrThrow(
                            "sale_items",
                            null,
                            saleItem
                    );

                    ContentValues stockUpdate = new ContentValues();
                    stockUpdate.put(
                            "stock",
                            stockBefore - item.quantity
                    );
                    stockUpdate.put(
                            "updated_at",
                            now
                    );

                    int updated = db.update(
                            "products",
                            stockUpdate,
                            "id = ? AND stock >= ? AND is_archived = 0",
                            new String[]{
                                    String.valueOf(item.productId),
                                    String.valueOf(item.quantity)
                            }
                    );

                    if (updated != 1) {
                        throw new Exception(
                                "ตัดสต็อกไม่สำเร็จ: " + productName
                        );
                    }

                    ContentValues movement = new ContentValues();
                    movement.put("product_id", item.productId);
                    movement.put("movement_type", "sale");
                    movement.put("quantity", -item.quantity);
                    movement.put("stock_before", stockBefore);
                    movement.put(
                            "stock_after",
                            stockBefore - item.quantity
                    );
                    movement.put("reference_id", saleId);
                    movement.put("created_at", now);

                    db.insertOrThrow(
                            "stock_movements",
                            null,
                            movement
                    );
                }

                db.setTransactionSuccessful();
                success = true;
            }

        } catch (Exception e) {
            errorMessage = e.getMessage() == null
                    ? "บันทึกการขายไม่สำเร็จ"
                    : e.getMessage();
        } finally {
            if (db != null && db.inTransaction()) {
                db.endTransaction();
            }

            saving = false;
            checkoutButton.setEnabled(true);
        }

        if (success) {
            double change = credit ? 0 : roundMoney(received - total);

            cart.clear();
            pendingSaleId = null;
            receivedInput.setText("");
            barcodeInput.setText("");

            renderCart();
            updateTotals();

            new AlertDialog.Builder(this)
                    .setTitle("คิดเงินสำเร็จ")
                    .setMessage(
                            "บันทึกการขายเรียบร้อย\n"
                                    + "ยอดสุทธิ: " + money(total) + " บาท\n"
                                    + "เงินทอน: " + money(change) + " บาท"
                    )
                    .setPositiveButton("ตกลง", null)
                    .show();

        } else {
            Toast.makeText(
                    this,
                    errorMessage,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void addSectionTitle(
            LinearLayout parent,
            String value
    ) {
        TextView view = text(value, 17, DARK, true);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(8);
        parent.addView(view, params);
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

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        view.setPadding(0, dp(3), 0, dp(3));
        return view;
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(color)
        );
        return button;
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

    private static double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String money(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        if (localDatabase != null) {
            localDatabase.close();
        }

        super.onDestroy();
    }
                }
