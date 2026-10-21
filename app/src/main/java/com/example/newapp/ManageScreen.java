package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Compatibility entry point for the existing store-management screen. */
public final class ManageScreen extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, ManageStoreScreen.class));
        finish();
    }
}
