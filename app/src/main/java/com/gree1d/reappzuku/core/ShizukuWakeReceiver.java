package com.gree1d.reappzuku.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Intentionally empty. Delivery of this explicit in-app broadcast starts the
 * normal ReAppzuku process, whose Application then attaches to Shizuku.
 */
public final class ShizukuWakeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // No work here: starting the normal application process is the work.
    }
}
