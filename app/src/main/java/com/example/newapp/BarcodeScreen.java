package com.example.newapp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import android.widget.ImageView;
import java.io.FileOutputStream;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class BarcodeScreen extends Activity {
    private PosDatabase helper;private LinearLayout root;
    @Override protected void onCreate(Bundle b){super.onCreate(b);helper=PosDatabase.get(this);OwnerSecurity.requireOwner(this,"สร้างบาร์โค้ดและจัดการราคาสินค้า",this::home);} 
    private void home(){root=PosUi.page(this,"สร้างบาร์โค้ด");
        PosUi.button(this,root,"สร้างบาร์โค้ดใหม่",v->create());
        PosUi.button(this,root,"คลังบาร์โค้ด",v->registry());
        PosUi.back(this,root);
    }
    private void create(){
        root=PosUi.page(this,"สร้างบาร์โค้ดใหม่");EditText name=PosUi.input(this,root,"ชื่อสินค้า");EditText price=PosUi.input(this,root,"ราคาขาย (บาท)");
        PosUi.line(this,root,"ระบบกำหนดเลขบาร์โค้ดอัตโนมัติ ไม่สามารถเลือกเลขเองได้");
        PosUi.button(this,root,"สร้างและบันทึก",v->{try{
            String n=PosUi.str(name);if(n.isEmpty()){name.setError("กรอกชื่อสินค้า");return;}final long[] generatedPriceHolder={PosUi.satang(price)};
            final String[] codeHolder={null};final boolean[] reusedHolder={false};
            SQLiteDatabase db=helper.getWritableDatabase();
            PosTransactions.execute(db,transaction->{
                Cursor found=transaction.query("barcode_registry",new String[]{"barcode","price_satang"},"name=? COLLATE NOCASE",new String[]{n},null,null,"created_at ASC","1");
                if(found.moveToFirst()){
                    codeHolder[0]=found.getString(0);long oldPrice=found.getLong(1);reusedHolder[0]=true;
                    if(oldPrice!=generatedPriceHolder[0])throw new IllegalStateException("ชื่อนี้มีบาร์โค้ด "+codeHolder[0]+" และราคาเดิม "+PosDatabase.money(oldPrice)+" บาทอยู่แล้ว กรุณาใช้คลังบาร์โค้ดเพื่อแก้ราคาอย่างชัดเจน");
                    generatedPriceHolder[0]=oldPrice;
                }found.close();
                if(!reusedHolder[0]){
                    long seq=10000000000L;Cursor c=transaction.query("app_meta",new String[]{"value"},"key='barcode_sequence'",null,null,null,null);if(c.moveToFirst())seq=Long.parseLong(c.getString(0));c.close();
                    if(seq<10000000000L||seq>99999999999L)seq=10000000000L;
                    while(seq<=99999999999L){String base=String.format(java.util.Locale.US,"%011d",seq);String candidate=base+upcaCheckDigit(base);Cursor exists=transaction.rawQuery("SELECT 1 FROM barcode_registry WHERE barcode=? UNION ALL SELECT 1 FROM products WHERE barcode=? LIMIT 1",new String[]{candidate,candidate});boolean used=exists.moveToFirst();exists.close();if(!used){codeHolder[0]=candidate;break;}seq++;}
                    if(codeHolder[0]==null)throw new IllegalStateException("ไม่สามารถหาเลขบาร์โค้ดว่างได้");
                    ContentValues reg=new ContentValues();reg.put("barcode",codeHolder[0]);reg.put("name",n);reg.put("price_satang",generatedPriceHolder[0]);reg.put("source","STORE");reg.put("created_at",PosDatabase.now());transaction.insertOrThrow("barcode_registry",null,reg);
                    ContentValues meta=new ContentValues();meta.put("value",String.valueOf(seq+1));transaction.update("app_meta",meta,"key='barcode_sequence'",null);
                    PosEvents.barcodeRegistered(transaction,codeHolder[0],n,generatedPriceHolder[0],"STORE");
                }
                return null;
            });
            price.setText(PosDatabase.money(generatedPriceHolder[0]));
            final String generatedCode=codeHolder[0];final long generatedPrice=generatedPriceHolder[0];final boolean wasReused=reusedHolder[0];
            showBarcode(generatedCode,n,generatedPrice,wasReused);
        }catch(Exception e){PosUi.result(this,false,"สร้างไม่สำเร็จ: "+e.getMessage(),null);}});
        PosUi.back(this,root);
    }
    private static int upcaCheckDigit(String elevenDigits){
        int sum=0;
        for(int i=0;i<elevenDigits.length();i++){
            int digit=Character.digit(elevenDigits.charAt(i),10);
            sum+=(i%2==0?3:1)*digit;
        }
        return (10-(sum%10))%10;
    }
    private void registry(){
        root=PosUi.page(this,"คลังบาร์โค้ด");EditText query=PosUi.input(this,root,"ค้นหาจากชื่อหรือบาร์โค้ด");PosUi.button(this,root,"ค้นหา",v->search(query.getText().toString().trim()));PosUi.button(this,root,"สแกนบาร์โค้ดเพื่อค้นหา",v->PosBarcode.launch(this,"สแกนบาร์โค้ดเพื่อค้นหา"));PosUi.button(this,root,"แสดงทั้งหมด",v->search(""));
        PosUi.back(this,root);
    }
    private void search(String q){
        root=PosUi.page(this,"ผลการค้นหาคลังบาร์โค้ด");Cursor c;
        if(q.isEmpty())c=helper.getReadableDatabase().query("barcode_registry",new String[]{"barcode","name","price_satang","source"} ,null,null,null,null,"created_at DESC","100");
        else c=helper.getReadableDatabase().query("barcode_registry",new String[]{"barcode","name","price_satang","source"},"barcode=? OR name LIKE ?",new String[]{q,"%"+q+"%"},null,null,"name COLLATE NOCASE","100");
        while(c.moveToNext()){
            String barcode=c.getString(0),name=c.getString(1),source=c.getString(3);long price=c.getLong(2);
            PosUi.button(this,root,name+" • "+barcode+" • "+PosDatabase.money(price)+" บาท • "+source,
                    v->showBarcode(barcode,name,price,false));
        }
        c.close();PosUi.back(this,root);
    }

    private void showBarcode(String barcode,String name,long price,boolean reused){
        root=PosUi.page(this,"บาร์โค้ดสินค้า");
        PosUi.line(this,root,"สินค้า: "+name);
        PosUi.line(this,root,"รหัสบาร์โค้ด: "+barcode);
        PosUi.line(this,root,"ราคา: "+PosDatabase.money(price)+" บาท");
        if(reused)PosUi.line(this,root,"ใช้บาร์โค้ดเดิมจากคลัง");
        try{
            Bitmap bitmap=makeBarcodeBitmap(barcode);
            ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            root.addView(image,new LinearLayout.LayoutParams(-1,PosUi.dp(this,150)));
            PosUi.button(this,root,"พิมพ์บาร์โค้ด",v->printBarcode(bitmap,barcode,name,price));
        }catch(Exception e){
            PosUi.line(this,root,"สร้างภาพบาร์โค้ดไม่สำเร็จ: "+(e.getMessage()==null?"รูปแบบรหัสไม่รองรับ":e.getMessage()));
        }
        PosUi.button(this,root,"กลับคลังบาร์โค้ด",v->registry());
        PosUi.back(this,root);
    }

    private Bitmap makeBarcodeBitmap(String value) throws Exception{
        BitMatrix matrix=new MultiFormatWriter().encode(value,BarcodeFormat.CODE_128,900,240);
        Bitmap bitmap=Bitmap.createBitmap(matrix.getWidth(),matrix.getHeight(),Bitmap.Config.ARGB_8888);
        for(int x=0;x<matrix.getWidth();x++)for(int y=0;y<matrix.getHeight();y++)
            bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
        return bitmap;
    }

    private void printBarcode(Bitmap bitmap,String barcode,String name,long price){
        PrintManager manager=(PrintManager)getSystemService(Context.PRINT_SERVICE);
        if(manager==null){PosUi.result(this,false,"อุปกรณ์ไม่รองรับการพิมพ์จากแอปนี้",null);return;}
        manager.print("บาร์โค้ด-"+barcode,new PrintDocumentAdapter(){
            private PrintAttributes attributes;
            @Override public void onLayout(PrintAttributes oldAttributes,PrintAttributes newAttributes,
                    CancellationSignal signal,LayoutResultCallback callback,android.os.Bundle extras){
                if(signal.isCanceled()){callback.onLayoutCancelled();return;}
                attributes=newAttributes;
                PrintDocumentInfo info=new PrintDocumentInfo.Builder("barcode-"+barcode+".pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build();
                callback.onLayoutFinished(info,true);
            }
            @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,
                    CancellationSignal signal,WriteResultCallback callback){
                PrintedPdfDocument document=null;
                try{
                    if(signal.isCanceled()){callback.onWriteCancelled();return;}
                    document=new PrintedPdfDocument(BarcodeScreen.this,attributes);
                    PdfDocument.Page page=document.startPage(1);
                    Canvas canvas=page.getCanvas();Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(Color.BLACK);paint.setTextSize(20);
                    canvas.drawText(name,36,50,paint);
                    canvas.drawText("ราคา "+PosDatabase.money(price)+" บาท",36,80,paint);
                    Rect target=new Rect(36,105,canvas.getWidth()-36,canvas.getHeight()/2);
                    canvas.drawBitmap(bitmap,null,target,paint);
                    paint.setTextSize(18);canvas.drawText(barcode,36,canvas.getHeight()/2+35,paint);
                    document.finishPage(page);
                    document.writeTo(new FileOutputStream(destination.getFileDescriptor()));
                    callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                }catch(Exception e){
                    callback.onWriteFailed(e.getMessage()==null?"สร้างเอกสารพิมพ์ไม่สำเร็จ":e.getMessage());
                }finally{
                    if(document!=null)document.close();
                    try{destination.close();}catch(Exception ignored){}
                }
            }
        },new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(new PrintAttributes.Resolution("barcode","Barcode",300,300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){super.onActivityResult(requestCode,resultCode,data);IntentResult r=PosBarcode.parseResult(requestCode,resultCode,data);if(r!=null){if(r.getContents()!=null)search(r.getContents());else home();}}
}