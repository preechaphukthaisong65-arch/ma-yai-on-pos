
package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ManageStoreScreen extends Activity {

    private int dp(float value) {
        return (int) (
                value * getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }

    private TextView makeText(
            String value,
            int size,
            boolean bold
    ) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.rgb(35, 45, 55));
        text.setGravity(Gravity.CENTER);

        if (bold) {
            text.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );
        }

        return text;
    }

    private Button makeMenuButton(
            String label,
            Runnable action
    ) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(17);
        button.setAllCaps(false);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.topMargin = dp(10);
        button.setLayoutParams(params);
        button.setOnClickListener(v -> action.run());

        return button;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 247, 247));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(20));

        TextView title = makeText(
                "จัดการร้านค้า",
                24,
                true
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        titleParams.bottomMargin = dp(12);
        root.addView(title, titleParams);

        TextView description = makeText(
                "เลือกเมนูที่ต้องการ",
                16,
                false
        );

        root.addView(
                description,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        root.addView(makeMenuButton(
                "แก้ไขราคาสินค้า",
                () -> {
                    Intent intent = new Intent(
                            ManageStoreScreen.this,
                            EditProductPriceScreen.class
                    );
                    startActivity(intent);
                }
        ));

        root.addView(makeMenuButton(
                "ค้นหาประวัติการแก้ไขราคา",
                () -> {
                    Intent intent = new Intent(
                            ManageStoreScreen.this,
                            PriceHistoryScreen.class
                    );
                    startActivity(intent);
                }
        ));

        root.addView(makeMenuButton(
                "กลับ",
                this::finish
        ));

        scroll.addView(root);
        setContentView(scroll);
    }
}
