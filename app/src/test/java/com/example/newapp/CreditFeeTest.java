package com.example.newapp;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CreditFeeTest {
    @Test public void roundsUpToNextSatang() {
        assertEquals(0L,PosDatabase.creditFee(0L));
        assertEquals(1L,PosDatabase.creditFee(1L));
        assertEquals(5L,PosDatabase.creditFee(100L));
        assertEquals(6L,PosDatabase.creditFee(101L));
        assertEquals(1L,PosDatabase.creditFee(20L));
    }

    @Test public void handlesMaximumLongWithoutOverflow() {
        long expected=Long.MAX_VALUE/20L+(Long.MAX_VALUE%20L==0L?0L:1L);
        assertEquals(expected,PosDatabase.creditFee(Long.MAX_VALUE));
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectsNegativeSubtotal() {
        PosDatabase.creditFee(-1L);
    }
}