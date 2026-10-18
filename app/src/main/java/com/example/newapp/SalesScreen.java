package com.example.newapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class SalesScreen extends BaseScreen {

    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int GREEN = Color.rgb(16, 185, 129);
    private static final int DARK = Color.rgb(31, 41, 55);
    private static final int GRAY = Color.rgb(107, 114, 128);
    private static final int WHITE = Color.WHITE;

    private LinearLayout productListLayout;
    private LinearLayout cartListLayout;
    private TextView totalAmountText;

    private ProductRepository productRepository;
    private SalesRepository salesRepository;

    private double currentTotal = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createBackground();
        productRepository = new ProductRepository(this);
        salesRepository = new SalesRepository(this);

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
        View rootView = content.getChildCount() > 0 ? content.getChildAt(0) : null;

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

        page.addView(text("ระบบขายหน้าร้าน (POS)", 25, DARK, true));
        page.addView(text("เลือกสินค้าเพื่อเพิ่มลงตะกร้าและชำระเงิน", 14, GRAY, false));

        addSpace(page, 16);

        page.addView(text("รายการสินค้า", 16, DARK, true));
        addSpace(page, 6);

        productListLayout = new LinearLayout(this);
        productListLayout.setOrientation(LinearLayout.VERTICAL);
        page.addView(productListLayout);

        addSpace(page, 20);

        page.addView(text("ตะกร้าสินค้า", 16, DARK, true));
        addSpace(page, 6);

        cartListLayout = new LinearLayout(this);
        cartListLayout.setOrientation(LinearLayout.VERTICAL);
        cartListLayout.setPadding(dp(12), dp(12), dp(12), dp(12));
        cartListLayout.setBackgroundColor(WHITE);
        page.addView(cartListLayout);

        addSpace(page, 16);

        LinearLayout totalLayout = new LinearLayout(this);
        totalLayout.setOrientation(LinearLayout.HORIZONTAL);
        totalLayout.setGravity(Gravity.CENTER_VERTICAL);
        totalLayout.setPadding(dp(12), dp(12), dp(12), dp(12));
        totalLayout.setBackgroundColor(WHITE);

        totalAmountText = text("ยอดรวม: 0.00 บาท", 18, DARK, true);
        totalLayout.addView(totalAmountText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button checkoutButton = button("ชำระเงิน", GREEN);
        checkoutButton.setOnClickListener(v -> checkoutAction());
        totalLayout.addView(checkoutButton, new LinearLayout.LayoutParams(dp(120), dp(48)));

        page.addView(totalLayout);
    }

    private void loadProducts() {
        productListLayout.removeAllViews();
        List<ProductRepository.Product> products = productRepository.getAllProducts();

        if (products.isEmpty()) {
            productListLayout.addView(text("ยังไม่มีสินค้าในระบบ กรุณาเพิ่มสินค้าก่อน", 14, GRAY, false));
            return;
        }

        for (ProductRepository.Product p : products) {
            LinearLayout itemBox = new LinearLayout(this);
            itemBox.setOrientation(LinearLayout.HORIZONTAL);
            itemBox.setPadding(dp(12), dp(10), dp(12), dp(10));
            itemBox.setBackgroundColor(WHITE);
            itemBox.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout infoBox = new LinearLayout(this);
            infoBox.setOrientation(LinearLayout.VERTICAL);
            infoBox.addView(text(p.name, 16, DARK, true));
            infoBox.addView(text("ราคา: " + String.format(Locale.US, "%,.2f", p.price) + " บาท | สต็อก: " + p.stock, 13, GRAY, false));

            itemBox.addView(infoBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            Button addButton = button("เพิ่ม", BLUE);
            addButton.setOnClickListener(v -> addToCart(p));
            itemBox.addView(addButton, new LinearLayout.LayoutParams(dp(70), dp(40)));

            productListLayout.addView(itemBox);
            addSpace(productListLayout, 8);
        }
    }

    private void addToCart(ProductRepository.Product product) {
        currentTotal += product.price;
        totalAmountText.setText("ยอดรวม: " + String.format(Locale.US, "%,.2f", currentTotal) + " บาท");

        TextView cartItemText = text("- " + product.name + " (" + String.format(Locale.US, "%,.2f", product.price) + " บาท)", 14, DARK, false);
        cartListLayout.addView(cartItemText);

        Toast.makeText(this, "เพิ่ม " + product.name + " ลงตะกร้าแล้ว", Toast.LENGTH_SHORT).show();
    }

    private void checkoutAction() {
        if (currentTotal <= 0) {
            Toast.makeText(this, "ตะกร้าสินค้าว่างเปล่า", Toast.LENGTH_SHORT).show();
            return;
        }

        String receiptNo = "REC" + System.currentTimeMillis();
        String soldAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new java.util.Date());

        boolean success = salesRepository.saveSale(receiptNo, currentTotal, "cash", soldAt);

        if (success) {
            Toast.makeText(this, "ชำระเงินสำเร็จ บันทึกรายการแล้ว", Toast.LENGTH_LONG).show();

            // จำลองการสั่งพิมพ์ใบเสร็จโดยไม่เรียกคลาสภายนอกให้เกิด Error
            Toast.makeText(this, "กำลังพิมพ์ใบเสร็จ: " + receiptNo, Toast.LENGTH_SHORT).show();

            // รีเซ็ตตะกร้า
            currentTotal = 0.0;
            cartListLayout.removeAllViews();
            totalAmountText.setText("ยอดรวม: 0.00 บาท");
            loadProducts();
        } else {
            Toast.makeText(this, "ไม่สามารถบันทึกการขายได้", Toast.LENGTH_LONG).show();
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }
        return view;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        return b;
    }

    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }

    @Override
    protected void onDestroy() {
        if (productRepository != null) productRepository.close();
        if (salesRepository != null) salesRepository.close();
        super.onDestroy();
    }
}
