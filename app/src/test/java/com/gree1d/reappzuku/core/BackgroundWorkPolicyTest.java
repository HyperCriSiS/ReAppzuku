package com.gree1d.reappzuku.core;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

public class BackgroundWorkPolicyTest {

    @Test
    public void noAutomationProducesNoBlockers() {
        assertEquals(
                EnumSet.noneOf(BackgroundWorkPolicy.Blocker.class),
                BackgroundWorkPolicy.resolveActiveBlockers(false, false, false, false, false));
    }

    @Test
    public void reportsEveryActiveContinuityRequirement() {
        assertEquals(
                EnumSet.allOf(BackgroundWorkPolicy.Blocker.class),
                BackgroundWorkPolicy.resolveActiveBlockers(true, true, true, true, true));
    }

    @Test
    public void reportsOnlyEnabledBlockers() {
        assertEquals(
                EnumSet.of(
                        BackgroundWorkPolicy.Blocker.SMART_LIFECYCLE,
                        BackgroundWorkPolicy.Blocker.RESTRICTIONS_SCHEDULE),
                BackgroundWorkPolicy.resolveActiveBlockers(false, true, false, false, true));
    }
}
