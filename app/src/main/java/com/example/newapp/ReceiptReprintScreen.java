package com.example.newapp;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.print.PrintManager;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;

public class ReceiptReprintScreen extends Activity {
    private StockSalesRepository repo; private LinearLayout root; private String focusId;
    @Override public void onCreate(Bundle b){super.onCreate(b);repo=new StockSalesRepository(this);focusId=getIntent().getStringExtra("billId");render();}
    private void render(){root=POSUi.page(this,"ใบเสร็จ / พิมพ์ซ้ำ");
        List<JSONObject> rows=repo.records("receipt");
        boolean found=false;
        for(JSONObject r:rows){JSONObject p=r.optJSONObject("payload");if(p==null)continue;
            String bill=p.optString("billId","");String receiptId=r.optString("recordId");
            if(focusId!=null&&!focusId.isEmpty()&&!focusId.equals(bill)&&!focusId.equals(receiptId))continue;
            found=true;String type=p.optString("receiptType","ใบเสร็จ");double total=p.optDouble("total",0);
            root.addView(POSUi.text(this,type+"\nเลขที่: "+p.optString("receiptId",receiptId)+"\nยอด: "+String.format(Locale.US,"%.2f",total)+" บาท\nสถานะพิมพ์: "+p.optString("printStatus","pending"),15));
            POSUi.button(this,root,"เปิดหน้าพิมพ์ใบเสร็จ",()->printReceipt(p,r));
        }
        if(!found)root.addView(POSUi.text(this,"ไม่พบใบเสร็จที่บันทึกไว้",16));
        POSUi.button(this,root,"กลับ",this::finish);
    }
    private void printReceipt(JSONObject p,JSONObject record){
        try{
            String html=html(p);
            WebView web=new WebView(this);web.getSettings().setJavaScriptEnabled(false);
            web.loadDataWithBaseURL(null,html,"text/HTML","UTF-8",null);
            PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);
            String job="ใบเสร็จ-"+p.optString("receiptId","POS");
            pm.print(job,web.createPrintDocumentAdapter(job),new android.print.PrintAttributes.Builder().build());
            JSONObject updated=new JSONObject(p.toString());updated.put("printStatus","print_dialog_opened");updated.put("lastPrintRequestedAt",System.currentTimeMillis());
            repo.saveRecord("receipt",record.optString("recordId"),updated);
            Toast.makeText(this,"เปิดหน้าพิมพ์แล้ว หากพิมพ์ไม่สำเร็จ ให้กลับมาพิมพ์ซ้ำจากใบเสร็จเดิม",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"เปิดหน้าพิมพ์ไม่สำเร็จ ใบเสร็จเดิมยังถูกเก็บไว้",Toast.LENGTH_LONG).show();}
    }
    private String html(JSONObject p){
        StringBuilder s=new StringBuilder("<html><meta charset='utf-8'><body style='font-family:sans-serif;width:280px'>");
        s.append("<h3>ร้านแม่ใหญ่อ่อน</h3><p>").append(escape(p.optString("receiptType","ใบเสร็จ"))).append("</p>");
        s.append("<p>เลขที่ ").append(escape(p.optString("receiptId",""))).append("</p>");
        JSONArray items=p.optJSONArray("items");
        if(items!=null)for(int i=0;i<items.length();i++){JSONObject x=items.optJSONObject(i);if(x==null)continue;
            s.append("<p>").append(escape(x.optString("name","สินค้า"))).append(" × ").append(x.optDouble("quantity",0))
                    .append("<br>").append(String.format(Locale.US,"%.2f",x.optDouble("lineTotal",0))).append(" บาท</p>");}
        if(p.has("customerName"))s.append("<p>ลูกค้า: ").append(escape(p.optString("customerName",""))).append("</p>");
        if(p.has("subtotal"))s.append("<p>ยอดสินค้า: ").append(String.format(Locale.US,"%.2f",p.optDouble("subtotal",0))).append(" บาท</p>");
        if(p.optDouble("serviceFee",0)>0)s.append("<p>ค่าบริการ: ").append(String.format(Locale.US,"%.2f",p.optDouble("serviceFee",0))).append(" บาท</p>");
        JSONArray billIds=p.optJSONArray("billIds");
        if(billIds!=null){s.append("<p>ชำระบิลเดิม:</p>");for(int i=0;i<billIds.length();i++)s.append("<p>").append(escape(billIds.optString(i,""))).append("</p>");}
        s.append("<hr><h3>รวม ").append(String.format(Locale.US,"%.2f",p.optDouble("total",0))).append(" บาท</h3>");
        s.append("<p>").append(escape(p.optString("paymentStatus",""))).append("</p></body></html>");return s.toString();
    }
    private String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
