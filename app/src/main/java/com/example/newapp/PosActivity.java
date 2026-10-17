package com.example.newapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class PosActivity extends AppCompatActivity {

    private EditText etScanBarcode;
    private Button btnScanAction, btnCheckout;
    private ListView listViewCart;
    private TextView tvCartTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pos);

        etScanBarcode = findViewById(R.id.etScanBarcode);
        btnScanAction = findViewById(R.id.btnScanAction);
        btnCheckout = findViewById(R.id.btnCheckout);
        listViewCart = findViewById(R.id.listViewCart);
        tvCartTotal = findViewById(R.id.tvCartTotal);

        btnScanAction.setOnClickListener(v -> {
            String code = etScanBarcode.getText().toString().trim();
            if(!code.isEmpty()){
                Toast.makeText(this, "สแกนสินค้า: " + code, Toast.LENGTH_SHORT).show();
                etScanBarcode.setText("");
            }
        });

        btnCheckout.setOnClickListener(v -> {
            Toast.makeText(this, "ระบบชำระเงิน", Toast.LENGTH_SHORT).show();
        });
    }
}
