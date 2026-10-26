package com.example.newapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class PosDateFormatTest {
    @Test public void normalizesBuddhistYearToGregorian() {
        assertEquals("2026-10-26", PosDateFormat.normalizeIsoDate("2569-10-26"));
    }

    @Test public void leavesGregorianYearUnchanged() {
        assertEquals("2026-10-26", PosDateFormat.normalizeIsoDate("2026-10-26"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedYear() {
        PosDateFormat.normalizeIsoDate("1800-01-01");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidDateShape() {
        PosDateFormat.normalizeIsoDate("26/10/2569");
    }
}
