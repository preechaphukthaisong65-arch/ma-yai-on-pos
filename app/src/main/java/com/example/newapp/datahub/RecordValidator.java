package com.example.newapp.datahub;

import org.json.JSONObject;

public final class RecordValidator {

    /**
     * Validate a local record before synchronization.
     */
    public boolean isValidRecord(JSONObject record) {
        if (record == null) {
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

        if (record.optInt("schemaVersion", 0) < 1) {
            return false;
        }

        Object payload = record.opt("payload");
        return payload instanceof JSONObject;
    }

    /**
     * Create a validated copy for cloud synchronization.
     * The original local record is not modified.
     */
    public JSONObject prepareSyncRecord(JSONObject record) {
        if (!isValidRecord(record)) {
            return null;
        }

        try {
            JSONObject copy = new JSONObject(record.toString());

            copy.put(
                    "updatedAt",
                    Math.max(
                            1L,
                            copy.optLong(
                                    "updatedAt",
                                    System.currentTimeMillis()
                            )
                    )
            );

            copy.put(
                    "revision",
                    Math.max(1L, copy.optLong("revision", 1L))
            );

            return copy;

        } catch (Exception error) {
            return null;
        }
    }
}
