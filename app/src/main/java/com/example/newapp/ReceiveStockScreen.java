package com.example.newapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.JSONObject;
import java.util.Locale;

public class ReceiveStockScreen extends Activity {
    private StockSalesRepository repo; private JSONObject selected; private String scannedBarcode="";
    private TextToSpeech tts;
    @Override public void onCreate(Bundle b){super.onCreate(b);repo=new StockSalesRepository(this);tts=new TextToSpeech(this,s->{if(s==TextToSpeech.SUCCESS)tts.setLanguage(new Locale("th","TH"));});render();}
    @Override protected void onDestroy(){if(tts!=null)tts.shutdown();super.onDestroy();}
    private void render(){LinearLayout root=POSUi.page(this,"เพิ่มสินค้าเข้าคลังสินค้า");
        POSUi.button(this,root,"สแกนบาร์โค้ดสินค้า",this::scan);
        if(!scannedBarcode.isEmpty())root.addView(POSUi.text(this,"บาร์โค้ด: "+scannedBarcode,15));
        if(selected!=null){root.addView(POSUi.text(this,repo.name(selected),18));root.addView(POSUi.text(this,"คงเหลือปัจจุบัน: "+repo.stock(selected)+" "+repo.unit(selected),15));
            EditText qty=POSUi.input(this,root,"จำนวนรับเข้า");POSUi.button(this,root,"บันทึกรับสินค้า",()->{double n=POSUi.number(qty);if(n<=0){qty.setError("จำนวนต้องมากกว่า 0");return;}
                if(repo.receive(selected,n)){Toast.makeText(this,"รับสินค้าเพิ่มแล้ว",Toast.LENGTH_SHORT).show();selected=null;scannedBarcode="";render();}else Toast.makeText(this,"บันทึกไม่สำเร็จ",Toast.LENGTH_LONG).show();});
        } else if(!scannedBarcode.isEmpty()){
            EditText name=POSUi.input(this,root,"ชื่อสินค้า");EditText price=POSUi.input(this,root,"ราคาขาย");price.setInputType(8194);
            EditText cost=POSUi.input(this,root,"ต้นทุน");cost.setInputType(8194);
            EditText unit=POSUi.input(this,root,"หน่วย เช่น ขวด/แพ็ค/ลัง");EditText limit=POSUi.input(this,root,"ลิมิตสต๊อก");
            EditText qty=POSUi.input(this,root,"จำนวนรับเข้าเริ่มต้น");
            POSUi.button(this,root,"บันทึกสินค้าใหม่",()->{double pr=POSUi.number(price),co=POSUi.number(cost),li=POSUi.number(limit),q=POSUi.number(qty);
                if(POSUi.value(name).isEmpty()){name.setError("กรอกชื่อสินค้า");return;}if(pr<0||co<0||li<0||q<0){Toast.makeText(this,"กรอกตัวเลขให้ถูกต้อง",Toast.LENGTH_SHORT).show();return;}
                if(repo.createProduct(POSUi.value(name),scannedBarcode,pr,co,POSUi.value(unit).isEmpty()?"ชิ้น":POSUi.value(unit),li,q)){
                    Toast.makeText(this,"บันทึกสินค้าใหม่แล้ว",Toast.LENGTH_SHORT).show();selected=null;scannedBarcode="";render();
                }else Toast.makeText(this,"บันทึกไม่สำเร็จหรือบาร์โค้ดซ้ำ",Toast.LENGTH_LONG).show();});
        }
        POSUi.button(this,root,"กลับ",this::finish);}
    private void scan(){IntentIntegrator i=new IntentIntegrator(this);i.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);i.setPrompt("สแกนบาร์โค้ดสินค้า");i.setBeepEnabled(false);i.setOrientationLocked(true);i.initiateScan();}
    @Override protected void onActivityResult(int a,int b,Intent d){super.onActivityResult(a,b,d);IntentResult r=IntentIntegrator.parseActivityResult(a,b,d);if(r==null||r.getContents()==null)return;
        scannedBarcode=r.getContents();selected=repo.findByBarcode(scannedBarcode);
        if(tts!=null){if(selected!=null)tts.speak(repo.name(selected)+"ค่ะ",TextToSpeech.QUEUE_FLUSH,null,"existingproduct");else tts.speak("สินค้าใหม่ค่ะ",TextToSpeech.QUEUE_FLUSH,null,"newproduct");}
        render();}
}
