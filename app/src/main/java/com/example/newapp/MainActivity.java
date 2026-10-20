
package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
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
import com.example.newapp.datahub.DeviceSessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import org.json.JSONObject;
import java.util.List;

public class MainActivity extends Activity {
    private TextView managementNotice;
    private ListenerRegistration deviceSessionListener;
    private boolean sessionRedirecting;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean reconciliationRunning;

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
        RetentionScheduler.schedule(getApplicationContext());

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
        DebtReminderScheduler.scheduleNext(this);
        setupSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupSystemBars();
        refreshManagementNotice();
        DebtReminderScheduler.scheduleNext(this);
        verifyActiveDeviceSession();
        registerNetworkReconciliation();
    }

    private void verifyActiveDeviceSession() {
        if (sessionRedirecting) return;
        String storeId = StoreIdentity.get(this);
        String ownerUid = StoreIdentity.getOwnerUid(this);
        if (storeId.isEmpty() || ownerUid.isEmpty()) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        if (deviceSessionListener != null) deviceSessionListener.remove();
        String deviceId = StoreIdentity.getOrCreateDeviceId(getApplicationContext());
        deviceSessionListener = new DeviceSessionManager().watch(storeId, deviceId, active -> runOnUiThread(() -> {
            if (active || isFinishing() || sessionRedirecting) return;
            sessionRedirecting = true;
            if (deviceSessionListener != null) {
                deviceSessionListener.remove();
                deviceSessionListener = null;
            }
            FirebaseAuth.getInstance().signOut();
            android.widget.Toast.makeText(this,
                    "เครื่องนี้ถูกแทนที่ด้วยการเข้าสู่ระบบจากอีกเครื่อง ข้อมูลในเครื่องยังคงเก็บไว้",
                    android.widget.Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, StartupActivity.class));
            finish();
        }));
    }

    private void registerNetworkReconciliation() {
        if (networkCallback != null) return;
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                reconcileCloudAfterReconnect();
            }
        };
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException error) {
            networkCallback = null;
        }
    }

    private void reconcileCloudAfterReconnect() {
        if (reconciliationRunning) return;
        String storeId = StoreIdentity.get(this);
        String ownerUid = StoreIdentity.getOwnerUid(this);
        if (storeId.isEmpty() || ownerUid.isEmpty()
                || com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null
                || !ownerUid.equals(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid())) {
            return;
        }
        reconciliationRunning = true;
        DataHub hub = DataHub.getInstance(getApplicationContext());
        hub.restoreMissingRecords(storeId, (restored, skipped, failed, message) -> runOnUiThread(() -> {
            reconciliationRunning = false;
            int duplicateBarcodes = hub.getDuplicateBarcodeCount(storeId);
            if (failed > 0 || duplicateBarcodes > 0) {
                String warning = failed > 0
                        ? "พบข้อมูลในเครื่องกับ Cloud ขัดแย้งกัน จึงหยุดซิงก์เพื่อรักษาข้อมูลเดิม"
                        : "พบบาร์โค้ดซ้ำ " + duplicateBarcodes + " รายการ จึงหยุดซิงก์ไว้ก่อน";
                android.widget.Toast.makeText(MainActivity.this, warning, android.widget.Toast.LENGTH_LONG).show();
                refreshManagementNotice();
                return;
            }
            hub.backupPendingRecords(storeId, (succeeded, uploadFailed, syncMessage) -> runOnUiThread(() -> {
                if (uploadFailed > 0) {
                    android.widget.Toast.makeText(MainActivity.this,
                            syncMessage + " (สำเร็จ " + succeeded + ", ไม่สำเร็จ " + uploadFailed + ")",
                            android.widget.Toast.LENGTH_LONG).show();
                }
                refreshManagementNotice();
            }));
        }));
    }

    @Override
    protected void onDestroy() {
        if (deviceSessionListener != null) {
            deviceSessionListener.remove();
            deviceSessionListener = null;
        }
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (RuntimeException ignored) { }
            networkCallback = null;
        }
        super.onDestroy();
    }

    private void refreshManagementNotice() {
        if (managementNotice == null) return;
        String storeId = StoreIdentity.get(this);
        if (storeId.isEmpty()) {
            managementNotice.setVisibility(View.GONE);
            return;
        }
        StockSalesRepository stockRepository = new StockSalesRepository(this);
        if (!StockLimitNotifier.isReconciled(this, storeId)) {
            StockLimitNotifier.reconcileIfNeeded(this, storeId, stockRepository.products());
        }
        StringBuilder summary = new StringBuilder();
        if (StockLimitNotifier.hasAnyLowStock(this, storeId)) {
            summary.append("มีสินค้า ต่ำกว่า ลิมิตสต๊อก");
        }
        String debtWarning = overdueDebtSummary(storeId);
        if (!debtWarning.isEmpty()) {
            if (summary.length() > 0) summary.append(" | ");
            summary.append(debtWarning);
        }
        DataHub dataHub = DataHub.getInstance(getApplicationContext());
        int pendingBackups = dataHub.getPendingBackupCount(storeId);
        if (pendingBackups > 0) {
            if (summary.length() > 0) summary.append(" | ");
            summary.append("สำรองข้อมูลค้าง ").append(pendingBackups).append(" รายการ — แตะข้อความนี้เพื่อลองซิงก์อีกครั้ง");
            managementNotice.setTextColor(Color.rgb(170, 80, 0));
            managementNotice.setOnClickListener(v -> {
                managementNotice.setText("กำลังซิงก์ข้อมูลที่ค้าง...");
                dataHub.backupPendingRecords(storeId, (succeeded, failed, message) -> runOnUiThread(() -> {
                    android.widget.Toast.makeText(MainActivity.this,
                            message + " (สำเร็จ " + succeeded + ", ไม่สำเร็จ " + failed + ")",
                            android.widget.Toast.LENGTH_LONG).show();
                    refreshManagementNotice();
                }));
            });
        } else {
            managementNotice.setTextColor(Color.rgb(45, 90, 55));
            managementNotice.setOnClickListener(null);
        }
        int duplicateBarcodes = dataHub.getDuplicateBarcodeCount(storeId);
        if (duplicateBarcodes > 0) {
            if (summary.length() > 0) summary.append(" | ");
            summary.append("⚠ พบบาร์โค้ดซ้ำ ").append(duplicateBarcodes)
                    .append(" รายการ — เก็บข้อมูลเดิมไว้ กรุณาตรวจสอบก่อนใช้บาร์โค้ดเหล่านี้");
            managementNotice.setTextColor(Color.rgb(170, 80, 0));
        }
        List<JSONObject> notices = DataHub.getInstance(getApplicationContext())
                .getRecordsByType(storeId, "app_notification");
        if (!notices.isEmpty()) {
            JSONObject payload = notices.get(0).optJSONObject("payload");
            String message = payload == null ? "" : payload.optString("message", "");
            if (!message.trim().isEmpty()) {
                if (summary.length() > 0) summary.append(" | ");
                summary.append("แจ้งเตือน: ").append(message);
            }
        }
        if (summary.length() == 0) {
            managementNotice.setVisibility(View.GONE);
            return;
        }
        managementNotice.setText(summary.toString());
        managementNotice.setVisibility(View.VISIBLE);
    }

    private String overdueDebtSummary(String storeId) {
        long cutoff = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L;
        StringBuilder text = new StringBuilder();
        for (JSONObject bill : DataHub.getInstance(getApplicationContext()).getRecordsByType(storeId, "sale_bill")) {
            JSONObject p = bill.optJSONObject("payload");
            if (p == null || !"credit".equals(p.optString("paymentType"))
                    || !"ค้างชำระ".equals(p.optString("paymentStatus"))) continue;
            long createdAt = p.optLong("createdAt", 0L);
            if (createdAt <= 0L || createdAt > cutoff) continue;
            if (text.length() > 0) text.append(", ");
            text.append(p.optString("customerName", "ไม่ระบุชื่อลูกค้า"))
                    .append(" ").append(String.format(java.util.Locale.US, "%.2f",
                            Math.max(0d, p.optDouble("remainingBalance", p.optDouble("total", 0d)))))
                    .append(" บาท");
            if (text.length() > 180) { text.append("…"); break; }
        }
        return text.length() == 0 ? "" : "หนี้เกิน 30 วัน: " + text;

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
        } else if ("สร้างบาร์โค้ด".equals(label)) {
            tile.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, BarcodeScreen.class)));
        } else if ("ประวัติการขาย".equals(label)) {
            tile.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, HistoryScreen.class)));
        } else if ("รายงานยอดขาย".equals(label)) {
            tile.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, ReportScreen.class)));
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
