package com.gree1d.reappzuku.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AppLaunchTriggerPolicyTest {
    @Test
    public void eligibilityRequiresBothFeaturesAndExactTargetPackage() {
        Set<String> targets = new HashSet<>();
        targets.add("com.example.target");

        assertTrue(AppLaunchTriggerPolicy.isEligible(
                true, true, targets, "com.example.target"));
        assertFalse(AppLaunchTriggerPolicy.isEligible(
                false, true, targets, "com.example.target"));
        assertFalse(AppLaunchTriggerPolicy.isEligible(
                true, false, targets, "com.example.target"));
        assertFalse(AppLaunchTriggerPolicy.isEligible(
                true, true, targets, "com.example"));
        assertFalse(AppLaunchTriggerPolicy.isEligible(
                true, true, Collections.emptySet(), "com.example.target"));
        assertFalse(AppLaunchTriggerPolicy.isEligible(
                true, true, null, "com.example.target"));
        assertFalse(AppLaunchTriggerPolicy.isEligible(
                true, true, targets, null));
    }

    @Test
    public void duplicateSuppressionIsPackageExactAndTimeBounded() {
        assertTrue(AppLaunchTriggerPolicy.isDuplicateWithinInterval(
                "com.example.target", "com.example.target", 10_000L, 14_999L, 5_000L));
        assertFalse(AppLaunchTriggerPolicy.isDuplicateWithinInterval(
                "com.example.target", "com.example.target", 10_000L, 15_000L, 5_000L));
        assertFalse(AppLaunchTriggerPolicy.isDuplicateWithinInterval(
                "com.example.target", "com.example.other", 10_000L, 10_100L, 5_000L));
        assertFalse(AppLaunchTriggerPolicy.isDuplicateWithinInterval(
                "com.example.target", null, 10_000L, 10_100L, 5_000L));
        assertFalse(AppLaunchTriggerPolicy.isDuplicateWithinInterval(
                null, "com.example.target", 10_000L, 10_100L, 5_000L));
        assertFalse(AppLaunchTriggerPolicy.isDuplicateWithinInterval(
                "com.example.target", "com.example.target", 10_000L, 10_100L, 0L));
    }

    @Test
    public void clockRollbackRemainsConservativelyDeduplicated() {
        assertTrue(AppLaunchTriggerPolicy.isDuplicateWithinInterval(
                "com.example.target", "com.example.target", 10_000L, 9_000L, 5_000L));
    }
}
