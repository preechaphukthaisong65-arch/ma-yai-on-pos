package com.example.newapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class BarcodeRulesTest {
    @Test public void upcACheckDigitMatchesKnownExamples() {
        assertEquals(0, BarcodeScreen.upcCheckDigit("03600029145"));
        assertEquals(2, BarcodeScreen.upcCheckDigit("01234567890"));
    }

    @Test public void generatedInternalBarcodeHasValidUpcACheckDigit() {
        String body = "29000000042";
        String code = body + BarcodeScreen.upcCheckDigit(body);
        assertEquals(12, code.length());
        assertTrue(code.matches("[0-9]{12}"));
        assertEquals(0, BarcodeScreen.upcCheckDigit(code.substring(0, 11)));
        int sum = 0;
        for (int i = 0; i < code.length(); i++) {
            sum += (code.charAt(i) - '0') * ((i % 2 == 0) ? 3 : 1);
        }
        assertEquals(0, sum % 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongLengthForCheckDigit() {
        BarcodeScreen.upcCheckDigit("1234567890");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonNumericCheckDigitInput() {
        BarcodeScreen.upcCheckDigit("1234567890A");
    }
}
