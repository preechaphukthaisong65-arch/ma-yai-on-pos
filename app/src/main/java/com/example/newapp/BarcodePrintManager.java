
package com.example.newapp;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * จัดการพิมพ์ฉลากบาร์โค้ดผ่าน PrinterService
 *
 * ใช้เครื่องพิมพ์ Bluetooth SPP ที่เชื่อมต่ออยู่แล้ว
 * รองรับการพิมพ์ซ้ำตามจำนวนที่กำหนด
 *
 * หมายเหตุ:
 * คำสั่ง ESC/POS และความกว้างการพิมพ์ขึ้นอยู่กับรุ่นเครื่องพิมพ์
 */
public final class BarcodePrintManager {

    private static final int MAX_WIDTH = 384;
    private static final int MAX_COPIES = 100;
    private static final Charset CHARSET =
            Charset.forName("UTF-8");

    private final PrinterService printerService;

    public BarcodePrintManager(PrinterService printerService) {
        if (printerService == null) {
            throw new IllegalArgumentException(
                    "ไม่พบบริการเครื่องพิมพ์"
            );
        }

        this.printerService = printerService;
    }

    /**
     * พิมพ์ฉลากบาร์โค้ดตามจำนวนที่ระบุ
     *
     * ควรเรียกจาก Background Thread
     *
     * @param productName ชื่อสินค้า
     * @param barcode หมายเลขบาร์โค้ด
     * @param price ราคาสินค้า
     * @param bitmap ภาพบาร์โค้ดที่สร้างด้วย BarcodeGenerator
     * @param quantity จำนวนฉลาก 1-100
     */
    public void printBarcode(
            String productName,
            String barcode,
            double price,
            Bitmap bitmap,
            int quantity
    ) throws IOException {

        if (!printerService.isConnected()) {
            throw new IOException(
                    "กรุณาเชื่อมต่อเครื่องพิมพ์ก่อน"
            );
        }

        if (productName == null || productName.trim().isEmpty()) {
            throw new IOException("ไม่พบชื่อสินค้า");
        }

        if (barcode == null || barcode.trim().isEmpty()) {
            throw new IOException("ไม่พบหมายเลขบาร์โค้ด");
        }

        if (bitmap == null || bitmap.isRecycled()) {
            throw new IOException("ไม่พบภาพบาร์โค้ด");
        }

        if (!Double.isFinite(price) || price < 0) {
            throw new IOException("ราคาสินค้าไม่ถูกต้อง");
        }

        if (quantity < 1 || quantity > MAX_COPIES) {
            throw new IOException(
                    "จำนวนพิมพ์ต้องอยู่ระหว่าง 1 ถึง "
                            + MAX_COPIES + " ดวง"
            );
        }

        byte[] imageCommand = bitmapToEscPos(bitmap);

        for (int i = 0; i < quantity; i++) {
            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            // เริ่มต้นเครื่องพิมพ์และจัดกึ่งกลาง
            output.write(new byte[]{
                    0x1B, 0x40,
                    0x1B, 0x61, 0x01
            });

            // ชื่อสินค้า
            output.write(
                    (productName.trim() + "\n")
                            .getBytes(CHARSET)
            );

            // ราคา
            String priceText = String.format(
                    Locale.US,
                    "%.2f บาท\n",
                    price
            );

            output.write(priceText.getBytes(CHARSET));

            // ภาพบาร์โค้ด
            output.write(imageCommand);

            // หมายเลขบาร์โค้ดใต้ภาพ
            output.write(
                    ("\n" + barcode.trim() + "\n\n")
                            .getBytes(CHARSET)
            );

            // ส่งฉลากหนึ่งดวงไปยังเครื่องพิมพ์
            printerService.printRaw(output.toByteArray());
        }

        printerService.feed(3);
    }

    /**
     * แปลง Bitmap เป็น ESC/POS GS v 0 raster image
     */
    private byte[] bitmapToEscPos(Bitmap source)
            throws IOException {

        int width = Math.min(source.getWidth(), MAX_WIDTH);

        int height = Math.max(
                1,
                (int) Math.round(
                        source.getHeight()
                                * (width / (double) source.getWidth())
                )
        );

        Bitmap scaled = Bitmap.createScaledBitmap(
                source,
                width,
                height,
                true
        );

        try {
            int widthBytes = (width + 7) / 8;

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            // GS v 0: raster bit image
            output.write(new byte[]{
                    0x1D,
                    0x76,
                    0x30,
                    0x00,
                    (byte) (widthBytes & 0xFF),
                    (byte) ((widthBytes >> 8) & 0xFF),
                    (byte) (height & 0xFF),
                    (byte) ((height >> 8) & 0xFF)
            });

            for (int y = 0; y < height; y++) {
                for (int byteX = 0;
                     byteX < widthBytes;
                     byteX++) {

                    int value = 0;

                    for (int bit = 0; bit < 8; bit++) {
                        int x = byteX * 8 + bit;

                        if (x >= width) {
                            continue;
                        }

                        int pixel = scaled.getPixel(x, y);

                        int gray = (
                                Color.red(pixel)
                                        + Color.green(pixel)
                                        + Color.blue(pixel)
                        ) / 3;

                        if (gray < 160) {
                            value |= (0x80 >> bit);
                        }
                    }

                    output.write(value);
                }
            }

            return output.toByteArray();

        } finally {
            if (scaled != source) {
                scaled.recycle();
            }
        }
    }
}
