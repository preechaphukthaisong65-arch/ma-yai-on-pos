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

        createBackground();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout.LayoutParams contentParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        contentParams.setMargins(0, 0, 0, dp(8));
        root.addView(content, contentParams);

        // 1. โลโก้ร้านอยู่บนสุดและเต็มความกว้าง
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logomain);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAdjustViewBounds(true);

        LinearLayout.LayoutParams logoParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(112)
                );

        content.addView(logo, logoParams);

        // 2. แถบแจ้งเตือนอยู่ใต้โลโก้
        View notification = createNotificationBar();

        LinearLayout.LayoutParams notificationParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(42)
                );

        notificationParams.setMargins(
                dp(12), dp(8), dp(12), dp(12)
        );

        content.addView(notification, notificationParams);

        // 3. เมนู 6 ปุ่ม
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
                dp(10), 0, dp(10), dp(8)
        );

        content.addView(menuGrid, gridParams);

        addMenu(menuGrid, R.drawable.menu_sell,
                SellScreen.class);

        addMenu(menuGrid, R.drawable.menu_stock,
                StockScreen.class);

        addMenu(menuGrid, R.drawable.menu_barcode,
                BarcodeScreen.class);

        addMenu(menuGrid, R.drawable.menu_history,
                HistoryScreen.class);

        addMenu(menuGrid, R.drawable.menu_manage,
                ManageScreen.class);

        addMenu(menuGrid, R.drawable.menu_report,
                ReportScreen.class);
    }

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
                GridLayout.spec(
                        GridLayout.UNDEFINED, 1f
                );

        params.rowSpec =
                GridLayout.spec(
                        GridLayout.UNDEFINED, 1f
                );

        params.setMargins(
                dp(5), dp(5), dp(5), dp(5)
        );

        grid.addView(button, params);
    }
}
