package com.reappzuku.securityprobe;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public final class ProbeActivity extends Activity {
    private static final String TAG = "ReAppzukuSecurityProbe";
    private static final String PRODUCT_PACKAGE = "com.gree1d.reappzuku";
    private static final long OBSERVATION_MS = 2_000L;

    private final List<ServiceConnection> acceptedConnections = new ArrayList<>();
    private volatile boolean binderExposed;
    private boolean bootBroadcastDenied;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        attemptBind("com.gree1d.reappzuku.service.AppLaunchAccessibilityService");
        attemptBind("com.gree1d.reappzuku.utils.ShappkyQuickTile");
        attemptBind("com.gree1d.reappzuku.utils.ShappkyBackgroundKillTile");
        bootBroadcastDenied = attemptProtectedBootBroadcast();

        new Handler(Looper.getMainLooper()).postDelayed(this::finishProbe, OBSERVATION_MS);
    }

    private void attemptBind(String className) {
        ComponentName component = new ComponentName(PRODUCT_PACKAGE, className);
        Intent intent = new Intent().setComponent(component);
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (service != null) {
                    binderExposed = true;
                    Log.e(TAG, "BINDER_EXPOSED=" + name.flattenToShortString());
                }
            }

            @Override
            public void onNullBinding(ComponentName name) {
                Log.i(TAG, "NULL_BINDING=" + name.flattenToShortString());
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        try {
            boolean accepted = bindService(intent, connection, BIND_AUTO_CREATE);
            if (accepted) {
                acceptedConnections.add(connection);
                Log.i(TAG, "BIND_REQUEST_ACCEPTED=" + component.flattenToShortString());
            } else {
                Log.i(TAG, "BIND_DENIED_FALSE=" + component.flattenToShortString());
            }
        } catch (SecurityException expected) {
            Log.i(TAG, "BIND_DENIED_SECURITY_EXCEPTION=" + component.flattenToShortString());
        }
    }

    private boolean attemptProtectedBootBroadcast() {
        Intent boot = new Intent(Intent.ACTION_BOOT_COMPLETED)
                .setComponent(new ComponentName(
                        PRODUCT_PACKAGE,
                        "com.gree1d.reappzuku.core.BootReceiver"));
        try {
            sendBroadcast(boot);
            Log.e(TAG, "BOOT_BROADCAST_NOT_DENIED");
            return false;
        } catch (SecurityException expected) {
            Log.i(TAG, "BOOT_BROADCAST_DENIED");
            return true;
        }
    }

    private void finishProbe() {
        for (ServiceConnection connection : acceptedConnections) {
            try {
                unbindService(connection);
            } catch (IllegalArgumentException ignored) {
                // A denied asynchronous bind may already have been discarded by the platform.
            }
        }
        acceptedConnections.clear();

        boolean pass = !binderExposed && bootBroadcastDenied;
        Log.i(TAG, "RESULT=" + (pass ? "PASS" : "FAIL")
                + " binderExposed=" + binderExposed
                + " bootBroadcastDenied=" + bootBroadcastDenied);
        setResult(pass ? RESULT_OK : RESULT_CANCELED);
        finishAndRemoveTask();
    }
}
