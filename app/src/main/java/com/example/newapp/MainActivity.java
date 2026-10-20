
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

import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;
import org.json.JSONObject;
import java.util.List;

public class MainActivity extends Activity {
    private TextView managementNotice;

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private void setupSystemBars() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupSystemBars();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(8), dp(12), dp(16));

        int backgroundId = getResources().getIdentifier(
                "app_background",
                "drawable",
                getPackageName()
        );

        if (backgroundId != 0) {
            page.setBackgroundResource(backgroundId);
        }

        // โลโก้ร้าน
        int logoId = getResources().getIdentifier(
                "logomain",
                "drawable",
                getPackageName()
        );

        if (logoId != 0) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(logoId);
            logo.setAdjustViewBounds(true);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);

            LinearLayout.LayoutParams logoParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            logoParams.bottomMargin = dp(6);
            page.addView(logo, logoParams);
        } else {
            TextView title = new TextView(this);
            title.setText("ร้านแม่ใหญ่อ่อน POS");
            title.setTextSize(25);
            title.setTextColor(Color.rgb(45, 90, 55));
            title.setGravity(Gravity.CENTER);
            page.addView(title);
        }

        // แถบข้อความต้อนรับ
        TextView ticker = new TextView(this);
        ticker.setText(
                "ยินดีต้อนรับสู่ร้านแม่ใหญ่อ่อน POS"
                        + "     •     ระบบขายสินค้าและจัดการร้านค้า"
        );
        ticker.setTextSize(14);
        ticker.setTextColor(Color.rgb(45, 90, 55));
        ticker.setSingleLine(true);
        ticker.setEllipsize(
                android.text.TextUtils.TruncateAt.MARQUEE
        );
        ticker.setMarqueeRepeatLimit(-1);
        ticker.setSelected(true);
        ticker.setPadding(dp(4), dp(8), dp(4), dp(8));
        page.addView(ticker);

        // Notification summary for successful store-management changes.
        managementNotice = new TextView(this);
        managementNotice.setTextColor(Color.rgb(45, 90, 55));
        managementNotice.setTextSize(13);
        managementNotice.setPadding(dp(8), dp(6), dp(8), dp(6));
        managementNotice.setVisibility(View.GONE);
        page.addView(managementNotice);

        // เมนูหลัก 6 ปุ่ม
        LinearLayout row1 = makeRow();
        LinearLayout row2 = makeRow();
        LinearLayout row3 = makeRow();

        addImageMenu(row1, "menu_sell", "ขายสินค้า");
        addImageMenu(row1, "menu_stock", "คลังสินค้า");

        addImageMenu(row2, "menu_history", "ประวัติการขาย");
        addImageMenu(row2, "menu_manage", "จัดการร้านค้า");

        addImageMenu(row3, "menu_barcode", "สร้างบาร์โค้ด");
        addImageMenu(row3, "menu_report", "รายงานยอดขาย");

        page.addView(row1);
        addSpace(page, 8);

        page.addView(row2);
        addSpace(page, 8);

        page.addView(row3);

        scroll.addView(page);
        setContentView(scroll);

        refreshManagementNotice();
        setupSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupSystemBars();
        refreshManagementNotice();
    }

    private void refreshManagementNotice() {
        if (managementNotice == null) return;
        String storeId = StoreIdentity.get(this);
        if (storeId.isEmpty()) {
            managementNotice.setVisibility(View.GONE);
            return;
        }
        List<JSONObject> notices = DataHub.getInstance(getApplicationContext())
                .getRecordsByType(storeId, "app_notification");
        if (notices.isEmpty()) {
            managementNotice.setVisibility(View.GONE);
            return;
        }
        JSONObject payload = notices.get(0).optJSONObject("payload");
        String message = payload == null ? "" : payload.optString("message", "");
        if (message.trim().isEmpty()) {
            managementNotice.setVisibility(View.GONE);
            return;
        }
        managementNotice.setText("แจ้งเตือน: " + message);
        managementNotice.setVisibility(View.VISIBLE);
    }

    private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private void addImageMenu(
            LinearLayout row,
            String imageName,
            String label) {

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(3), dp(3), dp(3), dp(3));

        ImageView image = new ImageView(this);

        int imageId = getResources().getIdentifier(
                imageName,
                "drawable",
                getPackageName()
        );

        if (imageId != 0) {
            image.setImageResource(imageId);
        }

        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);

        tile.addView(
                image,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(145)
                )
        );

        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextColor(Color.rgb(35, 45, 55));
        caption.setTextSize(14);
        caption.setGravity(Gravity.CENTER);
        caption.setMaxLines(2);

        tile.addView(
                caption,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        row.addView(
                tile,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        if ("จัดการร้านค้า".equals(label)) {
            tile.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, ManageStoreScreen.class)));
        } else if ("ขายสินค้า".equals(label)) {
            tile.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, SellScreen.class)));
        } else if ("คลังสินค้า".equals(label)) {
            tile.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, StockScreen.class)));
        }
    }

    private void addSpace(
            LinearLayout parent,
            int height) {

        View space = new View(this);

        parent.addView(
                space,
                new LinearLayout.LayoutParams(
                        1,
                        dp(height)
                )
        );
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
