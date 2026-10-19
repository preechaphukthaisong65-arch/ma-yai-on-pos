package com.example.newapp.printer;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * เครื่องมือสร้างคำสั่ง ESC/POS สำหรับงานพิมพ์ภาพ
 *
 * โมดูลนี้ไม่เชื่อมต่อเครื่องพิมพ์และไม่ส่งข้อมูลผ่าน Bluetooth/LAN
 * รองรับการสร้างคำสั่งภาพแบบ GS v 0 raster image
 *
 * ความกว้างสูงสุดต้องกำหนดตามความละเอียดที่เครื่องพิมพ์รองรับ
 * ยังต้องตรวจสอบคำสั่งกับเครื่องพิมพ์ RP30 รุ่นจริงก่อนใช้งาน
 */
public final class Rp30EscPosEncoder {

    private static final int MAX_RASTER_HEIGHT = 65535;
    private static final int BLACK_THRESHOLD = 160;

    private Rp30EscPosEncoder() {
        // Utility class
    }

    /**
     * สร้างคำสั่งเริ่มต้น ESC/POS
     */
    public static byte[] initialize() {
        return new byte[]{
                0x1B, 0x40
        };
    }

    /**
     * สร้างคำสั่งจัดแนวข้อความหรือภาพ
     *
     * 0 = ซ้าย
     * 1 = กึ่งกลาง
     * 2 = ขวา
     */
    public static byte[] align(int alignment) {
        if (alignment < 0 || alignment > 2) {
            throw new IllegalArgumentException(
                    "ค่าการจัดแนวต้องเป็น 0, 1 หรือ 2"
            );
        }

        return new byte[]{
                0x1B,
                0x61,
                (byte) alignment
        };
    }

    /**
     * สร้างคำสั่งขึ้นบรรทัดใหม่หรือเลื่อนกระดาษ
     *
     * @param lines จำนวนบรรทัด 0-255
     */
    public static byte[] feedLines(int lines) {
        if (lines < 0 || lines > 255) {
            throw new IllegalArgumentException(
                    "จำนวนบรรทัดต้องอยู่ระหว่าง 0 ถึง 255"
            );
        }

        return new byte[]{
                0x1B,
                0x64,
                (byte) lines
        };
    }

    /**
     * แปลง Bitmap เป็นคำสั่งภาพ ESC/POS GS v 0
     *
     * ภาพจะถูกย่อเมื่อกว้างเกิน maxWidthDots
     * ไม่ขยายภาพให้ใหญ่ขึ้นหากภาพมีขนาดเล็กกว่า
     *
     * @param source ภาพต้นฉบับที่ยังใช้งานได้
     * @param maxWidthDots ความกว้างสูงสุดตามความละเอียดที่รองรับ
     */
    public static byte[] bitmapToRaster(
            Bitmap source,
            int maxWidthDots
    ) throws IOException {

        if (source == null || source.isRecycled()) {
            throw new IOException(
                    "ไม่พบภาพหรือภาพถูกปิดใช้งานแล้ว"
            );
        }

        if (maxWidthDots < 8) {
            throw new IllegalArgumentException(
                    "ความกว้างภาพต้องไม่น้อยกว่า 8 จุด"
            );
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IOException(
                    "ขนาดภาพไม่ถูกต้อง"
            );
        }

        int targetWidth = Math.min(
                sourceWidth,
                maxWidthDots
        );

        int targetHeight = Math.max(
                1,
                (int) Math.round(
                        sourceHeight
                                * (targetWidth / (double) sourceWidth)
                )
        );

        if (targetHeight > MAX_RASTER_HEIGHT) {
            throw new IOException(
                    "ภาพสูงเกินขนาดที่คำสั่ง raster รองรับ"
            );
        }

        Bitmap scaled = source;

        if (targetWidth != sourceWidth) {
            scaled = Bitmap.createScaledBitmap(
                    source,
                    targetWidth,
                    targetHeight,
                    true
            );
        }

        try {
            return encodeRaster(scaled);

        } finally {
            if (scaled != source && !scaled.isRecycled()) {
                scaled.recycle();
            }
        }
    }

    /**
     * เข้ารหัสภาพเป็นคำสั่ง GS v 0
     */
    private static byte[] encodeRaster(
            Bitmap bitmap
    ) throws IOException {

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= 0 || height <= 0
                || height > MAX_RASTER_HEIGHT) {
            throw new IOException(
                    "ขนาดภาพไม่รองรับการพิมพ์"
            );
        }

        int widthBytes = (width + 7) / 8;

        if (widthBytes > 65535) {
            throw new IOException(
                    "ความกว้างภาพเกินขนาดคำสั่ง raster"
            );
        }

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        // GS v 0: normal raster image
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

                int packed = 0;

                for (int bit = 0; bit < 8; bit++) {
                    int x = byteX * 8 + bit;

                    if (x >= width) {
                        continue;
                    }

                    int pixel = bitmap.getPixel(x, y);

                    int alpha = Color.alpha(pixel);
                    int red = Color.red(pixel);
                    int green = Color.green(pixel);
                    int blue = Color.blue(pixel);

                    // พื้นหลังโปร่งใสถือเป็นสีขาว
                    int gray;

                    if (alpha < 128) {
                        gray = 255;
                    } else {
                        gray = (
                                red + green + blue
                        ) / 3;
                    }

                    if (gray < BLACK_THRESHOLD) {
                        packed |= 0x80 >> bit;
                    }
                }

                output.write(packed);
            }
        }

        return output.toByteArray();
    }

    /**
     * รวม byte arrays ตามลำดับที่ระบุ
     * ใช้ประกอบคำสั่งเริ่มต้น จัดแนว ภาพ และเลื่อนกระดาษ
     */
    public static byte[] combine(byte[]... commands)
            throws IOException {

        if (commands == null) {
            throw new IllegalArgumentException(
                    "รายการคำสั่งต้องไม่เป็น null"
            );
        }

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        for (byte[] command : commands) {
            if (command == null) {
                throw new IllegalArgumentException(
                        "พบคำสั่งที่เป็น null"
                );
            }

            output.write(command);
        }

        return output.toByteArray();
    }
}
