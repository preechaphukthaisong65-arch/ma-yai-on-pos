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

        contentParams.setMargins(
                dp(16), dp(12), dp(16), dp(12)
        );

        root.addView(content, contentParams);

        // แถบแจ้งเตือน
        View notification = createNotificationBar();

        LinearLayout.LayoutParams notificationParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(42)
                );

        notificationParams.bottomMargin = dp(12);
        content.addView(notification, notificationParams);

        // โลโก้ร้าน
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logomain);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout.LayoutParams logoParams =
                new LinearLayout.LayoutParams(
                        dp(112), dp(112)
                );

        logoParams.bottomMargin = dp(12);
        content.addView(logo, logoParams);

        // ปุ่มเมนูทั้ง 6
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
                GridLayout.spec(GridLayout.UNDEFINED, 1f);

        params.rowSpec =
                GridLayout.spec(GridLayout.UNDEFINED, 1f);

        params.setMargins(dp(5), dp(5), dp(5), dp(5));

        grid.addView(button, params);
    }
  }
