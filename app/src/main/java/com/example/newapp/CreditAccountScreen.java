package com.example.newapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CreditAccountScreen extends Activity {
    private StockSalesRepository repo; private LinearLayout root; private EditText first,last,received;
    private final List<JSONObject> bills=new ArrayList<>(); private final List<CheckBox> checks=new ArrayList<>();
    private TextView totalText; private boolean paying;
    @Override public void onCreate(Bundle b){super.onCreate(b);repo=new StockSalesRepository(this);renderSearch();}
    private void renderSearch(){root=POSUi.page(this,"บัญชีลูกค้าสินเชื่อ");
        first=POSUi.input(this,root,"ค้นหาชื่อลูกค้า");last=POSUi.input(this,root,"นามสกุลลูกค้า");
        POSUi.button(this,root,"ค้นหาบัญชีลูกค้า",()->showBills());
        POSUi.button(this,root,"กลับ",this::finish);}
    private void showBills(){
        String f=POSUi.value(first),l=POSUi.value(last);
        if(f.isEmpty()||l.isEmpty()){Toast.makeText(this,"กรอกชื่อและนามสกุลลูกค้า",Toast.LENGTH_SHORT).show();return;}
        JSONObject customer=repo.findCustomer(f,l);
        if(customer==null){Toast.makeText(this,"ไม่พบลูกค้ารายนี้",Toast.LENGTH_LONG).show();return;}
        String customerId=customer.optString("recordId");
        root=POSUi.page(this,"หนี้ค้างชำระ: "+f+" "+l);bills.clear();checks.clear();
        double outstanding=0;
        for(JSONObject bill:repo.records("sale_bill")){
            JSONObject p=bill.optJSONObject("payload");if(p==null)continue;
            if(!"credit".equals(p.optString("paymentType"))||!"ค้างชำระ".equals(p.optString("paymentStatus"))
                    ||!customerId.equals(p.optString("customerRecordId")))continue;
            bills.add(bill);double amount=Math.max(0d,p.optDouble("remainingBalance",p.optDouble("total",0d)));outstanding+=amount;
            CheckBox cb=new CheckBox(this);cb.setText("บิล "+bill.optString("recordId")+" — คงเหลือ "+String.format(Locale.US,"%.2f",amount)+" บาท");
            cb.setOnCheckedChangeListener((v,checked)->updateTotal());root.addView(cb);checks.add(cb);
        }
        root.addView(POSUi.text(this,"ยอดหนี้คงค้างทั้งหมด: "+String.format(Locale.US,"%.2f",outstanding)+" บาท",18));
        totalText=POSUi.text(this,"ยอดหนี้ที่เลือก: 0.00 บาท",18);root.addView(totalText);
        received=POSUi.input(this,root,"เงินสดที่รับ (ต้องเท่ากับยอดบิลที่เลือกเต็มจำนวน)");received.setInputType(8194);
        if(bills.isEmpty())root.addView(POSUi.text(this,"ไม่มีบิลค้างชำระ",16));
        POSUi.button(this,root,"ยืนยันรับชำระเงินสด",this::pay);
        POSUi.button(this,root,"ค้นหาลูกค้ารายอื่น",this::renderSearch);
        POSUi.button(this,root,"กลับ",this::finish);
        updateTotal();
    }
    private double selectedTotal(){double total=0;for(int i=0;i<checks.size();i++)if(checks.get(i).isChecked()){JSONObject p=bills.get(i).optJSONObject("payload");total+=Math.max(0d,p.optDouble("remainingBalance",p.optDouble("total",0d)));}return total;}
    private void updateTotal(){
        double total=selectedTotal();
        if(totalText!=null)totalText.setText("ยอดหนี้ที่เลือก: "+String.format(Locale.US,"%.2f",total)+" บาท");
        if(received!=null)received.setText(total>0?String.format(Locale.US,"%.2f",total):"");
    }
    private void pay(){
        List<JSONObject> chosen=new ArrayList<>();for(int i=0;i<checks.size();i++)if(checks.get(i).isChecked())chosen.add(bills.get(i));
        double total=selectedTotal(),cash=POSUi.number(received);
        if(chosen.isEmpty()){Toast.makeText(this,"เลือกบิลที่ต้องการชำระก่อน",Toast.LENGTH_SHORT).show();return;}
        if(!Double.isFinite(cash)||cash<=0||Math.abs(cash-total)>.009){Toast.makeText(this,"เงินสดรับต้องเท่ากับยอดเต็มของบิลที่เลือก: "+String.format(Locale.US,"%.2f",total)+" บาท",Toast.LENGTH_LONG).show();return;}
        if (paying) return;
        paying = true;
        Toast.makeText(this, "กำลังตรวจสอบยอดหนี้ล่าสุดและบันทึกบนเซิร์ฟเวอร์…", Toast.LENGTH_SHORT).show();
        repo.payCreditBillsOnline(chosen, cash, (succeeded, failed, message) -> runOnUiThread(() -> {
            paying = false;
            Toast.makeText(CreditAccountScreen.this, message, Toast.LENGTH_LONG).show();
            if (failed == 0) showBills();
        }));
    }
}
