package com.example.newapp;

import android.os.Bundle;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;

public class SalesHistoryActivity extends AppCompatActivity {
    private ListView listViewHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sales_history);
        listViewHistory = findViewById(R.id.listViewHistory);
    }
}
