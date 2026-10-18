package com.example.newapp;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class BarcodeScreen extends BaseScreen {

    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int DARK = Color.rgb(31, 41, 55);
    private static final int GRAY = Color.rgb(107, 114, 128);
    private static final int WHITE = Color.WHITE;

    private EditText nameInput;
    private EditText priceInput;
    private LinearLayout resultLayout;
    private ImageView barcodeImageView;
    private TextView infoText;

    private ProductRepository productRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createBackground();
        productRepository = new ProductRepository(this);

        buildScreen();
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

        page.addView(text("สร้างบาร์โค้ดสินค้า", 25, DARK, true));
        page.addView(text("กรอกชื่อและราคาเพื่อสร้างบาร์โค้ดอัตโนมัติ", 14, GRAY, false));

        addSpace(page, 18);

        page.addView(text("ชื่อสินค้า", 15, DARK, true));
        addSpace(page, 4);

        nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("เช่น น้ำดื่ม");
        nameInput.setTextSize(16);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        nameInput.setBackgroundColor(WHITE);
        page.addView(nameInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        addSpace(page, 14);

        page.addView(text("ราคาสินค้า (บาท)", 15, DARK, true));
        addSpace(page, 4);

        priceInput = new EditText(this);
        priceInput.setSingleLine(true);
        priceInput.setHint("0.00");
        priceInput.setTextSize(16);
        priceInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        priceInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        priceInput.setBackgroundColor(WHITE);
        page.addView(priceInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        addSpace(page, 20);

        Button generateButton = button("สร้างบาร์โค้ด", BLUE);
        page.addView(generateButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        generateButton.setOnClickListener(v -> generateBarcodeAction());

        addSpace(page, 20);

        resultLayout = new LinearLayout(this);
        resultLayout.setOrientation(LinearLayout.VERTICAL);
        resultLayout.setPadding(dp(16), dp(16), dp(16), dp(16));
        resultLayout.setBackgroundColor(WHITE);
        resultLayout.setVisibility(View.GONE);

        infoText = text("", 15, DARK, true);
        resultLayout.addView(infoText);

        addSpace(resultLayout, 10);

        barcodeImageView = new ImageView(this);
        barcodeImageView.setAdjustViewBounds(true);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(120)
        );
        imgParams.gravity = Gravity.CENTER;
        resultLayout.addView(barcodeImageView, imgParams);

        page.addView(resultLayout);
    }

    private void generateBarcodeAction() {
        String nameStr = nameInput.getText().toString().trim();
        String priceStr = priceInput.getText().toString().trim();

        if (nameStr.isEmpty()) {
            Toast.makeText(this, "กรุณากรอกชื่อสินค้า", Toast.LENGTH_SHORT).show();
            return;
        }

        if (priceStr.isEmpty()) {
            Toast.makeText(this, "กรุณากรอกราคาสินค้า", Toast.LENGTH_SHORT).show();
            return;
        }

        double priceVal;
        try {
            priceVal = Double.parseDouble(priceStr);
            if (priceVal < 0) {
                throw new NumberFormatException();
            }
        } catch (Exception e) {
            Toast.makeText(this, "ราคาสินค้าไม่ถูกต้อง", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // ใช้ ProductRepository เพื่อบันทึกสินค้าและรันหมายเลขบาร์โค้ดอัตโนมัติไม่ให้ซ้ำ
            ProductRepository.Product newProduct = productRepository.addProduct(
                    nameStr,
                    priceVal,
                    0,
                    0,
                    5,
                    null,
                    "ชิ้น"
            );

            if (newProduct != null && newProduct.barcode != null) {
                Bitmap bitmap = BarcodeGenerator.generateBarcodeBitmap(newProduct.barcode, 600, 200);

                if (bitmap != null) {
                    barcodeImageView.setImageBitmap(bitmap);
                    infoText.setText(
                            "ชื่อสินค้า: " + newProduct.name + "\n" +
                            "ราคา: " + String.format(Locale.US, "%,.2f", newProduct.price) + " บาท\n" +
                            "หมายเลขบาร์โค้ด: " + newProduct.barcode
                    );
                    resultLayout.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "สร้างบาร์โค้ดสำเร็จ", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "ไม่สามารถสร้างภาพบาร์โค้ดได้", Toast.LENGTH_SHORT).show();
                }
            }

        } catch (Exception e) {
            Toast.makeText(this, "เกิดข้อผิดพลาด: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        b.setTextSize(16);
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
        if (productRepository != null) {
            productRepository.close();
        }
        super.onDestroy();
    }
}
