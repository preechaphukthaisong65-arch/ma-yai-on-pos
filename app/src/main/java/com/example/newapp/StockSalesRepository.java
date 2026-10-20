package com.example.newapp;

import android.content.Context;
import com.example.newapp.datahub.DataHub;
import com.example.newapp.datahub.StoreIdentity;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Shared product, stock-ledger, customer, sale and receipt access through the existing DataHub. */
public final class StockSalesRepository {
    public static final String PRODUCT = "product";
    public static final String STOCK_HISTORY = "stock_history";
    private final DataHub hub;
    private final String storeId;

    public StockSalesRepository(Context context) {
        hub = DataHub.getInstance(context.getApplicationContext());
        storeId = StoreIdentity.get(context);
    }

    public String getStoreId() { return storeId; }
    public boolean isReady() { return storeId != null && !storeId.trim().isEmpty(); }
    public String newId(String prefix) { return prefix + ":" + UUID.randomUUID(); }

    public List<JSONObject> products() {
        List<JSONObject> result = new ArrayList<>();
        if (!isReady()) return result;
        for (JSONObject record : hub.getRecordsByType(storeId, PRODUCT)) {
            if (record.optJSONObject("payload") != null) result.add(record);
        }
        Collections.sort(result, Comparator.comparing(
                r -> payload(r).optString("name", ""), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public JSONObject findByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) return null;
        String wanted = barcode.trim();
        for (JSONObject r : products()) {
            if (wanted.equals(payload(r).optString("barcode", "").trim())) return r;
        }
        return null;
    }

    public JSONObject payload(JSONObject record) {
        JSONObject p = record == null ? null : record.optJSONObject("payload");
        return p == null ? new JSONObject() : p;
    }

    public String name(JSONObject r) { return payload(r).optString("name", ""); }
    public String barcode(JSONObject r) { return payload(r).optString("barcode", ""); }
    public double price(JSONObject r) { return payload(r).optDouble("price", 0d); }
    public double stock(JSONObject r) { return payload(r).optDouble("stockQuantity", 0d); }
    public double limit(JSONObject r) { return payload(r).optDouble("stockLimit", 0d); }
    public String unit(JSONObject r) { return payload(r).optString("unit", "ชิ้น"); }

    private JSONObject record(String id, String type, JSONObject payload) throws Exception {
        JSONObject r = new JSONObject();
        r.put("recordId", id);
        r.put("dataType", type);
        r.put("schemaVersion", 1);
        r.put("updatedAt", System.currentTimeMillis());
        r.put("revision", 1);
        r.put("payload", payload);
        return r;
    }

    private JSONObject history(JSONObject product, String type, double before, double after, String note) throws Exception {
        JSONObject p = payload(product);
        JSONObject h = new JSONObject();
        h.put("historyId", UUID.randomUUID().toString());
        h.put("productRecordId", product.optString("recordId"));
        h.put("productName", p.optString("name", ""));
        h.put("barcode", p.optString("barcode", ""));
        h.put("transactionType", type);
        h.put("beforeQuantity", before);
        h.put("afterQuantity", after);
        h.put("delta", after - before);
        h.put("changedAt", System.currentTimeMillis());
        h.put("note", note == null ? "" : note);
        return record(newId("stock_history"), STOCK_HISTORY, h);
    }

    public boolean createProduct(String name, String barcode, double price, double cost,
            String unit, double stockLimit, double initialQuantity) {
        if (!isReady() || name == null || name.trim().isEmpty() || barcode == null
                || barcode.trim().isEmpty() || price < 0 || cost < 0 || stockLimit < 0 || initialQuantity < 0
                || findByBarcode(barcode) != null) return false;
        try {
            JSONObject p = new JSONObject();
            p.put("name", name.trim()); p.put("barcode", barcode.trim());
            p.put("price", price); p.put("cost", cost); p.put("unit", unit == null ? "ชิ้น" : unit);
            p.put("stockLimit", stockLimit); p.put("stockQuantity", initialQuantity);
            p.put("createdAt", System.currentTimeMillis()); p.put("updatedAt", System.currentTimeMillis());
            String id = newId("product");
            JSONObject productRecord = record(id, PRODUCT, p);
            JSONObject h = new JSONObject();
            h.put("productRecordId", id); h.put("productName", name.trim()); h.put("barcode", barcode.trim());
            h.put("transactionType", "รับสินค้าเริ่มต้น"); h.put("beforeQuantity", 0);
            h.put("afterQuantity", initialQuantity); h.put("delta", initialQuantity);
            h.put("changedAt", System.currentTimeMillis()); h.put("note", "สร้างสินค้าใหม่");
            List<JSONObject> records = new ArrayList<>(); records.add(productRecord);
            records.add(record(newId("stock_history"), STOCK_HISTORY, h));
            return hub.saveRecordsAtomically(storeId, records);
        } catch (Exception e) { return false; }
    }

    public boolean receive(JSONObject product, double quantity) {
        if (!isReady() || product == null || quantity <= 0) return false;
        try {
            JSONObject p = new JSONObject(payload(product).toString());
            double before = p.optDouble("stockQuantity", 0d), after = before + quantity;
            p.put("stockQuantity", after); p.put("updatedAt", System.currentTimeMillis());
            List<JSONObject> records = new ArrayList<>();
            records.add(record(product.optString("recordId"), PRODUCT, p));
            records.add(history(product, "รับสินค้า", before, after, "รับเข้า +" + quantity));
            return hub.saveRecordsAtomically(storeId, records);
        } catch (Exception e) { return false; }
    }

    public boolean countStock(JSONObject product, double actual) {
        if (!isReady() || product == null || actual < 0) return false;
        try {
            JSONObject p = new JSONObject(payload(product).toString());
            double before = p.optDouble("stockQuantity", 0d);
            p.put("stockQuantity", actual); p.put("updatedAt", System.currentTimeMillis());
            List<JSONObject> records = new ArrayList<>();
            records.add(record(product.optString("recordId"), PRODUCT, p));
            records.add(history(product, "ตรวจนับสต๊อก", before, actual, "นับจริง " + actual));
            return hub.saveRecordsAtomically(storeId, records);
        } catch (Exception e) { return false; }
    }

    public boolean updateLimit(JSONObject product, double newLimit) {
        if (!isReady() || product == null || newLimit < 0) return false;
        try {
            JSONObject p = new JSONObject(payload(product).toString());
            p.put("stockLimit", newLimit); p.put("updatedAt", System.currentTimeMillis());
            List<JSONObject> records = new ArrayList<>();
            records.add(record(product.optString("recordId"), PRODUCT, p));
            boolean saved = hub.saveRecordsAtomically(storeId, records);
            if (saved) StockLimitNotifier.updateState(product.optString("recordId"),
                    stock(product) <= newLimit, stock(product), newLimit);
            return saved;
        } catch (Exception e) { return false; }
    }

    public List<JSONObject> history(String barcode) {
        List<JSONObject> out = new ArrayList<>();
        if (!isReady()) return out;
        for (JSONObject r : hub.getRecordsByType(storeId, STOCK_HISTORY)) {
            JSONObject p = r.optJSONObject("payload");
            if (p != null && barcode != null && barcode.equals(p.optString("barcode"))) out.add(r);
        }
        Collections.sort(out, (a,b) -> Long.compare(
                b.optJSONObject("payload").optLong("changedAt", 0L),
                a.optJSONObject("payload").optLong("changedAt", 0L)));
        return out;
    }

    public List<JSONObject> lowStockProducts() {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject p : products()) {
            JSONObject d = payload(p);
            if (d.optDouble("stockQuantity", 0d) <= d.optDouble("stockLimit", 0d)) out.add(p);
        }
        out.sort(Comparator.comparingDouble(this::stock));
        return out;
    }

    public List<JSONObject> records(String type) {
        return isReady() ? hub.getRecordsByType(storeId, type) : new ArrayList<>();
    }

    public boolean saveRecord(String type, String id, JSONObject payload) {
        return isReady() && hub.saveRecord(storeId, id, type, 1, 1, payload);
    }
}
