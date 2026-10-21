package com.example.newapp.datahub;

import android.content.Context;

import org.json.JSONObject;

import java.util.List;

public class DataHub {

    public static final String SYNC_PENDING = "pending";
    public static final String SYNC_SYNCED = "synced";
    public static final String SYNC_FAILED = "failed";

    private static volatile DataHub instance;

    private final LocalStore localStore;
    private final CloudSyncService cloudSyncService;
    private final DataHubStatus dataHubStatus;
    private final Context appContext;

    private DataHub(Context context) {
        appContext = context.getApplicationContext();
        localStore = new LocalStore(appContext);
        cloudSyncService = new CloudSyncService();
        dataHubStatus = new DataHubStatus();
    }

    public static DataHub getInstance(Context context) {
        if (instance == null) {
            synchronized (DataHub.class) {
                if (instance == null) {
                    instance = new DataHub(context);
                }
            }
        }

        return instance;
    }

    public interface OperationCallback {
        void onComplete(int succeeded, int failed, String message);
    }

    public interface RetentionDeleteCallback {
        void onComplete(boolean deleted, String message);
    }

    /**
     * บันทึกข้อมูลลง LocalStore ก่อน
     * ถ้าบันทึกสำเร็จ จะเริ่มซิงก์ข้อมูลขึ้น Cloud อัตโนมัติ
     *
     * วิธีนี้ไม่แก้ไขข้อมูลการขายหรือข้อมูลสต๊อก
     * และไม่ถือว่าการบันทึกในเครื่องล้มเหลว
     * เพียงเพราะการสำรอง Cloud ไม่สำเร็จ
     */
    public boolean saveRecord(
            String storeId,
            String recordId,
            String dataType,
            int schemaVersion,
            long revision,
            JSONObject payload
    ) {
        if (storeId == null || storeId.trim().isEmpty()
                || recordId == null || recordId.trim().isEmpty()
                || dataType == null || dataType.trim().isEmpty()
                || payload == null) {
            return false;
        }

        String checkedStoreId = storeId.trim();
        // Persist the active shop identity so WorkManager can retry pending
        // records after process death or device restart.
        StoreIdentity.save(appContext, checkedStoreId);

        JSONObject record = new JSONObject();

        try {
            record.put("storeId", checkedStoreId);
            record.put("recordId", recordId.trim());
            record.put("dataType", dataType.trim());
            record.put("schemaVersion", Math.max(1, schemaVersion));
            record.put("updatedAt", System.currentTimeMillis());

            // Always advance the local revision when replacing an existing
            // record, even if a caller accidentally reuses an old revision.
            JSONObject previous = localStore.getRecord(
                    checkedStoreId, recordId.trim()
            );
            long previousRevision = previous == null
                    ? 0L : previous.optLong("revision", 0L);
            long nextRevision = Math.max(
                    Math.max(1L, revision),
                    previousRevision + 1L
            );
            record.put("revision", nextRevision);
            record.put("payload", payload);
            record.put("syncStatus", SYNC_PENDING);

            boolean saved = localStore.saveRecord(record);

            refreshStatus(checkedStoreId);

            if (saved) {
                // เริ่มสำรองรายการที่รออยู่ในร้านนี้โดยอัตโนมัติ
                // ความล้มเหลวของ Cloud จะไม่ลบข้อมูลที่บันทึกในเครื่อง
                syncPendingRecords(checkedStoreId, null);
                SyncRetryScheduler.schedule(appContext, checkedStoreId);
            }

            return saved;

        } catch (Exception error) {
            dataHubStatus.setStatus(
                    SYNC_FAILED,
                    "บันทึกข้อมูลไม่สำเร็จ"
            );
            return false;
        }
    }

    /**
     * อ่านข้อมูลจาก LocalStore
     */
    public JSONObject getRecord(String storeId, String recordId) {
        if (storeId == null || recordId == null) {
            return null;
        }

        try {
            return localStore.getRecord(
                    storeId.trim(),
                    recordId.trim()
            );
        } catch (Exception error) {
            return null;
        }
    }

