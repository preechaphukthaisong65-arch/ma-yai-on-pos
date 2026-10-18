
package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class StockScreen extends Activity {

    private int dp(float value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
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
                        showNotReadyMessage();
                    }
                }
        );

        addMenuButton(
                root,
                "รับสินค้าเข้า",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showNotReadyMessage();
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

    private void showNotReadyMessage() {
        Toast.makeText(
                this,
                "เมนูนี้กำลังจัดทำ ยังไม่พร้อมใช้งาน",
                Toast.LENGTH_SHORT
        ).show();
    }
}
