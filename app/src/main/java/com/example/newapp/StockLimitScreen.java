package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.JSONObject;

public class StockLimitScreen extends Activity {
    private StockSalesRepository repo; private JSONObject selected;
    @Override public void onCreate(Bundle b){super.onCreate(b);repo=new StockSalesRepository(this);render();}
    private void render(){android.widget.LinearLayout root=POSUi.page(this,"ลิมิตสต๊อก");
        POSUi.button(this,root,"สแกนสินค้า",this::scan);
        if(selected!=null){root.addView(POSUi.text(this,repo.name(selected),18));root.addView(POSUi.text(this,"คงเหลือ: "+repo.stock(selected)+" "+repo.unit(selected),16));
            EditText limit=POSUi.input(this,root,"ลิมิตสต๊อกขั้นต่ำ");limit.setText(Double.toString(repo.limit(selected)));
            POSUi.button(this,root,"บันทึกลิมิต",()->{double n=POSUi.number(limit);if(n<0){limit.setError("กรอกลิมิตที่ถูกต้อง");return;}
                if(repo.updateLimit(selected,n)){Toast.makeText(this,"บันทึกลิมิตแล้ว",Toast.LENGTH_SHORT).show();selected=null;render();}
                else Toast.makeText(this,"บันทึกไม่สำเร็จ",Toast.LENGTH_LONG).show();});}
        POSUi.button(this,root,"สินค้าที่ถึงลิมิต",()->startActivity(new Intent(this,StockAlertScreen.class)));
        POSUi.button(this,root,"กลับ",this::finish);}
    private void scan(){IntentIntegrator i=new IntentIntegrator(this);i.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);i.setPrompt("สแกนบาร์โค้ดสินค้า");i.setBeepEnabled(false);i.setOrientationLocked(true);i.initiateScan();}
    @Override protected void onActivityResult(int a,int b,Intent d){super.onActivityResult(a,b,d);IntentResult r=IntentIntegrator.parseActivityResult(a,b,d);if(r==null||r.getContents()==null)return;selected=repo.findByBarcode(r.getContents());if(selected==null)Toast.makeText(this,"ไม่พบสินค้า",Toast.LENGTH_SHORT).show();render();}
}
