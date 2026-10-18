package com.example.newapp;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

public class BarcodeGenerator {

    public static final long INITIAL_BARCODE = 2000001L;

    /**
     * สร้างภาพ Bitmap ของบาร์โค้ดมาตรฐาน Code 128 จากข้อความหรือตัวเลขบาร์โค้ด
     */
    public static Bitmap generateBarcodeBitmap(String barcodeText, int width, int height) {
        if (barcodeText == null || barcodeText.trim().isEmpty()) {
            return null;
        }

        try {
            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix bitMatrix = writer.encode(
                    barcodeText.trim(),
                    BarcodeFormat.CODE_128,
                    width,
                    height
            );

            int matrixWidth = bitMatrix.getWidth();
            int matrixHeight = bitMatrix.getHeight();
            int[] pixels = new int[matrixWidth * matrixHeight];

            for (int y = 0; y < matrixHeight; y++) {
                int offset = y * matrixWidth;
                for (int x = 0; x < matrixWidth; x++) {
                    pixels[offset + x] = bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.RGB_565);
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight);
            return bitmap;

        } catch (Exception e) {
            return null;
        }
    }
}
