package com.example.newapp.datahub;

import org.json.JSONObject;

public class RestoreManager {

    /**
     * Validate that a backup record can be restored to the requested store.
     *
     * This method only validates data.
     * It does not write to the database or modify stock.
     */
    public boolean canRestore(JSONObject record, String expectedStoreId) {
        if (record == null || expectedStoreId == null) {
            return false;
        }

        String checkedStoreId = expectedStoreId.trim();
        if (checkedStoreId.isEmpty()) {
            return false;
        }

        String recordId = record.optString("recordId", "").trim();
        String storeId = record.optString("storeId", "").trim();
        String dataType = record.optString("dataType", "").trim();

        if (recordId.isEmpty()
                || storeId.isEmpty()
                || dataType.isEmpty()) {
            return false;
        }

        if (!checkedStoreId.equals(storeId)) {
            return false;
        }

        int schemaVersion = record.optInt("schemaVersion", 0);
        if (schemaVersion < 1) {
            return false;
        }

        Object payload = record.opt("payload");
        if (!(payload instanceof JSONObject)) {
            return false;
        }

        return true;
    }

    /**
     * Prepare a validated backup record for restore.
     *
     * The returned object is a copy. The original record is not modified.
     */
    public JSONObject prepareRestore(
            JSONObject record,
            String expectedStoreId
    ) {
        if (!canRestore(record, expectedStoreId)) {
            return null;
        }

        try {
            JSONObject restored = new JSONObject(record.toString());

            restored.put("storeId", expectedStoreId.trim());
            restored.put("syncStatus", DataHub.SYNC_SYNCED);

            return restored;

        } catch (Exception error) {
            return null;
        }
    }
}
