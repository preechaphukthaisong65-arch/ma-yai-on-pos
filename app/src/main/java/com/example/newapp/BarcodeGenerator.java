package com.example.newapp;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BarcodeGenerator";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // สร้าง Layout สำหรับแสดงผลตัวอย่าง (หรือจะใช้ setContentView(R.layout.activity_main) แทนก็ได้)
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        try {
            // 1. เรียกใช้งาน BarcodeGenerator เพื่อสร้างภาพบาร์โค้ด
            Bitmap barcodeImage = BarcodeGenerator.createCode128Bitmap(
                    "2000001",
                    600,
                    180
            );

            // 2. สร้าง ImageView เพื่อแสดงรูปภาพบาร์โค้ด
            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(barcodeImage);
            
            // ตั้งค่าขนาดและรูปแบบการแสดงผลของ ImageView ตามต้องการ
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    200
            );
            imageView.setLayoutParams(params);

            // 3. นำ ImageView ใส่เข้าไปในหน้าจอ
            layout.addView(imageView);

        } catch (WriterException e) {
            Log.e(TAG, "สร้างภาพบาร์โค้ดไม่สำเร็จ", e);
        }

        setContentView(layout);
    }
}

/**
 * คลาสสำหรับสร้างบาร์โค้ด Code 128 ด้วย ZXing
 */
final class BarcodeGenerator {

    private BarcodeGenerator() {
        // Utility class
    }

    public static Bitmap createCode128Bitmap(
            String value,
            int width,
            int height
    ) throws WriterException {

        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("หมายเลขบาร์โค้ดต้องไม่ว่าง");
        }

        if (width < 100 || height < 40) {
            throw new IllegalArgumentException("ขนาดภาพบาร์โค้ดเล็กเกินไป");
        }

        BitMatrix matrix = new MultiFormatWriter().encode(
                value.trim(),
                BarcodeFormat.CODE_128,
                width,
                height
        );

        int matrixWidth = matrix.getWidth();
        int matrixHeight = matrix.getHeight();

        int[] pixels = new int[matrixWidth * matrixHeight];

        for (int y = 0; y < matrixHeight; y++) {
            int offset = y * matrixWidth;

            for (int x = 0; x < matrixWidth; x++) {
                pixels[offset + x] = matrix.get(x, y)
                        ? Color.BLACK
                        : Color.WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(
                matrixWidth,
                matrixHeight,
                Bitmap.Config.ARGB_8888
        );

        bitmap.setPixels(
                pixels,
                0,
                matrixWidth,
                0,
                0,
                matrixWidth,
                matrixHeight
        );

        return bitmap;
    }
}
