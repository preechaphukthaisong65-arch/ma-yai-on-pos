package com.example.newapp;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Single shared implementation for the image-based previous-page button.
 * Screens supply only their navigation action; size, image, and placement stay consistent.
 */
final class BackNavigation {
    private static final int BUTTON_SIZE_DP = 64;
    private static final int LEFT_MARGIN_DP = 16;
    private static final float BOTTOM_GAP_SCREEN_RATIO = 0.05f;

    private BackNavigation() {}

    static ImageView attach(Activity activity, FrameLayout overlay, Runnable onBack) {
        if (activity == null || overlay == null || onBack == null) return null;
        int imageId = activity.getResources().getIdentifier(
                "back_button", "drawable", activity.getPackageName());
        if (imageId == 0) return null;

        ImageView button = new ImageView(activity);
        button.setImageResource(imageId);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setContentDescription("หน้าก่อนหน้า");
        button.setClickable(true);
        button.setFocusable(true);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                PosUi.dp(activity, BUTTON_SIZE_DP),
                PosUi.dp(activity, BUTTON_SIZE_DP),
                Gravity.BOTTOM | Gravity.LEFT);
        params.leftMargin = PosUi.dp(activity, LEFT_MARGIN_DP);
        params.bottomMargin = Math.round(
                activity.getResources().getDisplayMetrics().heightPixels
                        * BOTTOM_GAP_SCREEN_RATIO);
        overlay.addView(button, params);
        button.setOnClickListener(view -> onBack.run());
        return button;
    }
}
