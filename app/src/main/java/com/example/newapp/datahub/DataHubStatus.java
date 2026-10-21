package com.example.newapp.datahub;

public class DataHubStatus {

    private String status = DataHub.SYNC_PENDING;
    private String message = "ระบบซิงก์ข้อมูลพร้อมใช้งาน";
    private long updatedAt = System.currentTimeMillis();

    public synchronized void setStatus(String newStatus, String newMessage) {
        if (newStatus == null || newStatus.trim().isEmpty()) {
            status = DataHub.SYNC_FAILED;
        } else {
            status = newStatus.trim();
        }

        message = newMessage == null ? "" : newMessage.trim();
        updatedAt = System.currentTimeMillis();
    }

    public synchronized String getStatus() {
        return status;
    }

    public synchronized String getMessage() {
        return message;
    }

    public synchronized long getUpdatedAt() {
        return updatedAt;
    }

    public synchronized boolean hasPendingSync() {
        return DataHub.SYNC_PENDING.equals(status)
                || DataHub.SYNC_FAILED.equals(status);
    }

    public synchronized boolean isSynced() {
        return DataHub.SYNC_SYNCED.equals(status);
    }
}
