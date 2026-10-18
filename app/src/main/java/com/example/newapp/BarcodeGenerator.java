
package com.example.newapp;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.MultiFormatWriter;

public final class BarcodeGenerator {

    private BarcodeGenerator() {
        // Utility class
    }

    /**
     * สร้างภาพบาร์โค้ด Code 128
     *
     * @param value ข้อมูลบาร์โค้ด
     * @param width ความกว้างภาพเป็นพิกเซล
     * @param height ความสูงภาพเป็นพิกเซล
     */
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
