package com.example.newapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintJob;
import android.print.PrintManager;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class PrinterScreen extends BaseScreen {

    private LinearLayout root;
    private TextView connectionStatus;
    private TextView printerInfo;
    private TextView jobStatus;

    private PrintManager printManager;

    private final int BLUE = 0xFF2563EB;
    private final int DARK = 0xFF182230;
    private final int BACKGROUND = 0xFFF4F7FB;
    private final int GREEN = 0xFF159B72;
    private final int RED = 0xFFE05260;
    private final int GRAY = 0xFF667085;
    private final int WHITE = 0xFFFFFFFF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        printManager = (PrintManager) getSystemService(
                Context.PRINT_SERVICE
        );

        buildScreen();
        refreshPrintStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (printManager != null) {
            refreshPrintStatus();
        }
    }

    private void buildScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BACKGROUND);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(24));
        root.setBackgroundColor(BACKGROUND);

        scrollView.addView(root);
        setContentView(scrollView);

        TextView title = new TextView(this);
        title.setText("ระบบเครื่องพิมพ์");
        title.setTextSize(25);
        title.setTextColor(DARK);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(
                "ค้นหาเครื่องพิมพ์ ตรวจสอบสถานะ และทดลองพิมพ์"
        );
        subtitle.setTextSize(14);
        subtitle.setTextColor(GRAY);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(6);
        subtitleParams.bottomMargin = dp(18);

        root.addView(subtitle, subtitleParams);

        addStatusCard();
        addPrinterCard();
        addPrintJobCard();
        addInformationCard();

        Button backButton = createButton("กลับ", DARK);
        backButton.setOnClickListener(v -> finish());

        LinearLayout.LayoutParams backParams =
                new LinearLayout.LayoutParams(-1, dp(50));
        backParams.topMargin = dp(18);

        root.addView(backButton, backParams);
    }

    private void addStatusCard() {
        LinearLayout card = createCard();

        card.addView(createText(
                "สถานะระบบพิมพ์",
                18,
                DARK,
                true
        ));

        connectionStatus = createText(
                "กำลังตรวจสอบระบบ...",
                15,
                GRAY,
                false
        );

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(12);

        card.addView(connectionStatus, statusParams);

        TextView note = createText(
                "สถานะนี้ตรวจสอบระบบพิมพ์ของ Android " +
                "ไม่ได้ยืนยันว่าเครื่องพิมพ์จริงเชื่อมต่ออยู่",
                13,
                GRAY,
                false
        );

        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(8);

        card.addView(note, noteParams);
    }

    private void addPrinterCard() {
        LinearLayout card = createCard();

        card.addView(createText(
                "ค้นหาและเลือกเครื่องพิมพ์",
                18,
                DARK,
                true
        ));

        printerInfo = createText(
                "ใช้หน้าต่างพิมพ์ของ Android เพื่อเลือกเครื่องพิมพ์",
                14,
                GRAY,
                false
        );

        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(-1, -2);
        infoParams.topMargin = dp(10);

        card.addView(printerInfo, infoParams);

        Button searchButton = createButton(
                "ค้นหา / เลือกเครื่องพิมพ์",
                BLUE
        );

        LinearLayout.LayoutParams searchParams =
                new LinearLayout.LayoutParams(-1, dp(52));
        searchParams.topMargin = dp(16);

        card.addView(searchButton, searchParams);
        searchButton.setOnClickListener(v -> openPrintDialog());

        Button refreshButton = createButton(
                "ตรวจสอบสถานะอีกครั้ง",
                GREEN
        );

        LinearLayout.LayoutParams refreshParams =
                new LinearLayout.LayoutParams(-1, dp(48));
        refreshParams.topMargin = dp(10);

        card.addView(refreshButton, refreshParams);

        refreshButton.setOnClickListener(v -> {
            refreshPrintStatus();

            Toast.makeText(
                    PrinterScreen.this,
                    "อัปเดตสถานะแล้ว",
                    Toast.LENGTH_SHORT
            ).show();
        });
    }

    private void addPrintJobCard() {
        LinearLayout card = createCard();

        card.addView(createText(
                "สถานะงานพิมพ์",
                18,
                DARK,
                true
        ));

        jobStatus = createText(
                "ยังไม่มีข้อมูลการพิมพ์",
                14,
                GRAY,
                false
        );

        LinearLayout.LayoutParams jobParams =
                new LinearLayout.LayoutParams(-1, -2);
        jobParams.topMargin = dp(12);

        card.addView(jobStatus, jobParams);

        Button refreshJobsButton = createButton(
                "รีเฟรชสถานะงานพิมพ์",
                BLUE
        );

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(-1, dp(48));
        buttonParams.topMargin = dp(14);

        card.addView(refreshJobsButton, buttonParams);

        refreshJobsButton.setOnClickListener(
                v -> refreshPrintStatus()
        );
    }

    private void addInformationCard() {
        LinearLayout card = createCard();

        card.addView(createText(
                "ข้อมูลการรองรับ",
                18,
                DARK,
                true
        ));

        TextView info = createText(
                "• ใช้ระบบพิมพ์มาตรฐานของ Android\n\n" +
                "• เครื่องพิมพ์ที่ค้นพบขึ้นอยู่กับบริการพิมพ์ " +
                "และการเชื่อมต่อที่ติดตั้งในโทรศัพท์\n\n" +
                "• เครื่องพิมพ์ Bluetooth บางรุ่นอาจต้องใช้ " +
                "ไดรเวอร์หรือคำสั่งเฉพาะของผู้ผลิต\n\n" +
                "• ปุ่มทดลองพิมพ์จะสร้างเอกสารทดสอบ PDF\n\n" +
                "• สถานะงานพิมพ์ไม่ใช่การยืนยันว่ากระดาษ " +
                "ออกจากเครื่องจริงแล้ว",
                14,
                GRAY,
                false
        );

        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(-1, -2);
        infoParams.topMargin = dp(12);

        card.addView(info, infoParams);
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundColor(WHITE);
        card.setElevation(dp(2));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(14);

        root.addView(card, params);

        return card;
    }

    private TextView createText(
            String text,
            float size,
            int color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        return view;
    }

    private Button createButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(color)
        );

        return button;
    }

    private void openPrintDialog() {
        if (printManager == null) {
            Toast.makeText(
                    this,
                    "ไม่พบระบบพิมพ์ของ Android",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        try {
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

            printerInfo.setText(
                    "เปิดหน้าต่างพิมพ์แล้ว " +
                    "เลือกเครื่องพิมพ์ที่ระบบ Android แสดง"
            );

            refreshPrintStatus();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "เปิดระบบพิมพ์ไม่สำเร็จ: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void refreshPrintStatus() {
        if (printManager == null) {
            if (connectionStatus != null) {
                connectionStatus.setText(
                        "ไม่พบระบบพิมพ์ของ Android"
                );
                connectionStatus.setTextColor(RED);
            }
            return;
        }

        if (connectionStatus != null) {
            connectionStatus.setText(
                    "ระบบพิมพ์ Android พร้อมให้เรียกใช้งาน"
            );
            connectionStatus.setTextColor(GREEN);
        }

        if (jobStatus == null) {
            return;
        }

        try {
            List<PrintJob> jobs = printManager.getPrintJobs();

            if (jobs == null || jobs.isEmpty()) {
                jobStatus.setText(
                        "ยังไม่พบงานพิมพ์ในรายการล่าสุด"
                );
                return;
            }

            StringBuilder result = new StringBuilder();
            int count = Math.min(jobs.size(), 10);

            for (int i = 0; i < count; i++) {
                PrintJob job = jobs.get(i);

                if (job == null || job.getInfo() == null) {
                    continue;
                }

                String label = job.getInfo().getLabel();
                String state = getJobState(job);

                result.append("• ")
                        .append(label)
                        .append("\n  สถานะ: ")
                        .append(state)
                        .append("\n\n");
            }

            if (result.length() == 0) {
                jobStatus.setText(
                        "ไม่มีข้อมูลงานพิมพ์ที่แสดงได้"
                );
            } else {
                jobStatus.setText(result.toString());
            }

        } catch (Exception e) {
            jobStatus.setText(
                    "อ่านสถานะงานพิมพ์ไม่สำเร็จ: "
                    + e.getMessage()
            );
        }
    }

    private String getJobState(PrintJob job) {
        if (job.isCompleted()) {
            return "เสร็จสิ้นตามสถานะของระบบ";
        }

        if (job.isFailed()) {
            return "ล้มเหลว";
        }

        if (job.isCancelled()) {
            return "ยกเลิก";
        }

        if (job.isQueued()) {
            return "อยู่ในคิว";
        }

        if (job.isStarted()) {
            return "กำลังดำเนินการ";
        }

        return "กำลังตรวจสอบ";
    }

    private class TestPrintAdapter extends PrintDocumentAdapter {

        private PdfDocument pdfDocument;

        @Override
        public void onLayout(
                PrintAttributes oldAttributes,
                PrintAttributes newAttributes,
                CancellationSignal cancellationSignal,
                LayoutResultCallback callback,
                Bundle extras
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
                PageRange[] pages,
                ParcelFileDescriptor destination,
                CancellationSignal cancellationSignal,
                WriteResultCallback callback
        ) {
            FileOutputStream outputStream = null;

            try {
                if (cancellationSignal.isCanceled()) {
                    callback.onWriteCancelled();
                    return;
                }

                pdfDocument = new PdfDocument();

                PdfDocument.PageInfo pageInfo =
                        new PdfDocument.PageInfo.Builder(
                                595,
                                842,
                                1
                        ).create();

                PdfDocument.Page page =
                        pdfDocument.startPage(pageInfo);

                Canvas canvas = page.getCanvas();

                Paint paint = new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

                paint.setColor(DARK);
                paint.setTextSize(25);
                paint.setTypeface(
                        Typeface.create(
                                Typeface.DEFAULT,
                                Typeface.BOLD
                        )
                );

                canvas.drawText(
                        "ร้านแม่ใหญ่อ่อน POS",
                        50,
                        100,
                        paint
                );

                paint.setTypeface(Typeface.DEFAULT);
                paint.setTextSize(18);

                canvas.drawText(
                        "เอกสารทดสอบเครื่องพิมพ์",
                        50,
                        150,
                        paint
                );

                paint.setTextSize(14);

                canvas.drawText(
                        "หากอ่านข้อความนี้ได้ แสดงว่าเอกสารถูกสร้างแล้ว",
                        50,
                        190,
                        paint
                );

                canvas.drawText(
                        "กรุณาตรวจสอบผลการพิมพ์จากเครื่องจริง",
                        50,
                        220,
                        paint
                );

                pdfDocument.finishPage(page);

                if (cancellationSignal.isCanceled()) {
                    callback.onWriteCancelled();
                    return;
                }

                outputStream = new FileOutputStream(
                        destination.getFileDescriptor()
                );

                pdfDocument.writeTo(outputStream);
                outputStream.flush();

                callback.onWriteFinished(
                        new PageRange[]{
                                PageRange.ALL_PAGES
                        }
                );

            } catch (IOException | RuntimeException e) {
                callback.onWriteFailed(
                        e.getMessage() == null
                                ? "สร้างเอกสารทดสอบไม่สำเร็จ"
                                : e.getMessage()
                );

            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException ignored) {
                    }
                }

                if (pdfDocument != null) {
                    pdfDocument.close();
                    pdfDocument = null;
                }

                try {
                    destination.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
    }
