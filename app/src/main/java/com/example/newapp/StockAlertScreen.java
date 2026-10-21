package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import org.json.JSONObject;

public class StockAlertScreen extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);StockSalesRepository repo=new StockSalesRepository(this);LinearLayout root=POSUi.page(this,"แจ้งเตือนลิมิตสต๊อก");
        root.addView(POSUi.text(this,"มีสินค้า ต่ำกว่า ลิมิตสต๊อก",16));
        for(JSONObject p:repo.lowStockProducts())root.addView(POSUi.text(this,repo.name(p)+" — "+repo.stock(p)+" "+repo.unit(p)+" (ลิมิต "+repo.limit(p)+")",15));
        POSUi.button(this,root,"กลับ",this::finish);}
}
