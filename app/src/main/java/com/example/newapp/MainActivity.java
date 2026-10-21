package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView tickerView;\n    private static final String TICKER_BASE = "ยินดีต้อนรับสู่ร้านแม่ใหญ่อ่อน POS     •     ระบบขายสินค้าและจัดการร้านค้า";
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    private void hideSystemBars() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        hideSystemBars();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(8), dp(12), dp(16));
        int bg = getResources().getIdentifier("app_background", "drawable", getPackageName());
        if (bg != 0) page.setBackgroundResource(bg);

        int logoId = getResources().getIdentifier("logomain", "drawable", getPackageName());
        if (logoId != 0) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(logoId);
            logo.setAdjustViewBounds(true);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(6);
            page.addView(logo, lp);
        }

        TextView ticker = new TextView(this);
        tickerView = ticker;
        ticker.setText(TICKER_BASE);
        ticker.setTextSize(14);
        ticker.setTextColor(Color.rgb(45, 90, 55));
        ticker.setSingleLine(true);
        ticker.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        ticker.setMarqueeRepeatLimit(-1);
        ticker.setSelected(true);
        ticker.setPadding(dp(4), dp(8), dp(4), dp(8));
        page.addView(ticker);

        LinearLayout r1 = row(), r2 = row(), r3 = row();
        addMenu(r1, "menu_sell", "ขายสินค้า", SellScreen.class);
        addMenu(r1, "menu_stock", "คลังสินค้า", StockScreen.class);
        addMenu(r2, "menu_history", "ประวัติการขาย", HistoryScreen.class);
        addMenu(r2, "menu_manage", "จัดการร้านค้า", ManageStoreScreen.class);
        addMenu(r3, "menu_barcode", "สร้างบาร์โค้ด", BarcodeScreen.class);
        addMenu(r3, "menu_report", "รายงานยอดขาย", ReportScreen.class);
        page.addView(r1); space(page); page.addView(r2); space(page); page.addView(r3);
        scroll.addView(page);
        setContentView(scroll);
        hideSystemBars();
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private void addMenu(LinearLayout r, String imageName, String label, Class<? extends Activity> target) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(3), dp(3), dp(3), dp(3));
        ImageView image = new ImageView(this);
        int id = getResources().getIdentifier(imageName, "drawable", getPackageName());
        if (id != 0) image.setImageResource(id);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        tile.addView(image, new LinearLayout.LayoutParams(-1, dp(145)));
        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextColor(Color.rgb(35, 45, 55));
        caption.setTextSize(14);
        caption.setGravity(Gravity.CENTER);
        caption.setMaxLines(2);
        tile.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        r.addView(tile, new LinearLayout.LayoutParams(0, -2, 1f));
        tile.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, target)));
    }

    private void space(LinearLayout parent) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(-1, dp(8)));
    }

    @Override protected void onResume() { super.onResume(); hideSystemBars(); RetentionManager.run(this); boolean low=StockAlertManager.check(this); if(tickerView!=null){String message=low?TICKER_BASE+"     •     มีสินค้า ต่ำกว่า ลิมิตสต๊อก":TICKER_BASE;String notice=StoreNotificationManager.recentHomeNotice(this);if(!notice.isEmpty())message+="     •     "+notice;tickerView.setText(message);} }
}
