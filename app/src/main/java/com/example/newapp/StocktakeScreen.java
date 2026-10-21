package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class StocktakeScreen extends Activity {
    private StockSalesRepository repo; private LinearLayout root; private boolean historyMode=false;
    private JSONObject selected;
    @Override public void onCreate(Bundle b){super.onCreate(b);repo=new StockSalesRepository(this);build();}
    private void build(){root=POSUi.page(this,historyMode?"ประวัติการเปลี่ยนแปลงสต๊อก":"ตรวจนับสต๊อก");
        POSUi.button(this,root,"ตรวจนับสต๊อก",()->{historyMode=false;build();});
        POSUi.button(this,root,"ประวัติสต๊อก",()->{historyMode=true;build();});
        POSUi.button(this,root,"สแกนบาร์โค้ด",this::scan);
        if(selected!=null&&!historyMode)showCountForm(selected);
        POSUi.button(this,root,"กลับ",this::finish);
    }
    private void scan(){IntentIntegrator i=new IntentIntegrator(this);i.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);i.setPrompt("สแกนบาร์โค้ดสินค้า");i.setBeepEnabled(false);i.setOrientationLocked(true);i.initiateScan();}
    @Override protected void onActivityResult(int a,int b,Intent d){super.onActivityResult(a,b,d);IntentResult r=IntentIntegrator.parseActivityResult(a,b,d);if(r==null||r.getContents()==null)return;
        selected=repo.findByBarcode(r.getContents()); if(selected==null){Toast.makeText(this,"ไม่พบสินค้า",Toast.LENGTH_SHORT).show();return;}
        if(historyMode)showHistory(r.getContents());else build();}
    private void showCountForm(JSONObject p){
        root.addView(POSUi.text(this,repo.name(p),18));
        root.addView(POSUi.text(this,"คงเหลือปัจจุบัน: "+repo.stock(p)+" "+repo.unit(p),16));
        EditText actual=POSUi.input(this,root,"จำนวนที่นับได้จริง");
        POSUi.button(this,root,"บันทึกผลตรวจนับ",()->{double n=POSUi.number(actual);if(n<0){actual.setError("กรอกจำนวนจริงที่ถูกต้อง");return;}
            if(repo.countStock(p,n)){Toast.makeText(this,"บันทึกผลตรวจนับแล้ว",Toast.LENGTH_SHORT).show();selected=null;build();}
            else Toast.makeText(this,"บันทึกไม่สำเร็จ กรุณาลองใหม่",Toast.LENGTH_LONG).show();});
    }
    private void showHistory(String barcode){
        root.removeAllViews();
        root=POSUi.page(this,"ประวัติสต๊อก: "+repo.name(selected));
        List<JSONObject> rows=repo.history(barcode);
        if(rows.isEmpty())root.addView(POSUi.text(this,"ยังไม่มีประวัติ",16));
        for(JSONObject row:rows){JSONObject p=row.optJSONObject("payload");if(p==null)continue;
            long time=p.optLong("changedAt",0);
            root.addView(POSUi.text(this,DateFormat.getDateTimeInstance().format(new Date(time))+"\n"+p.optString("transactionType")
                    +"\nก่อน: "+p.optDouble("beforeQuantity")+" → หลัง: "+p.optDouble("afterQuantity")
                    +" (เปลี่ยน "+p.optDouble("delta")+")",15));}
        POSUi.button(this,root,"สแกนสินค้าอื่น",this::scan);
        POSUi.button(this,root,"กลับไปตรวจนับ",()->{historyMode=false;selected=null;build();});
    }
}
