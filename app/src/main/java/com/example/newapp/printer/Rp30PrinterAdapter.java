package com.example.newapp.printer;

import android.graphics.Bitmap;

import com.example.newapp.PrinterService;

import java.io.IOException;

/**
 * ตัวเชื่อมคำสั่งภาพ ESC/POS กับ PrinterService ที่มีอยู่
 *
 * ไม่เปิดการเชื่อมต่อ Bluetooth เอง
 * ผู้เรียกต้องเชื่อมต่อเครื่องพิมพ์ก่อน
 *
 * เรียก printBitmap() จาก Background Thread
 * เพื่อไม่ให้การส่งข้อมูลพิมพ์ทำให้หน้าจอค้าง
 */
public final class Rp30PrinterAdapter {

    /**
     * ความกว้างเริ่มต้นที่พบบ่อยในเครื่องพิมพ์ 80 มม.
     * ต้องตรวจสอบความละเอียดจริงของ RP30 ก่อนใช้งานจริง
     */
    public static final int DEFAULT_WIDTH_DOTS = 576;

    private static final int DEFAULT_FEED_LINES = 3;

    private final PrinterService printerService;
    private final int maxWidthDots;

    public Rp30PrinterAdapter(PrinterService printerService) {
        this(
                printerService,
                DEFAULT_WIDTH_DOTS
        );
    }

    /**
     * @param printerService บริการ Bluetooth printer ที่มีอยู่
     * @param maxWidthDots ความกว้างภาพสูงสุด หน่วย dot
     */
    public Rp30PrinterAdapter(
            PrinterService printerService,
            int maxWidthDots
    ) {
        if (printerService == null) {
            throw new IllegalArgumentException(
                    "ไม่พบบริการเครื่องพิมพ์"
            );
        }

        if (maxWidthDots < 8) {
            throw new IllegalArgumentException(
                    "ความกว้างภาพต้องไม่น้อยกว่า 8 จุด"
            );
        }

        this.printerService = printerService;
        this.maxWidthDots = maxWidthDots;
    }

    /**
     * พิมพ์ Bitmap แบบขาวดำและจัดกึ่งกลาง
     *
     * ภาพอาจเป็นโลโก้ ฉลาก หรือบาร์โค้ดที่สร้างไว้แล้ว
     * ไม่ได้สร้างภาพบาร์โค้ดใหม่ในเมธอดนี้
     *
     * @param bitmap ภาพที่จะพิมพ์
     */
    public void printBitmap(Bitmap bitmap)
            throws IOException {

        if (!printerService.isConnected()) {
            throw new IOException(
                    "กรุณาเชื่อมต่อเครื่องพิมพ์ก่อน"
            );
        }

        if (bitmap == null || bitmap.isRecycled()) {
            throw new IOException(
                    "ไม่พบภาพสำหรับพิมพ์"
            );
        }

        byte[] rasterCommand =
                Rp30EscPosEncoder.bitmapToRaster(
                        bitmap,
                        maxWidthDots
                );

        byte[] command = Rp30EscPosEncoder.combine(
                Rp30EscPosEncoder.initialize(),
                Rp30EscPosEncoder.align(1),
                rasterCommand,
                Rp30EscPosEncoder.feedLines(
                        DEFAULT_FEED_LINES
                )
        );

        printerService.printRaw(command);
    }

    /**
     * พิมพ์ Bitmap โดยกำหนดตำแหน่งจัดแนว
     *
     * 0 = ซ้าย
     * 1 = กึ่งกลาง
     * 2 = ขวา
     */
    public void printBitmap(
            Bitmap bitmap,
            int alignment
    ) throws IOException {

        if (!printerService.isConnected()) {
            throw new IOException(
                    "กรุณาเชื่อมต่อเครื่องพิมพ์ก่อน"
            );
        }

        if (bitmap == null || bitmap.isRecycled()) {
            throw new IOException(
                    "ไม่พบภาพสำหรับพิมพ์"
            );
        }

        byte[] rasterCommand =
                Rp30EscPosEncoder.bitmapToRaster(
                        bitmap,
                        maxWidthDots
                );

        byte[] command = Rp30EscPosEncoder.combine(
                Rp30EscPosEncoder.initialize(),
                Rp30EscPosEncoder.align(alignment),
                rasterCommand,
                Rp30EscPosEncoder.feedLines(
                        DEFAULT_FEED_LINES
                )
        );

        printerService.printRaw(command);
    }

    /**
     * คืนค่าความกว้างสูงสุดที่ตั้งไว้
     */
    public int getMaxWidthDots() {
        return maxWidthDots;
    }
}
