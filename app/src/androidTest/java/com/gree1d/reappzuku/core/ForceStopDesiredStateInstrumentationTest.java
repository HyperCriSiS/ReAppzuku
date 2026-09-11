package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ForceStopDesiredStateInstrumentationTest {
    private static final String ARG_FORCE_STOP_PROBE = "forceStopProbe";
    private static final String MARKER_PREFS = "reappzuku_force_stop_probe";
    private static final String MARKER_PID = "pid_before_force_stop";
    private static final String AUTO_KILL_WORK = "AutoKillWorker";

    private Context targetContext;
    private SharedPreferences prefs;
    private SharedPreferences markerPrefs;
    private WorkManager workManager;

    @Before
    public void requireExplicitForceStopProbe() {
        Assume.assumeTrue(
                "Force-stop probe is only run by the guarded process-death lane",
                "true".equals(InstrumentationRegistry.getArguments().getString(ARG_FORCE_STOP_PROBE)));
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = targetContext.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        markerPrefs = targetContext.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE);
        workManager = WorkManager.getInstance(targetContext);
    }

    @Test
    public void prepareDisabledStateBeforeForceStop() throws Exception {
        workManager.cancelUniqueWork(AUTO_KILL_WORK).getResult().get(10, TimeUnit.SECONDS);
        assertTrue(prefs.edit()
                .putBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false)
                .commit());

        String pid = shell("pidof " + targetContext.getPackageName()).trim();
        assertFalse("The app process must exist before the external force-stop", pid.isEmpty());
        assertTrue(markerPrefs.edit().clear().putString(MARKER_PID, pid).commit());

        assertFalse(BackgroundWorkPolicy.shouldRunForegroundService(targetContext));
        assertEquals(0, activeWorkCount(AUTO_KILL_WORK));
    }

    @Test
    public void verifyDisabledStateAfterExternalForceStop() throws Exception {
        String oldPid = markerPrefs.getString(MARKER_PID, "");
        assertFalse("Prepare phase did not persist a pre-force-stop pid", oldPid.isEmpty());

        assertFalse("Persisted AutoKill desired state changed across force-stop",
                prefs.getBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, true));
        assertFalse("Fresh process must still refuse the foreground AutoKill service",
                BackgroundWorkPolicy.shouldRunForegroundService(targetContext));
        assertEquals("Fresh process must not restore AutoKill Unique Work while disabled",
                0, activeWorkCount(AUTO_KILL_WORK));

        String newPid = shell("pidof " + targetContext.getPackageName()).trim();
        assertFalse("Verification requires a freshly started app process", newPid.isEmpty());
        assertFalse("External force-stop must have destroyed the original app process",
                oldPid.equals(newPid));
    }

    private int activeWorkCount(String uniqueName) throws Exception {
        int active = 0;
        List<WorkInfo> infos = workManager.getWorkInfosForUniqueWork(uniqueName)
                .get(10, TimeUnit.SECONDS);
        for (WorkInfo info : infos) {
            if (!info.getState().isFinished()) active++;
        }
        return active;
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
}
