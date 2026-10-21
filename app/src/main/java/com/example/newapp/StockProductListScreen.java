package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class StockProductListScreen extends Activity {
    private static final int PAGE_SIZE = 100;
    private StockSalesRepository repo;
    private List<JSONObject> allItems = new ArrayList<>();
    private EditText searchInput;
    private ListView list;
    private TextView pageInfo;
    private android.widget.Button previousButton, nextButton;
    private int page = 0;
    private List<JSONObject> filtered = new ArrayList<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        repo = new StockSalesRepository(this);
        allItems = repo.products();
        // The list is strictly ordered by quantity ascending, then name.
        allItems.sort(Comparator.comparingDouble((JSONObject r) -> repo.stock(r))
                .thenComparing(r -> repo.name(r), String.CASE_INSENSITIVE_ORDER));
        render();
        applySearch();
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(POSUi.dp(this, 12), POSUi.dp(this, 8), POSUi.dp(this, 12), POSUi.dp(this, 8));
        root.setBackgroundColor(android.graphics.Color.WHITE);
        TextView heading = POSUi.text(this, "สินค้าทั้งหมด", 22);
        heading.setGravity(android.view.Gravity.CENTER);
        root.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        searchInput = POSUi.input(this, root, "ค้นหาชื่อสินค้าหรือบาร์โค้ด");
        POSUi.button(this, root, "ค้นหา", this::applySearch);
        POSUi.button(this, root, "สแกนบาร์โค้ดเพื่อดูรายละเอียด", this::scan);

        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout paging = new LinearLayout(this);
        paging.setOrientation(LinearLayout.HORIZONTAL);
        previousButton = new android.widget.Button(this);
        previousButton.setText("ก่อนหน้า");
        previousButton.setOnClickListener(v -> {
            if (page > 0) {
                page--;
                showPage();
            }
        });
        nextButton = new android.widget.Button(this);
        nextButton.setText("ถัดไป");
        nextButton.setOnClickListener(v -> {
            int maxPage = Math.max(0, (filtered.size() - 1) / PAGE_SIZE);
            if (page < maxPage) {
                page++;
                showPage();
            }
        });
        paging.addView(previousButton, new LinearLayout.LayoutParams(0, -2, 1f));
        paging.addView(nextButton, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(paging);
        pageInfo = POSUi.text(this, "", 13);
        root.addView(pageInfo);
        POSUi.button(this, root, "กลับ", this::finish);
        setContentView(root);
    }

    private void applySearch() {
        String q = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        filtered = new ArrayList<>();
        for (JSONObject item : allItems) {
            String name = repo.name(item).toLowerCase(Locale.ROOT);
            String barcode = repo.barcode(item).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || name.contains(q) || barcode.contains(q)) filtered.add(item);
        }
        page = 0;
        showPage();
    }

    private void showPage() {
        int totalPages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int start = Math.min(page * PAGE_SIZE, filtered.size());
        int end = Math.min(start + PAGE_SIZE, filtered.size());
        List<String> labels = new ArrayList<>();
        for (int i = start; i < end; i++) {
            JSONObject item = filtered.get(i);
            // The list intentionally shows only the product name and quantity.
            labels.add(repo.name(item) + " — " + format(repo.stock(item)));
        }
        if (labels.isEmpty()) labels.add("ไม่พบสินค้าที่ตรงกับคำค้นหา");
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int position, View convert, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convert, parent);
                row.setTextSize(16);
                row.setPadding(POSUi.dp(StockProductListScreen.this, 8),
                        POSUi.dp(StockProductListScreen.this, 10),
                        POSUi.dp(StockProductListScreen.this, 8),
                        POSUi.dp(StockProductListScreen.this, 10));
                return row;
            }
        };
        list.setAdapter(adapter);
        pageInfo.setText(filtered.isEmpty() ? "ไม่พบรายการ" :
                "หน้า " + (page + 1) + " / " + totalPages + " — พบ " + filtered.size() + " รายการ");
        previousButton.setEnabled(page > 0);
        nextButton.setEnabled(page + 1 < totalPages);
    }

    private String format(double n) {
        return Math.abs(n - Math.rint(n)) < 0.000001d
                ? Long.toString(Math.round(n)) : String.format(Locale.US, "%.3f", n);
    }

    private void scan() {
        IntentIntegrator i = new IntentIntegrator(this);
        i.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        i.setPrompt("สแกนบาร์โค้ดสินค้า");
        i.setBeepEnabled(false);
        i.setOrientationLocked(true);
        i.initiateScan();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult r = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (r == null || r.getContents() == null) return;
        JSONObject p = repo.findByBarcode(r.getContents());
        if (p == null) {
            Toast.makeText(this, "ไม่พบสินค้า", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, repo.name(p) + "\\nบาร์โค้ด: " + repo.barcode(p)
                + "\\nราคาขาย: " + format(repo.price(p)) + " บาท"
                + "\\nต้นทุน: " + format(repo.payload(p).optDouble("cost", 0)) + " บาท"
                + "\\nคงเหลือ: " + format(repo.stock(p)) + " " + repo.unit(p),
                Toast.LENGTH_LONG).show();
    }
}
