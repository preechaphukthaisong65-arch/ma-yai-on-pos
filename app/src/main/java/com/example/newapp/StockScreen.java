package com.example.newapp;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class StockScreen extends BaseScreen {

    private static final int BLUE = 0xFF2457C5;
    private static final int DARK = 0xFF182230;
    private static final int BG = 0xFFF3F6FB;
    private static final int GREEN = 0xFF16845B;
    private static final int RED = 0xFFD94343;
    private static final int ORANGE = 0xFFE58A19;
    private static final int WHITE = Color.WHITE;

    private LinearLayout page;
    private LinearLayout content;
    private ProductRepository repository;
    private LocalDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = new ProductRepository(this);
        database = new LocalDatabase(this);

        createBackground();

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);

        root.addView(
                page,
                new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        showMenu();
    }

    // =====================================================
    // ส่วนประกอบหน้าจอ
    // =====================================================

    private void preparePage(String title) {
        page.removeAllViews();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackgroundColor(WHITE);

        TextView back = text("‹", 34, BLUE, true);
        back.setGravity(Gravity.CENTER);

        header.addView(
                back,
                new LinearLayout.LayoutParams(dp(44), dp(48))
        );

        back.setOnClickListener(v -> showMenu());

        TextView heading = text(title, 20, DARK, true);

        header.addView(
                heading,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        page.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(0xFFE0E6EF);

        page.addView(
                divider,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1)
                )
        );

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(24));

        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        page.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(
            String value,
            int size,
            int color,
            boolean bold
    ) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);

        if (bold) {
            t.setTypeface(null, Typeface.BOLD);
        }

        return t;
    }

    private GradientDrawable rounded(
            int color,
            int radius,
            int strokeColor
    ) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));

        if (strokeColor != 0) {
            d.setStroke(dp(1), strokeColor);
        }

        return d;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(rounded(WHITE, 16, 0xFFE3E9F2));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(12);
        box.setLayoutParams(params);

        return box;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(rounded(color, 12, 0));
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        return b;
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setTextColor(DARK);
        e.setHintTextColor(0xFF8B96A7);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(rounded(WHITE, 10, 0xFFD4DCE8));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(52)
                );

        params.bottomMargin = dp(10);
        e.setLayoutParams(params);

        return e;
    }

    private void label(String value) {
        TextView t = text(value, 14, DARK, true);
        t.setPadding(0, dp(4), 0, dp(6));
        content.addView(t);
    }

    private void message(String value) {
        TextView t = text(value, 13, 0xFF64748B, false);
        t.setPadding(0, dp(4), 0, dp(12));
        content.addView(t);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private String value(EditText e) {
        return e.getText().toString().trim();
    }

    private int parseInt(EditText e, String fieldName) {
        try {
            int result = Integer.parseInt(value(e));

            if (result < 0) {
                throw new NumberFormatException();
            }

            return result;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    fieldName + " ต้องเป็นจำนวนเต็มตั้งแต่ศูนย์ขึ้นไป"
            );
        }
    }

    private double parsePrice(EditText e) {
        try {
            double result = Double.parseDouble(value(e));

            if (Double.isNaN(result)
                    || Double.isInfinite(result)
                    || result < 0) {
                throw new NumberFormatException();
            }

            return result;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "กรุณากรอกราคาขายให้ถูกต้อง"
            );
        }
    }

    private String money(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    private String now() {
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
        ).format(new Date());
    }

    // =====================================================
    // 1. เมนูคลังสินค้า
    // =====================================================

    private void showMenu() {
        preparePage("คลังสินค้า");

        LinearLayout summary = card();
        summary.setBackground(rounded(BLUE, 16, 0));

        summary.addView(
                text("จัดการสินค้าและสต็อก", 20, WHITE, true)
        );

        summary.addView(
                text("ระบบใช้ราคาขายเท่านั้น", 14, 0xFFE7EEFF, false)
        );

        content.addView(summary);

        addMenuItem(
                "📦",
                "สินค้าทั้งหมด",
                "ค้นหาสินค้าและดูจำนวนคงเหลือ",
                this::showProducts
        );

        addMenuItem(
                "＋",
                "เพิ่มสินค้า",
                "เพิ่มสินค้าและสร้างบาร์โค้ดอัตโนมัติ",
                this::showAddProduct
        );

        addMenuItem(
                "↓",
                "รับสินค้าเข้า",
                "เพิ่มสินค้าเข้าคลังพร้อมบันทึกประวัติ",
                () -> showReceiveStock(null)
        );

        addMenuItem(
                "✓",
                "ตรวจนับสต็อก",
                "บันทึกจำนวนจริงและปรับยอดในระบบ",
                this::showStockCount
        );

        addMenuItem(
                "⚙",
                "ตั้งค่าสต็อกขั้นต่ำ",
                "ตั้งเกณฑ์เตือนสินค้าใกล้หมด",
                this::showMinStock
        );

        ArrayList<ProductRepository.Product> low =
                repository.getLowStockProducts();

        LinearLayout alert = card();

        alert.addView(
                text(
                        low.isEmpty()
                                ? "✓ ยังไม่มีสินค้าใกล้หมด"
                                : "⚠ สินค้าใกล้หมด " + low.size() + " รายการ",
                        16,
                        low.isEmpty() ? GREEN : ORANGE,
                        true
                )
        );

        content.addView(alert);
    }

    private void addMenuItem(
            String icon,
            String title,
            String subtitle,
            Runnable action
    ) {
        LinearLayout item = card();
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);

        TextView iconView = text(icon, 25, BLUE, true);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(rounded(0xFFEAF0FF, 12, 0));

        item.addView(
                iconView,
                new LinearLayout.LayoutParams(dp(54), dp(54))
        );

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(6), 0);

        info.addView(text(title, 16, DARK, true));
        info.addView(text(subtitle, 12, 0xFF64748B, false));

        item.addView(
                info,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        item.addView(text("›", 28, BLUE, true));

        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> action.run());

        content.addView(item);
    }

    // =====================================================
    // 2. สินค้าทั้งหมด
    // =====================================================

    private void showProducts() {
        preparePage("สินค้าทั้งหมด");

        EditText search = field(
                "ค้นหาชื่อสินค้า / บาร์โค้ด",
                android.text.InputType.TYPE_CLASS_TEXT
        );

        content.addView(search);

        TextView count = text("", 13, 0xFF64748B, false);
        content.addView(count);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        Runnable refresh = () -> {
            list.removeAllViews();

            ArrayList<ProductRepository.Product> products =
                    repository.getActiveProducts();

            String query = value(search).toLowerCase(Locale.ROOT);
            int visible = 0;

            for (ProductRepository.Product p : products) {
                String name = p.name == null ? "" : p.name;
                String barcode = p.barcode == null ? "" : p.barcode;

                if (!name.toLowerCase(Locale.ROOT).contains(query)
                        && !barcode.toLowerCase(Locale.ROOT).contains(query)) {
                    continue;
                }

                visible++;
                addProductCard(list, p);
            }

            count.setText("พบ " + visible + " รายการ");

            if (visible == 0) {
                TextView empty = text(
                        "ไม่พบสินค้า",
                        16,
                        0xFF64748B,
                        false
                );
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(28), 0, dp(28));
                list.addView(empty);
            }
        };

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                refresh.run();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        Button add = button("+ เพิ่มสินค้า", BLUE);
        add.setOnClickListener(v -> showAddProduct());
        content.addView(add);

        refresh.run();
    }

    private void addProductCard(
            LinearLayout list,
            ProductRepository.Product p
    ) {
        LinearLayout box = card();

        box.addView(text(p.name, 17, DARK, true));

        box.addView(
                text("บาร์โค้ด: " + p.barcode, 13, 0xFF64748B, false)
        );

        box.addView(
                text("ราคาขาย ฿" + money(p.price), 15, BLUE, true)
        );

        box.addView(
                text(
                        "คงเหลือ " + p.stock + " " + p.unit,
                        15,
                        p.isLowStock() ? RED : GREEN,
                        true
                )
        );

        if (p.isLowStock()) {
            box.addView(
                    text(
                            "⚠ ถึงเกณฑ์สต็อกขั้นต่ำ (" + p.minStock + ")",
                            12,
                            RED,
                            true
                    )
            );
        } else {
            box.addView(
                    text(
                            "สต็อกขั้นต่ำ " + p.minStock,
                            12,
                            0xFF64748B,
                            false
                    )
            );
        }

        Button edit = button("แก้ไขข้อมูลสินค้า", BLUE);
        edit.setOnClickListener(v -> showEditProduct(p.id));
        box.addView(edit);

        Button receive = button("รับสินค้าเข้า", GREEN);
        receive.setOnClickListener(v -> showReceiveStock(p.barcode));
        box.addView(receive);

        list.addView(box);
    }

    // =====================================================
    // 3. เพิ่มสินค้า
    // =====================================================

    private void showAddProduct() {
        preparePage("เพิ่มสินค้า");

        message("กรอกชื่อและราคาขาย ระบบสร้างบาร์โค้ดให้อัตโนมัติ");

        label("ชื่อสินค้า");
        EditText name = field(
                "เช่น น้ำดื่ม 600 มล.",
                android.text.InputType.TYPE_CLASS_TEXT
        );
        content.addView(name);

        label("ราคาขาย (บาท)");
        EditText price = field(
                "0.00",
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        content.addView(price);

        label("จำนวนเริ่มต้น");
        EditText stock = field(
                "0",
                android.text.InputType.TYPE_CLASS_NUMBER
        );
        content.addView(stock);

        label("สต็อกขั้นต่ำ");
        EditText min = field(
                "5",
                android.text.InputType.TYPE_CLASS_NUMBER
        );
        content.addView(min);

        label("หน่วยสินค้า");
        EditText unit = field(
                "ชิ้น / ขวด / แพ็ก / กิโลกรัม",
                android.text.InputType.TYPE_CLASS_TEXT
        );
        unit.setText("ชิ้น");
        content.addView(unit);

        Button save = button("บันทึกสินค้า", BLUE);
        content.addView(save);

        save.setOnClickListener(v -> {
            try {
                if (value(name).isEmpty()) {
                    throw new IllegalArgumentException(
                            "กรุณากรอกชื่อสินค้า"
                    );
                }

                ProductRepository.Product p =
                        repository.addProduct(
                                value(name),
                                parsePrice(price),
                                0,
                                parseInt(stock, "จำนวนเริ่มต้น"),
                                parseInt(min, "สต็อกขั้นต่ำ"),
                                null,
                                value(unit)
                        );

                new AlertDialog.Builder(this)
                        .setTitle("เพิ่มสินค้าสำเร็จ")
                        .setMessage(
                                "ชื่อสินค้า: " + p.name
                                        + "\nบาร์โค้ด: " + p.barcode
                                        + "\nราคาขาย: ฿" + money(p.price)
                                        + "\nจำนวนเริ่มต้น: " + p.stock
                        )
                        .setPositiveButton(
                                "ตกลง",
                                (dialog, which) -> showProducts()
                        )
                        .setCancelable(false)
                        .show();

            } catch (Exception ex) {
                toast(
                        ex.getMessage() == null
                                ? "บันทึกสินค้าไม่สำเร็จ"
                                : ex.getMessage()
                );
            }
        });
    }

    // =====================================================
    // 4. แก้ไขสินค้า
    // =====================================================

    private void showEditProduct(long productId) {
        ProductRepository.Product p = repository.getById(productId);

        if (p == null) {
            toast("ไม่พบสินค้า");
            showProducts();
            return;
        }

        preparePage("แก้ไขสินค้า");

        message("บาร์โค้ด: " + p.barcode);

        label("ชื่อสินค้า");
        EditText name = field(
                "ชื่อสินค้า",
                android.text.InputType.TYPE_CLASS_TEXT
        );
        name.setText(p.name);
        content.addView(name);

        label("ราคาขาย (บาท)");
        EditText price = field(
                "ราคาขาย",
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        price.setText(String.valueOf(p.price));
        content.addView(price);

        label("สต็อกขั้นต่ำ");
        EditText min = field(
                "สต็อกขั้นต่ำ",
                android.text.InputType.TYPE_CLASS_NUMBER
        );
        min.setText(String.valueOf(p.minStock));
        content.addView(min);

        label("หน่วยสินค้า");
        EditText unit = field(
                "หน่วยสินค้า",
                android.text.InputType.TYPE_CLASS_TEXT
        );
        unit.setText(p.unit);
        content.addView(unit);

        Button save = button("บันทึกการแก้ไข", BLUE);
        content.addView(save);

        save.setOnClickListener(v -> {
            try {
                if (value(name).isEmpty()) {
                    throw new IllegalArgumentException(
                            "กรุณากรอกชื่อสินค้า"
                    );
                }

                boolean success = repository.updateProduct(
                        p.id,
                        value(name),
                        parsePrice(price),
                        p.cost,
                        parseInt(min, "สต็อกขั้นต่ำ"),
                        p.imageData,
                        value(unit)
                );

                if (success) {
                    toast("บันทึกข้อมูลสินค้าแล้ว");
                    showProducts();
                } else {
                    toast("ไม่พบสินค้า");
                }

            } catch (Exception ex) {
                toast(
                        ex.getMessage() == null
                                ? "แก้ไขสินค้าไม่สำเร็จ"
                                : ex.getMessage()
                );
            }
        });
    }

    // =====================================================
    // 5. รับสินค้าเข้า
    // =====================================================

    private void showReceiveStock(String initialBarcode) {
        preparePage("รับสินค้าเข้า");

        label("บาร์โค้ดสินค้า");
        EditText barcode = field(
                "กรอกบาร์โค้ด",
                android.text.InputType.TYPE_CLASS_TEXT
        );

        if (initialBarcode != null) {
            barcode.setText(initialBarcode);
        }

        content.addView(barcode);

        Button find = button("ค้นหาสินค้า", BLUE);
        content.addView(find);

        LinearLayout details = card();
        content.addView(details);

        find.setOnClickListener(v -> {
            details.removeAllViews();

            ProductRepository.Product p =
                    repository.getByBarcode(value(barcode));

            if (p == null || p.archived) {
                details.addView(
                        text(
                                "ไม่พบสินค้า กรุณาตรวจสอบบาร์โค้ด",
                                14,
                                RED,
                                true
                        )
                );
                return;
            }

            details.addView(text(p.name, 18, DARK, true));

            details.addView(
                    text(
                            "ราคาขาย ฿" + money(p.price),
                            14,
                            BLUE,
                            true
                    )
            );

            details.addView(
                    text(
                            "จำนวนปัจจุบัน " + p.stock + " " + p.unit,
                            14,
                            DARK,
                            false
                    )
            );

            details.addView(
                    text("จำนวนที่รับเข้า", 14, DARK, true)
            );

            EditText quantity = field(
                    "จำนวนที่รับเข้า",
                    android.text.InputType.TYPE_CLASS_NUMBER
            );
            details.addView(quantity);

            Button save = button("ยืนยันรับสินค้าเข้า", GREEN);
            details.addView(save);

            save.setOnClickListener(w -> {
                try {
                    int amount = parseInt(quantity, "จำนวนรับเข้า");

                    if (amount <= 0) {
                        throw new IllegalArgumentException(
                                "จำนวนรับเข้าต้องมากกว่าศูนย์"
                        );
                    }

                    boolean success = repository.adjustStock(
                            p.id,
                            amount,
                            "RECEIVE",
                            null
                    );

                    if (success) {
                        toast("รับสินค้าเข้าเรียบร้อย");
                        showProducts();
                    } else {
                        toast("ไม่สามารถรับสินค้าเข้าได้");
                    }

                } catch (Exception ex) {
                    toast(
                            ex.getMessage() == null
                                    ? "รับสินค้าเข้าไม่สำเร็จ"
                                    : ex.getMessage()
                    );
                }
            });
        });
    }

    // =====================================================
    // 6. ตรวจนับสต็อก
    // =====================================================

    private void showStockCount() {
        preparePage("ตรวจนับสต็อก");

        message(
                "กรอกจำนวนที่นับได้จริง ระบบจะบันทึกผลต่าง " +
                "และปรับยอดหลังยืนยัน"
        );

        label("บาร์โค้ดสินค้า");
        EditText barcode = field(
                "กรอกบาร์โค้ดสินค้า",
                android.text.InputType.TYPE_CLASS_TEXT
        );
        content.addView(barcode);

        Button find = button("ค้นหาสินค้า", BLUE);
        content.addView(find);

        LinearLayout details = card();
        content.addView(details);

        find.setOnClickListener(v -> {
            details.removeAllViews();

            ProductRepository.Product p =
                    repository.getByBarcode(value(barcode));

            if (p == null || p.archived) {
                details.addView(text("ไม่พบสินค้า", 14, RED, true));
                return;
            }

            details.addView(text(p.name, 18, DARK, true));

            details.addView(
                    text(
                            "จำนวนในระบบ: " + p.stock + " " + p.unit,
                            14,
                            DARK,
                            true
                    )
            );

            TextView actualLabel = text(
                    "จำนวนที่นับได้จริง",
                    14,
                    DARK,
                    true
            );
            actualLabel.setPadding(0, dp(8), 0, dp(6));
            details.addView(actualLabel);

            EditText counted = field(
                    "จำนวนที่นับได้จริง",
                    android.text.InputType.TYPE_CLASS_NUMBER
            );
            details.addView(counted);

            Button save = button("บันทึกผลตรวจนับ", GREEN);
            details.addView(save);

            save.setOnClickListener(w -> {
                try {
                    int actual = parseInt(counted, "จำนวนที่นับได้");

                    new AlertDialog.Builder(this)
                            .setTitle("ยืนยันผลตรวจนับ")
                            .setMessage(
                                    "จำนวนเดิม: " + p.stock
                                            + "\nจำนวนที่นับได้: " + actual
                                            + "\nผลต่าง: " + (actual - p.stock)
                                            + "\n\nต้องการบันทึกและปรับสต็อกหรือไม่?"
                            )
                            .setNegativeButton("ยกเลิก", null)
                            .setPositiveButton(
                                    "ยืนยัน",
                                    (dialog, which) ->
                                            saveStockCount(p, actual)
                            )
                            .show();

                } catch (Exception ex) {
                    toast(
                            ex.getMessage() == null
                                    ? "จำนวนที่นับไม่ถูกต้อง"
                                    : ex.getMessage()
                    );
                }
            });
        });
    }

    private void saveStockCount(
            ProductRepository.Product p,
            int actual
    ) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();

        try {
            int current;

            Cursor c = db.query(
                    "products",
                    new String[]{"stock", "name", "is_archived"},
                    "id = ?",
                    new String[]{String.valueOf(p.id)},
                    null,
                    null,
                    null
            );

            try {
                if (!c.moveToFirst() || c.getInt(2) == 1) {
                    throw new IllegalStateException(
                            "ไม่พบสินค้าหรือสินค้าถูกเก็บถาวร"
                    );
                }

                current = c.getInt(0);

            } finally {
                c.close();
            }

            int difference = actual - current;

            ContentValues history = new ContentValues();
            history.put("product_id", p.id);
            history.put("product_name", p.name);
            history.put("system_stock", current);
            history.put("counted_stock", actual);
            history.put("difference", difference);
            history.put("created_at", now());

            long historyId = db.insertOrThrow(
                    "stock_count_history",
                    null,
                    history
            );

            if (difference != 0) {
                ContentValues update = new ContentValues();
                update.put("stock", actual);
                update.put("updated_at", now());

                int rows = db.update(
                        "products",
                        update,
                        "id = ? AND stock = ?",
                        new String[]{
                                String.valueOf(p.id),
                                String.valueOf(current)
                        }
                );

                if (rows != 1) {
                    throw new IllegalStateException(
                            "ยอดสต็อกเปลี่ยนระหว่างตรวจนับ กรุณาทำใหม่"
                    );
                }

                ContentValues movement = new ContentValues();
                movement.put("product_id", p.id);
                movement.put("movement_type", "STOCK_COUNT");
                movement.put("quantity", difference);
                movement.put("stock_before", current);
                movement.put("stock_after", actual);
                movement.put("reference_id", String.valueOf(historyId));
                movement.put("created_at", now());

                db.insertOrThrow(
                        "stock_movements",
                        null,
                        movement
                );
            }

            db.setTransactionSuccessful();

        } catch (Exception ex) {
            toast(
                    ex.getMessage() == null
                            ? "บันทึกผลตรวจนับไม่สำเร็จ"
                            : ex.getMessage()
            );
            return;

        } finally {
            db.endTransaction();
        }

        toast("บันทึกผลตรวจนับเรียบร้อย");
        showProducts();
    }

    // =====================================================
    // 7. ตั้งค่าสต็อกขั้นต่ำ
    // =====================================================

    private void showMinStock() {
        preparePage("ตั้งค่าสต็อกขั้นต่ำ");

        message(
                "เมื่อจำนวนคงเหลือน้อยกว่าหรือเท่ากับค่าขั้นต่ำ " +
                "ระบบจะแสดงเตือนสินค้าใกล้หมด"
        );

        EditText search = field(
                "ค้นหาชื่อสินค้า / บาร์โค้ด",
                android.text.InputType.TYPE_CLASS_TEXT
        );
        content.addView(search);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        /*
         * แก้ปัญหา variable refresh might not have been initialized:
         * ใช้ array เก็บ Runnable เพื่อให้เรียกตัวเองจาก callback ได้
         */
        final Runnable[] refresh = new Runnable[1];

        refresh[0] = () -> {
            list.removeAllViews();

            String query = value(search).toLowerCase(Locale.ROOT);

            for (ProductRepository.Product p :
                    repository.getActiveProducts()) {

                String name = p.name == null ? "" : p.name;
                String code = p.barcode == null ? "" : p.barcode;

                if (!name.toLowerCase(Locale.ROOT).contains(query)
                        && !code.toLowerCase(Locale.ROOT).contains(query)) {
                    continue;
                }

                LinearLayout box = card();

                box.addView(text(p.name, 16, DARK, true));

                box.addView(
                        text(
                                "คงเหลือ " + p.stock + " " + p.unit,
                                13,
                                p.isLowStock() ? RED : GREEN,
                                true
                        )
                );

                EditText minimum = field(
                        "จำนวนขั้นต่ำใหม่",
                        android.text.InputType.TYPE_CLASS_NUMBER
                );
                minimum.setText(String.valueOf(p.minStock));
                box.addView(minimum);

                Button save = button("บันทึกขั้นต่ำ", BLUE);
                box.addView(save);

                save.setOnClickListener(v -> {
                    try {
                        int newMin = parseInt(
                                minimum,
                                "สต็อกขั้นต่ำ"
                        );

                        boolean success = repository.updateProduct(
                                p.id,
                                p.name,
                                p.price,
                                p.cost,
                                newMin,
                                p.imageData,
                                p.unit
                        );

                        if (success) {
                            toast("บันทึกสต็อกขั้นต่ำแล้ว");
                            refresh[0].run();
                        } else {
                            toast("บันทึกไม่สำเร็จ");
                        }

                    } catch (Exception ex) {
                        toast(
                                ex.getMessage() == null
                                        ? "ข้อมูลไม่ถูกต้อง"
                                        : ex.getMessage()
                        );
                    }
                });

                list.addView(box);
            }
        };

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                refresh[0].run();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        refresh[0].run();
    }

    // =====================================================
    // ปิดการเชื่อมต่อ
    // =====================================================

    @Override
    protected void onDestroy() {
        if (repository != null) {
            repository.close();
        }

        if (database != null) {
            database.close();
        }

        super.onDestroy();
    }
                                 }
