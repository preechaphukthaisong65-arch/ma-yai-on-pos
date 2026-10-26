package com.example.newapp;

import android.content.Context;

/**
 * Retention is deliberately non-destructive until an archival/export path is implemented.
 * Bill headers, bill items, payments, ledgers, operational history, and audit entries are
 * report inputs; deleting any of them can make customer history or reports incomplete.
 */
final class RetentionManager {
    private RetentionManager() {}

    static void run(Context context) {
        // Intentionally retain transaction data. Do not purge rows by age alone.
        // A future archival implementation must export and verify records before deletion.
        PosDatabase.get(context).getReadableDatabase();
    }
}
