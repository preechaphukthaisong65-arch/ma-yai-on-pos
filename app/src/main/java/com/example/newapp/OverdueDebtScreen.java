
package com.example.newapp;

import android.Manifest;
import android.app.Activity;
import android.database.Cursor;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.DecimalFormat;

public class OverdueDebtScreen extends Activity {

    private static final int NOTIFICATION_PERMISSION_REQUEST = 902;
    private static final int OVERDUE_NOTIFICATION_ID = 3001;

    private LocalDatabase database;
    private LinearLayout list;
    private TextView summary;

    private final DecimalFormat money =
            new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        database = LocalDatabase.getInstance(this);

        buildScreen();

        requestNotificationPermissionIfNeeded();
        loadBills();
    }

    private int dp(float value) {
        return (int) (
                value * getResources().getDisplayMetrics().density
                        + 0.5f
        );
    }

    private TextView text(
            String value,
            int size,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xFF202020);

        if (bold) {
            view.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );
        }

        return view;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(16)
        );
        root.setBackgroundColor(0xFFF7F7F7);

        root.addView(
                text("บิลเครดิตค้างเกินหนึ่งเดือน", 22, true)
        );

        summary = text("", 16, true);

        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(8);

        root.addView(summary, summaryParams);

        Button refresh = new Button(this);
        refresh.setText("ตรวจสอบอีกครั้ง");
        refresh.setAllCaps(false);
        root.addView(refresh);

        refresh.setOnClickListener(v -> loadBills());

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams listParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        listParams.topMargin = dp(8);
        root.addView(list, listParams);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.POST_NOTIFICATIONS
                },
                NOTIFICATION_PERMISSION_REQUEST
        );
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }

        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            // ตรวจรายการใหม่หลังผู้ใช้ตอบเรื่องสิทธิ์แล้ว
            loadBills();
        }
    }

    private void loadBills() {
        if (list == null || database == null) {
            return;
        }

        list.removeAllViews();

        DebtAlertRepository repository =
                new DebtAlertRepository(database);

        int count = repository.countOverdueBills();
        double total = repository.getOverdueAmount();

        summary.setText(
                "จำนวน " + count + " บิล\n"
                        + "ยอดค้างรวม "
                        + money.format(total)
                        + " บาท"
        );

        /*
         * บันทึกเหตุการณ์และแสดงการแจ้งเตือนเมื่อมีบิลค้างเกินเดือน
         * และได้รับสิทธิ์แสดง Notification แล้วเท่านั้น
         * ใช้รหัสเดิมเพื่อไม่สร้างการแจ้งเตือนหลายรายการซ้อนกัน
         */
        if (count > 0 && hasNotificationPermission()) {
            boolean recorded =
                    repository.recordOverdueAlertIfNeeded();

            if (recorded) {
                AndroidNotificationService notificationService =
                        new AndroidNotificationService(this);

                notificationService.showOverdueDebt(
                        OVERDUE_NOTIFICATION_ID,
                        count
                );
            }
        }

        int shown = 0;

        try (Cursor cursor = repository.getOverdueBills()) {
            while (cursor.moveToNext()) {
                shown++;

                String billNo = cursor.getString(1);
                double amount = cursor.getDouble(2);
                String createdAt = cursor.getString(3);
                String first = cursor.getString(4);
                String last = cursor.getString(5);

                String customer = (
                        (first == null ? "" : first)
                                + " "
                                + (last == null ? "" : last)
                ).trim();

                if (customer.isEmpty()) {
                    customer = "ไม่ระบุลูกค้า";
                }

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(
                        dp(12),
                        dp(10),
                        dp(12),
                        dp(10)
                );
                card.setBackgroundColor(0xFFFFFFFF);

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(-1, -2);

                params.topMargin = dp(5);
                params.bottomMargin = dp(5);

                card.addView(text(
                        "บิล: " + safe(billNo),
                        16,
                        true
                ));

                card.addView(text(
                        "ลูกค้า: " + customer,
                        14,
                        false
                ));

                card.addView(text(
                        "วันที่ขาย: " + safe(createdAt),
                        13,
                        false
                ));

                card.addView(text(
                        "ยอดค้าง: "
                                + money.format(amount)
                                + " บาท",
                        16,
                        true
                ));

                list.addView(card, params);
            }
        } catch (Exception e) {
            list.addView(text(
                    "โหลดรายการไม่สำเร็จ กรุณาตรวจสอบฐานข้อมูล",
                    15,
                    false
            ));
            return;
        }

        if (shown == 0) {
            list.addView(text(
                    "ไม่มีบิลเครดิตค้างเกินหนึ่งเดือน",
                    16,
                    false
            ));
        }
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (list != null) {
            loadBills();
        }
    }
}
