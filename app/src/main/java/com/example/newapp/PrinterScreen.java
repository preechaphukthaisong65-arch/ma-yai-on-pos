package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

/** Printer integration is intentionally deferred by the project owner. */
public final class PrinterScreen extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = POSUi.page(this, "เครื่องพิมพ์");
        root.addView(POSUi.text(this,
                "ระบบเชื่อมต่อและสั่งพิมพ์ยังไม่เปิดใช้งานตามขอบเขตงานปัจจุบัน", 16));
        POSUi.button(this, root, "กลับ", this::finish);
    }
}
