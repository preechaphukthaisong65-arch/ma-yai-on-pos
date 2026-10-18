package com.example.newapp;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

/**
 * สร้างภาพบาร์โค้ด Code 128
 */
public final class BarcodeGenerator {

    private BarcodeGenerator() {
        // ไม่ต้องสร้าง object ของคลาสนี้
    }

    public static Bitmap createCode128Bitmap(
            String value,
            int width,
            int height
    ) throws WriterException {

        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "หมายเลขบาร์โค้ดต้องไม่ว่าง"
            );
        }

        if (width < 100 || height < 40) {
            throw new IllegalArgumentException(
                    "ขนาดภาพบาร์โค้ดเล็กเกินไป"
            );
        }

        BitMatrix matrix = new MultiFormatWriter().encode(
                value.trim(),
                BarcodeFormat.CODE_128,
                width,
                height
        );

        int imageWidth = matrix.getWidth();
        int imageHeight = matrix.getHeight();
        int[] pixels = new int[imageWidth * imageHeight];

        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                pixels[y * imageWidth + x] =
                        matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(
                imageWidth,
                imageHeight,
                Bitmap.Config.ARGB_8888
        );

        bitmap.setPixels(
                pixels,
                0,
                imageWidth,
                0,
                0,
                imageWidth,
                imageHeight
        );

        return bitmap;
    }
}
