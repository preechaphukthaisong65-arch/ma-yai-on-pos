package com.example.newapp;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;
import java.util.ArrayList;

public class StockAlertManager {

    /**
     * ตรวจสอบสินค้าที่เพิ่งเปลี่ยนสถานะเป็นสต๊อกต่ำ และดำเนินการบันทึกพร้อมส่งแจ้งเตือน
     */
    public static void checkAndNotifyNewLowStock(Context context) {
        // 1. สร้าง Repository สำหรับตรวจสอบสต๊อก
        LocalDatabase dbHelper = LocalDatabase.getInstance(context);
        StockAlertRepository alertRepository = new StockAlertRepository(dbHelper);

        // 2. สร้าง Repository และ Service สำหรับจัดการการแจ้งเตือน
        NotificationRepository notificationRepo = new NotificationRepository(dbHelper);
        AndroidNotificationService notificationService = new AndroidNotificationService(context);

        // 3. ดึงรายการสินค้าที่เพิ่งเปลี่ยนสถานะเป็นสต๊อกต่ำในรอบนี้
        List<StockAlertRepository.StockAlert> newAlerts = alertRepository.detectNewLowStockEvents();

        if (newAlerts != null && !newAlerts.isEmpty()) {
            for (StockAlertRepository.StockAlert alert : newAlerts) {
                
                // กำหนด event_key ให้ไม่ซ้ำกันสำหรับแต่ละสินค้า
                String eventKey = "low_stock_" + alert.productId;
                
                String title = "เตือนสินค้าใกล้หมด (Stock Low)";
                String message = String.format(
                        "สินค้า '%s' (Barcode: %s) คงเหลือ %.2f (ขั้นต่ำ: %.2f)",
                        alert.name,
                        alert.barcode,
                        alert.quantity,
                        alert.limit
                );

                // 4. บันทึกข้อความเตือนลงฐานข้อมูล
                notificationRepo.saveNotification(
                        eventKey,
                        title,
                        message,
                        System.currentTimeMillis()
                );

                // 5. ส่ง Android Notification ออกไป (ใช้ productId แปลงเป็น int เพื่อให้ Notification ID ไม่ซ้ำกัน)
                int notificationId = (int) alert.productId;
                notificationService.showNotification(notificationId, title, message);
            }
        }
    }
}
