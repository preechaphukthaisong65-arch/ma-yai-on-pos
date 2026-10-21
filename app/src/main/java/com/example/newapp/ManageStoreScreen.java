package com.example.newapp;
import android.app.Activity; import android.content.Intent; import android.graphics.Color; import android.os.Bundle; import android.view.Gravity; import android.widget.Button; import android.widget.LinearLayout; import android.widget.TextView;
public class ManageStoreScreen extends Activity {
 private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
 @Override protected void onCreate(Bundle b){super.onCreate(b); LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(20),dp(20),dp(20));root.setBackgroundColor(Color.WHITE);
 TextView title=new TextView(this);title.setText("จัดการร้านค้า");title.setTextSize(24);title.setTextColor(Color.rgb(45,90,55));title.setGravity(Gravity.CENTER);root.addView(title);
 Button edit=new Button(this);edit.setText("แก้ไขราคาสินค้า");edit.setOnClickListener(v->startActivity(new Intent(this,EditProductPriceScreen.class)));root.addView(edit,new LinearLayout.LayoutParams(-1,-2));
 Button history=new Button(this);history.setText("ค้นหาประวัติการแก้ไขราคา");history.setOnClickListener(v->startActivity(new Intent(this,PriceHistoryScreen.class)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);root.addView(history,p);
 Button back=new Button(this);back.setText("กลับหน้าหลัก");back.setOnClickListener(v->finish());root.addView(back);setContentView(root);}
}
