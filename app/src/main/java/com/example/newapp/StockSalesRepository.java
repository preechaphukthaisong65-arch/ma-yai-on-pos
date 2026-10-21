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
import java.util.Map;
import java.util.HashMap;

/** Shared product, stock-ledger, customer, sale and receipt access through the existing DataHub. */
public final class StockSalesRepository {
    public static final String PRODUCT = "product";
    public static final String STOCK_HISTORY = "stock_history";
    private final DataHub hub;
    private final String storeId;
    private List<JSONObject> productCache;
    private Map<String, JSONObject> barcodeCache;

    public StockSalesRepository(Context context) {
        hub = DataHub.getInstance(context.getApplicationContext());
        storeId = StoreIdentity.get(context);
        StockLimitNotifier.initialize(context);
    }

    public String getStoreId() { return storeId; }
    public boolean isReady() { return storeId != null && !storeId.trim().isEmpty(); }
    public String newId(String prefix) { return prefix + ":" + UUID.randomUUID(); }

    public synchronized List<JSONObject> products() {
        if (!isReady()) return new ArrayList<>();
        if (productCache == null) {
            productCache = new ArrayList<>();
            barcodeCache = new HashMap<>();
            for (JSONObject record : hub.getRecordsByType(storeId, PRODUCT)) {
                if (record.optJSONObject("payload") == null) continue;
                productCache.add(record);
                String code = payload(record).optString("barcode", "").trim();
                if (!code.isEmpty() && !barcodeCache.containsKey(code)) barcodeCache.put(code, record);
            }
            Collections.sort(productCache, Comparator.comparing(
                    r -> payload(r).optString("name", ""), String.CASE_INSENSITIVE_ORDER));
        }
        return new ArrayList<>(productCache);
    }

