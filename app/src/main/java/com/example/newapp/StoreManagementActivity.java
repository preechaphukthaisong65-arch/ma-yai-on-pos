package com.example.newapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class StoreManagementActivity extends AppCompatActivity {
    private Button btnStoreInfo, btnBluetoothPrinter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store_management);

        btnStoreInfo = findViewById(R.id.btnStoreInfo);
        btnBluetoothPrinter = findViewById(R.id.btnBluetoothPrinter);

        btnStoreInfo.setOnClickListener(v -> Toast.makeText(this, "จัดการข้อมูลร้าน", Toast.LENGTH_SHORT).show());
        btnBluetoothPrinter.setOnClickListener(v -> Toast.makeText(this, "ตั้งค่าเครื่องพิมพ์", Toast.LENGTH_SHORT).show());
    }
}
