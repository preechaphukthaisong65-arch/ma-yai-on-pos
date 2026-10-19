package com.example.newapp;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class StockAlertScreen extends Activity {

    private static final int NOTIFICATION_PERMISSION_REQUEST = 903;
    private static final int STOCK_NOTIFICATION_BASE_ID = 4000;

    private LocalDatabase database;
    private LinearLayout list;
    private TextView summary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        database = LocalDatabase.getInstance(this);

        buildScreen();
        requestNotificationPermissionIfNeeded();
        loadLowStock();
    }

    private int dp(float value) {
        return (int) (
                value * getResources().getDisplayMetrics().density
                        + 0.5f
        );
    }

    private TextView text(String value, int size, boolean bold) {
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
        root.setPadding(dp(14), dp(12), dp(14), dp(16));
        root.setBackgroundColor(0xFFF7F7F7);

        root.addView(
                text("สินค้าใกล้หมดหรือถึงลิมิตสต๊อก", 22, true)
        );

        summary = text("", 15, true);

        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(8);

        root.addView(summary, summaryParams);

        Button refresh = new Button(this);
        refresh.setText("ตรวจสอบใหม่");
        refresh.setAllCaps(false);
        refresh.setOnClickListener(v -> loadLowStock());
        root.addView(refresh);

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
            loadLowStock();
        }
    }

    private void loadLowStock() {
        if (database == null || list == null) {
            return;
        }

        list.removeAllViews();
        summary.setText("กำลังตรวจสอบสต๊อก...");

        int count = 0;

        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, name, stock_qty, min_stock " +
                "FROM products " +
                "WHERE stock_qty <= min_stock " +
                "ORDER BY stock_qty ASC, name COLLATE NOCASE ASC",
                null
        )) {
            while (cursor.moveToNext()) {
                count++;

                long productId = cursor.getLong(0);
                String name = cursor.getString(1);
                int quantity = cursor.getInt(2);
                int limit = cursor.getInt(3);

                String productName =
                        name == null || name.trim().isEmpty()
                                ? "สินค้าไม่ระบุชื่อ"
                                : name.trim();

                recordLowStockNotificationIfNeeded(
                        productId,
                        productName,
                        quantity,
                        limit
                );

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(
                        dp(12), dp(10), dp(12), dp(10)
                );
                card.setBackgroundColor(0xFFFFF1F0);

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(-1, -2);
                params.topMargin = dp(5);
                params.bottomMargin = dp(5);

                card.addView(text(
                        productName,
                        16,
                        true
                ));

                card.addView(text(
                        "คงเหลือ: " + quantity,
                        15,
                        true
                ));

                card.addView(text(
                        "ลิมิตสต๊อก: " + limit,
                        14,
                        false
                ));

                card.addView(text(
                        quantity == 0
                                ? "สถานะ: สินค้าหมด"
                                : quantity < limit
                                ? "สถานะ: ต่ำกว่าลิมิต"
                                : "สถานะ: ถึงลิมิต",
                        14,
                        true
                ));

                list.addView(card, params);
            }

        } catch (Exception e) {
            summary.setText("อ่านข้อมูลสต๊อกไม่สำเร็จ");

            list.addView(text(
                    "ไม่สามารถโหลดรายการสินค้าใกล้หมดได้ " +
                    "กรุณาลองตรวจสอบใหม่",
                    14,
                    false
            ));
            return;
        }

        summary.setText(
                "พบสินค้าใกล้หมดหรือถึงลิมิต " + count + " รายการ"
        );

        if (count == 0) {
            list.addView(text(
                    "ไม่มีสินค้าที่ถึงลิมิตสต๊อกในขณะนี้",
                    16,
                    false
            ));
        }
    }

    private void recordLowStockNotificationIfNeeded(
            long productId,
            String productName,
            int quantity,
            int limit
    ) {
        if (!hasNotificationPermission()) {
            return;
        }

        String today = LocalDatabase.now().substring(0, 10);
        String eventKey = "LOW_STOCK_" + productId + "_" + today;

        String message =
                "สินค้า " + productName
                        + " คงเหลือ " + quantity
                        + " หน่วย ลิมิตสต๊อก " + limit + " หน่วย";

        SQLiteDatabase db = database.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("event_type", "LOW_STOCK");
        values.put("message", message);
        values.put("created_at", LocalDatabase.now());
        values.put("is_read", 0);
        values.put("event_key", eventKey);

        long inserted = db.insertWithOnConflict(
                "notification_messages",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        // แจ้งเฉพาะเมื่อเพิ่มเหตุการณ์ของสินค้านี้ในวันนี้ได้ใหม่
        // ป้องกันการแจ้งซ้ำเมื่อรีเฟรชหรือเปิดหน้าจอซ้ำ
        if (inserted != -1) {
            AndroidNotificationService service =
                    new AndroidNotificationService(this);

            int notificationId =
                    STOCK_NOTIFICATION_BASE_ID
                            + (int) (productId % 1000000L);

            service.show(
                    notificationId,
                    "แจ้งเตือนสต๊อกสินค้า",
                    message
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (list != null) {
            loadLowStock();
        }
    }
}
