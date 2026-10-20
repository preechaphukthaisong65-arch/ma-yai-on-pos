package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class StockProductListScreen extends Activity {
    private StockSalesRepository repo;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); repo=new StockSalesRepository(this);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(POSUi.dp(this,12),POSUi.dp(this,8),POSUi.dp(this,12),POSUi.dp(this,8));
        page.addView(POSUi.text(this,"สินค้าทั้งหมด",22));
        POSUi.button(this,page,"สแกนบาร์โค้ดเพื่อดูรายละเอียด",this::scan);
        List<JSONObject> items=repo.products();
        items.sort(Comparator.comparingInt((JSONObject r)->repo.payload(r).has("stockQuantity")&&repo.stock(r)<=repo.limit(r)?0:1)
                .thenComparingDouble(repo::stock));
        List<String> labels=new ArrayList<>();
        for(JSONObject r:items)labels.add(repo.name(r)+" — "+format(repo.stock(r))+" "+repo.unit(r));
        TextView empty=POSUi.text(this,labels.isEmpty()?"ยังไม่มีสินค้า":"",15);
        if(labels.isEmpty())page.addView(empty);
        else {
            ListView list=new ListView(this);
            ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){
                @Override public android.view.View getView(int position,android.view.View convert,ViewGroup parent){
                    TextView row=(TextView)super.getView(position,convert,parent);row.setTextSize(16);
                    row.setPadding(POSUi.dp(StockProductListScreen.this,8),POSUi.dp(StockProductListScreen.this,10),POSUi.dp(StockProductListScreen.this,8),POSUi.dp(StockProductListScreen.this,10));
                    return row;
                }
            };
            list.setAdapter(adapter);
            page.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
        }
        POSUi.button(this,page,"กลับ",this::finish);
        setContentView(page);
    }
    private String format(double n){return n==(long)n?Long.toString((long)n):Double.toString(n);}
    private void scan(){IntentIntegrator i=new IntentIntegrator(this);i.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);i.setPrompt("สแกนบาร์โค้ดสินค้า");i.setBeepEnabled(false);i.setOrientationLocked(true);i.initiateScan();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);IntentResult r=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(r==null||r.getContents()==null)return;
        JSONObject p=repo.findByBarcode(r.getContents());
        if(p==null){Toast.makeText(this,"ไม่พบสินค้า",Toast.LENGTH_SHORT).show();return;}
        Toast.makeText(this,repo.name(p)+"\nบาร์โค้ด: "+repo.barcode(p)+"\nราคาขาย: "+format(repo.price(p))+" บาท\nต้นทุน: "+format(repo.payload(p).optDouble("cost",0))+" บาท\nคงเหลือ: "+format(repo.stock(p))+" "+repo.unit(p),Toast.LENGTH_LONG).show();
    }
}
