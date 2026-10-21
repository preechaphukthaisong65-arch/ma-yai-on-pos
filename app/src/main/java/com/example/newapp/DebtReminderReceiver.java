package com.example.newapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DebtReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        try{DebtReminderNotifier.sendWeekly(context);}catch(Exception ignored){}
        DebtReminderScheduler.scheduleNext(context);
    }
}