package com.example.newapp;
import android.content.Context;import com.example.newapp.datahub.DataHub;import com.example.newapp.datahub.StoreIdentity;import org.json.JSONObject;import java.util.ArrayList;import java.util.Collections;import java.util.Comparator;import java.util.List;
/** Shared DataHub product contract: dataType=product, payload name/barcode/price. */
public final class StoreManagementRepository {
 private final DataHub hub;private final String storeId;
 public StoreManagementRepository(Context c){hub=DataHub.getInstance(c.getApplicationContext());storeId=StoreIdentity.get(c);}
 public String getStoreId(){return storeId;}
 public JSONObject findProductByBarcode(String barcode){if(barcode==null||barcode.trim().isEmpty()||storeId.isEmpty())return null;for(JSONObject r:hub.getRecordsByType(storeId,"product")){JSONObject p=r.optJSONObject("payload");if(p!=null&&barcode.trim().equals(p.optString("barcode")))return r;}return null;}
 public String productName(JSONObject r){JSONObject p=r==null?null:r.optJSONObject("payload");return p==null?"":p.optString("name","");}
 public String barcode(JSONObject r){JSONObject p=r==null?null:r.optJSONObject("payload");return p==null?"":p.optString("barcode","");}
 public double price(JSONObject r){JSONObject p=r==null?null:r.optJSONObject("payload");return p==null?Double.NaN:p.optDouble("price",Double.NaN);}
 public boolean updatePrice(JSONObject r,double n){if(r==null||storeId.isEmpty())return false;return hub.savePriceChange(storeId,r.optString("recordId"),barcode(r),productName(r),price(r),n,System.currentTimeMillis());}
 public List<JSONObject> getPriceHistory(String barcode){List<JSONObject> out=new ArrayList<>();if(barcode==null||barcode.trim().isEmpty()||storeId.isEmpty())return out;for(JSONObject r:hub.getRecordsByType(storeId,"price_history")){JSONObject p=r.optJSONObject("payload");if(p!=null&&barcode.trim().equals(p.optString("barcode")))out.add(r);}Collections.sort(out,Comparator.comparingLong(r->{JSONObject p=r.optJSONObject("payload");return p==null?0L:p.optLong("changedAt",0L);}));return out;}
}
