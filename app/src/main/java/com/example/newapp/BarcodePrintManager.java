
package com.example.newapp;

import android.graphics.Bitmap;

import com.example.newapp.printer.Rp30LabelPrintManager;
import com.google.zxing.WriterException;

import java.io.IOException;

/**
 * ตัวจัดการพิมพ์บาร์โค้ดเดิม
 *
 * คงชื่อคลาสและเมธอดเดิมไว้ เพื่อให้หน้าจอเดิมเรียกใช้ได้
 * เปลี่ยนการสร้างงานพิมพ์ให้ใช้ตัวสร้างฉลาก RP30
 */
public final class BarcodePrintManager {

    private static final int MAX_COPIES = 100;

    private final PrinterService printerService;
    private final Rp30LabelPrintManager labelPrintManager;

    public BarcodePrintManager(PrinterService printerService) {
        if (printerService == null) {
            throw new IllegalArgumentException(
                    "ไม่พบบริการเครื่องพิมพ์"
            );
        }

        this.printerService = printerService;
        this.labelPrintManager = new Rp30LabelPrintManager(
                printerService
        );
    }

    /**
     * เมธอดเดิมสำหรับพิมพ์บาร์โค้ด
     *
     * คงพารามิเตอร์ bitmap ไว้เพื่อรองรับหน้าจอเดิม
     * ภาพฉลากใหม่จะสร้างจากชื่อสินค้า ราคา และหมายเลขบาร์โค้ด
     *
     * ควรเรียกจาก Background Thread
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

        if (Double.isNaN(price)
                || Double.isInfinite(price)
                || price < 0) {
            throw new IOException("ราคาสินค้าไม่ถูกต้อง");
        }

        if (quantity < 1 || quantity > MAX_COPIES) {
            throw new IOException(
                    "จำนวนพิมพ์ต้องอยู่ระหว่าง 1 ถึง "
                            + MAX_COPIES + " ดวง"
            );
        }

        try {
            labelPrintManager.printLabels(
                    productName.trim(),
                    barcode.trim(),
                    price,
                    quantity
            );
        } catch (WriterException e) {
            throw new IOException(
                    "สร้างภาพฉลากบาร์โค้ดไม่สำเร็จ",
                    e
            );
        } catch (IllegalArgumentException e) {
            throw new IOException(
                    "ข้อมูลฉลากไม่ถูกต้อง: " + e.getMessage(),
                    e
            );
        }
    }
}
