package com.example.newapp;

/**
 * Canonical retention policy.
 *
 * No transactional or report-source table may be purged by routine age-based cleanup.
 * This includes bills, bill_items, payments, payment_history, sales_ledger,
 * sales_ledger_items, stock_history, price_history, and audit_log.
 * Deletion may be enabled only after an archival/export workflow is implemented,
 * verified, and tested against historical reports.
 */
final class PosHistoryPolicy {
    private PosHistoryPolicy() {}

    static boolean mayPurgeHistoryTable(String table) {
        return false;
    }

    static boolean mayPurgeTransactionDetails() {
        return false;
    }
}
