package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** No Android clock, filesystem, shell, service or package mutations. */
public class RestrictionsClockReconciliationPolicyTest {
    private static RestrictionsScheduler.ScheduleEntry entry(
            String pkg, int startHour, int startMinute, int endHour, int endMinute,
            boolean enabled) {
        RestrictionsScheduler.ScheduleEntry e = new RestrictionsScheduler.ScheduleEntry();
        e.packageName = pkg;
        e.startHour = startHour;
        e.startMinute = startMinute;
        e.endHour = endHour;
        e.endMinute = endMinute;
        e.enabled = enabled;
        e.protectFlags = RestrictionsScheduler.PROTECT_ALL;
        return e;
    }

    @Test public void forwardJumpAcrossStartRequiresActivation() {
        var schedule = Collections.singletonList(entry("com.example.one", 9, 0, 10, 0, true));
        assertFalse(RestrictionsClockReconciliationPolicy.needsReconciliation(
                schedule, Collections.emptySet(), 8 * 60 + 59));
        assertTrue(RestrictionsClockReconciliationPolicy.needsReconciliation(
                schedule, Collections.emptySet(), 9 * 60 + 15));
        assertFalse(RestrictionsClockReconciliationPolicy.needsReconciliation(
                schedule, Collections.singleton("com.example.one"), 9 * 60 + 15));
    }

    @Test public void backwardJumpAcrossEndRequiresDeactivation() {
        var schedule = Collections.singletonList(entry("com.example.one", 9, 0, 10, 0, true));
        assertTrue(RestrictionsClockReconciliationPolicy.needsReconciliation(
                schedule, Collections.singleton("com.example.one"), 10 * 60));
    }

    @Test public void overnightAndDisabledWindowsAreCorrect() {
        var overnight = entry("com.example.night", 23, 0, 4, 0, true);
        var disabled = entry("com.example.off", 0, 0, 23, 59, false);
        assertEquals(Collections.singleton("com.example.night"),
                RestrictionsClockReconciliationPolicy.expectedPackages(
                        Arrays.asList(overnight, disabled), 30));
        assertEquals(Collections.emptySet(),
                RestrictionsClockReconciliationPolicy.expectedPackages(
                        Arrays.asList(overnight, disabled), 12 * 60));
    }

    @Test public void duplicatePackagesAndZeroLengthWindowsDoNotAmplify() {
        var one = entry("com.example.one", 12, 0, 13, 0, true);
        var duplicate = entry("com.example.one", 11, 0, 14, 0, true);
        var empty = entry("com.example.never", 12, 0, 12, 0, true);
        Set<String> expected = new HashSet<>(Collections.singleton("com.example.one"));
        assertEquals(expected, RestrictionsClockReconciliationPolicy.expectedPackages(
                Arrays.asList(one, duplicate, empty), 12 * 60 + 30));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesInvalidClockMinute() {
        RestrictionsClockReconciliationPolicy.expectedPackages(Collections.emptyList(), 1440);
    }
}
