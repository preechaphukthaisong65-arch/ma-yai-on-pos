package com.example.newapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class PosBusinessRulesTest {
    private static final long DAY = 24L * 60L * 60L * 1000L;

    @Test public void creditFeeRoundsUpToWholeSatang() {
        assertEquals(0L, PosBusinessRules.creditFee(0L));
        assertEquals(1L, PosBusinessRules.creditFee(1L));
        assertEquals(5L, PosBusinessRules.creditFee(100L));
        assertEquals(6L, PosBusinessRules.creditFee(101L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void creditFeeRejectsNegativeSubtotal() {
        PosBusinessRules.creditFee(-1L);
    }

    @Test public void creditFeeIsNeverAutomaticallyWaivedByDeviceClock() {
        long created = 1_000_000L;
        assertEquals(10_500L, PosBusinessRules.paymentDue("CREDIT", 10_000L, 10_500L, created, created));
        assertEquals(10_500L, PosBusinessRules.paymentDue("CREDIT", 10_000L, 10_500L, created, created + DAY));
        assertEquals(10_500L, PosBusinessRules.paymentDue("CREDIT", 10_000L, 10_500L, created, created + DAY + 1L));
        assertEquals(10_500L, PosBusinessRules.paymentDue("CREDIT", 10_000L, 10_500L, created, created - 1L));
    }

    @Test public void cashPaymentNeverWaivesFeeOrChangesTotal() {
        assertEquals(10_500L, PosBusinessRules.paymentDue("CASH", 10_000L, 10_500L, 1_000L, 1_001L));
    }

    @Test public void reversalKeepsCollectedCreditFeeAndReversesUnpaidFee() {
        assertEquals(0L, PosBusinessRules.reversalFee("CREDIT", "PAID", 500L));
        assertEquals(-500L, PosBusinessRules.reversalFee("CREDIT", "UNPAID", 500L));
        assertEquals(0L, PosBusinessRules.reversalFee("CASH", "PAID", 0L));
        assertEquals(-10_000L, PosBusinessRules.reversalSubtotal(10_000L));
        assertEquals(-10_500L, PosBusinessRules.reversalTotal(-10_000L, -500L));
    }

    @Test public void reversalWindowAndStatusesAreCentralized() {
        long created = 1_000_000L;
        assertTrue(PosBusinessRules.withinBillReversalWindow(created, created + DAY));
        assertFalse(PosBusinessRules.withinBillReversalWindow(created, created + DAY + 1L));
        assertFalse(PosBusinessRules.withinBillReversalWindow(created, created - 1L));
        assertTrue(PosBusinessRules.isReversibleStatus("PAID"));
        assertTrue(PosBusinessRules.isReversibleStatus("UNPAID"));
        assertFalse(PosBusinessRules.isReversibleStatus("CANCELLED"));
        assertFalse(PosBusinessRules.isReversibleStatus("RETURNED"));
    }
    @Test public void retentionPolicyNeverPurgesReportOrTransactionSources() {
        assertFalse(PosHistoryPolicy.mayPurgeHistoryTable("stock_history"));
        assertFalse(PosHistoryPolicy.mayPurgeHistoryTable("price_history"));
        assertFalse(PosHistoryPolicy.mayPurgeHistoryTable("payment_history"));
        assertFalse(PosHistoryPolicy.mayPurgeHistoryTable("sales_ledger"));
        assertFalse(PosHistoryPolicy.mayPurgeHistoryTable("sales_ledger_items"));
        assertFalse(PosHistoryPolicy.mayPurgeHistoryTable("audit_log"));
        assertFalse(PosHistoryPolicy.mayPurgeTransactionDetails());
    }

}
