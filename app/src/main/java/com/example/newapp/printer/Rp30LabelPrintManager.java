
package com.example.newapp.printer;

import android.graphics.Bitmap;

import com.example.newapp.PrinterService;
import com.google.zxing.WriterException;

import java.io.IOException;

/**
 * จัดการพิมพ์ฉลากสินค้าให้กับเครื่องพิมพ์ RP30
 *
 * ใช้ PrinterService เดิมและไม่เปิดการเชื่อมต่อเอง
 * ควรเรียกเมธอดพิมพ์จาก Background Thread
 */
public final class Rp30LabelPrintManager {

    private static final int MAX_COPIES = 100;

    private final Rp30PrinterAdapter printerAdapter;

    /**
     * สร้างตัวจัดการพิมพ์ด้วยความกว้างเริ่มต้น
     *
     * 576 จุดเป็นค่าเริ่มต้นทั่วไปของเครื่องพิมพ์ 80 มม.
     * ต้องตรวจสอบความละเอียดจริงของ RP30 ก่อนใช้งาน
     */
    public Rp30LabelPrintManager(
            PrinterService printerService
    ) {
        this(
                printerService,
                Rp30LabelRenderer.DEFAULT_WIDTH_DOTS
        );
    }

    /**
     * สร้างตัวจัดการพิมพ์โดยกำหนดความกว้างเอง
     */
    public Rp30LabelPrintManager(
            PrinterService printerService,
            int widthDots
    ) {
        if (printerService == null) {
            throw new IllegalArgumentException(
                    "ไม่พบบริการเครื่องพิมพ์"
            );
        }

        if (widthDots < 200) {
            throw new IllegalArgumentException(
                    "ความกว้างฉลากต้องไม่น้อยกว่า 200 จุด"
            );
        }

        this.printerAdapter = new Rp30PrinterAdapter(
                printerService,
                widthDots
        );
    }

    /**
     * พิมพ์ฉลากสินค้า
     *
     * @param productName ชื่อสินค้า
     * @param barcode หมายเลขบาร์โค้ด Code 128
     * @param price ราคาขาย
     * @param quantity จำนวนฉลากที่ต้องการพิมพ์ 1-100
     */
    public void printLabels(
            String productName,
            String barcode,
            double price,
            int quantity
    ) throws IOException, WriterException {

        if (quantity < 1 || quantity > MAX_COPIES) {
            throw new IllegalArgumentException(
                    "จำนวนฉลากต้องอยู่ระหว่าง 1 ถึง "
                            + MAX_COPIES + " ดวง"
            );
        }

        Bitmap label = Rp30LabelRenderer.createLabel(
                productName,
                barcode,
                price,
                printerAdapter.getMaxWidthDots()
        );

        try {
            for (int i = 0; i < quantity; i++) {
                printerAdapter.printBitmap(label);
            }
        } finally {
            if (!label.isRecycled()) {
                label.recycle();
            }
        }
    }
}
