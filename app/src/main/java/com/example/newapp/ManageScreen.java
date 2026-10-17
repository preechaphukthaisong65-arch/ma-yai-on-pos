package com.example.newapp;

import android.os.Bundle;

public class ManageScreen extends BaseScreen {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ใช้ภาพพื้นหลังร่วมกันทุกหน้า
        createBackground();

        // เว้นหน้าเปล่าไว้ก่อน
    }
}
