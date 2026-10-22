package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupScreen extends Activity {
    private static final int REQUEST_EXPORT=501,REQUEST_IMPORT=502;
    private LinearLayout root;
    @Override protected void onCreate(Bundle state){super.onCreate(state);home();}

    private void home(){
        root=PosUi.page(this,"สำรอง / กู้คืนข้อมูล");
        PosUi.line(this,root,"ไฟล์สำรองมีข้อมูลสินค้า ลูกค้า บิล และประวัติธุรกรรม ควรเก็บไว้ในพื้นที่ส่วนตัว");
        PosUi.button(this,root,"ส่งออกไฟล์สำรอง",v->chooseExport());
        PosUi.button(this,root,"นำเข้าไฟล์สำรอง",v->confirmImport());
        PosUi.back(this,root);
    }

    private void chooseExport(){
        String date=new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date());
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE,"maeyai-on-pos-backup-"+date+".db");
        startActivityForResult(intent,REQUEST_EXPORT);
    }

    private void confirmImport(){
        new AlertDialog.Builder(this).setTitle("ยืนยันนำเข้าข้อมูลสำรอง")
                .setMessage("การนำเข้าจะแทนที่ข้อมูลปัจจุบันทั้งหมด หากยังไม่มีไฟล์สำรองล่าสุด อย่าดำเนินการต่อ")
                .setNegativeButton("ยกเลิก",null)
                .setPositiveButton("เลือกไฟล์",(d,w)->{
                    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(intent,REQUEST_IMPORT);
                }).show();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==REQUEST_EXPORT)runTask("กำลังสำรองข้อมูล...",()->BackupService.exportTo(this,uri),
                "สำรองข้อมูลเรียบร้อย",false);
        else if(requestCode==REQUEST_IMPORT)new AlertDialog.Builder(this)
                .setTitle("แทนที่ข้อมูลปัจจุบัน")
                .setMessage("ยืนยันใช้ไฟล์นี้แทนฐานข้อมูลปัจจุบันหรือไม่? ระบบจะตรวจไฟล์ก่อน และจะพยายามคืนฐานข้อมูลเดิมหากกู้คืนไม่สำเร็จ")
                .setNegativeButton("ยกเลิก",null)
                .setPositiveButton("ยืนยันนำเข้า",(d,w)->runTask("กำลังตรวจและนำเข้าข้อมูล...",()->BackupService.restoreFrom(this,uri),
                        "นำเข้าข้อมูลสำเร็จ",true)).show();
    }

    private interface Task {void run() throws Exception;}
    private void runTask(String running,Task task,String success,boolean returnHome){
        root=PosUi.page(this,"สำรอง / กู้คืนข้อมูล");
        PosUi.line(this,root,running);
        new Thread(()->{
            Exception failure=null;
            try{task.run();}catch(Exception e){failure=e;}
            final Exception error=failure;
            runOnUiThread(()->{
                if(isFinishing())return;
                if(error==null){
                    new AlertDialog.Builder(this).setMessage(success)
                            .setPositiveButton("ตกลง",(d,w)->{
                                if(returnHome){
                                    Intent intent=new Intent(this,MainActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                    startActivity(intent);finish();
                                }else home();
                            }).show();
                }else{
                    String message=error.getMessage()==null?"เกิดข้อผิดพลาดในการจัดการไฟล์":error.getMessage();
                    new AlertDialog.Builder(this).setTitle("ดำเนินการไม่สำเร็จ").setMessage(message)
                            .setPositiveButton("ตกลง",(d,w)->home()).show();
                }
            });
        },"pos-backup").start();
    }
}