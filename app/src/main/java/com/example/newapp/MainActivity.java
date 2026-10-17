package com.example.newapp;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class MainActivity extends BaseScreen {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ใช้พื้นหลังเดิมของแอป
        createBackground();

        // ------------------------------------
        // 1. พื้นที่หลัก
        // ------------------------------------
        FrameLayout mainLayer = new FrameLayout(this);

        FrameLayout.LayoutParams mainLayerParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        root.addView(mainLayer, mainLayerParams);

        // ------------------------------------
        // 2. คำนวณความสูงโลโก้ตามสัดส่วนจริง
        // ------------------------------------
        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inJustDecodeBounds = true;

        BitmapFactory.decodeResource(
                getResources(),
                R.drawable.logomain,
                options
        );

        int imageWidth = options.outWidth;
        int imageHeight = options.outHeight;

        int screenWidth =
                getResources().getDisplayMetrics().widthPixels;

        int logoHeight;

        if (imageWidth > 0 && imageHeight > 0) {
            logoHeight = Math.round(
                    screenWidth * (imageHeight / (float) imageWidth)
            );
        } else {
            // สำรองกรณีอ่านขนาดภาพไม่ได้
            logoHeight = dp(180);
        }

        // ------------------------------------
        // 3. ภาพโลโก้ด้านหลัง
        // ------------------------------------
        ImageView logo = new ImageView(this);

        logo.setImageResource(R.drawable.logomain);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAdjustViewBounds(false);
        logo.setPadding(0, 0, 0, 0);

        FrameLayout.LayoutParams logoParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        logoHeight,
                        Gravity.TOP
                );

        mainLayer.addView(logo, logoParams);

        // ------------------------------------
        // 4. แถบแจ้งเตือนซ้อนทับโลโก้
        // ------------------------------------
        View notification = createNotificationBar();

        FrameLayout.LayoutParams notificationParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        dp(42),
                        Gravity.TOP
                );

        // วางแถบแจ้งเตือนบริเวณล่างของโลโก้
        notificationParams.setMargins(
                dp(12),
                Math.max(0, logoHeight - dp(42) - dp(8)),
                dp(12),
                0
        );

        mainLayer.addView(
                notification,
                notificationParams
        );

        // ------------------------------------
        // 5. ตารางเมนู 6 ปุ่มซ้อนบนภาพ
        // ------------------------------------
        GridLayout menuGrid = new GridLayout(this);

        menuGrid.setColumnCount(2);
        menuGrid.setRowCount(3);
        menuGrid.setUseDefaultMargins(false);

        FrameLayout.LayoutParams gridParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.TOP
                );

        // รักษาระยะขอบเมนูเดิม
        gridParams.setMargins(
                dp(10),
                logoHeight,
                dp(10),
                dp(8)
        );

        mainLayer.addView(menuGrid, gridParams);

        // ------------------------------------
        // 6. เมนูเดิมทั้ง 6 ปุ่ม
        // ------------------------------------
        addMenu(
                menuGrid,
                R.drawable.menu_sell,
                SellScreen.class
        );

        addMenu(
                menuGrid,
                R.drawable.menu_stock,
                StockScreen.class
        );

        addMenu(
                menuGrid,
                R.drawable.menu_barcode,
                BarcodeScreen.class
        );

        addMenu(
                menuGrid,
                R.drawable.menu_history,
                HistoryScreen.class
        );

        addMenu(
                menuGrid,
                R.drawable.menu_manage,
                ManageScreen.class
        );

        addMenu(
                menuGrid,
                R.drawable.menu_report,
                ReportScreen.class
        );
    }

    // ------------------------------------
    // สร้างปุ่มเมนู
    // ------------------------------------
    private void addMenu(
            GridLayout grid,
            int imageResource,
            Class<?> screenClass) {

        ImageView button =
                createImageButton(imageResource, screenClass);

        GridLayout.LayoutParams params =
                new GridLayout.LayoutParams();

        params.width = 0;
        params.height = 0;

        params.columnSpec =
                GridLayout.spec(GridLayout.UNDEFINED, 1f);

        params.rowSpec =
                GridLayout.spec(GridLayout.UNDEFINED, 1f);

        params.setMargins(
                dp(5),
                dp(5),
                dp(5),
                dp(5)
        );

        grid.addView(button, params);
    }
}
