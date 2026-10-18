
package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PrinterScreen extends Activity {

    private PrinterService printerService;
    private Spinner printerSpinner;
    private TextView statusText;
    private TextView connectionText;
    private Button connectButton;
    private Button testButton;
    private Button disconnectButton;

    private final List<PrinterService.PrinterDevice> printerDevices =
            new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        printerService = PrinterService.getInstance(this);

        buildScreen();
        refreshPrinters();
        refreshConnectionStatus();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView makeText(String text, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(0xFF202020);
        view.setGravity(Gravity.CENTER_VERTICAL);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return view;
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.topMargin = dp(6);
        params.bottomMargin = dp(6);
        button.setLayoutParams(params);

        return button;
    }

    private void buildScreen() {
        ScrollView scrollView = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.setBackgroundColor(0xFFF7F7F7);

        scrollView.addView(root);

        TextView title = makeText(
                "ตั้งค่าเครื่องพิมพ์ใบเสร็จ",
                23,
                true
        );

        root.addView(title);

        TextView description = makeText(
                "เลือกเครื่องพิมพ์ Bluetooth ที่จับคู่กับโทรศัพท์แล้ว",
                14,
                false
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        descriptionParams.topMargin = dp(8);
        descriptionParams.bottomMargin = dp(12);
        root.addView(description, descriptionParams);

        root.addView(makeText("เครื่องพิมพ์ที่จับคู่ไว้", 16, true));

        printerSpinner = new Spinner(this);

        LinearLayout.LayoutParams spinnerParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        spinnerParams.topMargin = dp(8);
        spinnerParams.bottomMargin = dp(8);
        root.addView(printerSpinner, spinnerParams);

        Button refreshButton = makeButton("รีเฟรชรายชื่อเครื่องพิมพ์");
        root.addView(refreshButton);
        refreshButton.setOnClickListener(v -> refreshPrinters());

        connectButton = makeButton("เชื่อมต่อเครื่องพิมพ์");
        root.addView(connectButton);
        connectButton.setOnClickListener(v -> connectSelectedPrinter());

        testButton = makeButton("พิมพ์ทดสอบ");
        root.addView(testButton);
        testButton.setOnClickListener(v -> printTest());

        disconnectButton = makeButton("ยกเลิกการเชื่อมต่อ");
        root.addView(disconnectButton);
        disconnectButton.setOnClickListener(v -> disconnectPrinter());

        TextView statusTitle = makeText("สถานะ", 17, true);

        LinearLayout.LayoutParams statusTitleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        statusTitleParams.topMargin = dp(18);
        root.addView(statusTitle, statusTitleParams);

        statusText = makeText("กำลังตรวจสอบสถานะ...", 15, false);
        root.addView(statusText);

        connectionText = makeText("ยังไม่ทราบเครื่องพิมพ์ที่เชื่อมต่อ", 14, false);

        LinearLayout.LayoutParams connectionParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        connectionParams.topMargin = dp(6);
        root.addView(connectionText, connectionParams);

        TextView note = makeText(
                "หมายเหตุ: เครื่องพิมพ์บางรุ่นอาจต้องตั้งค่ารหัสภาษาไทย " +
                "และคำสั่งตัดกระดาษให้ตรงกับรุ่นจริง",
                13,
                false
        );

        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        noteParams.topMargin = dp(16);
        root.addView(note, noteParams);

        setContentView(scrollView);
    }

    private void refreshPrinters() {
        if (!printerService.isBluetoothSupported()) {
            showStatus("โทรศัพท์เครื่องนี้ไม่รองรับ Bluetooth");
            showDevices(new ArrayList<>());
            return;
        }

        if (!printerService.isBluetoothEnabled()) {
            showStatus("กรุณาเปิด Bluetooth ในโทรศัพท์ก่อน");
            showDevices(new ArrayList<>());
            askEnableBluetooth();
            return;
        }

        try {
            List<PrinterService.PrinterDevice> found =
                    printerService.getPairedPrinters();

            showDevices(found);

            if (found.isEmpty()) {
                showStatus(
                        "ไม่พบอุปกรณ์ที่จับคู่ไว้ กรุณาจับคู่เครื่องพิมพ์ " +
                        "ในตั้งค่า Bluetooth ของโทรศัพท์ก่อน"
                );
            } else {
                showStatus("พบอุปกรณ์ที่จับคู่ไว้ " + found.size() + " เครื่อง");
            }

        } catch (SecurityException e) {
            showStatus(
                    "ไม่มีสิทธิ์เข้าถึง Bluetooth กรุณาตรวจสอบสิทธิ์ของแอป"
            );
        }

        refreshConnectionStatus();
    }

    private void showDevices(
            List<PrinterService.PrinterDevice> devices
    ) {
        printerDevices.clear();
        printerDevices.addAll(devices);

        List<String> labels = new ArrayList<>();

        if (printerDevices.isEmpty()) {
            labels.add("ไม่มีเครื่องพิมพ์ที่จับคู่ไว้");
        } else {
            for (PrinterService.PrinterDevice device : printerDevices) {
                labels.add(device.toString());
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        printerSpinner.setAdapter(adapter);
    }

    private void askEnableBluetooth() {
        new AlertDialog.Builder(this)
                .setTitle("เปิด Bluetooth")
                .setMessage("ต้องเปิด Bluetooth ก่อนจึงจะค้นหาเครื่องพิมพ์ได้")
                .setNegativeButton("ยกเลิก", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("เปิด Bluetooth", (dialog, which) -> {
                    try {
                        Intent intent =
                                new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

                        startActivity(intent);

                    } catch (Exception e) {
                        showStatus(
                                "กรุณาเปิด Bluetooth ผ่านการตั้งค่าโทรศัพท์"
                        );
                    }
                })
                .show();
    }

    private void connectSelectedPrinter() {
        if (printerDevices.isEmpty()) {
            showStatus("ยังไม่มีเครื่องพิมพ์ให้เลือก");
            return;
        }

        int position = printerSpinner.getSelectedItemPosition();

        if (position < 0 || position >= printerDevices.size()) {
            showStatus("กรุณาเลือกเครื่องพิมพ์");
            return;
        }

        PrinterService.PrinterDevice device =
                printerDevices.get(position);

        connectButton.setEnabled(false);
        showStatus("กำลังเชื่อมต่อ " + device.name + "...");

        /*
         * การเชื่อมต่อ Bluetooth เป็นงาน I/O
         * ควรทำบนเธรดเบื้องหลังเพื่อไม่ให้หน้าจอค้าง
         */
        new Thread(() -> {
            String result;

            try {
                printerService.connect(device.address);
                result = "เชื่อมต่อสำเร็จ: " + device.name;

            } catch (IOException | SecurityException e) {
                result = "เชื่อมต่อไม่สำเร็จ: " +
                        (e.getMessage() == null
                                ? "ตรวจสอบเครื่องพิมพ์และ Bluetooth"
                                : e.getMessage());
            }

            String finalResult = result;

            runOnUiThread(() -> {
                connectButton.setEnabled(true);
                showStatus(finalResult);
                refreshConnectionStatus();
            });
        }).start();
    }

    private void printTest() {
        if (!printerService.isConnected()) {
            showStatus("กรุณาเชื่อมต่อเครื่องพิมพ์ก่อน");
            return;
        }

        testButton.setEnabled(false);
        showStatus("กำลังส่งข้อมูลทดสอบไปยังเครื่องพิมพ์...");

        new Thread(() -> {
            String result;

            try {
                printerService.printTest();
                result = "ส่งข้อมูลทดสอบแล้ว กรุณาตรวจสอบกระดาษ";

            } catch (IOException e) {
                result = "พิมพ์ทดสอบไม่สำเร็จ: " +
                        (e.getMessage() == null
                                ? "เกิดข้อผิดพลาด"
                                : e.getMessage());

                printerService.disconnect();
            }

            String finalResult = result;

            runOnUiThread(() -> {
                testButton.setEnabled(true);
                showStatus(finalResult);
                refreshConnectionStatus();
            });
        }).start();
    }

    private void disconnectPrinter() {
        printerService.disconnect();
        showStatus("ยกเลิกการเชื่อมต่อแล้ว");
        refreshConnectionStatus();
    }

    private void refreshConnectionStatus() {
        if (printerService.isConnected()) {
            connectionText.setText(
                    "เชื่อมต่ออยู่: " +
                    printerService.getConnectedAddress()
            );
        } else {
            connectionText.setText("สถานะการเชื่อมต่อ: ยังไม่เชื่อมต่อ");
        }
    }

    private void showStatus(String message) {
        if (statusText != null) {
            statusText.setText(message);
        }

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    protected void onDestroy() {
        /*
         * ไม่ตัดการเชื่อมต่ออัตโนมัติเมื่อปิดหน้าจอ
         * เพื่อให้ส่วนอื่นของแอปสามารถใช้บริการพิมพ์ร่วมกันได้
         */
        super.onDestroy();
    }
}
