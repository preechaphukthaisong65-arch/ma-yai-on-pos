package com.example.newapp;

import android.os.Bundle;

public class SellScreen extends BaseScreen {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ใช้ภาพพื้นหลังร่วมกันทุกหน้า
        createBackground();

        // เว้นหน้าไว้เปล่าตามขอบเขตโปรเจกต์
    }
}
