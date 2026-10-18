package com.example.newapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends BaseScreen {

    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int GREEN = Color.rgb(16, 185, 129);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int ORANGE = Color.rgb(217, 119, 6);
    private static final int DARK = Color.rgb(31, 41, 55);
    private static final int GRAY = Color.rgb(107, 114, 128);
    private static final int WHITE = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createBackground();
        buildHomeScreen();

        // ตรวจสอบและกู้คืนข้อมูลอัตโนมัติจากคลาวด์เมื่อเปิดแอป (กรณีเปลี่ยนเครื่องหรือติดตั้งใหม่)
        checkAndRestoreDataOnStartup();
    }

    private void buildHomeScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(20), dp(20), dp(24));

        scroll.addView(page);

        ViewGroup content = findViewById(android.R.id.content);
        View rootView = content.getChildCount() > 0 ? content.getChildAt(0) : null;

        if (rootView instanceof ViewGroup) {
            ((ViewGroup) rootView).addView(
                    scroll,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        } else {
            content.addView(
                    scroll,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
        }

        // ส่วนหัวข้อร้าน
        page.addView(text("ร้านแม่ใหญ่อ่อน POS", 26, DARK, true));
        page.addView(text("ระบบจัดการร้านค้าและขายหน้าร้าน (Offline-First)", 14, GRAY, false));

        addSpace(page, 24);

        // เมนูปุ่มกดไปยังหน้าต่างๆ
        page.addView(createMenuButton("🛒  ระบบขายหน้าร้าน (POS)", GREEN, v -> {
            startActivity(new Intent(MainActivity.this, SalesScreen.class));
        }));

        addSpace(page, 12);

        page.addView(createMenuButton("📦  จัดการสต็อก / คลังสินค้า", BLUE, v -> {
            startActivity(new Intent(MainActivity.this, StockScreen.class));
        }));

        addSpace(page, 12);

        page.addView(createMenuButton("🖨️  สร้างและพิมพ์บาร์โค้ด", PURPLE, v -> {
            startActivity(new Intent(MainActivity.this, BarcodeScreen.class));
        }));

        addSpace(page, 12);

        page.addView(createMenuButton("📊  รายงานยอดขายและประวัติ", ORANGE, v -> {
            startActivity(new Intent(MainActivity.this, ReportScreen.class));
        }));
    }

    private void checkAndRestoreDataOnStartup() {
        SyncManager syncManager = new SyncManager(this);
        if (syncManager.isOnline()) {
            syncManager.restoreDataFromCloud((success, message) -> {
                runOnUiThread(() -> {
                    // แจ้งเตือนสถานะการซิงก์/กู้คืนข้อมูลเบื้องหลัง
                    if (success && !message.contains("ไม่พบข้อมูล")) {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }
    }

    private Button createMenuButton(String label, int bgColor, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(WHITE);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(20), dp(0), dp(20), dp(0));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
        b.setOnClickListener(listener);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)
        );
        b.setLayoutParams(params);
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }
        return view;
    }

    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }
}
