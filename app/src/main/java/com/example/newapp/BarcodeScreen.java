package com.example.newapp;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.graphics.Color;
public class BarcodeScreen extends Activity {
 @Override protected void onCreate(Bundle b) { super.onCreate(b); View v = new View(this); v.setBackgroundColor(Color.WHITE); setContentView(v); }
}