    /** Read records from the single shared DataHub dataset. */
    public List<JSONObject> getRecordsByType(String storeId, String dataType) {
        if (isBlank(storeId) || isBlank(dataType)) return new java.util.ArrayList<>();
        try { return localStore.getRecordsByType(storeId.trim(), dataType.trim()); }
        catch (Exception error) {
            dataHubStatus.setStatus(SYNC_FAILED, "อ่านข้อมูลกลางไม่สำเร็จ");
            return new java.util.ArrayList<>();
        }
    }

    /** Persist related domain records in one SQLite transaction, then queue cloud sync. */
    public synchronized boolean saveRecordsAtomically(String storeId, List<JSONObject> records) {
        if (isBlank(storeId) || records == null || records.isEmpty()) return false;
        String checkedStore = storeId.trim();
        try {
            java.util.List<JSONObject> prepared = new java.util.ArrayList<>();
            for (JSONObject input : records) {
                if (input == null) return false;
                String recordId = input.optString("recordId", "").trim();
                String dataType = input.optString("dataType", "").trim();
                JSONObject payload = input.optJSONObject("payload");
                if (recordId.isEmpty() || dataType.isEmpty() || payload == null) return false;
                JSONObject previous = localStore.getRecord(checkedStore, recordId);
                JSONObject record = new JSONObject();
                record.put("storeId", checkedStore);
                record.put("recordId", recordId);
                record.put("dataType", dataType);
                record.put("schemaVersion", Math.max(1, input.optInt("schemaVersion", 1)));
                record.put("updatedAt", Math.max(1L, input.optLong("updatedAt", System.currentTimeMillis())));
                // Ordinary local mutations advance the local revision. This is
                // required for sequential offline sales of the same SKU: each
                // queued sale carries the revision observed before that sale.
                // Server-committed snapshots use the dedicated atomic method and
                // preserve the authoritative server revision separately.
                long nextRevision = previous == null ? 1L
                        : Math.max(1L, previous.optLong("revision", 0L) + 1L);
                record.put("revision", Math.max(input.optLong("revision", 1L), nextRevision));
                // Internal compare-and-set metadata is consumed by LocalStore and
                // never persisted in the user payload or SQLite columns.
                if (input.has("expectedRevision")) {
                    record.put("expectedRevision", input.optLong("expectedRevision", -1L));
                }
                record.put("payload", payload);
                record.put("syncStatus", SYNC_PENDING);
                prepared.add(record);
            }
            if (!localStore.saveRecordsAtomically(prepared)) return false;
            StoreIdentity.save(appContext, checkedStore);
            refreshStatus(checkedStore);
            syncPendingRecords(checkedStore, null);
            SyncRetryScheduler.schedule(appContext, checkedStore);
            return true;
        } catch (Exception error) {
            dataHubStatus.setStatus(SYNC_FAILED, "บันทึกข้อมูลรายการที่เกี่ยวข้องไม่สำเร็จ");
            return false;
        }
    }

