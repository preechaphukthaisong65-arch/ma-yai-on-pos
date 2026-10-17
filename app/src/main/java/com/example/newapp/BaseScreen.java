package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class BaseScreen extends Activity {

    protected FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        enableFullscreen();
    }

    // แสดงผลเต็มหน้าจอ
    protected void enableFullscreen() {

        Window window = getWindow();

        window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        final View decorView =
                window.getDecorView();

        final int flags =
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;

        decorView.setSystemUiVisibility(flags);

        // แสดงผลเต็มจออีกครั้งเมื่อแถบระบบกลับมา
        decorView.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(
                            int visibility) {

                        decorView.setSystemUiVisibility(flags);
                    }
                }
        );
    }

    protected void createBackground() {

        root = new FrameLayout(this);

        ImageView background = new ImageView(this);
        background.setImageResource(
                R.drawable.app_background
        );

        background.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        FrameLayout.LayoutParams backgroundParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        root.addView(background, backgroundParams);

        setContentView(root);

        enableFullscreen();
    }

    protected View createNotificationBar() {

        TextView notification = new TextView(this);

        notification.setText("");
        notification.setGravity(Gravity.CENTER);
        notification.setTextColor(Color.WHITE);
        notification.setTextSize(14);

        GradientDrawable shape =
                new GradientDrawable();

        shape.setColor(0xCCFFFFFF);
        shape.setCornerRadius(dp(12));

        notification.setBackground(shape);

        return notification;
    }

    protected int dp(float value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics().density
                        + 0.5f
        );
    }

    protected ImageView createImageButton(
            int imageResource,
            final Class<?> screenClass) {

        ImageView button = new ImageView(this);

        button.setImageResource(imageResource);
        button.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        button.setPadding(
                dp(4), dp(4), dp(4), dp(4)
        );

        button.setClickable(true);
        button.setFocusable(true);

        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        android.content.Intent intent =
                                new android.content.Intent(
                                        BaseScreen.this,
                                        screenClass
                                );

                        startActivity(intent);
                    }
                }
        );

        return button;
    }

    @Override
    public void onBackPressed() {

        // หน้าย่อย: ย้อนกลับไปหน้าก่อนหน้า
        if (!(this instanceof MainActivity)) {
            super.onBackPressed();
            return;
        }

        // หน้าหลัก: ถามยืนยันก่อนออก
        new AlertDialog.Builder(this)
                .setTitle("ออกจากแอป")
                .setMessage(
                        "คุณต้องการออกจากแอป "
                        + "ร้านแม่ใหญ่อ่อน POS หรือไม่?"
                )
                .setNegativeButton(
                        "ใช้งานต่อ",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which) {

                                dialog.dismiss();
                                enableFullscreen();
                            }
                        }
                )
                .setPositiveButton(
                        "ออกจากแอป",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which) {

                                finish();
                            }
                        }
                )
                .setCancelable(true)
                .show();
    }
}
