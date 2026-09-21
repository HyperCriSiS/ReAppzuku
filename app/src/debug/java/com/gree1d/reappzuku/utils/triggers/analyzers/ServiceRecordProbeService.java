package com.gree1d.reappzuku.utils.triggers.analyzers;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public final class ServiceRecordProbeService extends Service {
    private final Binder binder = new Binder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
