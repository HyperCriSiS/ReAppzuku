package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExactAlarmDeniedInstrumentationTest {
    @Test
    public void deniedExactAlarmUsesBestEffortFallback() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);

        Context context = ApplicationProvider.getApplicationContext();
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        assertNotNull(alarmManager);

        // The dedicated runtime lane sets SCHEDULE_EXACT_ALARM to deny before this test.
        // In the normal instrumentation suite this test is skipped instead of depending on
        // device-specific initial special-access state.
        Assume.assumeFalse(alarmManager.canScheduleExactAlarms());
        assertFalse(ExactAlarmCapability.canScheduleExact(context));

        Intent intent = new Intent("com.gree1d.reappzuku.TEST_EXACT_ALARM_FALLBACK")
                .setPackage(context.getPackageName());
        PendingIntent operation = PendingIntent.getBroadcast(
                context,
                0x45584143,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmScheduler scheduler = new AlarmScheduler(context);
        assertEquals(
                AlarmScheduler.ScheduleResult.BEST_EFFORT,
                scheduler.scheduleRtcWakeup(
                        System.currentTimeMillis() + 60_000L,
                        operation,
                        true));
        scheduler.cancel(operation);
        operation.cancel();
    }
}
