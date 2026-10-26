package com.example.newapp;

/**
 * Canonical financial rules for POS sales, credit collection, and reversals.
 * All monetary values are integer satang; timestamps use AppClock milliseconds.
 */
final class PosBusinessRules {
    static final long CREDIT_FEE_WINDOW_MILLIS = 24L * 60L * 60L * 1000L;
    static final long BILL_REVERSAL_WINDOW_MILLIS = CREDIT_FEE_WINDOW_MILLIS;

    private PosBusinessRules() {}

    /** Credit unit price: cash price plus 5%, rounded to the nearest whole baht (half-up). */
    static long creditUnitPrice(long cashUnitPriceSatang) {
        if (cashUnitPriceSatang < 0L) throw new IllegalArgumentException("ราคาสินค้าต้องไม่ติดลบ");
        long numerator = Math.addExact(Math.multiplyExact(cashUnitPriceSatang, 105L), 5_000L);
        long wholeBaht = numerator / 10_000L;
        return Math.multiplyExact(wholeBaht, 100L);
    }

    /** Legacy fee helper retained only for historical bills created by older app versions. */
    static long creditFee(long subtotalSatang) {
        if (subtotalSatang < 0L) throw new IllegalArgumentException("ยอดขายเชื่อต้องไม่ติดลบ");
        long whole = subtotalSatang / 20L;
        return Math.addExact(whole, subtotalSatang % 20L == 0L ? 0L : 1L);
    }

    /** Amount due from an unpaid bill. Fee waivers must be explicitly approved by the owner; device time is not authorization. */
    static long paymentDue(String saleType, long subtotalSatang,
                           long totalSatang, long billCreatedAt, long now) {
        if (subtotalSatang < 0L || totalSatang < 0L)
            throw new IllegalArgumentException("ยอดบิลไม่ถูกต้อง");
        return totalSatang;
    }

    static boolean withinCreditFeeWindow(long billCreatedAt, long now) {
        return now >= billCreatedAt && now - billCreatedAt <= CREDIT_FEE_WINDOW_MILLIS;
    }

    static boolean withinBillReversalWindow(long billCreatedAt, long now) {
        return now >= billCreatedAt && now - billCreatedAt <= BILL_REVERSAL_WINDOW_MILLIS;
    }

    static boolean isReversibleStatus(String status) {
        return "PAID".equals(status) || "UNPAID".equals(status);
    }

    /** Net ledger reversal: a paid credit bill's already-collected fee is not refunded. */
    static long reversalSubtotal(long subtotalSatang) {
        return Math.negateExact(subtotalSatang);
    }

    static long reversalFee(String saleType, String status, long feeSatang) {
        if (!"CREDIT".equals(saleType) || "PAID".equals(status)) return 0L;
        return Math.negateExact(feeSatang);
    }

    static long reversalTotal(long reversalSubtotalSatang, long reversalFeeSatang) {
        return Math.addExact(reversalSubtotalSatang, reversalFeeSatang);
    }
}
