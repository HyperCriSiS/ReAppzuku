package com.gree1d.reappzuku.core;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackgroundWorkPolicyTest {
    @Test
    public void shizukuWakeObserverTracksRelevantChangesOnly() {
        assertTrue(BackgroundWorkPolicy.affectsShizukuWakePolicy(
                PreferenceKeys.KEY_PREVENT_SHIZUKU_AUTOSTART));
        assertTrue(BackgroundWorkPolicy.affectsShizukuWakePolicy(
                PreferenceKeys.KEY_AUTO_KILL_ENABLED));
        assertTrue(BackgroundWorkPolicy.affectsShizukuWakePolicy(
                PreferenceKeys.KEY_SLEEP_MODE_ENABLED));
        assertTrue(BackgroundWorkPolicy.affectsShizukuWakePolicy(
                PreferenceKeys.KEY_ACTIVE_PRESET));
        assertTrue(BackgroundWorkPolicy.affectsShizukuWakePolicy("restrictions_schedules"));
        assertFalse(BackgroundWorkPolicy.affectsShizukuWakePolicy(
                PreferenceKeys.KEY_EXIT_ON_BACK));
        assertFalse(BackgroundWorkPolicy.affectsShizukuWakePolicy(null));
    }


    @Test
    public void exhaustiveContinuityTruthTableMatchesBlockerSet() {
        BackgroundWorkPolicy.Blocker[] blockerByBit = {
                BackgroundWorkPolicy.Blocker.AUTO_KILL,
                BackgroundWorkPolicy.Blocker.SMART_LIFECYCLE,
                BackgroundWorkPolicy.Blocker.SLEEP_MODE,
                BackgroundWorkPolicy.Blocker.ACTIVE_PRESET,
                BackgroundWorkPolicy.Blocker.RESTRICTIONS_SCHEDULE
        };

        for (int mask = 0; mask < (1 << blockerByBit.length); mask++) {
            boolean autoKill = (mask & 1) != 0;
            boolean smartLifecycle = (mask & 2) != 0;
            boolean sleepMode = (mask & 4) != 0;
            boolean activePreset = (mask & 8) != 0;
            boolean restrictionSchedule = (mask & 16) != 0;

            EnumSet<BackgroundWorkPolicy.Blocker> expected =
                    EnumSet.noneOf(BackgroundWorkPolicy.Blocker.class);
            for (int bit = 0; bit < blockerByBit.length; bit++) {
                if (bit == 1) continue; // Legacy Smart no longer requires main-process continuity.
                if ((mask & (1 << bit)) != 0) expected.add(blockerByBit[bit]);
            }

            EnumSet<BackgroundWorkPolicy.Blocker> actual =
                    BackgroundWorkPolicy.resolveActiveBlockers(
                            autoKill,
                            smartLifecycle,
                            sleepMode,
                            activePreset,
                            restrictionSchedule);

            assertEquals("blocker mismatch for mask=" + mask, expected, actual);
            assertEquals(
                    "continuity mismatch for mask=" + mask,
                    (mask & ~2) != 0,
                    AutomationDesiredState.requiresBackgroundContinuity(
                            autoKill,
                            smartLifecycle,
                            sleepMode,
                            activePreset,
                            restrictionSchedule));
        }
    }

    @Test
    public void exhaustiveAppBehaviorCompatibilityTruthTable() {
        // Five historical inputs remain accepted, but legacy Smart alone is no longer a
        // continuity blocker because canonical Smart execution is WorkManager-backed.
        for (int mask = 0; mask < 32; mask++) {
            boolean continuityRequired = (mask & ~2) != 0;
            for (int requestedBits = 0; requestedBits < 4; requestedBits++) {
                boolean requestedExitOnBack = (requestedBits & 1) != 0;
                boolean requestedPreventAutoStart = (requestedBits & 2) != 0;

                assertEquals(
                        "Exit-on-Back mismatch for blockers=" + mask
                                + " requestedBits=" + requestedBits,
                        !continuityRequired && requestedExitOnBack,
                        BackgroundWorkPolicy.resolveExitOnBack(
                                continuityRequired, requestedExitOnBack));
                assertEquals(
                        "Shizuku on-demand mismatch for blockers=" + mask
                                + " requestedBits=" + requestedBits,
                        !continuityRequired && requestedPreventAutoStart,
                        BackgroundWorkPolicy.resolvePreventShizukuAutoStart(
                                continuityRequired, requestedPreventAutoStart));
            }
        }
    }
}