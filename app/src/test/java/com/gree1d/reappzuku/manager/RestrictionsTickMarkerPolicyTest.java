package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class RestrictionsTickMarkerPolicyTest {
    private static Set<String> of(String... packages) {
        Set<String> values = new HashSet<>();
        Collections.addAll(values, packages);
        return values;
    }

    @Test public void failedActivationMustNotClaimProtection() {
        Set<String> original = of("com.example.already");
        Set<String> next = RestrictionsTickMarkerPolicy.afterCompletedTransitions(
                original, Collections.emptySet(), Collections.emptySet());
        assertEquals(original, next);
        assertEquals(of("com.example.already"), original);
    }

    @Test public void successfulActivationOnlyAddsCompletedPackage() {
        Set<String> next = RestrictionsTickMarkerPolicy.afterCompletedTransitions(
                of("com.example.already"), of("com.example.ok"), Collections.emptySet());
        assertEquals(of("com.example.already", "com.example.ok"), next);
    }

    @Test public void failedStopOrRestoreKeepsPriorMarker() {
        Set<String> previous = of("com.example.failed", "com.example.restored");
        Set<String> next = RestrictionsTickMarkerPolicy.afterCompletedTransitions(
                previous, Collections.emptySet(), of("com.example.restored"));
        assertEquals(of("com.example.failed"), next);
        assertEquals(2, previous.size());
    }

    @Test public void completedActivationAndDeactivationAreAppliedTogether() {
        Set<String> next = RestrictionsTickMarkerPolicy.afterCompletedTransitions(
                of("com.example.old"), of("com.example.new"), of("com.example.old"));
        assertEquals(of("com.example.new"), next);
    }

    @Test public void snapshotIsIndependentAndRepeatedSuccessIsIdempotent() {
        Set<String> previous = of("com.example.one");
        Set<String> next = RestrictionsTickMarkerPolicy.afterCompletedTransitions(
                previous, of("com.example.one"), Collections.emptySet());
        next.remove("com.example.one");
        assertTrue(previous.contains("com.example.one"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullMarkerSets() {
        RestrictionsTickMarkerPolicy.afterCompletedTransitions(null, of("com.example"), of());
    }
}
