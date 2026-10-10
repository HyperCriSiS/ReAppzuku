package com.gree1d.reappzuku.manager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure, side-effect-free comparison of wall-clock schedule ownership markers. */
public final class RestrictionsClockReconciliationPolicy {
    private RestrictionsClockReconciliationPolicy() {}

    public static Set<String> expectedPackages(
            List<RestrictionsScheduler.ScheduleEntry> schedules, int minutesOfDay) {
        if (minutesOfDay < 0 || minutesOfDay >= 24 * 60) {
            throw new IllegalArgumentException("minutesOfDay out of range");
        }
        Set<String> expected = new HashSet<>();
        for (RestrictionsScheduler.ScheduleEntry entry : schedules) {
            if (entry == null || entry.packageName == null) continue;
            if (entry.isActiveNow(minutesOfDay / 60, minutesOfDay % 60)) {
                expected.add(entry.packageName);
            }
        }
        return expected;
    }

    public static boolean needsReconciliation(
            List<RestrictionsScheduler.ScheduleEntry> schedules,
            Set<String> currentMarkers, int minutesOfDay) {
        return !expectedPackages(schedules, minutesOfDay).equals(currentMarkers);
    }
}
