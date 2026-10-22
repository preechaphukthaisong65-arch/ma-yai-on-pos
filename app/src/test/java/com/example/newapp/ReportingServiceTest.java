package com.example.newapp;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ReportingServiceTest {
    private static final long DAY=86400000L;

    @Test public void creditFeeIsWaivedThroughExactTwentyFourHourBoundary() {
        assertEquals(10000L,ReportingService.payableNow("CREDIT",10000L,500L,10500L,1000L,1000L));
        assertEquals(10000L,ReportingService.payableNow("CREDIT",10000L,500L,10500L,1000L,1000L+DAY));
    }

    @Test public void feeIsChargedAfterTwentyFourHours() {
        assertEquals(10500L,ReportingService.payableNow("CREDIT",10000L,500L,10500L,1000L,1000L+DAY+1L));
    }

    @Test public void cashBillsDoNotUseCreditFeeWaiver() {
        assertEquals(10000L,ReportingService.payableNow("CASH",10000L,0L,10000L,1000L,1000L));
    }

    @Test public void futureDatedBillDoesNotUseCreditFeeWaiver() {
        assertEquals(10500L,ReportingService.payableNow("CREDIT",10000L,500L,10500L,2000L,1000L));
    }

    @Test(expected=IllegalStateException.class)
    public void rejectsInconsistentBillAmounts() {
        ReportingService.payableNow("CREDIT",10000L,500L,10499L,1000L,1000L);
    }
}