    /**
     * Atomically update a product price and append a history and home notice
     * record in the existing hub_records table.
     * Product payload contract: name, barcode, price; dataType=product.
     */
    public synchronized boolean savePriceChange(String storeId, String productRecordId,
            String barcode, String productName, double expectedOldPrice,
            double newPrice, long changedAt) {
        if (isBlank(storeId) || isBlank(productRecordId) || isBlank(barcode) || isBlank(productName)
                || !Double.isFinite(expectedOldPrice) || !Double.isFinite(newPrice) || newPrice < 0
                || Double.compare(expectedOldPrice, newPrice) == 0) return false;
        try {
            String checkedStore = storeId.trim();
            JSONObject current = localStore.getRecord(checkedStore, productRecordId.trim());
            if (current == null || !"product".equals(current.optString("dataType"))) return false;
            JSONObject payload = current.optJSONObject("payload");
            if (payload == null || !barcode.trim().equals(payload.optString("barcode"))
                    || !productName.equals(payload.optString("name"))
                    || Double.compare(expectedOldPrice, payload.optDouble("price", Double.NaN)) != 0) return false;
            JSONObject updatedProduct = new JSONObject(payload.toString());
            updatedProduct.put("price", newPrice);
            long now = Math.max(1L, changedAt);
            JSONObject productRecord = buildManagementRecord(checkedStore, productRecordId.trim(),
                    "product", current.optLong("revision", 0L) + 1L, now, updatedProduct);
            // Reject a price edit if another local action changed this product
            // between the read above and the SQLite transaction.
            productRecord.put("expectedRevision", current.optLong("revision", 0L));
            JSONObject history = new JSONObject();
            history.put("productRecordId", productRecordId.trim());
            history.put("productName", productName);
            history.put("barcode", barcode.trim());
            history.put("oldPrice", expectedOldPrice);
            history.put("newPrice", newPrice);
            history.put("changedAt", now);
            JSONObject historyRecord = buildManagementRecord(checkedStore,
                    "price_history:" + java.util.UUID.randomUUID(), "price_history", 1L, now, history);
            JSONObject notice = new JSONObject();
            notice.put("message", "เปลี่ยนราคาสินค้า " + productName + " จาก " + expectedOldPrice + " เป็น " + newPrice + " บาท");
            notice.put("changedAt", now);
            notice.put("eventType", "price_changed");
            JSONObject noticeRecord = buildManagementRecord(checkedStore,
                    "app_notification:price:" + java.util.UUID.randomUUID(), "app_notification", 1L, now, notice);
            java.util.List<JSONObject> records = new java.util.ArrayList<>();
            records.add(productRecord); records.add(historyRecord); records.add(noticeRecord);
            if (!localStore.saveRecordsAtomically(records)) return false;
            StoreIdentity.save(appContext, checkedStore);
            refreshStatus(checkedStore);
            syncPendingRecords(checkedStore, null);
            SyncRetryScheduler.schedule(appContext, checkedStore);
            return true;
        } catch (Exception error) {
            dataHubStatus.setStatus(SYNC_FAILED, "บันทึกราคาและประวัติไม่สำเร็จ");
            return false;
        }
    }

