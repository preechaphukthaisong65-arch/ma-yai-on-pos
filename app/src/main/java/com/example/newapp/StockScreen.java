package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;

public class StockScreen extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View blankPage = new View(this);
        blankPage.setBackgroundColor(Color.WHITE);
        setContentView(blankPage);
    }
}
