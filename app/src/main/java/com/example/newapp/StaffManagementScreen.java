package com.example.newapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

public final class StaffManagementScreen extends Activity {
    private PosDatabase helper;
    private LinearLayout root;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        helper = PosDatabase.get(this);
        OwnerSecurity.requireOwner(this, "จัดการบัญชีพนักงาน", this::home);
    }

    private void home() {
        root = PosUi.page(this, "จัดการบัญชีพนักงาน");
        PosUi.button(this, root, "เพิ่มพนักงาน", v -> addStaff());
        Cursor c = helper.getReadableDatabase().query("staff_users",
                new String[]{"id","username","display_name","active"}, null,null,null,null,"display_name COLLATE NOCASE ASC");
        while (c.moveToNext()) {
            long id=c.getLong(0);String username=c.getString(1),name=c.getString(2);boolean active=c.getInt(3)==1;
            PosUi.line(this,root,"#"+id+" • "+name+" ("+username+") • "+(active?"ใช้งาน":"ปิดใช้งาน"));
            if(active)PosUi.button(this,root,"ปิดบัญชี "+name,v->new AlertDialog.Builder(this)
                    .setMessage("ปิดบัญชีพนักงาน "+name+"? จะไม่สามารถเข้าสู่ระบบได้")
                    .setNegativeButton("ยกเลิก",null).setPositiveButton("ปิดบัญชี",(d,w)->deactivate(id,name)).show());
        }
        c.close();PosUi.back(this,root);
    }

    private void addStaff() {
        root=PosUi.page(this,"เพิ่มพนักงาน");
        EditText username=PosUi.input(this,root,"ชื่อผู้ใช้ (A-Z, 0-9, . _ -)");
        EditText name=PosUi.input(this,root,"ชื่อที่แสดงในประวัติ");
        EditText pin=PosUi.input(this,root,"PIN 6 หลัก");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        EditText confirm=PosUi.input(this,root,"ยืนยัน PIN");
        confirm.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        PosUi.button(this,root,"บันทึกพนักงาน",v->{
            String u=username.getText().toString().trim(),n=name.getText().toString().trim(),p=pin.getText().toString(),p2=confirm.getText().toString();
            if(!u.matches("[A-Za-z0-9_.-]{3,32}")){username.setError("ชื่อผู้ใช้ต้องมี 3–32 ตัวอักษร");return;}
            if(n.isEmpty()){name.setError("กรอกชื่อพนักงาน");return;}
            if(!p.matches("[0-9]{6}")||!p.equals(p2)){confirm.setError("PIN ต้องเป็นตัวเลข 6 หลักและต้องตรงกัน");return;}
            String salt=StaffSecurity.newSalt(),hash=StaffSecurity.hash(p,salt);long now=PosDatabase.now();
            try{
                SQLiteDatabase db=helper.getWritableDatabase();
                PosTransactions.execute(db,tx->{
                    ContentValues row=new ContentValues();row.put("username",u);row.put("display_name",n);row.put("pin_salt",salt);row.put("pin_hash",hash);row.put("active",1);row.put("created_at",now);
                    long id=tx.insertOrThrow("staff_users",null,row);
                    PosDatabase.audit(tx,"STAFF_CREATED","STAFF",String.valueOf(id),"username="+u+"; display_name="+n);
                    return null;
                });
                PosUi.result(this,true,"เพิ่มพนักงานเรียบร้อย",this::home);
            }catch(Exception e){PosUi.result(this,false,"เพิ่มพนักงานไม่สำเร็จ ชื่อผู้ใช้อาจซ้ำ: "+(e.getMessage()==null?"ข้อผิดพลาด":e.getMessage()),null);}
        });
        PosUi.back(this,root);
    }

    private void deactivate(long id,String name) {
        try{
            SQLiteDatabase db=helper.getWritableDatabase();
            PosTransactions.execute(db,tx->{
                ContentValues values=new ContentValues();values.put("active",0);
                if(tx.update("staff_users",values,"id=? AND active=1",new String[]{String.valueOf(id)})!=1)throw new IllegalStateException("บัญชีถูกปิดไปแล้ว");
                PosDatabase.audit(tx,"STAFF_DEACTIVATED","STAFF",String.valueOf(id),"display_name="+name);
                return null;
            });
            home();
        }catch(Exception e){PosUi.result(this,false,"ปิดบัญชีไม่สำเร็จ: "+(e.getMessage()==null?"ข้อผิดพลาด":e.getMessage()),null);}
    }
}
