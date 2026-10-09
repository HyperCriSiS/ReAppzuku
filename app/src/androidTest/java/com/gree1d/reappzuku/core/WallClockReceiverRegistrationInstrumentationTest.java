package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.List;

/**
 * Read-only installed-manifest contract. No device clock, zone, prefs,
 * alarms or privileged state is mutated.
 */
public class WallClockReceiverRegistrationInstrumentationTest {
    @Test
    public void installedReceiverRegistersBothProtectedClockChangeActions() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertReceiverFound(context, Intent.ACTION_BOOT_COMPLETED);
        assertReceiverFound(context, Intent.ACTION_TIME_CHANGED);
        assertReceiverFound(context, Intent.ACTION_TIMEZONE_CHANGED);
        assertFalse("TIME_TICK is not a manifest broadcast for this receiver",
                hasBootReceiver(context, Intent.ACTION_TIME_TICK));
    }

    private static void assertReceiverFound(Context context, String action) {
        assertTrue("Missing BootReceiver action: " + action,
                hasBootReceiver(context, action));
    }

    private static boolean hasBootReceiver(Context context, String action) {
        Intent intent = new Intent(action).setPackage(context.getPackageName());
        List<ResolveInfo> matches = context.getPackageManager().queryBroadcastReceivers(intent, 0);
        for (ResolveInfo match : matches) {
            if (match.activityInfo != null
                    && BootReceiver.class.getName().equals(match.activityInfo.name)
                    && context.getPackageName().equals(match.activityInfo.packageName)) {
                return true;
            }
        }
        return false;
    }
}
