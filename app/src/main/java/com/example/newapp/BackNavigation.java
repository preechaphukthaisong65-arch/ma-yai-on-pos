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
    private static final int BUTTON_SIZE_DP = 51;
    private static final int LEFT_MARGIN_DP = 16;
    private static final float BOTTOM_GAP_SCREEN_RATIO = 0.03f;

    private BackNavigation() {}

    static ImageView attach(Activity activity, FrameLayout overlay, Runnable onBack) {
        if (activity == null || overlay == null || onBack == null) return null;

        // Reserve the bottom 10% of every screen using this shared back button.
        // Resize the screen's content container to the top 90% so content and
        // menu controls cannot occupy the back button's dedicated area.
        int contentHeight = Math.round(
                activity.getResources().getDisplayMetrics().heightPixels * 0.90f);
        if (overlay.getChildCount() > 0) {
            View content = overlay.getChildAt(0);
            android.view.ViewGroup.LayoutParams rawParams = content.getLayoutParams();
            if (rawParams instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams contentParams = (FrameLayout.LayoutParams) rawParams;
                contentParams.height = contentHeight;
                contentParams.gravity = Gravity.TOP;
                overlay.updateViewLayout(content, contentParams);
            }
        }

        int imageId = activity.getResources().getIdentifier(
                "allback_button", "drawable", activity.getPackageName());
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
