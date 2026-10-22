package com.example.newapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;

/** Weekly debt reminders default to Monday at 09:00 device-local time. */
final class DebtReminderScheduler {
    private static final int REQUEST_CODE=64201;
    private DebtReminderScheduler(){}
    static void scheduleIfMissing(Context context){
        PendingIntent existing=pendingIntent(context,PendingIntent.FLAG_NO_CREATE);
        if(existing!=null)return;
        scheduleNext(context);
    }
    static void scheduleNext(Context context){
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(alarm==null)return;
        PendingIntent pi=pendingIntent(context,PendingIntent.FLAG_UPDATE_CURRENT);
        Calendar next=AppClock.calendar();next.setFirstDayOfWeek(Calendar.MONDAY);
        next.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);next.set(Calendar.HOUR_OF_DAY,9);next.set(Calendar.MINUTE,0);next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);
        if(next.getTimeInMillis()<=AppClock.now())next.add(Calendar.DAY_OF_YEAR,7);
        if(Build.VERSION.SDK_INT>=23)alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.getTimeInMillis(),pi);
        else alarm.setExact(AlarmManager.RTC_WAKEUP,next.getTimeInMillis(),pi);
    }
    private static PendingIntent pendingIntent(Context context,int flags){
        Intent intent=new Intent(context,DebtReminderReceiver.class);
        int actual=flags;
        if(Build.VERSION.SDK_INT>=23)actual|=PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context,REQUEST_CODE,intent,actual);
    }
}