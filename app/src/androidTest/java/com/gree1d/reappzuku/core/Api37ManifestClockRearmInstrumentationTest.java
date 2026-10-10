package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.manager.RestrictionsScheduler;
import com.gree1d.reappzuku.service.ShappkyService;

import org.json.JSONArray;
import org.junit.Assume;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Opt-in disposable API37 only. Installs a no-privilege future RTC fixture but
 * deliberately NEVER schedules its alarm. The real platform timezone change
 * must reach the installed BootReceiver and cause the production code to arm
 * SCHEDULER_TICK. We observe AlarmManager's actual pending-alarm inventory via
 * UiAutomation; no synthetic broadcast or privileged app operation is used.
 */
public final class Api37ManifestClockRearmInstrumentationTest {
    private static final String TAG = "ReAppzukuClockRearm";
    private static final String SCHEDULE_KEY = "restrictions_schedules";
    private static final String ACTION = RestrictionsScheduler.ACTION_SCHEDULER_TICK;

    @Test
    public void realTimezoneBroadcastArmsPreviouslyAbsentProductionRtcAlarm() throws Exception {
        Assume.assumeTrue("Disposable CI wrapper required",
                "verify".equals(InstrumentationRegistry.getArguments()
                        .getString("ci_manifest_clock_rearm")));
        assertEquals("CI must select a known target zone", "Pacific/Honolulu",
                InstrumentationRegistry.getArguments().getString("ci_expected_timezone"));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        Assume.assumeFalse("Never exercise a running privileged service",
                ShappkyService.isRunning());

        boolean hadSchedule = prefs.contains(SCHEDULE_KEY);
        String originalSchedule = prefs.getString(SCHEDULE_KEY, null);

        // Match the production PendingIntent identity. Cancel an old alarm only on
        // this disposable test emulator; it is restored from saved prefs in finally.
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        assertTrue("AlarmManager unavailable", alarms != null);
        Intent tickIntent = new Intent(context, RestrictionsScheduler.SchedulerReceiver.class);
        tickIntent.setAction(ACTION);
        PendingIntent tick = PendingIntent.getBroadcast(context, 2001, tickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        CountDownLatch received = new CountDownLatch(1);
        BroadcastReceiver observer = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent incoming) {
                if (incoming != null
                        && Intent.ACTION_TIMEZONE_CHANGED.equals(incoming.getAction())
                        && "Pacific/Honolulu".equals(incoming.getStringExtra("time-zone"))) {
                    received.countDown();
                }
            }
        };
        boolean registered = false;
        try {
            // Timezone is Etc/UTC when CI starts this test. An event three
            // hours ahead is safely in the future in UTC and Honolulu.
            assertEquals("Etc/UTC", java.util.TimeZone.getDefault().getID());
            Calendar now = Calendar.getInstance();
            int startMinute = (now.get(Calendar.HOUR_OF_DAY) * 60
                    + now.get(Calendar.MINUTE) + 180) % 1440;
            int endMinute = (startMinute + 20) % 1440;

            RestrictionsScheduler.ScheduleEntry fixture =
                    new RestrictionsScheduler.ScheduleEntry();
            fixture.packageName = InstrumentationRegistry.getInstrumentation()
                    .getContext().getPackageName();
            fixture.startHour = startMinute / 60;
            fixture.startMinute = startMinute % 60;
            fixture.endHour = endMinute / 60;
            fixture.endMinute = endMinute % 60;
            fixture.protectFlags = 0;
            fixture.onActivateAction = RestrictionsScheduler.ON_ACTIVATE_NOTHING;
            fixture.setBucketActive = false;
            assertTrue(prefs.edit().putString(SCHEDULE_KEY,
                    new JSONArray().put(fixture.toJson()).toString()).commit());

            alarms.cancel(tick);
            String baseline = dumpAlarmManager();
            assertFalse("Fixture must begin with no pending scheduler alarm",
                    hasScheduledTick(baseline));

            context.registerReceiver(observer,
                    new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
            registered = true;
            Log.i(TAG, "READY: fixture stored, no RTC alarm, observer registered");

            assertTrue("No real platform TIMEZONE_CHANGED arrived",
                    received.await(55, TimeUnit.SECONDS));

            // This test does not invoke BootReceiver, scheduleNextStatic or the
            // service. Only a real system broadcast can create the new alarm.
            boolean armed = false;
            for (int attempt = 0; attempt < 35; attempt++) {
                if (hasScheduledTick(dumpAlarmManager())) {
                    armed = true;
                    break;
                }
                Thread.sleep(400);
            }
            assertTrue("Installed manifest receiver did not arm SCHEDULER_TICK",
                    armed);
            assertTrue("Clock broadcast must never start a privileged service",
                    !ShappkyService.isRunning());
            Log.i(TAG, "ALARM_ARMED: real timezone broadcast caused production RTC rearm");
        } finally {
            if (registered) context.unregisterReceiver(observer);
            SharedPreferences.Editor restore = prefs.edit();
            if (hadSchedule) restore.putString(SCHEDULE_KEY, originalSchedule);
            else restore.remove(SCHEDULE_KEY);
            assertTrue("Restore original scheduler preference", restore.commit());
            // Restore the pre-test production alarm family, not the temporary fixture.
            RestrictionsScheduler.scheduleNextStatic(context);
        }
    }

    private static String dumpAlarmManager() throws Exception {
        StringBuilder result = new StringBuilder();
        try (ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("dumpsys alarm");
             BufferedReader input = new BufferedReader(new InputStreamReader(
                     new FileInputStream(fd.getFileDescriptor()), StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int count;
            while ((count = input.read(buf)) != -1) {
                result.append(buf, 0, count);
                if (result.length() > 1_000_000) {
                    throw new IllegalStateException("Alarm dump unexpectedly large");
                }
            }
        }
        return result.toString();
    }

    private static boolean hasScheduledTick(String alarmDump) {
        // The platform renders the PendingIntent action in its active alarm
        // inventory. A cancelled fixture cannot appear as an active alarm.
        return alarmDump.contains(ACTION)
                && alarmDump.contains("com.gree1d.reappzuku");
    }
}
