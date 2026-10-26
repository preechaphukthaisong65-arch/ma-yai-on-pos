package com.example.newapp;

/**
 * Canonical financial rules for POS sales, credit collection, and reversals.
 * All monetary values are integer satang; timestamps use AppClock milliseconds.
 */
final class PosBusinessRules {
    static final long CREDIT_FEE_WINDOW_MILLIS = 24L * 60L * 60L * 1000L;
    static final long BILL_REVERSAL_WINDOW_MILLIS = CREDIT_FEE_WINDOW_MILLIS;

    private PosBusinessRules() {}

    /** 5% credit fee, rounded upward to the next satang. */
    /** EAN-13 check digit for a 12-digit payload. */
    static String ean13CheckDigit(String payload) {
        if (payload == null || !payload.matches("[0-9]{12}"))
            throw new IllegalArgumentException("ข้อมูลบาร์โค้ดไม่ถูกต้อง");
        int sum = 0;
        for (int i = 0; i < payload.length(); i++)
            sum += (payload.charAt(i) - '0') * ((i % 2) == 0 ? 1 : 3);
        return String.valueOf((10 - (sum % 10)) % 10);
    }

    static long creditFee(long subtotalSatang) {
        if (subtotalSatang < 0L) throw new IllegalArgumentException("ยอดขายเชื่อต้องไม่ติดลบ");
        long whole = subtotalSatang / 20L;
        return Math.addExact(whole, subtotalSatang % 20L == 0L ? 0L : 1L);
    }

    /** Amount due when collecting an unpaid bill; fee is waived only inside the original 24-hour window. */
    static long paymentDue(String saleType, long subtotalSatang,
                           long totalSatang, long billCreatedAt, long now) {
        if (subtotalSatang < 0L || totalSatang < 0L)
            throw new IllegalArgumentException("ยอดบิลไม่ถูกต้อง");
        if ("CREDIT".equals(saleType) && withinCreditFeeWindow(billCreatedAt, now))
            return subtotalSatang;
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
