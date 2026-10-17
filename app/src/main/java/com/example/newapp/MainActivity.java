package com.example.newapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private ImageButton btnSell, btnWarehouse, btnHistory, btnManage, btnBarcode, btnReport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // เชื่อมโยงปุ่มรูปภาพจาก XML
        btnSell = findViewById(R.id.btnSell);
        btnWarehouse = findViewById(R.id.btnWarehouse);
        btnHistory = findViewById(R.id.btnHistory);
        btnManage = findViewById(R.id.btnManage);
        btnBarcode = findViewById(R.id.btnBarcode);
        btnReport = findViewById(R.id.btnReport);

        // 1. เมนูขายสินค้า -> แยกไป PosActivity
        btnSell.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PosActivity.class);
            startActivity(intent);
        });

        // 2. เมนูคลังสินค้า -> แยกไป WarehouseActivity
        btnWarehouse.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WarehouseActivity.class);
            startActivity(intent);
        });

        // 3. เมนูประวัติการขาย -> แยกไป SalesHistoryActivity
        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SalesHistoryActivity.class);
            startActivity(intent);
        });

        // 4. เมนูจัดการร้านค้า -> แยกไป StoreManagementActivity
        btnManage.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, StoreManagementActivity.class);
            startActivity(intent);
        });

        // 5. เมนูสร้างบาร์โค้ด -> แยกไป BarcodeActivity
        btnBarcode.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BarcodeActivity.class);
            startActivity(intent);
        });

        // 6. เมนูลายงานยอดขาย -> แยกไป SalesReportActivity
        btnReport.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SalesReportActivity.class);
            startActivity(intent);
        });
    }
}
