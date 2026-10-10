package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Disposable emulator only. Observes a genuine platform-originated timezone change.
 * This test NEVER sends the protected intent itself and never changes the clock,
 * timezone, app preferences, alarm state, package policies or privileged backend.
 * The CI host owns timezone mutation and restores its original settings in a trap.
 */
public final class Api37RealClockBroadcastDeliveryInstrumentationTest {
    private static final String TAG = "ReAppzukuClockProbe";

    @Test
    public void receivesRealSystemTimezoneBroadcastWithInstalledBootReceiver() throws Exception {
        String expectedZone = InstrumentationRegistry.getArguments()
                .getString("ci_expected_timezone");
        assertEquals("This probe only runs via the disposable CI wrapper",
                "Pacific/Honolulu", expectedZone);

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent manifestQuery = new Intent(Intent.ACTION_TIMEZONE_CHANGED)
                .setPackage(context.getPackageName());
        List<ResolveInfo> matches =
                context.getPackageManager().queryBroadcastReceivers(manifestQuery, 0);
        boolean installedBootReceiver = false;
        for (ResolveInfo info : matches) {
            if (info.activityInfo != null
                    && BootReceiver.class.getName().equals(info.activityInfo.name)
                    && context.getPackageName().equals(info.activityInfo.packageName)) {
                installedBootReceiver = true;
            }
        }
        assertTrue("Installed BootReceiver must subscribe to the system action",
                installedBootReceiver);

        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<Intent> observed = new AtomicReference<>();
        BroadcastReceiver observer = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent intent) {
                if (intent != null
                        && Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
                    observed.set(new Intent(intent));
                    delivered.countDown();
                }
            }
        };

        context.registerReceiver(observer, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
        try {
            Log.i(TAG, "READY: system-only timezone observer registered");
            assertTrue("Android never delivered a real timezone-change broadcast",
                    delivered.await(55, TimeUnit.SECONDS));
            Intent event = observed.get();
            assertNotNull(event);
            assertEquals(Intent.ACTION_TIMEZONE_CHANGED, event.getAction());
            assertEquals("System timezone extra must match requested real zone",
                    expectedZone, event.getStringExtra("time-zone"));
            Log.i(TAG, "DELIVERED: real system timezone broadcast, zone=" + expectedZone);
        } finally {
            context.unregisterReceiver(observer);
        }
    }
}
