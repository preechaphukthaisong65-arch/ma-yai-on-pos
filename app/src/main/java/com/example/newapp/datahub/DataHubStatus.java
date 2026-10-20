package com.example.newapp.datahub;

/**
 * สถานะของระบบ DataHub
 */
public final class DataHubStatus {

    public enum ConnectionState {
        UNKNOWN,
        ONLINE,
        OFFLINE
    }

    public enum BackupState {
        IDLE,
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED
    }

    private ConnectionState connectionState =
            ConnectionState.UNKNOWN;

    private BackupState backupState =
            BackupState.IDLE;

    private int pendingCount = 0;

    private String lastMessage = "";

    private long lastUpdatedAt = 0L;

    public DataHubStatus() {
    }

    public synchronized ConnectionState getConnectionState() {
        return connectionState;
    }

    public synchronized void setConnectionState(
            ConnectionState state
    ) {
        if (state == null) {
            state = ConnectionState.UNKNOWN;
        }

        connectionState = state;
        touch();
    }

    public synchronized BackupState getBackupState() {
        return backupState;
    }

    public synchronized void setBackupState(
            BackupState state
    ) {
        if (state == null) {
            state = BackupState.IDLE;
        }

        backupState = state;
        touch();
    }

    public synchronized int getPendingCount() {
        return pendingCount;
    }

    public synchronized void setPendingCount(int count) {
        pendingCount = Math.max(0, count);
        touch();

        if (pendingCount > 0
                && backupState == BackupState.IDLE) {
            backupState = BackupState.PENDING;
        }
    }

    public synchronized String getLastMessage() {
        return lastMessage;
    }

    public synchronized void setLastMessage(
            String message
    ) {
        lastMessage = message == null ? "" : message;
        touch();
    }

    public synchronized long getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public synchronized boolean hasPendingBackup() {
        return pendingCount > 0;
    }

    public synchronized boolean isOnline() {
        return connectionState == ConnectionState.ONLINE;
    }

    private void touch() {
        lastUpdatedAt = System.currentTimeMillis();
    }
}
