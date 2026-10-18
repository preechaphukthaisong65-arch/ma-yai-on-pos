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

public class MainActivity extends Activity {

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245, 247, 251));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(28), dp(20), dp(24));
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("ร้านแม่ใหญ่อ่อน POS");
        title.setTextSize(27);
        title.setTextColor(Color.rgb(31, 41, 55));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        page.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("ระบบขายสินค้าและจัดการร้านค้า");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(107, 114, 128));
        subtitle.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        -1, -2);
        subParams.topMargin = dp(8);
        page.addView(subtitle, subParams);

        addSpace(page, 28);

        addMenu(page, "🛒  ขายสินค้า",
                Color.rgb(16, 145, 95), SellScreen.class);

        addSpace(page, 12);

        addMenu(page, "📦  คลังสินค้า",
                Color.rgb(37, 99, 235), StockScreen.class);

        addSpace(page, 12);

        addMenu(page, "🏷️  สร้างบาร์โค้ด",
                Color.rgb(124, 58, 237), BarcodeScreen.class);

        addSpace(page, 12);

        addMenu(page, "📊  รายงานยอดขาย",
                Color.rgb(217, 119, 6), ReportScreen.class);

        setContentView(scroll);
    }

    private void addMenu(LinearLayout parent,
                         String label, int color,
                         Class<?> target) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(17);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(58));
        button.setBackgroundTintList(
                android.content.res.ColorStateList
                        .valueOf(color));

        button.setOnClickListener(v ->
                startActivity(new Intent(this, target)));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, dp(62));

        parent.addView(button, params);
    }

    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space,
                new LinearLayout.LayoutParams(1, dp(height)));
    }
}
