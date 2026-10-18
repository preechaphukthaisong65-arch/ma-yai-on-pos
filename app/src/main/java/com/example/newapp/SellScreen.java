
package com.example.newapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class SellScreen extends Activity {

    private static final int CAMERA_REQUEST = 801;

    private LocalDatabase database;
    private SQLiteDatabase db;

    private LinearLayout root;
    private LinearLayout content;
    private DecoratedBarcodeView barcodeView;
    private TextView statusText;

    private TextToSpeech tts;

    private boolean scanning = false;
    private boolean waitingForVoice = false;
    private boolean saleSaved = false;

    private final ArrayList<CartItem> cart = new ArrayList<>();

    private static class CartItem {
        long productId;
        String barcode;
        String name;
        String unit;
        int quantity;
        double unitPrice;

        double lineTotal() {
            return quantity * unitPrice;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        database = LocalDatabase.getInstance(this);
        db = database.getWritableDatabase();

        initializeVoice();
        buildScreen();
        showMenu();
    }

    private void initializeVoice() {
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("th", "TH"));

                tts.setOnUtteranceProgressListener(
                        new UtteranceProgressListener() {
                            @Override
                            public void onStart(String utteranceId) {
                            }

                            @Override
                            public void onDone(String utteranceId) {
                                runOnUiThread(() -> {
                                    if (scanning && barcodeView != null) {
                                        waitingForVoice = false;
                                        barcodeView.resume();
                                    }
                                });
                            }

                            @Override
                            public void onError(String utteranceId) {
                                runOnUiThread(() -> {
                                    if (scanning && barcodeView != null) {
                                        waitingForVoice = false;
                                        barcodeView.resume();
                                    }
                                });
                            }
                        }
                );
            }
        });
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("ขายสินค้า");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(45, 90, 55));
        title.setGravity(Gravity.CENTER);
        root.addView(title, params());

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 12, 0, 0);
        root.addView(content, params());

        scroll.addView(root);
        setContentView(scroll);
    }

    private void showMenu() {
        stopScanner();
        content.removeAllViews();

        addButton("เริ่มขายสินค้า", () -> {
            cart.clear();
            saleSaved = false;
            requestCameraAndStart();
        });

        addButton("กลับ", this::finish);
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
        content.removeAllViews();

        scanning = true;
        waitingForVoice = false;

        barcodeView = new DecoratedBarcodeView(this);
        barcodeView.setStatusText("สแกนบาร์โค้ดสินค้า");

        content.addView(
                barcodeView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        statusText = new TextView(this);
        statusText.setText("กำลังสแกนสินค้า");
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        content.addView(statusText, params());

        Button endButton = new Button(this);
        endButton.setText("จบการสแกน");
        endButton.setOnClickListener(v -> {
            stopScanner();
            showCart();
        });
        content.addView(endButton, params());

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
                barcodeView.pause();

                runOnUiThread(() -> handleScannedBarcode(barcode));
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> points) {
            }
        });

        barcodeView.resume();
    }

    private void handleScannedBarcode(String barcode) {
        Cursor cursor = null;

        try {
            cursor = db.query(
                    "products",
                    new String[]{
                            "id",
                            "barcode",
                            "name",
                            "unit",
                            "price",
                            "stock_qty"
                    },
                    "barcode = ?",
                    new String[]{barcode},
                    null,
                    null,
                    null,
                    "1"
            );

            if (!cursor.moveToFirst()) {
                speakAndContinue("ไม่พบสินค้าในระบบค่ะ");
                return;
            }

            long productId = cursor.getLong(
                    cursor.getColumnIndexOrThrow("id")
            );

            String name = cursor.getString(
                    cursor.getColumnIndexOrThrow("name")
            );

            String unit = cursor.getString(
                    cursor.getColumnIndexOrThrow("unit")
            );

            double price = cursor.getDouble(
                    cursor.getColumnIndexOrThrow("price")
            );

            int stock = cursor.getInt(
                    cursor.getColumnIndexOrThrow("stock_qty")
            );

            CartItem existing = findCartItem(barcode);
            int requestedQty = existing == null
                    ? 1 : existing.quantity + 1;

            if (requestedQty > stock) {
                speakAndContinue("สินค้าไม่เพียงพอค่ะ");
                return;
            }

            if (existing == null) {
                CartItem item = new CartItem();
                item.productId = productId;
                item.barcode = barcode;
                item.name = name;
                item.unit = unit == null ? "ชิ้น" : unit;
                item.quantity = 1;
                item.unitPrice = price;
                cart.add(item);
            } else {
                existing.quantity++;
            }

            if (statusText != null) {
                statusText.setText(
                        "เพิ่ม " + name
                                + "\nจำนวนในตะกร้า: " + requestedQty
                );
            }

            speakAndContinue(name + " 1 "
                    + (unit == null ? "ชิ้น" : unit) + " ค่ะ");

        } catch (Exception e) {
            android.util.Log.e("SellScreen", "Scan product failed", e);
            speakAndContinue("อ่านข้อมูลสินค้าไม่สำเร็จค่ะ");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private CartItem findCartItem(String barcode) {
        for (CartItem item : cart) {
            if (item.barcode.equals(barcode)) {
                return item;
            }
        }
        return null;
    }

    private void speakAndContinue(String message) {
        if (tts == null) {
            waitingForVoice = false;

            if (scanning && barcodeView != null) {
                barcodeView.resume();
            }
            return;
        }

        int result = tts.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "scan_" + UUID.randomUUID()
        );

        if (result == TextToSpeech.ERROR) {
            waitingForVoice = false;

            if (scanning && barcodeView != null) {
                barcodeView.resume();
            }
        }
    }

    private void stopScanner() {
        scanning = false;
        waitingForVoice = false;

        if (barcodeView != null) {
            try {
                barcodeView.pause();
                barcodeView.setVisibility(View.GONE);
            } catch (Exception e) {
                android.util.Log.w(
                        "SellScreen",
                        "Unable to stop scanner cleanly",
                        e
                );
            }
            barcodeView = null;
        }
    }

    private void showCart() {
        content.removeAllViews();

        if (cart.isEmpty()) {
            content.addView(
                    text("ยังไม่มีสินค้าในรายการขาย"),
                    params()
            );
            addButton("เริ่มสแกนใหม่", this::startScanner);
            addButton("กลับ", this::showMenu);
            return;
        }

        content.addView(text("สรุปรายการขาย"), params());

        for (CartItem item : cart) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(4, 8, 4, 8);

            row.addView(
                    text(item.name
                            + "\nจำนวน " + item.quantity + " " + item.unit
                            + "\nราคา/หน่วย " + money(item.unitPrice)
                            + " บาท"
                            + "\nรวม " + money(item.lineTotal()) + " บาท"),
                    params()
            );

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button minus = new Button(this);
            minus.setText("−");
            minus.setOnClickListener(v -> {
                if (item.quantity > 1) {
                    item.quantity--;
                } else {
                    cart.remove(item);
                }
                showCart();
            });

            Button plus = new Button(this);
            plus.setText("+");
            plus.setOnClickListener(v -> {
                item.quantity++;
                showCart();
            });

            Button remove = new Button(this);
            remove.setText("ลบรายการ");
            remove.setOnClickListener(v -> {
                cart.remove(item);
                showCart();
            });

            actions.addView(minus, weightParams());
            actions.addView(plus, weightParams());
            actions.addView(remove, weightParams());

            row.addView(actions, params());
            content.addView(row, params());
        }

        content.addView(
                text("ยอดรวมสินค้า: " + money(subtotal()) + " บาท"),
                params()
        );

        addButton("รับชำระเงินสด", this::showCashPayment);
        addButton("ขายเชื่อ", this::showCreditPayment);
        addButton("สแกนสินค้าเพิ่ม", this::startScanner);
        addButton("ยกเลิกหน้าขาย", this::showMenu);
    }

    private double subtotal() {
        double total = 0;

        for (CartItem item : cart) {
            total += item.lineTotal();
        }

        return total;
    }

    private void showCashPayment() {
        if (cart.isEmpty()) {
            toast("ไม่มีสินค้าในตะกร้า");
            return;
        }

        content.removeAllViews();

        double total = subtotal();

        content.addView(
                text("ยอดชำระ " + money(total) + " บาท"),
                params()
        );

        EditText cashInput = new EditText(this);
        cashInput.setHint("จำนวนเงินที่ลูกค้าจ่าย");
        cashInput.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        content.addView(cashInput, params());

        addButton("ยืนยันรับเงินและบันทึกการขาย", () -> {
            double received;

            try {
                received = Double.parseDouble(
                        cashInput.getText().toString().trim()
                );
            } catch (Exception e) {
                toast("กรุณากรอกจำนวนเงินให้ถูกต้อง");
                return;
            }

            if (received < total) {
                toast("เงินที่รับไม่พอ");
                return;
            }

            double change = received - total;

            new AlertDialog.Builder(this)
                    .setTitle("ยืนยันการขาย")
                    .setMessage(
                            "ยอดขาย " + money(total) + " บาท\n"
                                    + "รับเงิน " + money(received) + " บาท\n"
                                    + "เงินทอน " + money(change) + " บาท"
                    )
                    .setNegativeButton("กลับไปแก้ไข", null)
                    .setPositiveButton("ยืนยันขาย", (dialog, which) ->
                            saveSale("CASH", null, null, received, change))
                    .show();
        });

        addButton("กลับรายการสินค้า", this::showCart);
    }

    private void showCreditPayment() {
        if (cart.isEmpty()) {
            toast("ไม่มีสินค้าในตะกร้า");
            return;
        }

        content.removeAllViews();

        content.addView(
                text("ขายเชื่อ — ค่าบริการ 5%"),
                params()
        );

        EditText firstName = new EditText(this);
        firstName.setHint("ชื่อลูกค้า");
        content.addView(firstName, params());

        EditText lastName = new EditText(this);
        lastName.setHint("นามสกุลลูกค้า");
        content.addView(lastName, params());

        double fee = subtotal() * 0.05;
        double total = subtotal() + fee;

        content.addView(
                text("ยอดสินค้า: " + money(subtotal()) + " บาท"),
                params()
        );

        content.addView(
                text("ค่าบริการ 5%: " + money(fee) + " บาท"),
                params()
        );

        content.addView(
                text("ยอดค้างชำระ: " + money(total) + " บาท"),
                params()
        );

        addButton("ยืนยันขายเชื่อ", () -> {
            String first = firstName.getText().toString().trim();
            String last = lastName.getText().toString().trim();

            if (first.isEmpty() || last.isEmpty()) {
                toast("กรุณากรอกชื่อและนามสกุลลูกค้า");
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("ยืนยันขายเชื่อ")
                    .setMessage(
                            "ลูกค้า: " + first + " " + last
                                    + "\nยอดค้าง: " + money(total) + " บาท"
                    )
                    .setNegativeButton("ยกเลิก", null)
                    .setPositiveButton("ยืนยัน", (dialog, which) ->
                            saveSale("CREDIT", first, last, 0, 0))
                    .show();
        });

        addButton("กลับรายการสินค้า", this::showCart);
    }

    private void saveSale(
            String paymentType,
            String firstName,
            String lastName,
            double cashReceived,
            double changeAmount
    ) {
        if (saleSaved) {
            toast("บิลนี้ถูกบันทึกแล้ว");
            return;
        }

        if (cart.isEmpty()) {
            toast("ไม่มีสินค้าในตะกร้า");
            return;
        }

        long customerId = -1;

        db.beginTransaction();

        try {
            if ("CREDIT".equals(paymentType)) {
                Cursor customerCursor = db.query(
                        "customers",
                        new String[]{"id"},
                        "first_name = ? AND last_name = ?",
                        new String[]{firstName, lastName},
                        null,
                        null,
                        "id ASC",
                        "1"
                );

                try {
                    if (customerCursor.moveToFirst()) {
                        customerId = customerCursor.getLong(0);
                    }
                } finally {
                    customerCursor.close();
                }

                if (customerId < 0) {
                    android.content.ContentValues customerValues =
                            new android.content.ContentValues();

                    customerValues.put("first_name", firstName);
                    customerValues.put("last_name", lastName);
                    customerValues.put("created_at", LocalDatabase.now());

                    customerId = db.insertOrThrow(
                            "customers",
                            null,
                            customerValues
                    );
                }
            }

            for (CartItem item : cart) {
                Cursor stockCursor = db.query(
                        "products",
                        new String[]{"stock_qty"},
                        "id = ?",
                        new String[]{String.valueOf(item.productId)},
                        null,
                        null,
                        null,
                        "1"
                );

                try {
                    if (!stockCursor.moveToFirst()
                            || stockCursor.getInt(0) < item.quantity) {
                        throw new IllegalStateException(
                                "สินค้าไม่เพียงพอ: " + item.name
                        );
                    }
                } finally {
                    stockCursor.close();
                }
            }

            double sub = subtotal();
            double fee = "CREDIT".equals(paymentType) ? sub * 0.05 : 0;
            double total = sub + fee;

            String billNumber = "B"
                    + new java.text.SimpleDateFormat(
                            "yyyyMMddHHmmss",
                            Locale.US
                    ).format(new java.util.Date())
                    + UUID.randomUUID().toString()
                    .substring(0, 6).toUpperCase(Locale.US);

            String status = "CREDIT".equals(paymentType)
                    ? "PENDING" : "PAID";

            android.content.ContentValues saleValues =
                    new android.content.ContentValues();

            saleValues.put("bill_number", billNumber);
            saleValues.put("payment_type", paymentType);
            saleValues.put("status", status);
            if (customerId >= 0) {
                saleValues.put("customer_id", customerId);
            }
            saleValues.put("subtotal", sub);
            saleValues.put("service_fee", fee);
            saleValues.put("total", total);
            saleValues.put("cash_received", cashReceived);
            saleValues.put("change_amount", changeAmount);
            saleValues.put("created_at", LocalDatabase.now());
            saleValues.put(
                    "paid_at",
                    "CASH".equals(paymentType) ? LocalDatabase.now() : null
            );
            saleValues.put("receipt_text", "");
            saleValues.put("print_status", "NOT_PRINTED");

            long saleId = db.insertOrThrow(
                    "sales",
                    null,
                    saleValues
            );

            StringBuilder receipt = new StringBuilder();
            receipt.append("ร้านแม่ใหญ่อ่อน POS\n");
            receipt.append("เลขบิล: ").append(billNumber).append('\n');
            receipt.append("ประเภท: ").append(paymentType).append('\n');
            if (customerId >= 0) {
                receipt.append("ลูกค้า: ")
                        .append(firstName).append(' ')
                        .append(lastName).append('\n');
            }
            receipt.append("------------------------------\n");

            for (CartItem item : cart) {
                android.content.ContentValues line =
                        new android.content.ContentValues();

                line.put("sale_id", saleId);
                line.put("product_id", item.productId);
                line.put("product_name", item.name);
                line.put("barcode", item.barcode);
                line.put("unit", item.unit);
                line.put("quantity", item.quantity);
                line.put("unit_price", item.unitPrice);
                line.put("line_total", item.lineTotal());

                db.insertOrThrow("sale_items", null, line);

                Cursor currentCursor = db.query(
                        "products",
                        new String[]{"stock_qty"},
                        "id = ?",
                        new String[]{String.valueOf(item.productId)},
                        null,
                        null,
                        null,
                        "1"
                );

                int before;
                try {
                    if (!currentCursor.moveToFirst()) {
                        throw new IllegalStateException("ไม่พบสินค้า");
                    }
                    before = currentCursor.getInt(0);
                } finally {
                    currentCursor.close();
                }

                int after = before - item.quantity;

                android.content.ContentValues productUpdate =
                        new android.content.ContentValues();
                productUpdate.put("stock_qty", after);
                productUpdate.put("updated_at", LocalDatabase.now());

                int updated = db.update(
                        "products",
                        productUpdate,
                        "id = ? AND stock_qty >= ?",
                        new String[]{
                                String.valueOf(item.productId),
                                String.valueOf(item.quantity)
                        }
                );

                if (updated != 1) {
                    throw new IllegalStateException(
                            "ตัดสต๊อกไม่สำเร็จ: " + item.name
                    );
                }

                android.content.ContentValues movement =
                        new android.content.ContentValues();

                movement.put("product_id", item.productId);
                movement.put("operation", "SALE");
                movement.put("qty_before", before);
                movement.put("qty_after", after);
                movement.put("qty_diff", after - before);
                movement.put("note", "ขายบิล " + billNumber);
                movement.put("created_at", LocalDatabase.now());

                db.insertOrThrow(
                        "stock_movements",
                        null,
                        movement
                );

                receipt.append(item.name)
                        .append(" x")
                        .append(item.quantity)
                        .append(" = ")
                        .append(money(item.lineTotal()))
                        .append('\n');
            }

            receipt.append("------------------------------\n");
            receipt.append("ยอดสินค้า: ").append(money(sub)).append('\n');
            receipt.append("ค่าบริการ: ").append(money(fee)).append('\n');
            receipt.append("ยอดสุทธิ: ").append(money(total)).append('\n');

            if ("CASH".equals(paymentType)) {
                receipt.append("รับเงิน: ")
                        .append(money(cashReceived)).append('\n');
                receipt.append("เงินทอน: ")
                        .append(money(changeAmount)).append('\n');
            } else {
                receipt.append("สถานะ: ค้างชำระ\n");
            }

            android.content.ContentValues receiptUpdate =
                    new android.content.ContentValues();
            receiptUpdate.put("receipt_text", receipt.toString());

            db.update(
                    "sales",
                    receiptUpdate,
                    "id = ?",
                    new String[]{String.valueOf(saleId)}
            );

            db.setTransactionSuccessful();
            saleSaved = true;

            showSavedReceipt(receipt.toString());

        } catch (Exception e) {
            android.util.Log.e("SellScreen", "Save sale failed", e);
            toast("บันทึกการขายไม่สำเร็จ: " + e.getMessage());
        } finally {
            db.endTransaction();
        }
    }

    private void showSavedReceipt(String receipt) {
        stopScanner();
        content.removeAllViews();

        content.addView(
                text("บันทึกการขายสำเร็จ"),
                params()
        );

        TextView receiptView = text(receipt);
        receiptView.setTextIsSelectable(true);
        content.addView(receiptView, params());

        addButton("กลับเมนูขาย", this::showMenu);
    }

    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(16);
        view.setTextColor(Color.DKGRAY);
        view.setPadding(8, 12, 8, 12);
        return view;
    }

    private void addButton(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        content.addView(button, params());
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
    }

    private String money(double value) {
        return String.format(Locale.US, "%,.2f", value);
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

        if (requestCode == CAMERA_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScanner();
        } else if (requestCode == CAMERA_REQUEST) {
            toast("ต้องอนุญาตกล้องเพื่อสแกนสินค้า");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (scanning && barcodeView != null && !waitingForVoice) {
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
