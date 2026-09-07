package com.gree1d.reappzuku.utils.triggers.analyzers;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Test-only service used to make the ActivityManager ServiceRecord runtime probe deterministic. */
public class ProcessDumpProbeService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
