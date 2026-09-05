package com.gree1d.reappzuku.core;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

public class BackgroundWorkPolicyTest {

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
                    mask != 0,
                    AutomationDesiredState.requiresBackgroundContinuity(
                            autoKill,
                            smartLifecycle,
                            sleepMode,
                            activePreset,
                            restrictionSchedule));
        }
    }
}
