package com.example.newapp;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Conservative retention pass. Unknown types and unsynced records are preserved. */
public final class RetentionManager {
    private static final long DAY_MS=24L*60L*60L*1000L;
    private static final long PAID_BILL_RETENTION_MS=30L*DAY_MS;
    private static final String PREFS="pos_retention_policy", LAST_RUN="last_run_at";
    private RetentionManager() {}

    public static void runIfDue(Context context) {
        Context app=context.getApplicationContext();
        String storeId=StoreIdentity.get(app);
        if(storeId==null||storeId.trim().isEmpty()) return;
        SharedPreferences prefs=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        synchronized(RetentionManager.class) {
            long last=prefs.getLong(LAST_RUN,0L);
            if(last>0L&&now-last<DAY_MS) return;
            prefs.edit().putLong(LAST_RUN,now).apply();
        }
        Thread worker=new Thread(()->new RetentionManager().run(app,storeId.trim()),"pos-data-retention");
        worker.setDaemon(true); worker.start();
    }

    private void run(Context context,String storeId) {
        DataHub hub=DataHub.getInstance(context);
        long now=System.currentTimeMillis();
        long billCutoff=now-PAID_BILL_RETENTION_MS;
        long salesCutoff=calendarCutoff(now,Calendar.YEAR,-1);
        long editCutoff=calendarCutoff(now,Calendar.MONTH,-6);
        if(!ensureSaleHistory(hub,storeId)) return;

        for(JSONObject bill:hub.getRecordsByType(storeId,"sale_bill")) {
            JSONObject p=bill.optJSONObject("payload");
            if(p==null||!"จ่ายแล้ว".equals(p.optString("paymentStatus"))) continue;
            long paidAt=p.optLong("paidAt",0L);
            if(paidAt<=0L&&"cash".equals(p.optString("paymentType"))) paidAt=p.optLong("createdAt",0L);
            if(paidAt>0L&&paidAt<=billCutoff) {
                hub.deleteExpiredRecord(storeId,bill.optString("recordId"),"sale_bill",billCutoff,(deleted,message)->{});
            }
        }
        for(JSONObject history:hub.getRecordsByType(storeId,"sale_history")) {
            JSONObject p=history.optJSONObject("payload");
            if(p==null) continue;
            long createdAt=p.optLong("createdAt",0L);
            if(createdAt>0L&&createdAt<=salesCutoff) {
                hub.deleteExpiredRecord(storeId,history.optString("recordId"),"sale_history",salesCutoff,(deleted,message)->{});
            }
        }
        // price_history is the explicit edit-history type. Keep stock_history as a ledger.
        for(JSONObject history:hub.getRecordsByType(storeId,"price_history")) {
            JSONObject p=history.optJSONObject("payload");
            if(p==null) continue;
            long changedAt=p.optLong("changedAt",0L);
            if(changedAt>0L&&changedAt<=editCutoff) {
                hub.deleteExpiredRecord(storeId,history.optString("recordId"),"price_history",editCutoff,(deleted,message)->{});
            }
        }
    }

    private long calendarCutoff(long now,int field,int amount) {
        Calendar c=Calendar.getInstance(); c.setTimeInMillis(now); c.add(field,amount); return c.getTimeInMillis();
    }

    private boolean ensureSaleHistory(DataHub hub,String storeId) {
        List<JSONObject> histories=hub.getRecordsByType(storeId,"sale_history");
        Map<String,JSONObject> byBill=new HashMap<>();
        for(JSONObject history:histories) {
            JSONObject p=history.optJSONObject("payload");
            if(p==null) continue;
            String id=p.optString("billId","").trim();
            if(!id.isEmpty()&&!byBill.containsKey(id)) byBill.put(id,history);
        }
        List<JSONObject> updates=new ArrayList<>();
        for(JSONObject bill:hub.getRecordsByType(storeId,"sale_bill")) {
            JSONObject bp=bill.optJSONObject("payload");
            String billId=bill.optString("recordId","").trim();
            if(bp==null||billId.isEmpty()||bp.optLong("createdAt",0L)<=0L) continue;
            JSONObject existing=byBill.get(billId);
            JSONObject old=existing==null?null:existing.optJSONObject("payload");
            try {
                JSONObject hp=old==null?new JSONObject():new JSONObject(old.toString());
                hp.put("billId",billId); hp.put("eventType","sale_confirmed");
                hp.put("paymentType",bp.optString("paymentType",""));
                hp.put("paymentStatus",bp.optString("paymentStatus",""));
                hp.put("total",bp.optDouble("total",0d));
                hp.put("createdAt",bp.optLong("createdAt",0L));
                hp.put("customerRecordId",bp.optString("customerRecordId",""));
                hp.put("saleSnapshot",new JSONObject(bp.toString()));
                if(old!=null&&hp.toString().equals(old.toString())) continue;
                JSONObject r=new JSONObject();
                r.put("recordId",existing==null?"sale_history:retention:"+billId:existing.optString("recordId"));
                r.put("dataType","sale_history"); r.put("schemaVersion",1);
                r.put("updatedAt",System.currentTimeMillis());
                r.put("revision",existing==null?1L:existing.optLong("revision",1L)+1L);
                r.put("payload",hp); updates.add(r);
            } catch(Exception error) { return false; }
        }
        for(int start=0;start<updates.size();start+=50) {
            int end=Math.min(updates.size(),start+50);
            if(!hub.saveRecordsAtomically(storeId, new ArrayList<>(updates.subList(start,end)))) return false;
        }
        return true;
    }
}
