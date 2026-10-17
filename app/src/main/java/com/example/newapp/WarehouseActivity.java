package com.example.newapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class WarehouseActivity extends AppCompatActivity {

    private ListView listViewWarehouse;
    private Button btnAddProduct;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warehouse);

        listViewWarehouse = findViewById(R.id.listViewWarehouse);
        btnAddProduct = findViewById(R.id.btnAddProduct);

        btnAddProduct.setOnClickListener(v -> {
            Toast.makeText(this, "เปิดหน้าเพิ่มสินค้าใหม่", Toast.LENGTH_SHORT).show();
        });
    }
}
