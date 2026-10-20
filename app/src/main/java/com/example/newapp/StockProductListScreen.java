package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.JSONObject;
import java.util.List;
import java.util.Comparator;

public class StockProductListScreen extends Activity {
    private StockSalesRepository repo;
    private LinearLayout root;
    @Override public void onCreate(Bundle b) { super.onCreate(b); repo=new StockSalesRepository(this); root=POSUi.page(this,"สินค้าทั้งหมด");
        POSUi.button(this,root,"สแกนบาร์โค้ดเพื่อดูรายละเอียด",this::scan); render(); }
    private void render() {
        List<JSONObject> items=repo.products();
        items.sort(Comparator.comparingInt((JSONObject r)->repo.stock(r)<=repo.limit(r)?0:1).thenComparingDouble(repo::stock));
        for(JSONObject r:items) root.addView(POSUi.text(this,repo.name(r)+" — "+format(repo.stock(r))+" "+repo.unit(r),16));
    }
    private String format(double n){return n==(long)n?Long.toString((long)n):Double.toString(n);}
    private void scan(){IntentIntegrator i=new IntentIntegrator(this);i.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);i.setPrompt("สแกนบาร์โค้ดสินค้า");i.setBeepEnabled(false);i.setOrientationLocked(true);i.initiateScan();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);IntentResult r=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(r==null||r.getContents()==null)return;
        JSONObject p=repo.findByBarcode(r.getContents()); if(p==null){Toast.makeText(this,"ไม่พบสินค้า",Toast.LENGTH_SHORT).show();return;}
        Toast.makeText(this,repo.name(p)+"\nคงเหลือ "+repo.stock(p)+" "+repo.unit(p),Toast.LENGTH_LONG).show();}
}
