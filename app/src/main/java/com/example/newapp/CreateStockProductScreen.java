package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Compatibility entry point; product creation is handled by stock receiving. */
public final class CreateStockProductScreen extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, ReceiveStockScreen.class));
        finish();
    }
}
