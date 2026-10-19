
package com.example.newapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

public class StockScreen extends Activity {

    private static final int NOTIFICATION_REQUEST = 823;

    private AndroidNotificationService notificationService;

    private int dp(float value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        notificationService = new AndroidNotificationService(this);

        buildScreen();
        syncNotificationPermissionAndStatus();
    }

    /**
     * ตรวจสิทธิ์การแจ้งเตือนและซิงก์สถานะสต๊อกต่ำ
     * ไม่แก้ไขข้อมูลสินค้าและไม่เปลี่ยนโครงสร้างฐานข้อมูล
     */
    private void syncNotificationPermissionAndStatus() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    NOTIFICATION_REQUEST
            );
            return;
        }

        syncLowStockSafely();
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

        if (requestCode == NOTIFICATION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            syncLowStockSafely();
        }
    }

    private void syncLowStockSafely() {
        try {
            notificationService.syncLowStockNotification();
        } catch (Exception e) {
            Log.w(
                    "StockScreen",
                    "ซิงก์การแจ้งเตือนสต๊อกไม่สำเร็จ",
                    e
            );
        }
    }

    private void buildScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("คลังสินค้า");
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(45, 90, 55));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(10));

        root.addView(
                title,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView description = new TextView(this);
        description.setText("เลือกรายการที่ต้องการจัดการ");
        description.setTextSize(16);
        description.setTextColor(Color.DKGRAY);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 0, 0, dp(20));

        root.addView(
                description,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        addMenuButton(
                root,
                "เพิ่มสินค้าใหม่เข้าคลัง",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(
                                StockScreen.this,
                                CreateStockProductScreen.class
                        ));
                    }
                }
        );

        addMenuButton(
                root,
                "สินค้าทั้งหมด",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(
                                StockScreen.this,
                                StockProductListScreen.class
                        ));
                    }
                }
        );

        addMenuButton(
                root,
                "ตรวจนับสต๊อก",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(
                                StockScreen.this,
                                StocktakeScreen.class
                        ));
                    }
                }
        );

        addMenuButton(
                root,
                "ตั้งค่าลิมิตสต๊อก",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(
                                StockScreen.this,
                                StockLimitScreen.class
                        ));
                    }
                }
        );

        addMenuButton(
                root,
                "สินค้าใกล้หมด",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(
                                StockScreen.this,
                                StockAlertScreen.class
                        ));
                    }
                }
        );

        addMenuButton(
                root,
                "รับสินค้าเข้า",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(
                                StockScreen.this,
                                ReceiveStockScreen.class
                        ));
                    }
                }
        );

        setContentView(scrollView);
    }

    private void addMenuButton(
            LinearLayout root,
            String text,
            View.OnClickListener listener
    ) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(17);
        button.setAllCaps(false);
        button.setMinHeight(dp(56));
        button.setOnClickListener(listener);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(12);
        root.addView(button, params);
    }
}
