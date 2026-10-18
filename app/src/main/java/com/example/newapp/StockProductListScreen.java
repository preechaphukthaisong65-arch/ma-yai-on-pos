
package com.example.newapp;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Locale;

/**
 * หน้ารายการสินค้าทั้งหมดในคลัง
 *
 * แสดงเฉพาะชื่อสินค้าและจำนวนคงเหลือ
 * เรียงจำนวนคงเหลือจากน้อยไปมาก
 * ใช้ Cursor จากฐานข้อมูลเดิมเพื่อรองรับรายการจำนวนมาก
 */
public class StockProductListScreen extends Activity {

    private ProductRepository productRepository;
    private ListView productList;
    private TextView emptyMessage;
    private ProductCursorAdapter adapter;
    private Cursor productCursor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        productRepository = new ProductRepository(this);

        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(8));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("สินค้าทั้งหมดในคลัง");
        title.setTextSize(23);
        title.setTextColor(Color.rgb(45, 90, 55));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, dp(8), 0, dp(12));
        root.addView(
                title,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        productList = new ListView(this);
        productList.setDividerHeight(dp(1));
        productList.setBackgroundColor(Color.WHITE);

        emptyMessage = new TextView(this);
        emptyMessage.setText("ยังไม่มีสินค้าในคลัง");
        emptyMessage.setTextSize(17);
        emptyMessage.setTextColor(Color.DKGRAY);
        emptyMessage.setGravity(Gravity.CENTER);
        emptyMessage.setVisibility(View.GONE);

        root.addView(
                emptyMessage,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(100)
                )
        );

        root.addView(
                productList,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        adapter = new ProductCursorAdapter();
        productList.setAdapter(adapter);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadProducts();
    }

    private void reloadProducts() {
        Cursor newCursor = null;

        try {
            newCursor = productRepository
                    .getAllProductsByStockAscending();

            Cursor oldCursor = productCursor;
            productCursor = newCursor;
            adapter.changeCursor(newCursor);
            newCursor = null;

            if (oldCursor != null && !oldCursor.isClosed()) {
                oldCursor.close();
            }

            boolean hasProducts =
                    productCursor != null
                            && productCursor.getCount() > 0;

            emptyMessage.setVisibility(
                    hasProducts ? View.GONE : View.VISIBLE
            );
            productList.setVisibility(
                    hasProducts ? View.VISIBLE : View.GONE
            );

        } catch (Exception e) {
            if (newCursor != null && !newCursor.isClosed()) {
                newCursor.close();
            }

            emptyMessage.setText(
                    "โหลดรายการสินค้าไม่สำเร็จ กรุณากลับเข้าหน้านี้อีกครั้ง"
            );
            emptyMessage.setVisibility(View.VISIBLE);
            productList.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        if (adapter != null) {
            adapter.changeCursor(null);
        }

        if (productCursor != null && !productCursor.isClosed()) {
            productCursor.close();
        }

        productCursor = null;
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    /**
     * Adapter อ่านข้อมูลจาก Cursor ทีละแถว
     * ไม่สร้างรายการ Product ทั้งหมดไว้ใน ArrayList
     */
    private class ProductCursorAdapter extends BaseAdapter {

        private final int nameColor = Color.rgb(35, 35, 35);
        private final int quantityColor = Color.rgb(45, 90, 55);

        private int nameColumn = -1;
        private int quantityColumn = -1;
        private int idColumn = -1;

        void changeCursor(Cursor cursor) {
            if (cursor == null) {
                nameColumn = -1;
                quantityColumn = -1;
                idColumn = -1;
                notifyDataSetChanged();
                return;
            }

            nameColumn = cursor.getColumnIndex("name");
            quantityColumn = cursor.getColumnIndex("stock_qty");
            idColumn = cursor.getColumnIndex("id");

            if (nameColumn < 0
                    || quantityColumn < 0
                    || idColumn < 0) {
                throw new IllegalStateException(
                        "คอลัมน์ข้อมูลสินค้าไม่ครบ"
                );
            }

            notifyDataSetChanged();
        }

        private Cursor getCurrentCursor() {
            return productCursor;
        }

        @Override
        public int getCount() {
            Cursor cursor = getCurrentCursor();
            return cursor == null || cursor.isClosed()
                    ? 0 : cursor.getCount();
        }

        @Override
        public Object getItem(int position) {
            Cursor cursor = getCurrentCursor();

            if (cursor == null
                    || cursor.isClosed()
                    || !cursor.moveToPosition(position)) {
                return null;
            }

            return cursor;
        }

        @Override
        public long getItemId(int position) {
            Cursor cursor = getCurrentCursor();

            if (cursor == null
                    || cursor.isClosed()
                    || !cursor.moveToPosition(position)
                    || idColumn < 0) {
                return 0;
            }

            return cursor.getLong(idColumn);
        }

        @Override
        public View getView(
                int position,
                View convertView,
                ViewGroup parent
        ) {
            RowHolder holder;

            if (convertView == null) {
                LinearLayout row = new LinearLayout(
                        StockProductListScreen.this
                );
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(
                        dp(8), dp(12), dp(8), dp(12)
                );

                TextView nameView = new TextView(
                        StockProductListScreen.this
                );
                nameView.setTextSize(17);
                nameView.setTextColor(nameColor);
                nameView.setGravity(Gravity.CENTER_VERTICAL);
                nameView.setMaxLines(2);

                LinearLayout.LayoutParams nameParams =
                        new LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1
                        );

                row.addView(nameView, nameParams);

                TextView quantityView = new TextView(
                        StockProductListScreen.this
                );
                quantityView.setTextSize(17);
                quantityView.setTextColor(quantityColor);
                quantityView.setGravity(
                        Gravity.CENTER_VERTICAL | Gravity.RIGHT
                );
                quantityView.setMinWidth(dp(76));

                row.addView(
                        quantityView,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );

                holder = new RowHolder();
                holder.name = nameView;
                holder.quantity = quantityView;

                row.setTag(holder);
                convertView = row;
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            Cursor cursor = getCurrentCursor();

            if (cursor != null
                    && !cursor.isClosed()
                    && cursor.moveToPosition(position)) {

                String name = cursor.getString(nameColumn);
                int quantity = cursor.getInt(quantityColumn);

                holder.name.setText(
                        name == null ? "" : name
                );

                holder.quantity.setText(
                        String.format(
                                Locale.getDefault(),
                                "%,d",
                                quantity
                        )
                );
            }

            return convertView;
        }
    }

    private static class RowHolder {
        TextView name;
        TextView quantity;
    }
}
