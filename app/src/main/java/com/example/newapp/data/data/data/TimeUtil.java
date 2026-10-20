package com.example.newapp.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * เวลาเก็บเป็นมิลลิวินาที UTC (epoch) เพื่อเรียงลำดับข้ามเครื่องได้ถูกต้อง
 * แสดงผลเป็นเวลาไทย (UTC+7)
 */
public final class TimeUtil {

    private static final String THAILAND_ZONE = "Asia/Bangkok";

    private TimeUtil() {
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    public static String format(long millis) {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
        );
        format.setTimeZone(TimeZone.getTimeZone(THAILAND_ZONE));
        return format.format(new Date(millis));
    }
}
