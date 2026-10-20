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
    private final CloudBackupService cloudBackupService;
    private final DataHubStatus dataHubStatus;

    private DataHub(Context context) {
        localStore = new LocalStore(context.getApplicationContext());
        cloudBackupService = new CloudBackupService();
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

    public interface RestoreCallback {
        void onComplete(
                int restored,
                int skipped,
                int failed,
                String message
        );
    }

    /**
     * Save a record locally.
     * This method does not update stock or any other business data.
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

        JSONObject record = new JSONObject();

        try {
            record.put("storeId", storeId.trim());
            record.put("recordId", recordId.trim());
            record.put("dataType", dataType.trim());
            record.put("schemaVersion", Math.max(1, schemaVersion));
            record.put("updatedAt", System.currentTimeMillis());
            record.put("revision", Math.max(1L, revision));
            record.put("payload", payload);
            record.put("syncStatus", SYNC_PENDING);

            boolean saved = localStore.saveRecord(record);

            refreshStatus(storeId);

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
     * Read a local record.
     */
    public JSONObject getRecord(String storeId, String recordId) {
        if (storeId == null || recordId == null) {
            return null;
        }

        return localStore.getRecord(storeId.trim(), recordId.trim());
    }

    /**
     * Return the number of local records that are not synced.
     */
    public int getPendingBackupCount() {
        return localStore.getPendingCount();
    }

    /**
     * Upload pending or failed records for the selected store.
     * Call this method explicitly when a backup is required.
     */
    public void backupPendingRecords(
            String storeId,
            OperationCallback callback
    ) {
        final String checkedStoreId =
                storeId == null ? "" : storeId.trim();

        if (checkedStoreId.isEmpty()) {
            completeOperation(
                    callback, 0, 0, "ไม่พบรหัสร้านค้า"
            );
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
                    callback, 0, 0, "อ่านรายการที่รอสำรองไม่สำเร็จ"
            );
            return;
        }

        if (records == null || records.isEmpty()) {
            refreshStatus(checkedStoreId);
            completeOperation(
                    callback, 0, 0, "ไม่มีรายการที่รอสำรอง"
            );
            return;
        }

        processBackupRecords(
                checkedStoreId,
                records,
                0,
                0,
                0,
                callback
        );
    }

    private void processBackupRecords(
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
                    ? "สำรองข้อมูลเสร็จแล้ว"
                    : "สำรองข้อมูลเสร็จบางส่วน โปรดลองใหม่ภายหลัง";

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
                || !storeId.equals(record.optString("storeId", ""))) {
            processBackupRecords(
                    storeId,
                    records,
                    index + 1,
                    succeeded,
                    failed + 1,
                    callback
            );
            return;
        }

        String recordId = record.optString("recordId", "").trim();

        if (recordId.isEmpty()) {
            processBackupRecords(
                    storeId,
                    records,
                    index + 1,
                    succeeded,
                    failed + 1,
                    callback
            );
            return;
        }

        cloudBackupService.upload(
                record,
                new CloudBackupService.BackupCallback() {
                    @Override
                    public void onSuccess() {
                        boolean marked = localStore.markSyncStatus(
                                storeId,
                                recordId,
                                SYNC_SYNCED
                        );

                        processBackupRecords(
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
                        localStore.markSyncStatus(
                                storeId,
                                recordId,
                                SYNC_FAILED
                        );

                        processBackupRecords(
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
     * Restore records that do not already exist locally.
     * Existing local records are never overwritten.
     */
    public void restoreMissingRecords(
            String storeId,
            RestoreCallback callback
    ) {
        final String checkedStoreId =
                storeId == null ? "" : storeId.trim();

        if (checkedStoreId.isEmpty()) {
            completeRestore(
                    callback, 0, 0, 1, "ไม่พบรหัสร้านค้า"
            );
            return;
        }

        cloudBackupService.fetchStoreRecords(
                checkedStoreId,
                new CloudBackupService.RecordsCallback() {
                    @Override
                    public void onSuccess(List<JSONObject> records) {
                        int restored = 0;
                        int skipped = 0;
                        int failed = 0;

                        if (records == null) {
                            completeRestore(
                                    callback,
                                    0,
                                    0,
                                    1,
                                    "ไม่ได้รับรายการข้อมูลสำรอง"
                            );
                            return;
                        }

                        RestoreManager restoreManager =
                                new RestoreManager();

                        for (JSONObject record : records) {
                            if (record == null
                                    || !checkedStoreId.equals(
                                            record.optString("storeId", "")
                                    )) {
                                failed++;
                                continue;
                            }

                            if (!restoreManager.canRestore(
                                    record,
                                    checkedStoreId
                            )) {
                                failed++;
                                continue;
                            }

                            try {
                                boolean inserted =
                                        localStore.insertRestoredRecordIfAbsent(
                                                record
                                        );

                                if (inserted) {
                                    restored++;
                                } else {
                                    /*
                                     * The record may already exist.
                                     * Do not overwrite existing local data.
                                     */
                                    JSONObject existing =
                                            localStore.getRecord(
                                                    checkedStoreId,
                                                    record.optString("recordId", "")
                                            );

                                    if (existing != null) {
                                        skipped++;
                                    } else {
                                        failed++;
                                    }
                                }

                            } catch (Exception error) {
                                failed++;
                            }
                        }

                        refreshStatus(checkedStoreId);

                        String message = failed == 0
                                ? "กู้คืนข้อมูลเสร็จแล้ว"
                                : "กู้คืนข้อมูลเสร็จบางส่วน";

                        completeRestore(
                                callback,
                                restored,
                                skipped,
                                failed,
                                message
                        );
                    }

                    @Override
                    public void onFailure(String message) {
                        completeRestore(
                                callback,
                                0,
                                0,
                                1,
                                message == null
                                        ? "ดาวน์โหลดข้อมูลสำรองไม่สำเร็จ"
                                        : message
                        );
                    }
                }
        );
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
                    "มี " + pending + " รายการรอสำรอง"
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
            callback.onComplete(succeeded, failed, message);
        }
    }

    private void completeRestore(
            RestoreCallback callback,
            int restored,
            int skipped,
            int failed,
            String message
    ) {
        if (callback != null) {
            callback.onComplete(
                    restored,
                    skipped,
                    failed,
                    message
            );
        }
    }

    public DataHubStatus getStatus() {
        return dataHubStatus;
    }
}
