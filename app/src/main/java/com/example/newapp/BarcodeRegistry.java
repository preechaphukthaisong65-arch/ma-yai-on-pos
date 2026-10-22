package com.example.newapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Locale;

/** Central rules for looking up and maintaining the barcode registry. */
final class BarcodeRegistry {
    static final class Entry {
        final String barcode;
        final String name;
        final long priceSatang;
        final String source;
        Entry(String barcode, String name, long priceSatang, String source) {
            this.barcode = barcode;
            this.name = name;
            this.priceSatang = priceSatang;
            this.source = source;
        }
    }

    private BarcodeRegistry() {}

    static Entry find(SQLiteDatabase db, String barcode) {
        Cursor c = db.query("barcode_registry",
                new String[]{"barcode", "name", "price_satang", "source"},
                "barcode=?", new String[]{barcode}, null, null, null, "1");
        try {
            if (!c.moveToFirst()) return null;
            return new Entry(c.getString(0), c.getString(1), c.getLong(2), c.getString(3));
        } finally {
            c.close();
        }
    }

    static void ensureConsistent(SQLiteDatabase db, String barcode, String name, long priceSatang) {
        Entry entry = find(db, barcode);
        if (entry != null && (!name.equals(entry.name) || priceSatang != entry.priceSatang)) {
            throw new IllegalStateException("บาร์โค้ดนี้มีข้อมูลในคลังบาร์โค้ดไม่ตรงกัน กรุณาสแกนตรวจสอบก่อน");
        }
    }

    /** Inserts only when absent; an existing barcode must carry the same identity and price. */
    static void registerIfMissing(SQLiteDatabase db, String barcode, String name,
                                  long priceSatang, String source, long createdAt) {
        ensureConsistent(db, barcode, name, priceSatang);
        if (find(db, barcode) != null) return;
        ContentValues values = new ContentValues();
        values.put("barcode", barcode);
        values.put("name", name);
        values.put("price_satang", priceSatang);
        values.put("source", source);
        values.put("created_at", createdAt);
        db.insertOrThrow("barcode_registry", null, values);
    }

    /** Allocates a unique 12-digit code and advances the sequence in the same transaction. */
    static String nextGenerated(SQLiteDatabase db) {
        Cursor seqCursor = db.query("app_meta", new String[]{"value"},
                "key='barcode_sequence'", null, null, null, null);
        long sequence = 100000000000L;
        try {
            if (seqCursor.moveToFirst()) sequence = Long.parseLong(seqCursor.getString(0));
        } finally {
            seqCursor.close();
        }

        while (sequence <= 999999999999L) {
            String candidate = String.format(Locale.US, "%012d", sequence);
            Cursor exists = db.rawQuery(
                    "SELECT 1 FROM barcode_registry WHERE barcode=? UNION ALL " +
                    "SELECT 1 FROM products WHERE barcode=? LIMIT 1",
                    new String[]{candidate, candidate});
            boolean used;
            try { used = exists.moveToFirst(); } finally { exists.close(); }
            if (!used) {
                ContentValues meta = new ContentValues();
                meta.put("key", "barcode_sequence");
                meta.put("value", String.valueOf(sequence + 1));
                db.insertWithOnConflict("app_meta", null, meta, SQLiteDatabase.CONFLICT_REPLACE);
                return candidate;
            }
            sequence++;
        }
        throw new IllegalStateException("ไม่สามารถหาเลขบาร์โค้ดว่างได้");
    }
}
