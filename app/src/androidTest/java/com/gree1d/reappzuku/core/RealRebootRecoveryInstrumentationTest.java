package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.manager.RestrictionsScheduler;
import com.gree1d.reappzuku.utils.PresetModel;

import org.json.JSONArray;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class RealRebootRecoveryInstrumentationTest {
    private static final String ARG_REAL_REBOOT_PROBE = "realRebootProbe";
    private static final String MARKER_PREFS = "reappzuku_real_reboot_probe";
    private static final String MARKER_BOOT_ID = "boot_id";
    private static final String RESTRICTIONS_SCHEDULES = "restrictions_schedules";

    private static final String AUTO_KILL_WORK = "AutoKillWorker";
    private static final String SMART_PERIODIC_WORK = "SmartLifecyclePeriodic";
    private static final String SMART_BOOT_WORK = "SmartLifecycleBootCleanup";

    private Context targetContext;
    private SharedPreferences prefs;
    private SharedPreferences markerPrefs;
    private WorkManager workManager;

    @Before
    public void requireExplicitRealRebootProbe() {
        Assume.assumeTrue(
                "Real reboot probe is only run by the guarded reboot lane",
                "true".equals(InstrumentationRegistry.getArguments().getString(ARG_REAL_REBOOT_PROBE)));
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = targetContext.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        markerPrefs = targetContext.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE);
        workManager = WorkManager.getInstance(targetContext);
    }

    @Test
    public void prepareForRealReboot() throws Exception {
        cancelAndAwait(AUTO_KILL_WORK);
        cancelAndAwait(SMART_PERIODIC_WORK);
        cancelAndAwait(SMART_BOOT_WORK);

        PresetManager presetManager = new PresetManager(targetContext);
        assertTrue(presetManager.clearPresetStorageBlocking(PresetModel.PRESET_1));
        assertTrue(presetManager.clearPresetStorageBlocking(PresetModel.PRESET_2));
        presetManager.cancelAlarms(PresetModel.PRESET_1);
        presetManager.cancelAlarms(PresetModel.PRESET_2);

        int nowMinutes = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60
                + Calendar.getInstance().get(Calendar.MINUTE);
        int presetStart = (nowMinutes + 12 * 60) % (24 * 60);
        int presetEnd = (presetStart + 10) % (24 * 60);
        int restrictionStart = (nowMinutes + 10 * 60) % (24 * 60);
        int restrictionEnd = (restrictionStart + 10) % (24 * 60);

        PresetModel preset = new PresetModel(PresetModel.PRESET_1);
        preset.name = "Real reboot probe";
        preset.enabled = true;
        preset.autoKillEnabled = false;
        preset.periodicKillEnabled = false;
        preset.killInterval = 15;
        preset.ramThreshold = 80;
        preset.killMode = 1;
        preset.startHour = presetStart / 60;
        preset.startMinute = presetStart % 60;
        preset.endHour = presetEnd / 60;
        preset.endMinute = presetEnd % 60;
        assertTrue(presetManager.savePresetBlocking(preset));

        RestrictionsScheduler.ScheduleEntry restriction = new RestrictionsScheduler.ScheduleEntry();
        restriction.packageName = targetContext.getPackageName();
        restriction.startHour = restrictionStart / 60;
        restriction.startMinute = restrictionStart % 60;
        restriction.endHour = restrictionEnd / 60;
        restriction.endMinute = restrictionEnd % 60;
        restriction.enabled = true;
        restriction.onActivateAction = RestrictionsScheduler.ON_ACTIVATE_NOTHING;
        JSONArray schedules = new JSONArray();
        schedules.put(restriction.toJson());

        assertTrue(prefs.edit().clear()
                .putBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false)
                .putBoolean(PreferenceKeys.KEY_SMART_LIFECYCLE_ENABLED, true)
                .putBoolean(PreferenceKeys.KEY_SMART_BOOT_CLEANUP_ENABLED, true)
                .putString(RESTRICTIONS_SCHEDULES, schedules.toString())
                .commit());

        String bootId = shell("cat /proc/sys/kernel/random/boot_id").trim();
        assertFalse("Kernel boot id must be observable before reboot", bootId.isEmpty());
        assertTrue(markerPrefs.edit().clear().putString(MARKER_BOOT_ID, bootId).commit());
    }

    @Test
    public void verifyAfterRealReboot() throws Exception {
        String previousBootId = markerPrefs.getString(MARKER_BOOT_ID, null);
        assertNotNull("Prepare phase did not persist a boot id", previousBootId);
        String currentBootId = shell("cat /proc/sys/kernel/random/boot_id").trim();
        assertFalse("Kernel boot id must be observable after reboot", currentBootId.isEmpty());
        assertFalse("The verification phase must run after a real kernel reboot",
                previousBootId.equals(currentBootId));

        PresetModel restoredPreset = new PresetManager(targetContext).loadPreset(PresetModel.PRESET_1);
        assertNotNull("Enabled preset storage must survive reboot", restoredPreset);
        assertTrue("Preset must remain enabled across reboot", restoredPreset.enabled);
        assertTrue("Restriction schedule must survive reboot",
                prefs.getString(RESTRICTIONS_SCHEDULES, "").length() > 2);

        awaitCondition(() -> activeWorkCount(SMART_PERIODIC_WORK) == 1,
                "Smart Lifecycle periodic Unique Work was not restored after reboot");
        assertEquals("AutoKill must remain disabled after reboot",
                0, activeWorkCount(AUTO_KILL_WORK));
        assertTrue("Boot cleanup must be enqueued at most once",
                workInfoCount(SMART_BOOT_WORK) <= 1);

        awaitCondition(() -> {
            String alarms = relevantAlarmDump();
            return alarms.contains("com.gree1d.reappzuku.PRESET_ACTIVATE")
                    && alarms.contains("com.gree1d.reappzuku.PRESET_DEACTIVATE")
                    && alarms.contains("SCHEDULER_TICK");
        }, "Preset/restriction alarms were not rebuilt after reboot");

        String alarms = relevantAlarmDump();
        assertTrue(alarms.contains("com.gree1d.reappzuku.PRESET_ACTIVATE"));
        assertTrue(alarms.contains("com.gree1d.reappzuku.PRESET_DEACTIVATE"));
        assertTrue(alarms.contains("SCHEDULER_TICK"));
    }

    private int activeWorkCount(String uniqueName) throws Exception {
        int active = 0;
        for (WorkInfo info : workInfos(uniqueName)) {
            if (!info.getState().isFinished()) active++;
        }
        return active;
    }

    private int workInfoCount(String uniqueName) throws Exception {
        return workInfos(uniqueName).size();
    }

    private List<WorkInfo> workInfos(String uniqueName) throws Exception {
        return workManager.getWorkInfosForUniqueWork(uniqueName).get(10, TimeUnit.SECONDS);
    }

    private void cancelAndAwait(String uniqueName) throws Exception {
        workManager.cancelUniqueWork(uniqueName).getResult().get(10, TimeUnit.SECONDS);
    }

    private String relevantAlarmDump() throws Exception {
        return shell("dumpsys alarm | grep -E 'PRESET_ACTIVATE|PRESET_DEACTIVATE|SCHEDULER_TICK' || true");
    }

    private void awaitCondition(CheckedCondition condition, String failureMessage) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 20_000L;
        Throwable lastFailure = null;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            try {
                if (condition.evaluate()) return;
            } catch (Throwable t) {
                lastFailure = t;
            }
            android.os.SystemClock.sleep(500L);
        }
        if (lastFailure instanceof Exception) throw (Exception) lastFailure;
        throw new AssertionError(failureMessage);
    }

    private String shell(String command) throws Exception {
        ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        try (FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            descriptor.close();
        }
    }

    private interface CheckedCondition {
        boolean evaluate() throws Exception;
    }
}
