package com.example.newapp.printer;

import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;

public class PrinterManager {

    /**
     * ตรวจสอบว่าระบบพิมพ์ของ Android พร้อมใช้งานหรือไม่
     */
    public static boolean isAvailable(Context context) {
        return context != null && context.getSystemService(Context.PRINT_SERVICE) != null;
    }

    /**
     * สั่งพิมพ์เอกสารผ่านระบบพิมพ์มาตรฐานของ Android (รองรับเครื่องพิมพ์ที่เชื่อมต่อ)
     */
    public static PrintJob print(Context context, String printJobName, PrintDocumentAdapter adapter, PrintAttributes attributes) {
        if (!isAvailable(context)) {
            return null;
        }

        try {
            PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
            if (printManager != null) {
                return printManager.print(printJobName, adapter, attributes);
            }
        } catch (Exception e) {
            // จัดการข้อผิดพลาดเมื่อไม่สามารถสั่งพิมพ์ได้
        }

        return null;
    }
}
