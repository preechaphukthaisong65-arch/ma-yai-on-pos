package com.example.newapp;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class ManageScreen extends BaseScreen {

    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int DARK = Color.rgb(31, 41, 55);
    private static final int GRAY = Color.rgb(107, 114, 128);
    private static final int WHITE = Color.WHITE;
    private static final int GREEN = Color.rgb(22, 163, 74);

    private LocalDatabase localDatabase;
    private SQLiteDatabase database;

    private LinearLayout customerList;
    private EditText searchInput;
    private TextView summaryText;

    private final ArrayList<String> availableColumns =
            new ArrayList<>();

    private String idColumn;
    private String nameColumn;
    private String phoneColumn;
    private String noteColumn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createBackground();

        localDatabase = new LocalDatabase(this);
        database = localDatabase.getWritableDatabase();

        inspectCustomerTable();
        buildScreen();
        loadCustomers();
    }

    private void inspectCustomerTable() {
        Cursor cursor = null;

        try {
            cursor = database.rawQuery(
                    "PRAGMA table_info(customers)",
                    null
            );

            while (cursor.moveToNext()) {
                String column = cursor.getString(
                        cursor.getColumnIndexOrThrow("name")
                );

                availableColumns.add(column);
            }

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "ตรวจสอบตารางลูกค้าไม่สำเร็จ",
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        idColumn = firstExisting(
                "id",
                "_id",
                "customer_id"
        );

        nameColumn = firstExisting(
                "name",
                "customer_name",
                "full_name"
        );

        phoneColumn = firstExisting(
                "phone",
                "phone_number",
                "mobile",
                "telephone"
        );

        noteColumn = firstExisting(
                "note",
                "notes",
                "address",
                "details"
        );
    }

    private String firstExisting(String... candidates) {
        for (String candidate : candidates) {
            if (availableColumns.contains(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(14), dp(16), dp(24));

        scroll.addView(page);

        ViewGroup content = findViewById(android.R.id.content);
        View rootView = content.getChildCount() > 0
                ? content.getChildAt(0)
                : null;

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

        page.addView(text(
                "จัดการลูกค้า",
                25,
                DARK,
                true
        ));

        page.addView(text(
                "เพิ่มและค้นหาข้อมูลลูกค้า",
                14,
                GRAY,
                false
        ));

        addSpace(page, 14);

        summaryText = text(
                "รายชื่อลูกค้า",
                15,
                BLUE,
                true
        );
        page.addView(summaryText);

        addSpace(page, 10);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(16);
        searchInput.setHint("ค้นหาชื่อหรือเบอร์โทรศัพท์");
        searchInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        searchInput.setBackgroundColor(WHITE);

        page.addView(
                searchInput,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(50)
                )
        );

        addSpace(page, 12);

        TextView addButton = button(
                "+ เพิ่มลูกค้า",
                GREEN
        );

        page.addView(
                addButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(48)
                )
        );

        addSpace(page, 14);

        customerList = new LinearLayout(this);
        customerList.setOrientation(LinearLayout.VERTICAL);

        page.addView(
                customerList,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {}

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                loadCustomers();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        addButton.setOnClickListener(v -> showCustomerForm(null));
    }

    private void loadCustomers() {
        customerList.removeAllViews();

        if (nameColumn == null) {
            customerList.addView(text(
                    "ไม่พบคอลัมน์ชื่อในตาราง customers " +
                    "กรุณาตรวจสอบโครงสร้างฐานข้อมูล",
                    14,
                    Color.RED,
                    false
            ));
            return;
        }

        Cursor cursor = null;
        int count = 0;

        try {
            String keyword = searchInput == null
                    ? ""
                    : searchInput.getText().toString().trim();

            StringBuilder sql = new StringBuilder(
                    "SELECT * FROM customers"
            );

            ArrayList<String> args = new ArrayList<>();

            if (!keyword.isEmpty()) {
                sql.append(" WHERE ")
                        .append(quoteIdentifier(nameColumn))
                        .append(" LIKE ?");

                args.add("%" + keyword + "%");

                if (phoneColumn != null) {
                    sql.append(" OR ")
                            .append(quoteIdentifier(phoneColumn))
                            .append(" LIKE ?");

                    args.add("%" + keyword + "%");
                }
            }

            if (idColumn != null) {
                sql.append(" ORDER BY ")
                        .append(quoteIdentifier(idColumn))
                        .append(" DESC");
            }

            cursor = database.rawQuery(
                    sql.toString(),
                    args.toArray(new String[0])
            );

            while (cursor.moveToNext()) {
                long id = idColumn == null
                        ? -1
                        : cursor.getLong(
                                cursor.getColumnIndexOrThrow(idColumn)
                        );

                String name = getColumnValue(cursor, nameColumn);
                String phone = getColumnValue(cursor, phoneColumn);
                String note = getColumnValue(cursor, noteColumn);

                addCustomerCard(id, name, phone, note);
                count++;
            }

            summaryText.setText("ลูกค้าทั้งหมดที่แสดง: " + count + " ราย");

            if (count == 0) {
                customerList.addView(text(
                        "ยังไม่มีข้อมูลลูกค้า",
                        15,
                        GRAY,
                        false
                ));
            }

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "อ่านข้อมูลลูกค้าไม่สำเร็จ",
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void addCustomerCard(
            long id,
            String name,
            String phone,
            String note
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(WHITE);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(10);
        customerList.addView(card, params);

        card.addView(text(
                name == null || name.isEmpty()
                        ? "ไม่ระบุชื่อ"
                        : name,
                17,
                DARK,
                true
        ));

        if (phone != null && !phone.isEmpty()) {
            card.addView(text(
                    "โทรศัพท์: " + phone,
                    14,
                    GRAY,
                    false
            ));
        }

        if (note != null && !note.isEmpty()) {
            card.addView(text(
                    "ข้อมูลเพิ่มเติม: " + note,
                    14,
                    GRAY,
                    false
            ));
        }

        TextView editButton = text(
                "ดูรายละเอียด / แก้ไข  ›",
                14,
                BLUE,
                true
        );
        editButton.setGravity(Gravity.END);
        card.addView(editButton);

        card.setOnClickListener(v -> {
            if (id >= 0) {
                showCustomerForm(id);
            } else {
                showCustomerDetails(name, phone, note);
            }
        });
    }

    private void showCustomerDetails(
            String name,
            String phone,
            String note
    ) {
        String message =
                "ชื่อ: " + safe(name)
                + "\nโทรศัพท์: " + safe(phone)
                + "\nข้อมูลเพิ่มเติม: " + safe(note);

        new AlertDialog.Builder(this)
                .setTitle("รายละเอียดลูกค้า")
                .setMessage(message)
                .setPositiveButton("ปิด", null)
                .show();
    }

    private void showCustomerForm(Long customerId) {
        if (nameColumn == null) {
            Toast.makeText(
                    this,
                    "ไม่สามารถเพิ่มลูกค้าได้ เพราะไม่พบคอลัมน์ชื่อ",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("ชื่อลูกค้า *");

        EditText phoneInput = new EditText(this);
        phoneInput.setSingleLine(true);
        phoneInput.setHint("เบอร์โทรศัพท์");

        EditText noteInput = new EditText(this);
        noteInput.setHint("ข้อมูลเพิ่มเติม");
        noteInput.setMinLines(2);
        noteInput.setGravity(Gravity.TOP);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));

        form.addView(nameInput);

        if (phoneColumn != null) {
            form.addView(phoneInput);
        }

        if (noteColumn != null) {
            form.addView(noteInput);
        }

        if (customerId != null) {
            Cursor cursor = null;

            try {
                cursor = database.query(
                        "customers",
                        null,
                        quoteIdentifier(idColumn) + " = ?",
                        new String[]{String.valueOf(customerId)},
                        null,
                        null,
                        null
                );

                if (cursor.moveToFirst()) {
                    nameInput.setText(
                            getColumnValue(cursor, nameColumn)
                    );

                    if (phoneColumn != null) {
                        phoneInput.setText(
                                getColumnValue(cursor, phoneColumn)
                        );
                    }

                    if (noteColumn != null) {
                        noteInput.setText(
                                getColumnValue(cursor, noteColumn)
                        );
                    }
                }

            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(customerId == null
                        ? "เพิ่มลูกค้า"
                        : "แก้ไขข้อมูลลูกค้า")
                .setView(form)
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("บันทึก", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String name = nameInput.getText()
                                .toString().trim();

                        String phone = phoneInput.getText()
                                .toString().trim();

                        String note = noteInput.getText()
                                .toString().trim();

                        if (name.isEmpty()) {
                            nameInput.setError("กรุณากรอกชื่อลูกค้า");
                            return;
                        }

                        saveCustomer(
                                customerId,
                                name,
                                phone,
                                note
                        );

                        dialog.dismiss();
                    });
        });

        dialog.show();
    }

    private void saveCustomer(
            Long customerId,
            String name,
            String phone,
            String note
    ) {
        ContentValues values = new ContentValues();
        values.put(nameColumn, name);

        if (phoneColumn != null) {
            values.put(phoneColumn, phone);
        }

        if (noteColumn != null) {
            values.put(noteColumn, note);
        }

        try {
            if (customerId == null) {
                database.insertOrThrow(
                        "customers",
                        null,
                        values
                );

                Toast.makeText(
                        this,
                        "เพิ่มลูกค้าแล้ว",
                        Toast.LENGTH_SHORT
                ).show();

            } else {
                int changed = database.update(
                        "customers",
                        values,
                        quoteIdentifier(idColumn) + " = ?",
                        new String[]{String.valueOf(customerId)}
                );

                if (changed > 0) {
                    Toast.makeText(
                            this,
                            "บันทึกข้อมูลลูกค้าแล้ว",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            loadCustomers();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "บันทึกไม่สำเร็จ: ตรวจสอบโครงสร้างตารางลูกค้า",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private String getColumnValue(
            Cursor cursor,
            String column
    ) {
        if (column == null) {
            return "";
        }

        int index = cursor.getColumnIndex(column);

        if (index < 0 || cursor.isNull(index)) {
            return "";
        }

        return cursor.getString(index);
    }

    private String quoteIdentifier(String identifier) {
        if (identifier == null
                || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid database column");
        }

        return "\"" + identifier + "\"";
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty()
                ? "-"
                : value;
    }

    private TextView text(
            String value,
            int size,
            int color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, dp(3), 0, dp(3));

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        return view;
    }

    private TextView button(String value, int color) {
        TextView view = text(value, 16, WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(color);
        return view;
    }

    private void addSpace(LinearLayout parent, int height) {
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
    protected void onDestroy() {
        if (localDatabase != null) {
            localDatabase.close();
        }

        super.onDestroy();
    }
                                     }
