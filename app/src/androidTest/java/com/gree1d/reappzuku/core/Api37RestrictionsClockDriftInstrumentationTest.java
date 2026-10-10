package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.manager.RestrictionsScheduler;

import org.json.JSONArray;
import org.junit.Assume;
import org.junit.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Opt-in disposable API37 read-only clock recovery decision probe.
 * Never invokes a service, sends a protected broadcast or changes system time.
 * Temporarily writes only two scheduler preference keys and restores them.
 */
public final class Api37RestrictionsClockDriftInstrumentationTest {
    @Test
    public void detectsClockDriftWithoutMutatingMarkersOrLaunchingService() throws Exception {
        Assume.assumeTrue("Only disposable test emulator",
                "verify".equals(InstrumentationRegistry.getArguments()
                        .getString("ci_clock_reconcile")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        final String scheduleKey = "restrictions_schedules";
        final String markerKey = "temp_protected_packages";
        final boolean hadSchedule = prefs.contains(scheduleKey);
        final boolean hadMarkers = prefs.contains(markerKey);
        final String originalSchedule = prefs.getString(scheduleKey, null);
        final Set<String> originalMarkers = new HashSet<>(
                prefs.getStringSet(markerKey, Collections.emptySet()));
        String fixture = InstrumentationRegistry.getInstrumentation()
                .getContext().getPackageName();

        try {
            Calendar now = Calendar.getInstance();
            int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            int start = (nowMinute + 1430) % 1440;
            int end = (nowMinute + 10) % 1440;
            RestrictionsScheduler.ScheduleEntry entry = new RestrictionsScheduler.ScheduleEntry();
            entry.packageName = fixture;
            entry.startHour = start / 60;
            entry.startMinute = start % 60;
            entry.endHour = end / 60;
            entry.endMinute = end % 60;
            entry.protectFlags = 0;
            entry.onActivateAction = RestrictionsScheduler.ON_ACTIVATE_NOTHING;
            JSONArray schedules = new JSONArray().put(entry.toJson());
            assertTrue(prefs.edit().putString(scheduleKey, schedules.toString())
                    .putStringSet(markerKey, Collections.emptySet()).commit());

            assertTrue("Newly active window needs a repair",
                    RestrictionsScheduler.needsClockReconciliation(context));
            assertEquals("Read-only preview never modifies protection marker",
                    Collections.emptySet(), prefs.getStringSet(markerKey, Collections.emptySet()));

            assertTrue(prefs.edit()
                    .putStringSet(markerKey, Collections.singleton(fixture)).commit());
            assertFalse("Already reconciled window is a no-op",
                    RestrictionsScheduler.needsClockReconciliation(context));
            assertEquals(Collections.singleton(fixture),
                    prefs.getStringSet(markerKey, Collections.emptySet()));

            assertTrue(prefs.edit().putString(scheduleKey, "[invalid-json").commit());
            assertFalse("Malformed schedule must never permit privileged repair",
                    RestrictionsScheduler.needsClockReconciliation(context));
            assertEquals(Collections.singleton(fixture),
                    prefs.getStringSet(markerKey, Collections.emptySet()));
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (hadSchedule) restore.putString(scheduleKey, originalSchedule);
            else restore.remove(scheduleKey);
            if (hadMarkers) restore.putStringSet(markerKey, originalMarkers);
            else restore.remove(markerKey);
            assertTrue("Restore original scheduler state", restore.commit());
        }
    }
}
