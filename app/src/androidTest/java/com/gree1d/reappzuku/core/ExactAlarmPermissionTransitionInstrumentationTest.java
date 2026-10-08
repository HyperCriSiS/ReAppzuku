package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;

/**
 * Scoped scheduler compatibility tests. The API-37 CI lane changes the app's
 * exact-alarm AppOp outside the app between independent instrumentation sessions.
 * The request is safely in the future and is cancelled in all outcomes.
 */
public class ExactAlarmPermissionTransitionInstrumentationTest {
    private static final int REQUEST_CODE = 0x58414354;

    @Test
    public void preAndroidSCanStillScheduleExactWithoutSpecialAccess() {
        Assume.assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.S);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(ExactAlarmCapability.canScheduleExact(context));
        assertEquals(AlarmScheduler.ScheduleResult.EXACT, scheduleAndCancel(context));
    }

    @Test
    public void deniedUsesBestEffortFallback() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        assertNotNull(manager);
        assertFalse("CI must revoke exact-alarm special access before this stage",
                manager.canScheduleExactAlarms());
        assertFalse(ExactAlarmCapability.canScheduleExact(context));
        assertEquals(AlarmScheduler.ScheduleResult.BEST_EFFORT,
                scheduleAndCancel(context));
    }

    @Test
    public void grantedUsesExactAfterDelayedPermission() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        assertNotNull(manager);
        assertTrue("CI must grant exact-alarm special access before this stage",
                manager.canScheduleExactAlarms());
        assertTrue(ExactAlarmCapability.canScheduleExact(context));
        assertEquals(AlarmScheduler.ScheduleResult.EXACT,
                scheduleAndCancel(context));
    }

    private static AlarmScheduler.ScheduleResult scheduleAndCancel(Context context) {
        AlarmScheduler scheduler = new AlarmScheduler(context);
        assertTrue(scheduler.isAvailable());
        Intent intent = new Intent("com.gree1d.reappzuku.TEST_EXACT_ALARM_TRANSITION")
                .setPackage(context.getPackageName());
        PendingIntent operation = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            return scheduler.scheduleRtcWakeup(
                    System.currentTimeMillis() + 24L * 60L * 60L * 1000L,
                    operation,
                    true);
        } finally {
            scheduler.cancel(operation);
            operation.cancel();
        }
    }
}
