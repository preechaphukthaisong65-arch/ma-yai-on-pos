package com.example.newapp;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class BaseScreen extends Activity {

    protected FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void createBackground() {

        root = new FrameLayout(this);

        ImageView background = new ImageView(this);
        background.setImageResource(R.drawable.app_background);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);

        FrameLayout.LayoutParams backgroundParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        root.addView(background, backgroundParams);

        setContentView(root);
    }

    protected View createNotificationBar() {

        TextView notification = new TextView(this);

        notification.setText("");
        notification.setGravity(Gravity.CENTER);
        notification.setTextColor(Color.WHITE);
        notification.setTextSize(14);

        GradientDrawable shape = new GradientDrawable();
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
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(
                dp(4), dp(4), dp(4), dp(4)
        );

        button.setClickable(true);
        button.setFocusable(true);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                android.content.Intent intent =
                        new android.content.Intent(
                                BaseScreen.this,
                                screenClass
                        );

                startActivity(intent);
            }
        });

        return button;
    }
}
