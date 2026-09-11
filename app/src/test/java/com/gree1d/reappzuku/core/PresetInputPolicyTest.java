package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class PresetInputPolicyTest {
    @Test
    public void acceptsClockBoundaryValues() {
        PresetInputPolicy.requireClockTime("start", 0, 0);
        PresetInputPolicy.requireClockTime("end", 23, 59);
    }

    @Test
    public void rejectsHoursOutsideDay() {
        assertThrows(IllegalArgumentException.class,
                () -> PresetInputPolicy.requireClockTime("start", -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PresetInputPolicy.requireClockTime("start", 24, 0));
    }

    @Test
    public void rejectsMinutesOutsideHour() {
        assertThrows(IllegalArgumentException.class,
                () -> PresetInputPolicy.requireClockTime("start", 12, -1));
        assertThrows(IllegalArgumentException.class,
                () -> PresetInputPolicy.requireClockTime("start", 12, 60));
    }

    @Test
    public void boundsPackageCollections() {
        PresetInputPolicy.requirePackageCount("whitelistedApps", 0);
        PresetInputPolicy.requirePackageCount(
                "whitelistedApps", PresetInputPolicy.MAX_PACKAGE_ENTRIES);
        assertThrows(IllegalArgumentException.class, () ->
                PresetInputPolicy.requirePackageCount(
                        "whitelistedApps", PresetInputPolicy.MAX_PACKAGE_ENTRIES + 1));
    }
}
