
package com.example.newapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PrinterService {

    private static final UUID SERIAL_PORT_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final Charset PRINTER_CHARSET =
            Charset.forName("UTF-8");

    private static PrinterService instance;

    private final Context context;
    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private String connectedAddress;

    public static final class PrinterDevice {
        public final String name;
        public final String address;

        public PrinterDevice(String name, String address) {
            this.name = name == null ? "Bluetooth Printer" : name;
            this.address = address;
        }

        @Override
        public String toString() {
            return name + " (" + address + ")";
        }
    }

    private PrinterService(Context context) {
        this.context = context.getApplicationContext();
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public static synchronized PrinterService getInstance(Context context) {
        if (instance == null) {
            instance = new PrinterService(context);
        }
        return instance;
    }

    public synchronized boolean isBluetoothSupported() {
        adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null;
    }

    public synchronized boolean isBluetoothEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    public synchronized boolean isConnected() {
        return socket != null
                && socket.isConnected()
                && outputStream != null;
    }

    public synchronized String getConnectedAddress() {
        return connectedAddress;
    }

    /**
     * คืนรายชื่อเครื่อง Bluetooth ที่จับคู่กับโทรศัพท์ไว้แล้ว
     * ต้องให้สิทธิ์ Bluetooth ตาม Android เวอร์ชันที่ใช้อยู่ก่อนเรียก
     */
    public synchronized List<PrinterDevice> getPairedPrinters() {
        List<PrinterDevice> result = new ArrayList<>();

        adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter == null || !adapter.isEnabled()) {
            return result;
        }

        Set<BluetoothDevice> devices = adapter.getBondedDevices();

        if (devices == null) {
            return result;
        }

        for (BluetoothDevice device : devices) {
            String name;

            try {
                name = device.getName();
            } catch (SecurityException e) {
                name = "Bluetooth Printer";
            }

            result.add(new PrinterDevice(name, device.getAddress()));
        }

        Collections.sort(result, (a, b) ->
                a.name.compareToIgnoreCase(b.name));

        return result;
    }

    /**
     * เชื่อมต่อเครื่องพิมพ์ด้วย MAC address จากรายการจับคู่
     */
    public synchronized boolean connect(String macAddress)
            throws IOException {

        if (macAddress == null || macAddress.trim().isEmpty()) {
            throw new IOException("กรุณาเลือกเครื่องพิมพ์");
        }

        adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter == null) {
            throw new IOException("โทรศัพท์ไม่รองรับ Bluetooth");
        }

        if (!adapter.isEnabled()) {
            throw new IOException("กรุณาเปิด Bluetooth");
        }

        disconnect();

        BluetoothDevice device;

        try {
            device = adapter.getRemoteDevice(macAddress.trim());
            socket = device.createRfcommSocketToServiceRecord(
                    SERIAL_PORT_UUID
            );

            adapter.cancelDiscovery();
            socket.connect();
            outputStream = socket.getOutputStream();
            connectedAddress = macAddress.trim();

            return true;

        } catch (SecurityException e) {
            disconnect();
            throw new IOException(
                    "ไม่มีสิทธิ์ Bluetooth กรุณาอนุญาตในการตั้งค่าแอป", e
            );
        } catch (IOException e) {
            disconnect();
            throw e;
        }
    }

    /**
     * เริ่มต้นเครื่องพิมพ์ ESC/POS
     */
    public synchronized void initialize() throws IOException {
        ensureConnected();

        outputStream.write(new byte[]{
                0x1B, 0x40
        });

        outputStream.flush();
    }

    /**
     * พิมพ์ข้อความทั่วไป
     * หมายเหตุ: ภาษาไทยจะแสดงถูกต้องหรือไม่ ขึ้นกับ code page
     * และเฟิร์มแวร์ของเครื่องพิมพ์
     */
    public synchronized void printText(String text) throws IOException {
        ensureConnected();

        if (text == null) {
            text = "";
        }

        outputStream.write(text.getBytes(PRINTER_CHARSET));
        outputStream.flush();
    }

    /**
     * พิมพ์ข้อความและขึ้นบรรทัดใหม่
     */
    public synchronized void printLine(String text) throws IOException {
        printText((text == null ? "" : text) + "\n");
    }

    /**
     * ป้อนกระดาษก่อนตัด
     */
    public synchronized void feed(int lines) throws IOException {
        ensureConnected();

        int count = Math.max(0, Math.min(lines, 20));

        outputStream.write(new byte[]{
                0x1B, 0x64, (byte) count
        });

        outputStream.flush();
    }

    /**
     * คำสั่งตัดกระดาษแบบทั่วไป
     * เครื่องพิมพ์บางรุ่นอาจไม่รองรับ
     */
    public synchronized void cutPaper() throws IOException {
        ensureConnected();

        outputStream.write(new byte[]{
                0x1D, 0x56, 0x00
        });

        outputStream.flush();
    }

    /**
     * ส่งข้อมูล ESC/POS ที่จัดรูปแบบไว้แล้ว
     */
    public synchronized void printRaw(byte[] data) throws IOException {
        ensureConnected();

        if (data == null || data.length == 0) {
            throw new IOException("ไม่มีข้อมูลสำหรับพิมพ์");
        }

        outputStream.write(data);
        outputStream.flush();
    }

    /**
     * พิมพ์ทดสอบแบบข้อความ
     */
    public synchronized void printTest() throws IOException {
        initialize();
        printLine("MAE YAI ON POS");
        printLine("PRINTER TEST");
        printLine("------------------------------");
        printLine("Bluetooth connection OK");
        feed(4);
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            throw new IOException("ยังไม่ได้เชื่อมต่อเครื่องพิมพ์");
        }
    }

    public synchronized void disconnect() {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException ignored) {
            }
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        outputStream = null;
        socket = null;
        connectedAddress = null;
    }
}
