package com.gree1d.reappzuku.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;



public class CollectStatsReceiver extends BroadcastReceiver {

    private static final String FILE_NAME = "CollectStatsReceiver";

    public static final String ACTION_COLLECT_SNAPSHOT =
            "com.gree1d.reappzuku.COLLECT_SNAPSHOT";

    static final String WAKELOCK_TAG = "reappzuku:CollectStatsSnapshot";

    private static final long WAKELOCK_TIMEOUT_MS = 30_000L;

    static volatile PowerManager.WakeLock snapshotWakeLock;

    static void acquireSnapshotWakeLock(Context context) {
        releaseSnapshotWakeLock();
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) {

            return;
        }
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
        wl.setReferenceCounted(false);
        wl.acquire(WAKELOCK_TIMEOUT_MS);
        snapshotWakeLock = wl;

    }

    public static void releaseSnapshotWakeLock() {
        PowerManager.WakeLock wl = snapshotWakeLock;
        if (wl != null && wl.isHeld()) {
            wl.release();

        }
        snapshotWakeLock = null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_COLLECT_SNAPSHOT.equals(intent.getAction())) {

            return;
        }



        acquireSnapshotWakeLock(context);

        Intent serviceIntent = new Intent(context, ShappkyService.class);
        serviceIntent.setAction("TAKE_SNAPSHOT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
