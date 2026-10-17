package com.example.newapp;

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

        // สร้างพื้นหลังแอป
        createBackground();

        // พื้นที่เนื้อหาหลัก
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout.LayoutParams contentParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        // ไม่เว้นขอบด้านซ้ายและขวา
        contentParams.setMargins(0, 0, 0, 0);

        root.addView(content, contentParams);

        // ------------------------------------
        // 1. โลโก้ร้าน เต็มความกว้างหน้าจอ
        // ------------------------------------
        ImageView logo = new ImageView(this);

        logo.setImageResource(R.drawable.logomain);

        // ขยายรูปให้เต็มพื้นที่ที่กำหนด
        logo.setScaleType(ImageView.ScaleType.FIT_XY);

        // ไม่ให้ ImageView ปรับความสูงเอง
        logo.setAdjustViewBounds(false);

        // ไม่ให้มีขอบหรือระยะห่างภายใน
        logo.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams logoParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(112)
                );

        logoParams.setMargins(0, 0, 0, 0);

        content.addView(logo, logoParams);

        // ------------------------------------
        // 2. แถบแจ้งเตือนใต้โลโก้
        // ------------------------------------
        View notification = createNotificationBar();

        LinearLayout.LayoutParams notificationParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(42)
                );

        notificationParams.setMargins(
                dp(12),
                dp(8),
                dp(12),
                dp(12)
        );

        content.addView(
                notification,
                notificationParams
        );

        // ------------------------------------
        // 3. เมนูหลัก 6 ปุ่ม
        // ------------------------------------
        GridLayout menuGrid = new GridLayout(this);

        menuGrid.setColumnCount(2);
        menuGrid.setRowCount(3);
        menuGrid.setUseDefaultMargins(false);

        LinearLayout.LayoutParams gridParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                );

        gridParams.setMargins(
                dp(10),
                0,
                dp(10),
                dp(8)
        );

        content.addView(menuGrid, gridParams);

        // ปุ่มขายสินค้า
        addMenu(
                menuGrid,
                R.drawable.menu_sell,
                SellScreen.class
        );

        // ปุ่มคลังสินค้า
        addMenu(
                menuGrid,
                R.drawable.menu_stock,
                StockScreen.class
        );

        // ปุ่มสร้างบาร์โค้ด
        addMenu(
                menuGrid,
                R.drawable.menu_barcode,
                BarcodeScreen.class
        );

        // ปุ่มประวัติการขาย
        addMenu(
                menuGrid,
                R.drawable.menu_history,
                HistoryScreen.class
        );

        // ปุ่มจัดการร้าน
        addMenu(
                menuGrid,
                R.drawable.menu_manage,
                ManageScreen.class
        );

        // ปุ่มรายงาน
        addMenu(
                menuGrid,
                R.drawable.menu_report,
                ReportScreen.class
        );
    }

    // ------------------------------------
    // สร้างปุ่มเมนูจากภาพ PNG
    // ------------------------------------
    private void addMenu(
            GridLayout grid,
            int imageResource,
            Class<?> screenClass) {

        ImageView button =
                createImageButton(
                        imageResource,
                        screenClass
                );

        GridLayout.LayoutParams params =
                new GridLayout.LayoutParams();

        // ให้แต่ละปุ่มแบ่งพื้นที่เท่ากัน
        params.width = 0;
        params.height = 0;

        params.columnSpec =
                GridLayout.spec(
                        GridLayout.UNDEFINED,
                        1f
                );

        params.rowSpec =
                GridLayout.spec(
                        GridLayout.UNDEFINED,
                        1f
                );

        // ระยะห่างระหว่างปุ่ม
        params.setMargins(
                dp(5),
                dp(5),
                dp(5),
                dp(5)
        );

        grid.addView(button, params);
    }
}
