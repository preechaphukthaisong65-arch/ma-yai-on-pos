package com.example.newapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintJob;
import android.print.PrintManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;

import java.io.FileOutputStream;
import java.util.List;

public class PrinterScreen extends BaseScreen {

    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int DARK = Color.rgb(31, 41, 55);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int GRAY = Color.rgb(107, 114, 128);
    private static final int WHITE = Color.WHITE;

    private TextView connectionStatus;
    private TextView jobsStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createBackground();
        buildScreen();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (connectionStatus != null) {
            refreshStatus();
        }
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(14), dp(16), dp(24));

        scroll.addView(page);

        ViewGroup content = findViewById(android.R.id.content);
        View rootView = content.getChildCount() > 0
                ? content.getChildAt(0)
                : null;

        if (rootView instanceof ViewGroup) {
            ((ViewGroup) rootView).addView(
                    scroll,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        } else {
            content.addView(
                    scroll,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        }

        page.addView(text(
                "ระบบเครื่องพิมพ์",
                25,
                DARK,
                true
        ));

        page.addView(text(
                "ค้นหาเครื่องพิมพ์ผ่านระบบ Android",
                14,
                GRAY,
                false
        ));

        addSpace(page, 18);

        page.addView(text(
                "สถานะระบบพิมพ์",
                18,
                DARK,
                true
        ));

        connectionStatus = text(
                "กำลังตรวจสอบ...",
                15,
                GRAY,
                false
        );

        page.addView(connectionStatus);

        addSpace(page, 12);

        TextView searchButton = button(
                "ค้นหา / เลือกเครื่องพิมพ์",
                BLUE
        );

        page.addView(
                searchButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(50)
                )
        );

        searchButton.setOnClickListener(v -> startPrinterDiscovery());

        addSpace(page, 10);

        TextView refreshButton = button(
                "ตรวจสอบสถานะอีกครั้ง",
                GREEN
        );

        page.addView(
                refreshButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(46)
                )
        );

        refreshButton.setOnClickListener(v -> refreshStatus());

        addSpace(page, 22);

        page.addView(text(
                "สถานะงานพิมพ์ล่าสุด",
                18,
                DARK,
                true
        ));

        jobsStatus = text(
                "ยังไม่มีข้อมูล",
                14,
                GRAY,
                false
        );

        jobsStatus.setGravity(Gravity.TOP);
        page.addView(jobsStatus);

        addSpace(page, 18);

        page.addView(text(
                "หมายเหตุ: หน้าค้นหาเครื่องพิมพ์เป็นของ Android " +
                "เครื่องพิมพ์ที่แสดงขึ้นอยู่กับบริการพิมพ์ที่ติดตั้ง " +
                "และการรองรับของเครื่องพิมพ์แต่ละรุ่น",
                13,
                GRAY,
                false
        ));
    }

    private void startPrinterDiscovery() {
        try {
            PrintManager printManager =
                    (PrintManager) getSystemService(
                            Context.PRINT_SERVICE
                    );

            if (printManager == null) {
                connectionStatus.setText(
                        "อุปกรณ์ไม่สามารถเรียกใช้ระบบพิมพ์ของ Android ได้"
                );
                return;
            }

            PrintAttributes attributes =
                    new PrintAttributes.Builder()
                            .setMediaSize(
                                    PrintAttributes.MediaSize.ISO_A4
                            )
                            .setResolution(
                                    new PrintAttributes.Resolution(
                                            "default",
                                            "Default",
                                            300,
                                            300
                                    )
                            )
                            .setMinMargins(
                                    PrintAttributes.Margins.NO_MARGINS
                            )
                            .build();

            printManager.print(
                    "ทดสอบเครื่องพิมพ์ ร้านแม่ใหญ่อ่อน POS",
                    new TestPrintAdapter(),
                    attributes
            );

            connectionStatus.setText(
                    "เปิดหน้าระบบพิมพ์แล้ว " +
                    "โปรดเลือกเครื่องพิมพ์และตรวจสอบสถานะใน Android"
            );

        } catch (Exception e) {
            connectionStatus.setText(
                    "เปิดระบบพิมพ์ไม่สำเร็จ: " + e.getMessage()
            );
        }
    }

    private void refreshStatus() {
        PrintManager printManager =
                (PrintManager) getSystemService(
                        Context.PRINT_SERVICE
                );

        if (printManager == null) {
            connectionStatus.setText(
                    "ไม่พบระบบพิมพ์ของ Android"
            );
            jobsStatus.setText("ไม่สามารถอ่านสถานะงานพิมพ์ได้");
            return;
        }

        connectionStatus.setText(
                "ระบบพิมพ์ Android พร้อมตรวจสอบ\n" +
                "สถานะการเชื่อมต่อเครื่องจริงต้องตรวจในหน้าระบบพิมพ์"
        );

        try {
            List<PrintJob> jobs = printManager.getPrintJobs();

            if (jobs == null || jobs.isEmpty()) {
                jobsStatus.setText("ยังไม่มีงานพิมพ์ในรายการ");
                return;
            }

            StringBuilder result = new StringBuilder();

            for (int i = jobs.size() - 1; i >= 0; i--) {
                PrintJob job = jobs.get(i);

                result.append(job.getInfo().getLabel())
                        .append("\n");

                if (job.isCompleted()) {
                    result.append("สถานะ: พิมพ์เสร็จแล้ว");
                } else if (job.isFailed()) {
                    result.append("สถานะ: พิมพ์ไม่สำเร็จ");
                } else if (job.isCancelled()) {
                    result.append("สถานะ: ยกเลิก");
                } else if (job.isQueued()) {
                    result.append("สถานะ: รอพิมพ์");
                } else if (job.isStarted()) {
                    result.append("สถานะ: กำลังดำเนินการ");
                } else {
                    result.append("สถานะ: ")
                            .append(job.getInfo().getState());
                }

                result.append("\n\n");
            }

            jobsStatus.setText(result.toString());

        } catch (Exception e) {
            jobsStatus.setText(
                    "ไม่สามารถอ่านรายการงานพิมพ์ได้"
            );
        }
    }

    private class TestPrintAdapter extends PrintDocumentAdapter {

        private final int pageWidth = 595;
        private final int pageHeight = 842;

        @Override
        public void onLayout(
                PrintAttributes oldAttributes,
                PrintAttributes newAttributes,
                CancellationSignal cancellationSignal,
                LayoutResultCallback callback,
                android.os.Bundle extras
        ) {
            if (cancellationSignal.isCanceled()) {
                callback.onLayoutCancelled();
                return;
            }

            PrintDocumentInfo info =
                    new PrintDocumentInfo.Builder(
                            "printer_test.pdf"
                    )
                            .setContentType(
                                    PrintDocumentInfo.CONTENT_TYPE_DOCUMENT
                            )
                            .setPageCount(1)
                            .build();

            callback.onLayoutFinished(info, true);
        }

        @Override
        public void onWrite(
                android.print.PageRange[] pages,
                ParcelFileDescriptor destination,
                CancellationSignal cancellationSignal,
                WriteResultCallback callback
        ) {
            PdfDocument pdf = new PdfDocument();

            try {
                if (cancellationSignal.isCanceled()) {
                    callback.onWriteCancelled();
                    return;
                }

                PdfDocument.PageInfo pageInfo =
                        new PdfDocument.PageInfo.Builder(
                                pageWidth,
                                pageHeight,
                                1
                        ).create();

                PdfDocument.Page page = pdf.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Color.BLACK);
                paint.setTextSize(24f);
                paint.setFakeBoldText(true);

                canvas.drawText(
                        "ร้านแม่ใหญ่อ่อน POS",
                        60,
                        100,
                        paint
                );

                paint.setFakeBoldText(false);
                paint.setTextSize(18f);

                canvas.drawText(
                        "เอกสารทดสอบเครื่องพิมพ์",
                        60,
                        150,
                        paint
                );

                paint.setTextSize(14f);

                canvas.drawText(
                        "หากอ่านข้อความนี้ได้ แสดงว่ามีการพิมพ์เอกสาร",
                        60,
                        195,
                        paint
                );

                canvas.drawText(
                        "โปรดตรวจสอบขนาดกระดาษและคุณภาพการพิมพ์",
                        60,
                        225,
                        paint
                );

                pdf.finishPage(page);

                if (cancellationSignal.isCanceled()) {
                    callback.onWriteCancelled();
                    return;
                }

                FileOutputStream output =
                        new FileOutputStream(
                                destination.getFileDescriptor()
                        );

                pdf.writeTo(output);
                output.flush();

                callback.onWriteFinished(
                        new android.print.PageRange[]{
                                android.print.PageRange.ALL_PAGES
                        }
                );

            } catch (Exception e) {
                callback.onWriteFailed(e.getMessage());

            } finally {
                pdf.close();
            }
        }
    }

    private TextView text(
            String value,
            int size,
            int color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(4), 0, dp(4));

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        return view;
    }

    private TextView button(String value, int color) {
        TextView view = text(value, 15, WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(color);
        return view;
    }

    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);

        parent.addView(
                space,
                new LinearLayout.LayoutParams(
                        1,
                        dp(height)
                )
        );
    }
                 }
