package com.example.newapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.example.newapp.datahub.ServerSaleService;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SellScreen extends Activity {
    private static final int CAMERA_PERMISSION = 722;
    private StockSalesRepository repo;
    private DecoratedBarcodeView scanner;
    private TextToSpeech tts;
    private boolean acceptingScan = true, scanning = false, confirmed = false;
    private String lastBarcode = "";
    private long lastBarcodeAt = 0;
    private final LinkedHashMap<String, CartLine> cart = new LinkedHashMap<>();
    private String currentBillId;
    private String pendingBillId;
    private TextView changeText;
    private EditText cashReceived;
    private static final class CartLine {
        JSONObject product; double quantity;
        CartLine(JSONObject p){product=p;quantity=1;}
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); repo=new StockSalesRepository(this);
        tts=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS){
            POSVoice.configure(tts);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String id){}
                @Override public void onDone(String id){runOnUiThread(()->{acceptingScan=true;});}
                @Override public void onError(String id){runOnUiThread(()->{acceptingScan=true;});}
            });
        }});
        showScanner();
    }
    @Override protected void onResume(){super.onResume();if(scanning&&scanner!=null&&hasCameraPermission())scanner.resume();}
    @Override protected void onPause(){if(scanner!=null)scanner.pause();super.onPause();}
    @Override protected void onDestroy(){if(tts!=null)tts.shutdown();super.onDestroy();}

    private boolean hasCameraPermission(){return Build.VERSION.SDK_INT<23||ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;}
    private void showScanner(){
        scanning=true;acceptingScan=true;
        if(!hasCameraPermission()){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION);return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.BLACK);
        scanner=new DecoratedBarcodeView(this);scanner.setStatusText("");
        root.addView(scanner,new LinearLayout.LayoutParams(-1,0,1f));
        Button finish=new Button(this);finish.setText("จบการสแกน");finish.setAllCaps(false);
        finish.setOnClickListener(v->{scanning=false;scanner.pause();showCart();});
        root.addView(finish,new LinearLayout.LayoutParams(-1,POSUi.dp(this,56)));
        setContentView(root);
        scanner.decodeContinuous(new BarcodeCallback(){@Override public void barcodeResult(BarcodeResult result){
            if(result==null||result.getText()==null||!acceptingScan||!scanning)return;
            String code=result.getText().trim();long now=System.currentTimeMillis();
            if(code.equals(lastBarcode)&&now-lastBarcodeAt<1800)return;
            acceptingScan=false;lastBarcode=code;lastBarcodeAt=now;
            JSONObject product=repo.findByBarcode(code);
            if(product==null){acceptingScan=true;return;}
            String id=product.optString("recordId");
            CartLine line=cart.get(id);
            if(line==null){line=new CartLine(product);cart.put(id,line);}else line.quantity+=1;
            String utterance=repo.name(product)+" "+format(line.quantity)+" "+repo.unit(product)+"ค่ะ";
            if(tts!=null&&tts.getLanguage()!=null)tts.speak(utterance,TextToSpeech.QUEUE_FLUSH,null,"scan-"+UUID.randomUUID());
            else acceptingScan=true;
        }
        @Override public void possibleResultPoints(java.util.List<com.google.zxing.ResultPoint> points) { }
        });
        scanner.resume();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==CAMERA_PERMISSION&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)showScanner();
        else if(requestCode==CAMERA_PERMISSION){Toast.makeText(this,"ต้องอนุญาตกล้องเพื่อสแกนสินค้า",Toast.LENGTH_LONG).show();finish();}
    }

    private void showCart(){
        scanning=false;
        LinearLayout root=POSUi.page(this,"รายการขาย");
        if(cart.isEmpty())root.addView(POSUi.text(this,"ยังไม่มีสินค้าที่สแกน",16));
        double subtotal=0;
        for(CartLine line:cart.values()){
            double lineTotal=repo.price(line.product)*line.quantity;subtotal+=lineTotal;
            root.addView(POSUi.text(this,repo.name(line.product)+"\n"+format(line.quantity)+" "+repo.unit(line.product)
                    +" × "+money(repo.price(line.product))+" = "+money(lineTotal)+" บาท",16));
        }
        root.addView(POSUi.text(this,"ยอดรวม: "+money(subtotal)+" บาท",22));
        final double totalSubtotal = subtotal;
        POSUi.button(this,root,"สแกนสินค้าเพิ่ม",this::showScanner);
        POSUi.button(this,root,"ขายเงินสด",()->showCash(totalSubtotal));
        POSUi.button(this,root,"ขายเชื่อ",()->showCredit(totalSubtotal));
        POSUi.button(this,root,"บัญชีลูกค้าสินเชื่อ",()->startActivity(new Intent(this,CreditAccountScreen.class)));
        POSUi.button(this,root,"ใบเสร็จ / พิมพ์ซ้ำ",()->startActivity(new Intent(this,ReceiptReprintScreen.class)));
        POSUi.button(this,root,"กลับหน้าหลัก",this::finish);
        speakSummary(subtotal);
    }
    private double subtotal(){double s=0;for(CartLine l:cart.values())s+=repo.price(l.product)*l.quantity;return s;}
    private void speakSummary(double total){if(tts==null)return;StringBuilder summary=new StringBuilder("สรุปรายการ ");for(CartLine line:cart.values())summary.append(repo.name(line.product)).append(" ").append(format(line.quantity)).append(" ").append(repo.unit(line.product)).append(", ");summary.append("ยอดรวม ").append(money(total)).append(" บาทค่ะ");tts.speak(summary.toString(),TextToSpeech.QUEUE_FLUSH,null,"cart-total");}

    private void showCash(double total){
        LinearLayout root=POSUi.page(this,"ชำระเงินสด");
        root.addView(POSUi.text(this,"ยอดที่ต้องชำระ: "+money(total)+" บาท",20));
        cashReceived=POSUi.input(this,root,"จำนวนเงินที่รับมา");cashReceived.setInputType(8194);
        changeText=POSUi.text(this,"เงินทอน: 0 บาท",18);root.addView(changeText);
        cashReceived.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){double paid=POSUi.number(cashReceived);changeText.setText("เงินทอน: "+money(Math.max(0,paid-total))+" บาท");}public void afterTextChanged(Editable e){}});
        POSUi.button(this,root,"ยืนยันรับเงินและบันทึกการขาย",()->confirmSale(total,Math.max(0,POSUi.number(cashReceived)), "cash",null));
        POSUi.button(this,root,"กลับรายการสินค้า",this::showCart);
    }
    private void showCredit(double subtotal){
        LinearLayout root=POSUi.page(this,"ขายสินค้าแบบเครดิต");
        root.addView(POSUi.text(this,"ยอดสินค้า: "+money(subtotal)+" บาท",16));
        root.addView(POSUi.text(this,"ค่าบริการ 5%: "+money(subtotal*.05)+" บาท",16));
        root.addView(POSUi.text(this,"ยอดหนี้รวม: "+money(subtotal*1.05)+" บาท",20));
        EditText first=POSUi.input(this,root,"ชื่อลูกค้า");EditText last=POSUi.input(this,root,"นามสกุลลูกค้า");
        TextView customerStatus=POSUi.text(this,"กรอกชื่อและนามสกุลเพื่อค้นหาลูกค้าเดิม",14);root.addView(customerStatus);
        Runnable lookup=()->{JSONObject c=repo.findCustomer(POSUi.value(first),POSUi.value(last));customerStatus.setText(c==null?"ยังไม่พบลูกค้าเดิม — จะสร้างข้อมูลลูกค้าเมื่อยืนยัน":"พบลูกค้าเดิม — จะใช้บัญชีลูกค้ารายนี้");};
        first.setOnFocusChangeListener((v,has)->{if(!has)lookup.run();});last.setOnFocusChangeListener((v,has)->{if(!has)lookup.run();});
        POSUi.button(this,root,"ยืนยันขายเชื่อและบันทึกหนี้",()->{
            String f=POSUi.value(first),l=POSUi.value(last);if(f.isEmpty()||l.isEmpty()){Toast.makeText(this,"กรุณากรอกชื่อและนามสกุลลูกค้า",Toast.LENGTH_LONG).show();return;}
            JSONObject customer=repo.findCustomer(f,l);
            if(customer==null){try{JSONObject p=new JSONObject();p.put("firstName",f);p.put("lastName",l);p.put("createdAt",System.currentTimeMillis());
                if(!repo.saveCustomer(p)){Toast.makeText(this,"บันทึกข้อมูลลูกค้าไม่สำเร็จ",Toast.LENGTH_LONG).show();return;}
                customer=repo.findCustomer(f,l);
            }catch(Exception e){Toast.makeText(this,"บันทึกลูกค้าไม่สำเร็จ",Toast.LENGTH_LONG).show();return;}}
            confirmSale(subtotal*1.05,0,"credit",customer);
        });
        POSUi.button(this,root,"กลับรายการสินค้า",this::showCart);
    }

    private void confirmSale(double total,double received,String type,JSONObject customer){
        if(confirmed)return;
        if(type.equals("cash")&&received<total){Toast.makeText(this,"จำนวนเงินที่รับมาต้องไม่น้อยกว่ายอดชำระ",Toast.LENGTH_LONG).show();return;}
        if(cart.isEmpty()){Toast.makeText(this,"ไม่มีสินค้าในรายการ",Toast.LENGTH_SHORT).show();return;}
        confirmed=true;
        String billId = pendingBillId == null ? repo.newId("sale_bill") : pendingBillId;
        pendingBillId = billId;
        try{
            double sub=subtotal();double fee=type.equals("credit")?sub*.05:0;
            JSONObject bill=new JSONObject();bill.put("billId",billId);bill.put("createdAt",System.currentTimeMillis());
            bill.put("paymentType",type);bill.put("paymentStatus",type.equals("cash")?"จ่ายแล้ว":"ค้างชำระ");
            bill.put("subtotal",sub);bill.put("serviceFee",fee);bill.put("total",total);
            bill.put("debt",type.equals("credit")?total:0);bill.put("remainingBalance",type.equals("credit")?total:0);
            bill.put("cashReceived",type.equals("cash")?received:0);bill.put("change",type.equals("cash")?received-total:0);
            if(customer!=null){
                bill.put("customerRecordId",customer.optString("recordId"));
                JSONObject cp=customer.optJSONObject("payload");
                if(cp!=null){
                    String customerFirstName=cp.optString("firstName","").trim();
                    String customerLastName=cp.optString("lastName","").trim();
                    bill.put("customerFirstName",customerFirstName);
                    bill.put("customerLastName",customerLastName);
                    bill.put("customerName",(customerFirstName+" "+customerLastName).trim());
                }
            }
            JSONArray lines=new JSONArray();List<JSONObject> products=new ArrayList<>();List<Double> quantities=new ArrayList<>();
            for(CartLine line:cart.values()){
                JSONObject item=new JSONObject();item.put("productRecordId",line.product.optString("recordId"));item.put("name",repo.name(line.product));
                item.put("barcode",repo.barcode(line.product));item.put("quantity",line.quantity);item.put("unit",repo.unit(line.product));
                item.put("unitPrice",repo.price(line.product));item.put("lineTotal",repo.price(line.product)*line.quantity);lines.put(item);
                products.add(line.product);quantities.add(line.quantity);
            }
            bill.put("items",lines);
            JSONObject receipt=new JSONObject();receipt.put("receiptId","receipt:"+billId);receipt.put("billId",billId);
            receipt.put("receiptType",type.equals("credit")?"ใบเสร็จขายเชื่อ":"ใบเสร็จรับเงิน");receipt.put("createdAt",System.currentTimeMillis());
            receipt.put("subtotal",sub);receipt.put("serviceFee",fee);receipt.put("total",total);receipt.put("printStatus","pending");receipt.put("items",lines);
            receipt.put("cashReceived",type.equals("cash")?received:0);receipt.put("change",type.equals("cash")?received-total:0);
            receipt.put("paymentStatus",type.equals("cash")?"จ่ายแล้ว":"ค้างชำระ");
            if(customer!=null){
                receipt.put("customerRecordId",bill.optString("customerRecordId"));
                receipt.put("customerFirstName",bill.optString("customerFirstName"));
                receipt.put("customerLastName",bill.optString("customerLastName"));
                receipt.put("customerName",bill.optString("customerName"));
            }
            if (hasInternetConnection()) {
                List<JSONObject> serverItems = new ArrayList<>();
                for (int i = 0; i < products.size(); i++) {
                    JSONObject item = new JSONObject();
                    item.put("productRecordId", products.get(i).optString("recordId"));
                    item.put("quantity", quantities.get(i));
                    item.put("localRevision", products.get(i).optLong("revision", 0L));
                    serverItems.add(item);
                }
                new ServerSaleService(getApplicationContext()).commit(
                        repo.getStoreId(), billId, type, received,
                        bill.optString("customerRecordId", ""), customer, serverItems,
                        new ServerSaleService.Callback() {
                            @Override public void onSuccess(List<JSONObject> records, boolean alreadyCommitted) {
                                runOnUiThread(() -> {
                                    if (records == null || records.isEmpty()) {
                                        confirmed = true;
                                        cart.clear();
                                        currentBillId = billId;
                                        showSaleNeedsReconciliation(billId,
                                                "เซิร์ฟเวอร์แจ้งว่าบิลนี้ถูกบันทึกแล้ว แต่ยังยืนยันข้อมูลในเครื่องไม่ได้ "
                                                + "ห้ามกดยืนยันขายซ้ำ ให้ตรวจใบเสร็จและข้อมูลร้านก่อน");
                                        return;
                                    }
                                    if (!repo.applyServerCommittedSaleRecords(records)) {
                                        confirmed = true;
                                        cart.clear();
                                        currentBillId = billId;
                                        showSaleNeedsReconciliation(billId,
                                                "เซิร์ฟเวอร์บันทึกการขายและตัดสต๊อกแล้ว แต่ข้อมูลในเครื่องมี revision ขัดแย้ง "
                                                + "จึงไม่เขียนทับข้อมูลเดิม ห้ามขายซ้ำบิลนี้");
                                        return;
                                    }
                                    cart.clear();
                                    currentBillId = billId;
                                    pendingBillId = null;
                                    showSaleSaved(billId, bill.optString("paymentStatus"), total);
                                });
                            }
                            @Override public void onFailure(String message) {
                                runOnUiThread(() -> {
                                    confirmed = false;
                                    Toast.makeText(SellScreen.this,
                                            "เซิร์ฟเวอร์ยังไม่ยืนยันการขาย: " + message
                                            + " กรุณาตรวจสอบสถานะก่อนลองใหม่",
                                            Toast.LENGTH_LONG).show();
                                });
                            }
                        });
                return;
            }
            // Offline sales remain in SQLite and pending sync; reconnect reconciliation
            // must still detect conflicts rather than blindly overwrite the server.
            if (repo.confirmSale(billId,bill,products,quantities,receipt)) {
                cart.clear(); currentBillId=billId; pendingBillId=null;
                showSaleSaved(billId, bill.optString("paymentStatus"), total);
            } else {
                confirmed=false;
                Toast.makeText(this,"บันทึกการขายไม่สำเร็จ กรุณาตรวจสอบก่อนลองใหม่",Toast.LENGTH_LONG).show();
            }
        }catch(Exception e){confirmed=false;Toast.makeText(this,"เกิดข้อผิดพลาดในการบันทึกการขาย",Toast.LENGTH_LONG).show();}
    }
    private boolean hasInternetConnection() {
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return false;
            Network active = manager.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities caps = manager.getNetworkCapabilities(active);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException error) { return false; }
    }

    private void showSaleSaved(String billId, String paymentStatus, double total) {
        LinearLayout root=POSUi.page(this,"บันทึกการขายแล้ว");
        root.addView(POSUi.text(this,"สถานะ: "+paymentStatus,18));
        root.addView(POSUi.text(this,"ยอดรวม: "+money(total)+" บาท",22));
        root.addView(POSUi.text(this,"บันทึกใบเสร็จแล้ว — พร้อมเปิดหน้าพิมพ์หรือพิมพ์ซ้ำ",15));
        POSUi.button(this,root,"เปิดใบเสร็จ / พิมพ์",()->{Intent i=new Intent(this,ReceiptReprintScreen.class);i.putExtra("billId",billId);startActivity(i);});
        POSUi.button(this,root,"กลับหน้าหลัก",this::finish);
        setContentView(root);
    }

    private void showSaleNeedsReconciliation(String billId, String message) {
        LinearLayout root=POSUi.page(this,"ต้องตรวจสอบข้อมูลการขาย");
        root.addView(POSUi.text(this,message,17));
        root.addView(POSUi.text(this,"รหัสบิล: "+billId,14));
        POSUi.button(this,root,"เปิดใบเสร็จ / ตรวจสอบบิล",()->{
            Intent i=new Intent(this,ReceiptReprintScreen.class);i.putExtra("billId",billId);startActivity(i);
        });
        POSUi.button(this,root,"กลับหน้าหลัก",this::finish);
        setContentView(root);
    }

    private String money(double n){return String.format(Locale.US,"%.2f",n);}
    private String format(double n){return n==(long)n?Long.toString((long)n):Double.toString(n);}
}
