package com.example.newapp;

/** Pure UPC-A validation rules, independent of Android runtime APIs. */
final class BarcodeRules {
    private BarcodeRules() {}

    static int upcCheckDigit(String elevenDigits) {
        if (elevenDigits == null || elevenDigits.length() != 11
                || !elevenDigits.matches("[0-9]{11}")) {
            throw new IllegalArgumentException("เลขบาร์โค้ดภายในไม่ถูกต้อง");
        }
        int sum = 0;
        for (int i = 0; i < elevenDigits.length(); i++) {
            sum += (elevenDigits.charAt(i) - '0') * ((i % 2 == 0) ? 3 : 1);
        }
        return (10 - (sum % 10)) % 10;
    }

    static boolean isValidUpcA(String code) {
        if (code == null || code.length() != 12 || !code.matches("[0-9]{12}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < code.length(); i++) {
            sum += (code.charAt(i) - '0') * ((i % 2 == 0) ? 3 : 1);
        }
        return sum % 10 == 0;
    }
}
