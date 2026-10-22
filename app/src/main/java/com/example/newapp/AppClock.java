package com.example.newapp;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Shared application clock. The phone's system clock and current device time zone
 * are the single source of time for POS records, reports, and scheduled tasks.
 */
public final class AppClock {
    private AppClock() {}

    /** Current timestamp from the device clock, in milliseconds since the epoch. */
    public static long now() {
        return System.currentTimeMillis();
    }

    /** Calendar configured with the phone's current locale/time zone and current time. */
    public static Calendar calendar() {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
        calendar.setTimeInMillis(now());
        return calendar;
    }

    /** Format a stored timestamp using the phone's current time zone and locale. */
    public static String format(long timestamp, String pattern) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.getDefault());
        formatter.setTimeZone(TimeZone.getDefault());
        return formatter.format(new Date(timestamp));
    }
}
