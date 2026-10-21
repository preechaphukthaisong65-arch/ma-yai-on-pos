package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

public class StockScreen extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root=POSUi.page(this,"คลังสินค้า");
        POSUi.button(this,root,"สินค้าทั้งหมด",()->startActivity(new Intent(this,StockProductListScreen.class)));
        POSUi.button(this,root,"ตรวจนับสต๊อก",()->startActivity(new Intent(this,StocktakeScreen.class)));
        POSUi.button(this,root,"ลิมิตสต๊อก",()->startActivity(new Intent(this,StockLimitScreen.class)));
        POSUi.button(this,root,"เพิ่มสินค้าเข้าคลังสินค้า",()->startActivity(new Intent(this,ReceiveStockScreen.class)));
        POSUi.button(this,root,"กลับหน้าหลัก",this::finish);
    }
}
