package com.example.newapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class BarcodeRulesTest {
    @Test public void upcACheckDigitMatchesKnownExamples() {
        assertEquals(2, BarcodeRules.upcCheckDigit("03600029145"));
        assertEquals(5, BarcodeRules.upcCheckDigit("01234567890"));
    }

    @Test public void generatedInternalBarcodeHasValidUpcACheckDigit() {
        String body = "29000000042";
        String code = body + BarcodeRules.upcCheckDigit(body);
        assertEquals(12, code.length());
        assertTrue(code.matches("[0-9]{12}"));
        assertEquals(code.charAt(11) - '0', BarcodeRules.upcCheckDigit(code.substring(0, 11)));
        assertTrue(BarcodeRules.isValidUpcA(code));
    }

    @Test public void rejectsInvalidFullUpcACodes() {
        assertFalse(BarcodeRules.isValidUpcA("123"));
        assertFalse(BarcodeRules.isValidUpcA("012345678901"));
        assertFalse(BarcodeRules.isValidUpcA(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongLengthForCheckDigit() {
        BarcodeRules.upcCheckDigit("1234567890");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonNumericCheckDigitInput() {
        BarcodeRules.upcCheckDigit("1234567890A");
    }
}
