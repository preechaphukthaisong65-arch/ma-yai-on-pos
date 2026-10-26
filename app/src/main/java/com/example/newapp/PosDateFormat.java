package com.example.newapp;

import java.util.Locale;

/** Normalizes report date input to Gregorian ISO dates before parsing timestamps. */
public final class PosDateFormat {
    private PosDateFormat() {}

    /**
     * Accepts YYYY-MM-DD in either Gregorian (e.g. 2026) or Thai Buddhist (e.g. 2569)
     * years and returns a Gregorian ISO date. Rejects ambiguous/unsupported years.
     */
    public static String normalizeIsoDate(String input) {
        if (input == null) throw new IllegalArgumentException("กรุณากรอกวันที่");
        String value = input.trim();
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("ใช้รูปแบบ YYYY-MM-DD");
        int year = Integer.parseInt(value.substring(0, 4));
        if (year >= 2400 && year <= 2700) year -= 543;
        if (year < 1900 || year > 2200)
            throw new IllegalArgumentException("ปีต้องเป็น ค.ศ. 1900–2200 หรือ พ.ศ. 2443–2743");
        return String.format(Locale.US, "%04d%s", year, value.substring(4));
    }
}
