package com.example.newapp;

/**
 * Retention policy for records that can affect reports or auditability.
 * Financial ledgers, payment history, stock/price history, and audit log are append-only
 * and must not be deleted by routine retention. Only closed bill detail may be purged.
 */
final class PosHistoryPolicy {
    static final long CLOSED_BILL_DETAIL_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L;

    private PosHistoryPolicy() {}

    static long closedBillDetailCutoff(long now) {
        return Math.subtractExact(now, CLOSED_BILL_DETAIL_RETENTION_MILLIS);
    }

    static boolean mayPurgeHistoryTable(String table) {
        // Explicit deny-list: report/audit history is permanent unless a separate,
        // reviewed archival/export migration is introduced.
        return false;
    }
}
