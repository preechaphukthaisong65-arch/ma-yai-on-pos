package com.example.newapp;

import android.content.Context;
import android.os.Build;
import android.print.PrintAttributes;
import android.print.PrintJob;
import android.print.PrintJobInfo;
import android.print.PrintManager;
import android.print.PrintDocumentAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * PrinterManager
 *
 * โมดูลกลางสำหรับจัดการงานพิมพ์ของร้านแม่ใหญ่อ่อน POS
 *
 * รองรับ:
 * - Android Print Framework
 * - เปิดหน้าต่างเลือกเครื่องพิมพ์ของ Android
 * - ส่ง PrintDocumentAdapter เข้าระบบพิมพ์
 * - ตรวจสอบสถานะงานพิมพ์
 * - แสดงรายการงานพิมพ์ล่าสุด
 *
 * หมายเหตุ:
 * - สถานะงานพิมพ์ไม่ใช่หลักฐานว่ากระดาษออกจากเครื่องแล้ว
 * - การค้นหาเครื่อง Bluetooth โดยตรงต้องใช้โมดูลเพิ่มเติม
 * - เครื่องพิมพ์บางรุ่นต้องใช้แอปหรือ SDK จากผู้ผลิต
 */
public final class PrinterManager {

    private PrinterManager() {
        // ป้องกันการสร้าง instance
    }

    /**
     * ผลลัพธ์สถานะระบบพิมพ์
     */
    public static final class PrinterStatus {

        public final boolean frameworkAvailable;
        public final int totalJobs;
        public final int queuedJobs;
        public final int startedJobs;
        public final int completedJobs;
        public final int failedJobs;
        public final int canceledJobs;
        public final String message;

        private PrinterStatus(
                boolean frameworkAvailable,
                int totalJobs,
                int queuedJobs,
                int startedJobs,
                int completedJobs,
                int failedJobs,
                int canceledJobs,
                String message
        ) {
            this.frameworkAvailable = frameworkAvailable;
            this.totalJobs = totalJobs;
            this.queuedJobs = queuedJobs;
            this.startedJobs = startedJobs;
            this.completedJobs = completedJobs;
            this.failedJobs = failedJobs;
            this.canceledJobs = canceledJobs;
            this.message = message;
        }
    }

