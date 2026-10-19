
package com.example.newapp;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class StockAlertScreen extends Activity {

    private LocalDatabase database;
    private LinearLayout list;
    private TextView summary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        database = LocalDatabase.getInstance(this);
        buildScreen();
        loadLowStock();
    }

    private int dp(float value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xFF202020);

        if (bold) {
            view.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );
        }

        return view;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(16));
        root.setBackgroundColor(0xFFF7F7F7);

        root.addView(
                text("สินค้าใกล้หมดหรือถึงลิมิตสต๊อก", 22, true)
        );

        summary = text("", 15, true);

        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(8);

        root.addView(summary, summaryParams);

        Button refresh = new Button(this);
        refresh.setText("ตรวจสอบใหม่");
        refresh.setAllCaps(false);
        refresh.setOnClickListener(v -> loadLowStock());
        root.addView(refresh);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams listParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        listParams.topMargin = dp(8);
        root.addView(list, listParams);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void loadLowStock() {
        list.removeAllViews();
        summary.setText("กำลังตรวจสอบสต๊อก...");

        int count = 0;

        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT name, stock_qty, min_stock " +
                "FROM products " +
                "WHERE stock_qty <= min_stock " +
                "ORDER BY stock_qty ASC, name COLLATE NOCASE ASC",
                null
        )) {
            while (cursor.moveToNext()) {
                count++;

                String name = cursor.getString(0);
                int quantity = cursor.getInt(1);
                int limit = cursor.getInt(2);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(
                        dp(12), dp(10), dp(12), dp(10)
                );
                card.setBackgroundColor(0xFFFFF1F0);

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(-1, -2);
                params.topMargin = dp(5);
                params.bottomMargin = dp(5);

                card.addView(text(
                        name == null || name.trim().isEmpty()
                                ? "สินค้าไม่ระบุชื่อ"
                                : name,
                        16,
                        true
                ));

                card.addView(text(
                        "คงเหลือ: " + quantity,
                        15,
                        true
                ));

                card.addView(text(
                        "ลิมิตสต๊อก: " + limit,
                        14,
                        false
                ));

                card.addView(text(
                        quantity == 0
                                ? "สถานะ: สินค้าหมด"
                                : quantity < limit
                                ? "สถานะ: ต่ำกว่าลิมิต"
                                : "สถานะ: ถึงลิมิต",
                        14,
                        true
                ));

                list.addView(card, params);
            }

        } catch (Exception e) {
            summary.setText("อ่านข้อมูลสต๊อกไม่สำเร็จ");

            list.addView(text(
                    "ไม่สามารถโหลดรายการสินค้าใกล้หมดได้ " +
                    "กรุณาลองตรวจสอบใหม่",
                    14,
                    false
            ));
            return;
        }

        summary.setText(
                "พบสินค้าใกล้หมดหรือถึงลิมิต " + count + " รายการ"
        );

        if (count == 0) {
            list.addView(text(
                    "ไม่มีสินค้าที่ถึงลิมิตสต๊อกในขณะนี้",
                    16,
                    false
            ));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (list != null) {
            loadLowStock();
        }
    }
}
