package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView tickerView;
    private static final String TICKER_BASE = "ยินดีต้อนรับสู่ร้านแม่ใหญ่อ่อน POS     •     ระบบขายสินค้าและจัดการร้านค้า";
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
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
        OwnerAccess.ensureUnlocked(this, this::buildHome);
    }

    private void buildHome() {
        hideSystemBars();
        FrameLayout frame = new FrameLayout(this);
        int bg = getResources().getIdentifier("app_background", "drawable", getPackageName());
        if (bg != 0) frame.setBackgroundResource(bg);

        // Draw the logo as a full-width background layer. Its intrinsic aspect ratio
        // determines its height, so it can extend behind the ticker and menu tiles.
        int logoId = getResources().getIdentifier("logomain", "drawable", getPackageName());
        if (logoId != 0) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(logoId);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setAdjustViewBounds(true);
            android.graphics.drawable.Drawable logoDrawable = logo.getDrawable();
            int intrinsicWidth = logoDrawable == null ? 0 : logoDrawable.getIntrinsicWidth();
            int intrinsicHeight = logoDrawable == null ? 0 : logoDrawable.getIntrinsicHeight();
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int logoHeight = intrinsicWidth > 0 && intrinsicHeight > 0
                    ? Math.round(screenWidth * (intrinsicHeight / (float) intrinsicWidth))
                    : dp(180);
            FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, Math.max(dp(1), logoHeight),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            frame.addView(logo, logoParams);
        }

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.BOTTOM);
        page.setPadding(dp(8), dp(4), dp(8), dp(6));
        page.setBackgroundColor(Color.TRANSPARENT);

        TextView ticker = new TextView(this);
        tickerView = ticker;
        ticker.setText(TICKER_BASE);
        ticker.setTextSize(21);
        ticker.setTextColor(Color.rgb(35, 55, 40));
        // Light translucent white backing improves ticker/notification text contrast
        // while allowing the shared home wallpaper to remain visible.
        ticker.setBackgroundColor(Color.argb(218, 255, 255, 255));
        ticker.setSingleLine(true);
        ticker.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        ticker.setMarqueeRepeatLimit(-1);
        ticker.setSelected(true);
        ticker.setPadding(dp(4), dp(2), dp(4), dp(2));
        page.addView(ticker, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout r1 = row(), r2 = row(), r3 = row();
        addMenu(r1, "menu_sell", "ขายสินค้า", SellScreen.class);
        addMenu(r1, "menu_stock", "คลังสินค้า", StockScreen.class);
        addMenu(r2, "menu_history", "ประวัติการขาย", HistoryScreen.class);
        addMenu(r2, "menu_manage", "จัดการร้านค้า", ManageStoreScreen.class);
        addMenu(r3, "menu_barcode", "สร้างบาร์โค้ด", BarcodeScreen.class);
        addMenu(r3, "menu_report", "รายงานยอดขาย", ReportScreen.class);
        page.addView(r1, new LinearLayout.LayoutParams(-1, 0, 1f)); space(page);
        page.addView(r2, new LinearLayout.LayoutParams(-1, 0, 1f)); space(page);
        page.addView(r3, new LinearLayout.LayoutParams(-1, 0, 1f));
        FrameLayout.LayoutParams pageParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Math.round(getResources().getDisplayMetrics().heightPixels * 0.65f),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        // Keep 8% of the screen clear below the menu group, shifting the group upward.
        pageParams.bottomMargin = Math.round(getResources().getDisplayMetrics().heightPixels * 0.08f);
        frame.addView(page, pageParams);
        PosUi.showRoot(this,frame);
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
        image.setAdjustViewBounds(false);
        tile.addView(image, new LinearLayout.LayoutParams(-1, 0, 1f));
        r.addView(tile, new LinearLayout.LayoutParams(0, -1, 1f));
        tile.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, target)));
    }

    private void space(LinearLayout parent) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(-1, dp(3)));
    }

    @Override protected void onResume() { super.onResume(); hideSystemBars(); RetentionManager.run(this); DebtReminderScheduler.scheduleIfMissing(this); boolean low=StockAlertManager.check(this); DebtReminderNotifier.hasOverdue(this); if(tickerView!=null){String message=low?TICKER_BASE+"     •     มีสินค้า ต่ำกว่า ลิมิตสต๊อก":TICKER_BASE;String notice=StoreNotificationManager.recentHomeNotice(this);if(!notice.isEmpty())message+="     •     "+notice;String debtSummary=DebtReminderNotifier.homeSummary(this);if(!debtSummary.isEmpty())message+="     •     "+debtSummary;tickerView.setText(message);tickerView.setOnClickListener(null);} }
}
