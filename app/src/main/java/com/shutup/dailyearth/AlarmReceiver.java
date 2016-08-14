package com.shutup.dailyearth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {
    public AlarmReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
       if (BuildConfig.DEBUG) Log.d("AlarmReceiver", "onReceive");
    }
}