    private JSONObject buildManagementRecord(String storeId, String recordId, String dataType,
            long revision, long updatedAt, JSONObject payload) throws org.json.JSONException {
        JSONObject record = new JSONObject();
        record.put("storeId", storeId); record.put("recordId", recordId);
        record.put("dataType", dataType); record.put("schemaVersion", 1);
        record.put("updatedAt", updatedAt); record.put("revision", Math.max(1L, revision));
        record.put("payload", payload); record.put("syncStatus", SYNC_PENDING);
        return record;
    }

    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }

    private boolean hasPendingOfflineSales(String storeId) {
        for (JSONObject record : getRecordsByType(storeId, "sale_bill")) {
            JSONObject payload = record.optJSONObject("payload");
            if (payload != null && payload.optBoolean("offlinePending", false)) return true;
        }
        return false;
    }

    /**
     * Apply records returned by a successful authoritative Cloud Function.
     * Unlike ordinary local edits, these snapshots are already committed in
     * Firestore, so persist them locally as synced without queuing another upload.
     */
    public synchronized boolean saveServerCommittedRecordsAtomically(
            String storeId, List<JSONObject> records) {
        if (isBlank(storeId) || records == null || records.isEmpty()) return false;
        String checkedStore = storeId.trim();
        try {
            java.util.List<JSONObject> prepared = new java.util.ArrayList<>();
            java.util.Set<String> keys = new java.util.HashSet<>();
            for (JSONObject input : records) {
                if (input == null) return false;
                String recordId = input.optString("recordId", "").trim();
                String dataType = input.optString("dataType", "").trim();
                JSONObject payload = input.optJSONObject("payload");
                if (recordId.isEmpty() || dataType.isEmpty() || payload == null
                        || !keys.add(recordId)) return false;
                String recordStore = input.optString("storeId", checkedStore).trim();
                if (!checkedStore.equals(recordStore)) return false;
                JSONObject previous = localStore.getRecord(checkedStore, recordId);
                JSONObject record = new JSONObject();
                record.put("storeId", checkedStore);
                record.put("recordId", recordId);
                record.put("dataType", dataType);
                record.put("schemaVersion", Math.max(1, input.optInt("schemaVersion", 1)));
                record.put("updatedAt", Math.max(1L, input.optLong("updatedAt", System.currentTimeMillis())));
                // The server revision is authoritative; do not increment it again locally.
                record.put("revision", Math.max(1L, input.optLong("revision", 1L)));
                if (input.has("expectedRevision")) {
                    record.put("expectedRevision", input.optLong("expectedRevision", -1L));
                }
                record.put("payload", payload);
                prepared.add(record);
            }
            boolean saved = localStore.saveServerCommittedRecordsAtomically(prepared);
            if (saved) {
                StoreIdentity.save(appContext, checkedStore);
                refreshStatus(checkedStore);
            }
            return saved;
        } catch (Exception error) {
            dataHubStatus.setStatus(SYNC_FAILED, "บันทึกข้อมูลที่ยืนยันจากเซิร์ฟเวอร์ไม่สำเร็จ");
            return false;
        }
    }

    /**
     * จำนวนรายการในเครื่องที่ยังไม่ได้ซิงก์สำเร็จ
     */
    public int getPendingSyncCount() {
        return localStore.getPendingCount();
    }

    /**
     * Number of records for one store that still need a successful cloud sync.
     */
    public int getPendingSyncCount(String storeId) {
        if (storeId == null || storeId.trim().isEmpty()) {
            return 0;
        }
        return localStore.getPendingCount(storeId.trim());
    }

    /**
     * สำรองรายการ pending หรือ failed ของร้านที่ระบุ
     */
    public void syncPendingRecords(
            String storeId,
            OperationCallback callback
    ) {
        final String checkedStoreId =
                storeId == null ? "" : storeId.trim();

        if (checkedStoreId.isEmpty()) {
            completeOperation(
                    callback,
                    0,
                    0,
                    "ไม่พบรหัสร้านค้า"
            );
            return;
        }

        // An offline sale changes several related records (bill, stock, receipt,
        // ledger and possibly customer). Never upload those records one-by-one:
        // the dedicated idempotent server transaction must reconcile the sale first.
        if (hasPendingOfflineSales(checkedStoreId)) {
            completeOperation(callback, 0, 1,
                    "มีรายการขายออฟไลน์รอยืนยันกับเซิร์ฟเวอร์ จึงพักการซิงก์รายรายการเพื่อป้องกันข้อมูลขัดแย้ง");
            return;
        }

        final List<JSONObject> records;

        try {
            records = localStore.getRecordsNeedingBackup(
                    checkedStoreId,
                    500
            );
        } catch (Exception error) {
            completeOperation(
                    callback,
                    0,
                    1,
                    "อ่านรายการที่รอซิงก์ไม่สำเร็จ"
            );
            return;
        }

        if (records == null || records.isEmpty()) {
            refreshStatus(checkedStoreId);

            completeOperation(
                    callback,
                    0,
                    0,
                    "ไม่มีรายการที่รอซิงก์"
            );
            return;
        }

        processSyncRecords(
                checkedStoreId,
                records,
                0,
                0,
                0,
                callback
        );
    }

    private void processSyncRecords(
            String storeId,
            List<JSONObject> records,
            int index,
            int succeeded,
            int failed,
            OperationCallback callback
    ) {
        if (index >= records.size()) {
            refreshStatus(storeId);

            String message = failed == 0
                    ? "ซิงก์ข้อมูลเสร็จแล้ว"
                    : "ซิงก์ข้อมูลเสร็จบางส่วน โปรดลองใหม่ภายหลัง";

            completeOperation(
                    callback,
                    succeeded,
                    failed,
                    message
            );
            return;
        }

        JSONObject record = records.get(index);

        if (record == null
                || !storeId.equals(
                        record.optString("storeId", "")
                )) {
            processSyncRecords(
                    storeId,
                    records,
                    index + 1,
                    succeeded,
                    failed + 1,
                    callback
            );
            return;
        }

        String recordId =
                record.optString("recordId", "").trim();

        if (recordId.isEmpty()) {
            processSyncRecords(
                    storeId,
                    records,
                    index + 1,
                    succeeded,
                    failed + 1,
                    callback
            );
            return;
        }

        final long uploadedRevision = record.optLong("revision", 1L);

        JSONObject uploadRecord;
        try {
            uploadRecord = new JSONObject(record.toString());
            uploadRecord.put("deviceId", StoreIdentity.getOrCreateDeviceId(appContext));
        } catch (Exception error) {
            processSyncRecords(storeId, records, index + 1, succeeded, failed + 1, callback);
            return;
        }

        cloudSyncService.upload(
                uploadRecord,
                new CloudSyncService.SyncCallback() {
                    @Override
                    public void onSuccess() {
                        boolean marked = localStore.markSyncStatusIfRevision(
                                storeId,
                                recordId,
                                uploadedRevision,
                                SYNC_SYNCED
                        );

                        processSyncRecords(
                                storeId,
                                records,
                                index + 1,
                                succeeded + (marked ? 1 : 0),
                                failed + (marked ? 0 : 1),
                                callback
                        );
                    }

                    @Override
                    public void onFailure(String message) {
                        // เก็บรายการที่สำรองไม่สำเร็จไว้ให้ลองใหม่ภายหลัง
                        localStore.markSyncStatusIfRevision(
                                storeId,
                                recordId,
                                uploadedRevision,
                                SYNC_FAILED
                        );

                        processSyncRecords(
                                storeId,
                                records,
                                index + 1,
                                succeeded,
                                failed + 1,
                                callback
                        );
                    }
                }
        );
    }

    /**
     * โหลดจาก Cloudเฉพาะรายการที่ยังไม่มีในเครื่อง
     * ไม่เขียนทับข้อมูลเดิมที่มีอยู่ในเครื่อง
     */
    /** Re-check policy, sync state and revision before deleting remote then local. */
    public void deleteExpiredRecord(String storeId, String recordId, String dataType,
            long cutoff, RetentionDeleteCallback callback) {
        String checkedStore = storeId == null ? "" : storeId.trim();
        String checkedId = recordId == null ? "" : recordId.trim();
        if (checkedStore.isEmpty() || checkedId.isEmpty() || cutoff <= 0L) {
            completeRetentionDelete(callback, false, "ข้อมูลสำหรับตรวจอายุไม่ถูกต้อง");
            return;
        }
        final JSONObject record;
        try { record = localStore.getRecord(checkedStore, checkedId); }
        catch (Exception error) {
            completeRetentionDelete(callback, false, "อ่านข้อมูลก่อนลบไม่สำเร็จ");
            return;
        }
        if (record == null || !dataType.equals(record.optString("dataType"))
                || !SYNC_SYNCED.equals(record.optString("syncStatus"))) {
            completeRetentionDelete(callback, false, "ข้อมูลยังไม่ซิงก์สำเร็จหรือชนิดไม่ตรง");
            return;
        }
        JSONObject payload = record.optJSONObject("payload");
        if (payload == null || !isRetentionEligible(checkedStore, record, payload, cutoff)) {
            completeRetentionDelete(callback, false, "ข้อมูลยังไม่เข้าเงื่อนไขเก็บรักษา");
            return;
        }
        long revision = record.optLong("revision", 0L);
        if (revision < 1L) {
            completeRetentionDelete(callback, false, "ไม่พบ revision ที่ตรวจสอบได้");
            return;
        }
        cloudSyncService.deleteRecord(checkedStore, checkedId, dataType, revision,
                new CloudSyncService.DeleteCallback() {
                    @Override public void onSuccess() {
                        boolean deleted = localStore.deleteRecordIfSyncedAndRevision(checkedStore, checkedId, revision);
                        refreshStatus(checkedStore);
                        completeRetentionDelete(callback, deleted,
                                deleted ? "ลบตามนโยบายเก็บข้อมูลแล้ว"
                                        : "คลาวด์ลบแล้ว แต่ข้อมูลในเครื่องเปลี่ยนระหว่างดำเนินการ จึงเก็บข้อมูลในเครื่องไว้");
                    }
                    @Override public void onFailure(String message) {
                        completeRetentionDelete(callback, false, message == null ? "ลบข้อมูลสำรองไม่สำเร็จ" : message);
                    }
                });
    }

    /**
     * Returns the number of extra records sharing a non-empty barcode across
     * product and barcode_item records. This is an audit only: no record is
     * deleted or modified. Call after cloud sync/reconnect.
     */
    public int getDuplicateBarcodeCount(String storeId) {
        java.util.Map<String, Integer> productCounts = new java.util.HashMap<>();
        java.util.Map<String, Integer> libraryCounts = new java.util.HashMap<>();
        int duplicates = 0;
        try {
            for (JSONObject record : localStore.getRecordsByType(storeId, "product")) {
                JSONObject payload = record.optJSONObject("payload");
                if (payload == null) continue;
                String barcode = payload.optString("barcode", "").trim();
                if (!barcode.isEmpty()) productCounts.put(barcode, productCounts.getOrDefault(barcode, 0) + 1);
            }
            for (JSONObject record : localStore.getRecordsByType(storeId, "barcode_item")) {
                JSONObject payload = record.optJSONObject("payload");
                if (payload == null) continue;
                String barcode = payload.optString("barcode", "").trim();
                if (!barcode.isEmpty()) libraryCounts.put(barcode, libraryCounts.getOrDefault(barcode, 0) + 1);
            }
            // One product record plus one barcode-library record for the same
            // barcode is an intentional cross-reference, not a duplicate.
            // Duplicates are multiple product records or multiple library records
            // sharing the same barcode value.
            for (Integer count : productCounts.values()) duplicates += Math.max(0, count - 1);
            for (Integer count : libraryCounts.values()) duplicates += Math.max(0, count - 1);
            return duplicates;
        } catch (Exception ignored) {
            // Audit failure must not mutate or delete any source records.
            return Math.max(1, duplicates);
        }
    }

    private boolean isRetentionEligible(String storeId, JSONObject record, JSONObject payload, long cutoff) {
        String type = record.optString("dataType", "");
        if ("sale_bill".equals(type)) {
            if (!"จ่ายแล้ว".equals(payload.optString("paymentStatus"))
                    || payload.optDouble("remainingBalance", 0d) > 0.009d) return false;
            long paidAt = payload.optLong("paidAt", 0L);
            if (paidAt <= 0L && "cash".equals(payload.optString("paymentType"))) paidAt = payload.optLong("createdAt", 0L);
            return paidAt > 0L && paidAt <= cutoff
                    && hasSyncedSaleSnapshot(storeId, record.optString("recordId"), payload);
        }
        if ("sale_history".equals(type)) {
            long createdAt = payload.optLong("createdAt", 0L);
            return createdAt > 0L && createdAt <= cutoff;
        }
        if ("price_history".equals(type)) {
            long changedAt = payload.optLong("changedAt", 0L);
            return changedAt > 0L && changedAt <= cutoff;
        }
        return false;
    }

    private boolean hasSyncedSaleSnapshot(String storeId, String billId, JSONObject billPayload) {
        try {
            for (JSONObject history : localStore.getRecordsByType(storeId, "sale_history")) {
                JSONObject hp = history.optJSONObject("payload");
                if (hp == null || !billId.equals(hp.optString("billId", ""))) continue;
                JSONObject snapshot = hp.optJSONObject("saleSnapshot");
                return snapshot != null && snapshot.toString().equals(billPayload.toString())
                        && SYNC_SYNCED.equals(history.optString("syncStatus"));
            }
        } catch (Exception ignored) { return false; }
        return false;
    }

    private void completeRetentionDelete(RetentionDeleteCallback callback, boolean deleted, String message) {
        if (callback != null) callback.onComplete(deleted, message);
    }

    private void refreshStatus(String storeId) {
        int pending = localStore.getPendingCount(storeId);

        if (pending == 0) {
            dataHubStatus.setStatus(
                    SYNC_SYNCED,
                    "ไม่มีรายการค้างสำรอง"
            );
        } else {
            dataHubStatus.setStatus(
                    SYNC_PENDING,
                    "มี " + pending + " รายการรอซิงก์"
            );
        }
    }

    private void completeOperation(
            OperationCallback callback,
            int succeeded,
            int failed,
            String message
    ) {
        if (callback != null) {
            callback.onComplete(
                    succeeded,
                    failed,
                    message
            );
        }
    }

    public void commitCreditPayment(String storeId, List<JSONObject> selectedBills,
            double receivedAmount, OperationCallback callback) {
        String checkedStore = storeId == null ? "" : storeId.trim();
        if (checkedStore.isEmpty()) {
            completeOperation(callback, 0, 1, "ไม่พบรหัสร้านค้า");
            return;
        }
        // Validate public API inputs before iterating; a null/empty selection must
        // fail closed with a callback instead of throwing on the caller's thread.
        if (selectedBills == null || selectedBills.isEmpty() || selectedBills.size() > 100) {
            completeOperation(callback, 0, 1, "กรุณาเลือกบิลที่ต้องการชำระ 1–100 บิล");
            return;
        }
        try {
            for (JSONObject selected : selectedBills) {
                if (selected == null) {
                    completeOperation(callback, 0, 1, "พบบิลที่ไม่มีข้อมูล");
                    return;
                }
                JSONObject current = localStore.getRecord(checkedStore, selected.optString("recordId", ""));
                if (current == null || current.optLong("revision", -1L) != selected.optLong("revision", -2L)) {
                    completeOperation(callback, 0, 1, "ข้อมูลบิลในเครื่องเปลี่ยนไป กรุณาค้นหาบิลใหม่ก่อนรับชำระ");
                    return;
                }
            }
        } catch (Exception error) {
            completeOperation(callback, 0, 1, "ตรวจสอบข้อมูลบิลในเครื่องไม่สำเร็จ");
            return;
        }
        String deviceId = StoreIdentity.getOrCreateDeviceId(appContext);
        cloudSyncService.commitCreditPayment(checkedStore, selectedBills, receivedAmount, deviceId,
                new CloudSyncService.CreditPaymentCallback() {
                    @Override public void onSuccess(List<JSONObject> committedRecords) {
                        try {
                            boolean saved = localStore.saveRecordsAtomically(committedRecords);
                            if (!saved) {
                                completeOperation(callback, 0, 1,
                                        "เซิร์ฟเวอร์รับชำระแล้ว แต่ข้อมูลในเครื่องมีการเปลี่ยนแปลง กรุณาเข้าสู่ระบบใหม่เพื่อโหลดจาก Cloudและตรวจสอบ ห้ามรับเงินซ้ำ");
                                return;
                            }
                            refreshStatus(checkedStore);
                            completeOperation(callback, committedRecords.size(), 0,
                                    "บันทึกการชำระหนี้บนเซิร์ฟเวอร์แบบธุรกรรมเดียวแล้ว");
                        } catch (Exception error) {
                            completeOperation(callback, 0, 1,
                                    "เซิร์ฟเวอร์รับชำระแล้ว แต่ซ่อมข้อมูลในเครื่องไม่สำเร็จ กรุณาโหลดจาก Cloudจาก Cloud และห้ามรับเงินซ้ำ");
                        }
                    }
                    @Override public void onFailure(String message) {
                        completeOperation(callback, 0, 1, message);
                    }
                });
    }

    public void reserveBarcode(String storeId, String barcode, OperationCallback callback) {
        String deviceId = StoreIdentity.getOrCreateDeviceId(appContext);
        cloudSyncService.reserveBarcode(storeId, barcode, deviceId, new CloudSyncService.SyncCallback() {
            @Override public void onSuccess() {
                if (callback != null) callback.onComplete(1, 0, "จองบาร์โค้ดสำเร็จ");
            }
            @Override public void onFailure(String message) {
                if (callback != null) callback.onComplete(0, 1, message);
            }
        });
    }

    public DataHubStatus getStatus() {
        return dataHubStatus;
    }
                            }
