package com.example.newapp;

import android.database.sqlite.SQLiteDatabase;

/**
 * Shared transaction boundary for multi-table POS operations.
 * Every successful operation is committed as one unit; exceptions roll back.
 */
final class PosTransactions {
    interface Work<T> {
        T run(SQLiteDatabase database) throws Exception;
    }

    private PosTransactions() {}

    static <T> T execute(SQLiteDatabase database, Work<T> work) throws Exception {
        if (database == null) throw new IllegalArgumentException("ฐานข้อมูลไม่พร้อมใช้งาน");
        if (work == null) throw new IllegalArgumentException("ไม่พบขั้นตอนธุรกรรม");
        if (database.inTransaction()) {
            throw new IllegalStateException("ไม่อนุญาตให้เปิดธุรกรรม POS ซ้อนกัน");
        }

        database.beginTransaction();
        try {
            T result = work.run(database);
            database.setTransactionSuccessful();
            return result;
        } finally {
            database.endTransaction();
        }
    }
}
