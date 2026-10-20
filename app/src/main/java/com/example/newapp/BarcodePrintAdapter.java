package com.example.newapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.pdf.PrintedPdfDocument;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

final class BarcodePrintAdapter extends PrintDocumentAdapter {
    private final Context context;
    private final String jobName, barcode, productName;
    private final double price;
    private final int quantity;
    private PrintAttributes attributes;
    private int pageCount;

    BarcodePrintAdapter(Context context, String jobName, String barcode,
            String productName, double price, int quantity) {
        this.context = context.getApplicationContext();
        this.jobName = jobName;
        this.barcode = barcode;
        this.productName = productName;
        this.price = price;
        this.quantity = Math.max(1, Math.min(500, quantity));
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
            CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
        attributes = newAttributes;
        pageCount = (quantity + 23) / 24;
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }
        PrintDocumentInfo info = new PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pageCount)
                .build();
        callback.onLayoutFinished(info, !newAttributes.equals(oldAttributes));
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
            CancellationSignal cancellationSignal, WriteResultCallback callback) {
        PdfDocument document = null;
        try {
            document = new PrintedPdfDocument(context, attributes);
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                if (cancellationSignal.isCanceled()) {
                    callback.onWriteCancelled();
                    return;
                }
                if (!includes(pages, pageIndex)) continue;
                PdfDocument.Page page = document.startPage(pageIndex + 1);
                drawPage(page.getCanvas(), pageIndex);
                document.finishPage(page);
            }
            FileOutputStream output = new FileOutputStream(destination.getFileDescriptor());
            document.writeTo(output);
            output.flush();
            callback.onWriteFinished(new PageRange[]{new PageRange(0, Math.max(0, pageCount - 1))});
        } catch (Exception error) {
            callback.onWriteFailed(error.getMessage() == null ? "สร้างเอกสารพิมพ์ไม่สำเร็จ" : error.getMessage());
        } finally {
            if (document != null) document.close();
            try { destination.close(); } catch (IOException ignored) { }
        }
    }

    private boolean includes(PageRange[] ranges, int page) {
        if (ranges == null || ranges.length == 0) return true;
        for (PageRange range : ranges) if (page >= range.getStart() && page <= range.getEnd()) return true;
        return false;
    }

    private void drawPage(Canvas canvas, int pageIndex) throws Exception {
        int width = canvas.getWidth(), height = canvas.getHeight();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(9f);
        int margin = 18, columns = 3, rows = 8;
        float cellWidth = (width - margin * 2f) / columns;
        float cellHeight = (height - margin * 2f) / rows;
        BitMatrix matrix = new MultiFormatWriter().encode(barcode, BarcodeFormat.CODE_128, 360, 90);
        Bitmap barcodeBitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                barcodeBitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        for (int slot = 0; slot < 24; slot++) {
            int itemIndex = pageIndex * 24 + slot;
            if (itemIndex >= quantity) break;
            int col = slot % columns, row = slot / columns;
            float left = margin + col * cellWidth;
            float top = margin + row * cellHeight;
            canvas.drawRect(left, top, left + cellWidth - 3, top + cellHeight - 3, paint);
            paint.setColor(Color.WHITE);
            canvas.drawRect(left + 1, top + 1, left + cellWidth - 4, top + cellHeight - 4, paint);
            paint.setColor(Color.BLACK);
            paint.setTextSize(8.5f);
            canvas.drawText(productName.length() > 26 ? productName.substring(0, 25) : productName,
                    left + cellWidth / 2f, top + 13, paint);
            Rect src = new Rect(0, 0, barcodeBitmap.getWidth(), barcodeBitmap.getHeight());
            Rect dst = new Rect((int)(left + 8), (int)(top + 17),
                    (int)(left + cellWidth - 11), (int)(top + cellHeight - 24));
            canvas.drawBitmap(barcodeBitmap, src, dst, paint);
            paint.setTextSize(8f);
            canvas.drawText(barcode, left + cellWidth / 2f, top + cellHeight - 13, paint);
            canvas.drawText(String.format(Locale.US, "%.2f บาท", price),
                    left + cellWidth / 2f, top + cellHeight - 3, paint);
        }
        barcodeBitmap.recycle();
    }
}