    /**
     * ตรวจสอบว่า Android Print Framework ใช้งานได้หรือไม่
     */
    public static boolean isAvailable(Context context) {
        if (context == null) {
            return false;
        }

        try {
            Object service = context.getSystemService(
                    Context.PRINT_SERVICE
            );

            return service instanceof PrintManager;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ส่งงานพิมพ์เข้าสู่ระบบของ Android
     *
     * adapter คือ PrintDocumentAdapter ที่สร้างเอกสาร
     * ส่วน attributes ใช้กำหนดกระดาษและคุณสมบัติการพิมพ์
     *
     * คืนค่า PrintJob เมื่อส่งคำขอเข้าสู่ระบบได้
     * คืนค่า null หากไม่สามารถส่งคำขอได้
     */
    public static PrintJob print(
            Context context,
            String jobName,
            PrintDocumentAdapter adapter,
            PrintAttributes attributes
    ) {
        if (context == null || adapter == null) {
            return null;
        }

        if (!(context instanceof android.app.Activity)) {
            return null;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return null;
        }

        try {
            PrintManager manager = (PrintManager)
                    context.getSystemService(Context.PRINT_SERVICE);

            if (manager == null) {
                return null;
            }

            String safeJobName = jobName;

            if (safeJobName == null
                    || safeJobName.trim().isEmpty()) {
                safeJobName = "MaeyaiOn POS Print";
            }

            if (attributes == null) {
                attributes = new PrintAttributes.Builder()
                        .setMediaSize(
                                PrintAttributes.MediaSize.ISO_A4
                        )
                        .setColorMode(
                                PrintAttributes.COLOR_MODE_COLOR
                        )
                        .setMinMargins(
                                PrintAttributes.Margins.NO_MARGINS
                        )
                        .build();
            }

            return manager.print(
                    safeJobName,
                    adapter,
                    attributes
            );

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ดึงรายการงานพิมพ์ที่ Android รายงาน
     */
    public static List<PrintJob> getPrintJobs(Context context) {
        List<PrintJob> result = new ArrayList<>();

        if (!isAvailable(context)) {
            return result;
        }

        try {
            PrintManager manager = (PrintManager)
                    context.getSystemService(Context.PRINT_SERVICE);

            if (manager != null) {
                List<PrintJob> jobs = manager.getPrintJobs();

                if (jobs != null) {
                    result.addAll(jobs);
                }
            }
        } catch (Exception ignored) {
            // คืนรายการว่างหากอ่านสถานะไม่ได้
        }

        return result;
    }

    /**
     * สรุปสถานะงานพิมพ์
     */
    public static PrinterStatus getStatus(Context context) {

        boolean available = isAvailable(context);

        int total = 0;
        int queued = 0;
        int started = 0;
        int completed = 0;
        int failed = 0;
        int canceled = 0;

        if (available) {
            List<PrintJob> jobs = getPrintJobs(context);
            total = jobs.size();

            for (PrintJob job : jobs) {
                if (job == null || job.getInfo() == null) {
                    continue;
                }

                int state = job.getInfo().getState();

                switch (state) {
                    case PrintJobInfo.STATE_CREATED:
                    case PrintJobInfo.STATE_QUEUED:
                    case PrintJobInfo.STATE_BLOCKED:
                        queued++;
                        break;

                    case PrintJobInfo.STATE_STARTED:
                        started++;
                        break;

                    case PrintJobInfo.STATE_COMPLETED:
                        completed++;
                        break;

                    case PrintJobInfo.STATE_FAILED:
                        failed++;
                        break;

                    case PrintJobInfo.STATE_CANCELED:
                        canceled++;
                        break;

                    default:
                        break;
                }
            }
        }

        String message;

        if (!available) {
            message = "ไม่สามารถเข้าถึงระบบพิมพ์ของ Android";
        } else if (queued > 0 || started > 0) {
            message = "มีงานพิมพ์รอดำเนินการ";
        } else if (failed > 0) {
            message = "มีงานพิมพ์ผิดพลาด โปรดตรวจสอบรายละเอียด";
        } else {
            message = "ระบบพิมพ์ Android พร้อมให้เรียกใช้งาน";
        }

        return new PrinterStatus(
                available,
                total,
                queued,
                started,
                completed,
                failed,
                canceled,
                message
        );
    }

    /**
     * แปลงสถานะงานพิมพ์เป็นข้อความภาษาไทย
     */
    public static String getJobStatusText(PrintJob job) {

        if (job == null) {
            return "ไม่พบข้อมูลงานพิมพ์";
        }

        try {
            PrintJobInfo info = job.getInfo();

            if (info == null) {
                return "ไม่สามารถอ่านสถานะงานพิมพ์";
            }

            switch (info.getState()) {
                case PrintJobInfo.STATE_CREATED:
                    return "สร้างงานพิมพ์แล้ว";

                case PrintJobInfo.STATE_QUEUED:
                    return "รอพิมพ์";

                case PrintJobInfo.STATE_STARTED:
                    return "กำลังดำเนินการพิมพ์";

                case PrintJobInfo.STATE_BLOCKED:
                    return "งานพิมพ์ถูกพักไว้";

                case PrintJobInfo.STATE_COMPLETED:
                    return "ระบบรายงานว่าพิมพ์เสร็จ";

                case PrintJobInfo.STATE_FAILED:
                    return "พิมพ์ผิดพลาด";

                case PrintJobInfo.STATE_CANCELED:
                    return "ยกเลิกงานพิมพ์แล้ว";

                default:
                    return "ไม่ทราบสถานะงานพิมพ์";
            }

        } catch (Exception e) {
            return "ไม่สามารถอ่านสถานะงานพิมพ์";
        }
    }

    /**
     * ดึงชื่อของงานพิมพ์
     */
    public static String getJobName(PrintJob job) {

        if (job == null) {
            return "งานพิมพ์";
        }

        try {
            PrintJobInfo info = job.getInfo();

            if (info != null
                    && info.getLabel() != null
                    && !info.getLabel().trim().isEmpty()) {
                return info.getLabel();
            }
        } catch (Exception ignored) {
            // ใช้ชื่อสำรอง
        }

        return "งานพิมพ์";
    }
}
