
package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.WriterException;

public class BarcodeScreen extends Activity {

    private EditText nameInput;
    private EditText priceInput;
    private TextView barcodeText;
    private ImageView barcodeImage;

    private LocalDatabase database;
    private ProductRepository productRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        database = LocalDatabase.getInstance(this);
        productRepository = new ProductRepository(database);

        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("ระบบบาร์โค้ด");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(45, 90, 55));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("ชื่อสินค้า");
        root.addView(nameInput);

        priceInput = new EditText(this);
        priceInput.setSingleLine(true);
        priceInput.setHint("ราคาขาย");
        priceInput.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        root.addView(priceInput);

        Button createButton = new Button(this);
        createButton.setText("สร้างบาร์โค้ด");
        root.addView(createButton);

        barcodeText = new TextView(this);
        barcodeText.setTextSize(18);
        barcodeText.setTextColor(Color.BLACK);
        barcodeText.setGravity(Gravity.CENTER);
        root.addView(barcodeText);

        barcodeImage = new ImageView(this);
        barcodeImage.setAdjustViewBounds(true);
        barcodeImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(
                barcodeImage,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        220
                )
        );

        createButton.setOnClickListener(v -> createBarcode());

        scroll.addView(root);
        setContentView(scroll);
    }

    private void createBarcode() {
        String name = nameInput.getText().toString().trim();
        String priceText = priceInput.getText().toString().trim();

        if (name.isEmpty() || priceText.isEmpty()) {
            Toast.makeText(
                    this,
                    "กรุณากรอกชื่อสินค้าและราคา",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        final double price;

        try {
            price = Double.parseDouble(priceText);
        } catch (NumberFormatException e) {
            Toast.makeText(
                    this,
                    "รูปแบบราคาไม่ถูกต้อง",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (price < 0 || Double.isNaN(price)
                || Double.isInfinite(price)) {
            Toast.makeText(
                    this,
                    "ราคาต้องไม่ติดลบ",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // จองเลขจากฐานข้อมูลกลางก่อนบันทึกสินค้า
        String barcode = database.reserveNextBarcode();

        if (barcode == null || barcode.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "จองหมายเลขบาร์โค้ดไม่สำเร็จ",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        boolean created = productRepository.createProduct(
                barcode,
                name,
                price,
                0.0,
                "ชิ้น",
                0
        );

        if (!created) {
            Toast.makeText(
                    this,
                    "บันทึกสินค้าไม่สำเร็จ กรุณาตรวจสอบบาร์โค้ดซ้ำ",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        try {
            Bitmap bitmap = BarcodeGenerator.createCode128Bitmap(
                    barcode,
                    700,
                    200
            );

            barcodeText.setText(
                    "สินค้า: " + name + "\n"
                            + "ราคา: " + price + " บาท\n"
                            + "บาร์โค้ด: " + barcode
            );

            barcodeImage.setImageBitmap(bitmap);

        } catch (WriterException | IllegalArgumentException e) {
            barcodeText.setText(
                    "บันทึกสินค้าแล้ว แต่สร้างภาพบาร์โค้ดไม่สำเร็จ\n"
                            + barcode
            );

            Toast.makeText(
                    this,
                    "สร้างภาพบาร์โค้ดไม่สำเร็จ",
                    Toast.LENGTH_LONG
            ).show();
        }
    }
}
