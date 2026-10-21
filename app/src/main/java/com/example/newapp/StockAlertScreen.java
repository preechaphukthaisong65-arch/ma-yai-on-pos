package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

/** Low-stock alerts intentionally reveal details only in the all-products screen. */
public final class StockAlertScreen extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = POSUi.page(this, "แจ้งเตือนลิมิตสต๊อก");
        root.addView(POSUi.text(this, "มีสินค้า ต่ำกว่า ลิมิตสต๊อก", 16));
        POSUi.button(this, root, "ดูรายละเอียดในสินค้าทั้งหมด", () ->
                startActivity(new Intent(this, StockProductListScreen.class)));
        POSUi.button(this, root, "กลับ", this::finish);
    }
}
