package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class ExportedEntrypointAbuseInstrumentationTest {
    private static final String PRODUCT_PACKAGE = "com.gree1d.reappzuku";

    @Test
    public void foreignTestPrincipalCannotBindProtectedExportedServices() {
        Context foreign = InstrumentationRegistry.getInstrumentation().getContext();
        assertNotNull(foreign);
        assertFalse("Test package must be a distinct foreign principal",
                PRODUCT_PACKAGE.equals(foreign.getPackageName()));

        assertBindDenied(foreign,
                "com.gree1d.reappzuku.service.AppLaunchAccessibilityService");
        assertBindDenied(foreign,
                "com.gree1d.reappzuku.utils.ShappkyQuickTile");
        assertBindDenied(foreign,
                "com.gree1d.reappzuku.utils.ShappkyBackgroundKillTile");
    }

    @Test
    public void foreignTestPrincipalCannotSendProtectedBootBroadcast() {
        Context foreign = InstrumentationRegistry.getInstrumentation().getContext();
        assertNotNull(foreign);
        SecurityException denial = null;
        try {
            Intent boot = new Intent(Intent.ACTION_BOOT_COMPLETED)
                    .setComponent(new ComponentName(
                            PRODUCT_PACKAGE,
                            "com.gree1d.reappzuku.core.BootReceiver"));
            foreign.sendBroadcast(boot);
        } catch (SecurityException expected) {
            denial = expected;
        }
        assertNotNull("BOOT_COMPLETED injection from a foreign app must be denied", denial);
    }

    private static void assertBindDenied(Context foreign, String className) {
        Intent intent = new Intent().setComponent(new ComponentName(PRODUCT_PACKAGE, className));
        AtomicBoolean connected = new AtomicBoolean(false);
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                connected.set(true);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        boolean bound = false;
        try {
            // Android is allowed to report permission denial either by throwing a
            // SecurityException or by returning false. Both are secure outcomes; a true return
            // would mean the foreign principal obtained a service binding and must fail.
            bound = foreign.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException expected) {
            // Secure denial path.
        } finally {
            if (bound) {
                foreign.unbindService(connection);
            }
        }

        assertFalse("Protected service must reject a foreign bind for " + className, bound);
        assertFalse("Protected service callback must never connect", connected.get());
    }
}
