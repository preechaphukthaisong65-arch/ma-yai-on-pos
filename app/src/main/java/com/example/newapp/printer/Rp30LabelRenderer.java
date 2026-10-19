package com.example.newapp.printer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import com.example.newapp.BarcodeGenerator;
import com.google.zxing.WriterException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * สร้างภาพฉลากสินค้า ประกอบด้วยชื่อสินค้า ราคา และบาร์โค้ด Code 128
 *
 * ผลลัพธ์เป็น Bitmap สำหรับส่งต่อให้ Rp30PrinterAdapter
 * คลาสนี้ไม่เชื่อมต่อเครื่องพิมพ์และไม่แก้ไขฐานข้อมูล
 */
public final class Rp30LabelRenderer {

    public static final int DEFAULT_WIDTH_DOTS = 576;

    private static final int MIN_WIDTH_DOTS = 200;
    private static final int MARGIN = 16;
    private static final int MAX_NAME_LINES = 3;

    private Rp30LabelRenderer() {
        // Utility class
    }

    /**
     * สร้างฉลากสินค้าเป็น Bitmap
     *
     * @param productName ชื่อสินค้า
     * @param barcode หมายเลขบาร์โค้ด
     * @param price ราคาขาย
     * @return ภาพฉลากพื้นหลังสีขาว
     */
    public static Bitmap createLabel(
            String productName,
            String barcode,
            double price
    ) throws WriterException {

        return createLabel(
                productName,
                barcode,
                price,
                DEFAULT_WIDTH_DOTS
        );
    }

    /**
     * สร้างฉลากโดยกำหนดความกว้างเป็นจุดพิมพ์
     */
    public static Bitmap createLabel(
            String productName,
            String barcode,
            double price,
            int widthDots
    ) throws WriterException {

        if (productName == null || productName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "กรุณาระบุชื่อสินค้า"
            );
        }

        if (barcode == null || barcode.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "กรุณาระบุหมายเลขบาร์โค้ด"
            );
        }

        if (Double.isNaN(price)
                || Double.isInfinite(price)
                || price < 0) {
            throw new IllegalArgumentException(
                    "ราคาสินค้าไม่ถูกต้อง"
            );
        }

        if (widthDots < MIN_WIDTH_DOTS) {
            throw new IllegalArgumentException(
                    "ความกว้างฉลากต้องไม่น้อยกว่า "
                            + MIN_WIDTH_DOTS + " จุด"
            );
        }

        String cleanName = productName.trim();
        String cleanBarcode = barcode.trim();

        Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setColor(Color.BLACK);
        namePaint.setTextSize(25);
        namePaint.setTypeface(Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
        ));

        Paint pricePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pricePaint.setColor(Color.BLACK);
        pricePaint.setTextSize(23);
        pricePaint.setTypeface(Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
        ));

        Paint barcodeTextPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        barcodeTextPaint.setColor(Color.BLACK);
        barcodeTextPaint.setTextSize(20);
        barcodeTextPaint.setTypeface(Typeface.DEFAULT);

        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(1);

        int textWidth = widthDots - (MARGIN * 2);

        List<String> nameLines = wrapText(
                cleanName,
                namePaint,
                textWidth,
                MAX_NAME_LINES
        );

        int nameLineHeight = 31;
        int nameBlockHeight = nameLines.size() * nameLineHeight;

        String priceText = String.format(
                Locale.US,
                "%.2f บาท",
                price
        );

        int barcodeWidth = widthDots - (MARGIN * 2);
        int barcodeHeight = 90;

        Bitmap barcodeBitmap =
                BarcodeGenerator.createCode128Bitmap(
                        cleanBarcode,
                        barcodeWidth,
                        barcodeHeight
                );

        int height = MARGIN
                + nameBlockHeight
                + 12
                + 30
                + 12
                + 1
                + 10
                + barcodeHeight
                + 8
                + 26
                + MARGIN;

        Bitmap label = Bitmap.createBitmap(
                widthDots,
                height,
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(label);
        canvas.drawColor(Color.WHITE);

        int y = MARGIN + 25;

        for (String line : nameLines) {
            drawCenteredText(
                    canvas,
                    line,
                    widthDots,
                    y,
                    namePaint
            );

            y += nameLineHeight;
        }

        y += 5;

        drawCenteredText(
                canvas,
                priceText,
                widthDots,
                y,
                pricePaint
        );

        y += 13;

        canvas.drawLine(
                MARGIN,
                y,
                widthDots - MARGIN,
                y,
                linePaint
        );

        y += 10;

        Rect destination = new Rect(
                MARGIN,
                y,
                widthDots - MARGIN,
                y + barcodeHeight
        );

        canvas.drawBitmap(
                barcodeBitmap,
                null,
                destination,
                null
        );

        y += barcodeHeight + 25;

        drawCenteredText(
                canvas,
                cleanBarcode,
                widthDots,
                y,
                barcodeTextPaint
        );

        barcodeBitmap.recycle();

        return label;
    }

    private static void drawCenteredText(
            Canvas canvas,
            String text,
            int width,
            int baseline,
            Paint paint
    ) {
        float textWidth = paint.measureText(text);
        float x = (width - textWidth) / 2f;

        canvas.drawText(
                text,
                Math.max(0, x),
                baseline,
                paint
        );
    }

    /**
     * ตัดชื่อสินค้าตามความกว้างที่กำหนด
     * รองรับชื่อภาษาไทยที่ไม่มีช่องว่างระหว่างบางคำ
     */
    private static List<String> wrapText(
            String text,
            Paint paint,
            int maxWidth,
            int maxLines
    ) {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            lines.add("-");
            return lines;
        }

        StringBuilder line = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            String candidate = line.toString() + character;

            if (line.length() > 0
                    && paint.measureText(candidate) > maxWidth) {

                lines.add(line.toString());
                line.setLength(0);

                if (lines.size() >= maxLines) {
                    break;
                }
            }

            line.append(character);
        }

        if (lines.size() < maxLines && line.length() > 0) {
            lines.add(line.toString());
        }

        if (lines.isEmpty()) {
            lines.add("-");
        }

        // ป้องกันข้อความยาวเกินพื้นที่ฉลาก
        if (lines.size() == maxLines) {
            String lastLine = lines.get(maxLines - 1);

            if (paint.measureText(lastLine) >= maxWidth) {
                lines.set(
                        maxLines - 1,
                        ellipsize(lastLine, paint, maxWidth)
                );
            }
        }

        return lines;
    }

    private static String ellipsize(
            String text,
            Paint paint,
            int maxWidth
    ) {
        String suffix = "...";
        String result = text;

        while (result.length() > 0
                && paint.measureText(result + suffix) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }

        return result + suffix;
    }
}