    public synchronized JSONObject findByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) return null;
        products();
        return barcodeCache == null ? null : barcodeCache.get(barcode.trim());
    }

    private synchronized void invalidateProductCache() {
        productCache = null;
        barcodeCache = null;
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

    private JSONObject recordExpected(String id, String type, JSONObject payload,
            long expectedRevision) throws Exception {
        JSONObject r = record(id, type, payload);
        r.put("expectedRevision", Math.max(0L, expectedRevision));
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
            List<JSONObject> records = new ArrayList<>();
            records.add(productRecord);
            records.add(record(newId("stock_history"), STOCK_HISTORY, h));

            // Keep a separate barcode-library entry for factory barcodes entered
            // through stock receiving. The barcode library stores identity/price
            // only; stock quantities and stock history remain product/ledger data.
            boolean libraryEntryExists = false;
            for (JSONObject existingBarcode : hub.getRecordsByType(storeId, "barcode_item")) {
                JSONObject existingPayload = existingBarcode.optJSONObject("payload");
                if (existingPayload != null
                        && barcode.trim().equals(existingPayload.optString("barcode", "").trim())) {
                    libraryEntryExists = true;
                    break;
                }
            }
            if (!libraryEntryExists) {
                JSONObject barcodePayload = new JSONObject();
                barcodePayload.put("name", name.trim());
                barcodePayload.put("barcode", barcode.trim());
                barcodePayload.put("price", price);
                barcodePayload.put("source", "factory");
                barcodePayload.put("productRecordId", id);
                barcodePayload.put("createdAt", System.currentTimeMillis());
                records.add(record(newId("barcode_item"), "barcode_item", barcodePayload));
            }

            boolean saved = hub.saveRecordsAtomically(storeId, records);
            if (saved) { invalidateProductCache(); StockLimitNotifier.updateState(storeId, id, initialQuantity <= stockLimit, initialQuantity, stockLimit); }
            return saved;
        } catch (Exception e) { return false; }
    }

    public boolean receive(JSONObject product, double quantity) {
        if (!isReady() || product == null || quantity <= 0) return false;
        try {
            JSONObject p = new JSONObject(payload(product).toString());
            double before = p.optDouble("stockQuantity", 0d), after = before + quantity;
            p.put("stockQuantity", after); p.put("updatedAt", System.currentTimeMillis());
            List<JSONObject> records = new ArrayList<>();
            records.add(recordExpected(product.optString("recordId"), PRODUCT, p,
                    product.optLong("revision", 0L)));
            records.add(history(product, "รับสินค้า", before, after, "รับเข้า +" + quantity));
            boolean saved = hub.saveRecordsAtomically(storeId, records);
            double limit = p.optDouble("stockLimit", 0d);
            if (saved) { invalidateProductCache(); StockLimitNotifier.updateState(storeId, product.optString("recordId"), after <= limit, after, limit); }
            return saved;
        } catch (Exception e) { return false; }
    }

    public boolean countStock(JSONObject product, double actual) {
        if (!isReady() || product == null || actual < 0) return false;
        try {
            JSONObject p = new JSONObject(payload(product).toString());
            double before = p.optDouble("stockQuantity", 0d);
            p.put("stockQuantity", actual); p.put("updatedAt", System.currentTimeMillis());
            List<JSONObject> records = new ArrayList<>();
            records.add(recordExpected(product.optString("recordId"), PRODUCT, p,
                    product.optLong("revision", 0L)));
            records.add(history(product, "ตรวจนับสต๊อก", before, actual, "นับจริง " + actual));
            boolean saved = hub.saveRecordsAtomically(storeId, records);
            double limit = p.optDouble("stockLimit", 0d);
            if (saved) { invalidateProductCache(); StockLimitNotifier.updateState(storeId, product.optString("recordId"), actual <= limit, actual, limit); }
            return saved;
        } catch (Exception e) { return false; }
    }

    public boolean updateLimit(JSONObject product, double newLimit) {
        if (!isReady() || product == null || newLimit < 0) return false;
        try {
            JSONObject p = new JSONObject(payload(product).toString());
            p.put("stockLimit", newLimit); if (!p.has("stockQuantity")) p.put("stockQuantity", 0d); p.put("updatedAt", System.currentTimeMillis());
            List<JSONObject> records = new ArrayList<>();
            records.add(recordExpected(product.optString("recordId"), PRODUCT, p,
                    product.optLong("revision", 0L)));
            boolean saved = hub.saveRecordsAtomically(storeId, records);
            if (saved) { invalidateProductCache(); StockLimitNotifier.updateState(storeId, product.optString("recordId"),
                    stock(product) <= newLimit, stock(product), newLimit); }
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
            if (d.has("stockQuantity") && d.has("stockLimit")
                    && d.optDouble("stockQuantity", 0d) <= d.optDouble("stockLimit", 0d)) out.add(p);
        }
        out.sort(Comparator.comparingDouble(this::stock));
        return out;
    }

    /** Confirm a sale and its stock ledger in one local SQLite transaction. The bill ID makes retries idempotent. */
    public synchronized boolean confirmSale(String billId, JSONObject billPayload, List<JSONObject> soldProducts,
            List<Double> quantities, JSONObject receiptPayload) {
        if (!isReady() || billId == null || billId.trim().isEmpty() || billPayload == null
                || receiptPayload == null || soldProducts == null || quantities == null
                || soldProducts.size() != quantities.size() || soldProducts.isEmpty()) return false;
        if (hub.getRecord(storeId, billId) != null) return false;
        try {
            // This local-only sale must be reconciled as one server transaction.
            // Preserve the cloud revision observed before each local stock decrement.
            // Ensure pending sales have a strictly increasing order even if two
            // confirmations occur in the same millisecond or the device clock moves back.
            long saleCreatedAt = Math.max(1L, billPayload.optLong("createdAt", System.currentTimeMillis()));
            for (JSONObject pendingBill : hub.getRecordsByType(storeId, "sale_bill")) {
                JSONObject pendingPayload = pendingBill.optJSONObject("payload");
                if (pendingPayload != null && pendingPayload.optBoolean("offlinePending", false)) {
                    saleCreatedAt = Math.max(saleCreatedAt, pendingPayload.optLong("createdAt", 0L) + 1L);
                }
            }
            billPayload.put("createdAt", saleCreatedAt);
            billPayload.put("offlinePending", true);
            org.json.JSONArray saleItems = billPayload.optJSONArray("items");
            if (saleItems == null || saleItems.length() != soldProducts.size()) return false;
            // Validate every line against the latest stored product before preparing any writes.
            List<JSONObject> currentProducts = new ArrayList<>();
            List<JSONObject> updatedPayloads = new ArrayList<>();
            List<Double> beforeQuantities = new ArrayList<>();
            List<Double> afterQuantities = new ArrayList<>();
            java.util.Set<String> seenProductIds = new java.util.HashSet<>();
            for (int i = 0; i < soldProducts.size(); i++) {
                JSONObject product = soldProducts.get(i);
                Double quantityValue = quantities.get(i);
                if (product == null || quantityValue == null) return false;
                double qty = quantityValue;
                if (!Double.isFinite(qty) || qty <= 0d) return false;
                String productId = product.optString("recordId", "");
                if (productId.trim().isEmpty() || !seenProductIds.add(productId)) return false;
                JSONObject current = hub.getRecord(storeId, productId);
                if (current == null || !PRODUCT.equals(current.optString("dataType"))) return false;
                saleItems.getJSONObject(i).put("localRevision", current.optLong("revision", 0L));
                JSONObject p = new JSONObject(payload(current).toString());
                double before = p.optDouble("stockQuantity", 0d);
                if (!Double.isFinite(before) || before < 0d || qty > before + 0.000001d) return false;
                double after = before - qty;
                if (after < 0d && after > -0.000001d) after = 0d;
                if (after < 0d) return false;
                p.put("stockQuantity", after);
                p.put("updatedAt", System.currentTimeMillis());
                currentProducts.add(current);
                updatedPayloads.add(p);
                beforeQuantities.add(before);
                afterQuantities.add(after);
            }
            List<JSONObject> records = new ArrayList<>();
            records.add(record(billId, "sale_bill", billPayload));
            records.add(record("receipt:" + billId, "receipt", receiptPayload));
            JSONObject saleAudit = new JSONObject();
            saleAudit.put("billId", billId); saleAudit.put("eventType", "sale_confirmed");
            saleAudit.put("paymentType", billPayload.optString("paymentType"));
            saleAudit.put("paymentStatus", billPayload.optString("paymentStatus"));
            saleAudit.put("total", billPayload.optDouble("total", 0d));
            saleAudit.put("createdAt", System.currentTimeMillis());
            saleAudit.put("customerRecordId", billPayload.optString("customerRecordId", ""));
            // Preserve the complete sale snapshot so retained reports/history remain useful
            // after the original bill reaches its retention deadline.
            saleAudit.put("saleSnapshot", new JSONObject(billPayload.toString()));
            records.add(record("sale_history:" + billId, "sale_history", saleAudit));
            for (int i = 0; i < currentProducts.size(); i++) {
                JSONObject current = currentProducts.get(i);
                records.add(recordExpected(current.optString("recordId"), PRODUCT,
                        updatedPayloads.get(i), current.optLong("revision", 0L)));
                JSONObject saleHistory = new JSONObject();
                saleHistory.put("historyId", "stock_history:sale:" + billId + ":" + i);
                saleHistory.put("productRecordId", current.optString("recordId"));
                saleHistory.put("productName", payload(current).optString("name", ""));
                saleHistory.put("barcode", payload(current).optString("barcode", ""));
                saleHistory.put("transactionType", "ขายสินค้า");
                saleHistory.put("beforeQuantity", beforeQuantities.get(i));
                saleHistory.put("afterQuantity", afterQuantities.get(i));
                saleHistory.put("delta", afterQuantities.get(i) - beforeQuantities.get(i));
                saleHistory.put("changedAt", System.currentTimeMillis());
                saleHistory.put("note", "ขายบิล " + billId);
                records.add(record("stock_history:sale:" + billId + ":" + i, STOCK_HISTORY, saleHistory));
            }
            boolean saved = hub.saveRecordsAtomically(storeId, records);
            if (saved) {
                invalidateProductCache();
                for (int i = 0; i < currentProducts.size(); i++) {
                    JSONObject latest = hub.getRecord(storeId, currentProducts.get(i).optString("recordId"));
                    if (latest != null) StockLimitNotifier.updateState(storeId, latest.optString("recordId"),
                            stock(latest) <= limit(latest), stock(latest), limit(latest));
                }
            }
            return saved;
        } catch (Exception e) { return false; }
    }

    /** Apply a server-committed sale snapshot locally using revision compare-and-set. */
    public synchronized boolean applyServerCommittedSaleRecords(List<JSONObject> records) {
        if (!isReady() || records == null || records.isEmpty()) return false;
        for (JSONObject record : records) {
            if (record == null || !storeId.equals(record.optString("storeId", ""))
                    || record.optString("recordId", "").trim().isEmpty()
                    || record.optJSONObject("payload") == null) return false;
        }
        boolean saved = hub.saveServerCommittedRecordsAtomically(storeId, records);
        if (saved) {
            invalidateProductCache();
            for (JSONObject record : records) {
                if (!PRODUCT.equals(record.optString("dataType"))) continue;
                JSONObject latest = hub.getRecord(storeId, record.optString("recordId"));
                if (latest != null) StockLimitNotifier.updateState(storeId, latest.optString("recordId"),
                        stock(latest) <= limit(latest), stock(latest), limit(latest));
            }
        }
        return saved;
    }

    /**
     * Record a cash payment against one or more credit bills.
     * Every selected bill must be paid in full; partial bill payments are not supported.
     */
    public synchronized boolean payCreditBills(List<JSONObject> selectedBills, double received) {
        if (!isReady() || selectedBills == null || selectedBills.isEmpty()
                || !Double.isFinite(received) || received <= 0d) return false;
        try {
            List<JSONObject> refreshed = new ArrayList<>();
            List<Long> balancesCents = new ArrayList<>();
            long dueCents = 0L;
            for (JSONObject old : selectedBills) {
                if (old == null) return false;
                String billId = old.optString("recordId", "").trim();
                if (billId.isEmpty()) return false;
                // Reject duplicate bill IDs to avoid allocating the same debt twice.
                for (JSONObject already : refreshed) {
                    if (billId.equals(already.optString("recordId", ""))) return false;
                }
                JSONObject current = hub.getRecord(storeId, billId);
                JSONObject p = current == null ? null : current.optJSONObject("payload");
                if (p == null || !"credit".equals(p.optString("paymentType"))
                        || !"ค้างชำระ".equals(p.optString("paymentStatus"))) return false;
                double rawBalance = p.has("remainingBalance")
                        ? p.optDouble("remainingBalance", Double.NaN)
                        : p.optDouble("total", Double.NaN);
                if (!Double.isFinite(rawBalance) || rawBalance <= 0d) return false;
                long cents = Math.round(rawBalance * 100d);
                if (cents <= 0L || Long.MAX_VALUE - dueCents < cents) return false;
                dueCents += cents;
                balancesCents.add(cents);
                refreshed.add(current);
            }
            long receivedCents = Math.round(received * 100d);
            if (receivedCents <= 0L || receivedCents != dueCents) return false;

            String paymentId = newId("credit_payment");
            long now = System.currentTimeMillis();
            org.json.JSONArray ids = new org.json.JSONArray();
            org.json.JSONArray allocations = new org.json.JSONArray();
            List<JSONObject> records = new ArrayList<>();
            long remainingPayment = receivedCents;
            for (int i = 0; i < refreshed.size() && remainingPayment > 0L; i++) {
                JSONObject current = refreshed.get(i);
                long balanceCents = balancesCents.get(i);
                long allocatedCents = Math.min(balanceCents, remainingPayment);
                if (allocatedCents <= 0L) continue;
                long newBalanceCents = balanceCents - allocatedCents;
                JSONObject p = new JSONObject(current.optJSONObject("payload").toString());
                p.put("remainingBalance", newBalanceCents / 100d);
                p.put("paymentStatus", newBalanceCents == 0L ? "จ่ายแล้ว" : "ค้างชำระ");
                p.put("lastPaymentId", paymentId);
                p.put("lastPaymentAt", now);
                if (newBalanceCents == 0L) {
                    p.put("paidAt", now);
                } else {
                    p.remove("paidAt");
                }
                records.add(recordExpected(current.optString("recordId"), "sale_bill", p,
                        current.optLong("revision", 0L)));
                String billId = current.optString("recordId");
                ids.put(billId);
                JSONObject allocation = new JSONObject();
                allocation.put("billId", billId);
                allocation.put("amount", allocatedCents / 100d);
                allocation.put("remainingBalance", newBalanceCents / 100d);
                allocation.put("paymentStatus", newBalanceCents == 0L ? "จ่ายแล้ว" : "ค้างชำระ");
                allocations.put(allocation);
                remainingPayment -= allocatedCents;
            }
            if (remainingPayment != 0L) return false;

            JSONObject payment = new JSONObject();
            payment.put("paymentId", paymentId);
            payment.put("paidAt", now);
            payment.put("amount", receivedCents / 100d);
            payment.put("paymentMethod", "cash");
            payment.put("billIds", ids);
            payment.put("allocations", allocations);
            payment.put("isPartialPayment", false);
            records.add(record(paymentId, "credit_payment", payment));

            JSONObject receipt = new JSONObject();
            receipt.put("receiptId", "receipt:" + paymentId);
            receipt.put("paymentId", paymentId);
            receipt.put("receiptType", "ใบเสร็จรับชำระหนี้");
            receipt.put("createdAt", now);
            receipt.put("total", receivedCents / 100d);
            receipt.put("billIds", ids);
            receipt.put("allocations", allocations);
            receipt.put("paymentStatus", "รับชำระแล้ว");
            receipt.put("printStatus", "pending");
            records.add(record("receipt:" + paymentId, "receipt", receipt));

            JSONObject history = new JSONObject();
            history.put("paymentId", paymentId);
            history.put("paidAt", now);
            history.put("amount", receivedCents / 100d);
            history.put("billIds", ids);
            history.put("allocations", allocations);
            history.put("eventType", "credit_payment");
            records.add(record(newId("credit_payment_history"), "credit_payment_history", history));
            return hub.saveRecordsAtomically(storeId, records);
        } catch (Exception e) { return false; }
    }

    public void payCreditBillsOnline(List<JSONObject> chosen, double receivedAmount,
            DataHub.OperationCallback callback) {
        if (!isReady()) {
            if (callback != null) callback.onComplete(0, 1, "ไม่พบรหัสร้านค้า");
            return;
        }
        hub.commitCreditPayment(storeId, chosen, receivedAmount, callback);
    }

    public List<JSONObject> customers() { return records("credit_customer"); }

    public JSONObject findCustomer(String firstName, String lastName) {
        if (firstName == null || lastName == null) return null;
        for (JSONObject r : customers()) {
            JSONObject p = r.optJSONObject("payload");
            if (p != null && firstName.trim().equalsIgnoreCase(p.optString("firstName", "").trim())
                    && lastName.trim().equalsIgnoreCase(p.optString("lastName", "").trim())) return r;
        }
        return null;
    }

    public boolean saveCustomer(JSONObject customer) {
        if (!isReady() || customer == null) return false;
        try {
            String first = customer.optString("firstName", "").trim();
            String last = customer.optString("lastName", "").trim();
            if (first.isEmpty() || last.isEmpty()) return false;
            JSONObject existing = findCustomer(first, last);
            if (existing != null) return true;
            return saveRecord("credit_customer", newId("credit_customer"), customer);
        } catch (Exception e) { return false; }
    }

    public List<JSONObject> records(String type) {
        return isReady() ? hub.getRecordsByType(storeId, type) : new ArrayList<>();
    }

    public boolean saveRecord(String type, String id, JSONObject payload) {
        return isReady() && hub.saveRecord(storeId, id, type, 1, 1, payload);
    }
}
