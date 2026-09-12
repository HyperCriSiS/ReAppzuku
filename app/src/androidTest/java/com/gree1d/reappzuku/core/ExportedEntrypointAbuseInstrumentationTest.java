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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class ExportedEntrypointAbuseInstrumentationTest {
    private static final String PRODUCT_PACKAGE = "com.gree1d.reappzuku";
    private static final long BIND_OBSERVATION_MS = 1_500L;

    @Test
    public void foreignTestPrincipalCannotBindProtectedExportedServices() throws Exception {
        Context foreign = InstrumentationRegistry.getInstrumentation().getContext();
        assertNotNull(foreign);
        assertFalse("Test package must be a distinct foreign principal",
                PRODUCT_PACKAGE.equals(foreign.getPackageName()));

        assertNoForeignBinder(foreign,
                "com.gree1d.reappzuku.service.AppLaunchAccessibilityService");
        assertNoForeignBinder(foreign,
                "com.gree1d.reappzuku.utils.ShappkyQuickTile");
        assertNoForeignBinder(foreign,
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

    private static void assertNoForeignBinder(Context foreign, String className) throws Exception {
        Intent intent = new Intent().setComponent(new ComponentName(PRODUCT_PACKAGE, className));
        AtomicBoolean connected = new AtomicBoolean(false);
        CountDownLatch connectedLatch = new CountDownLatch(1);
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (service != null) {
                    connected.set(true);
                }
                connectedLatch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        boolean bound = false;
        try {
            // Android may reject a protected explicit bind synchronously (SecurityException or
            // false) or accept the request while still withholding the Binder asynchronously.
            // The security invariant is that the foreign principal never receives a Binder.
            bound = foreign.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (bound) {
                connectedLatch.await(BIND_OBSERVATION_MS, TimeUnit.MILLISECONDS);
            }
        } catch (SecurityException expected) {
            // Secure synchronous denial path.
        } finally {
            if (bound) {
                try {
                    foreign.unbindService(connection);
                } catch (IllegalArgumentException ignored) {
                    // The platform may already have discarded a denied asynchronous bind.
                }
            }
        }

        assertFalse("Protected service exposed a Binder to foreign principal: " + className,
                connected.get());
    }
}
