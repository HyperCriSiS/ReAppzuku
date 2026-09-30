package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.db.AppPolicy;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppPolicyLegacyMigratorTest {
    @Test
    public void smartOwnsLegacyBlacklistConflict() {
        AppPolicyLegacyMigrator.LegacySnapshot legacy = base();
        legacy.autoKillEnabled = true;
        legacy.smartLifecycleEnabled = true;
        legacy.blacklistedApps = set("com.example.app");

        AppPolicy policy = only(AppPolicyLegacyMigrator.planPolicies(
                legacy, set("com.example.app"), Collections.emptySet(),
                Collections.emptySet(), 123L));

        assertEquals(AppPolicy.STRATEGY_SMART, policy.strategy);
        assertEquals(AppPolicy.SOURCE_LEGACY_MIGRATED, policy.source);
    }

    @Test
    public void activeSleepModeProtectsPackageBeforeSmartOrImmediate() {
        AppPolicyLegacyMigrator.LegacySnapshot legacy = base();
        legacy.autoKillEnabled = true;
        legacy.smartLifecycleEnabled = true;
        legacy.sleepModeEnabled = true;
        legacy.blacklistedApps = set("com.example.app");
        legacy.sleepTimerApps = set("com.example.app");

        AppPolicy policy = only(AppPolicyLegacyMigrator.planPolicies(
                legacy, set("com.example.app"), Collections.emptySet(),
                Collections.emptySet(), 123L));

        assertEquals(AppPolicy.STRATEGY_PROTECTED, policy.strategy);
    }

    @Test
    public void disabledTimerSleepDoesNotOverrideActiveImmediateKill() {
        AppPolicyLegacyMigrator.LegacySnapshot legacy = base();
        legacy.autoKillEnabled = true;
        legacy.sleepModeEnabled = false;
        legacy.blacklistedApps = set("com.example.app");
        legacy.sleepTimerApps = set("com.example.app");

        AppPolicy policy = only(AppPolicyLegacyMigrator.planPolicies(
                legacy, set("com.example.app"), Collections.emptySet(),
                Collections.emptySet(), 123L));

        assertEquals(AppPolicy.STRATEGY_IMMEDIATE, policy.strategy);
    }

    @Test
    public void permanentAndStillFrozenTimerAppsRemainProtected() {
        AppPolicyLegacyMigrator.LegacySnapshot legacy = base();
        legacy.autoKillEnabled = true;
        legacy.blacklistedApps = set("com.example.permanent", "com.example.frozen");
        legacy.sleepPermanentApps = set("com.example.permanent");
        legacy.sleepFrozenTimerApps = set("com.example.frozen");

        List<AppPolicy> policies = AppPolicyLegacyMigrator.planPolicies(
                legacy,
                set("com.example.permanent", "com.example.frozen"),
                Collections.emptySet(),
                Collections.emptySet(),
                123L);

        assertEquals(AppPolicy.STRATEGY_PROTECTED, byPackage(policies, "com.example.permanent").strategy);
        assertEquals(AppPolicy.STRATEGY_PROTECTED, byPackage(policies, "com.example.frozen").strategy);
    }

    @Test
    public void whitelistModeMaterializesCurrentInstalledTargets() {
        AppPolicyLegacyMigrator.LegacySnapshot legacy = base();
        legacy.autoKillEnabled = true;
        legacy.whitelistMode = true;
        legacy.whitelistedApps = set("com.example.keep");

        List<AppPolicy> policies = AppPolicyLegacyMigrator.planPolicies(
                legacy,
                set("com.example.keep", "com.example.kill"),
                Collections.emptySet(),
                Collections.emptySet(),
                123L);

        assertEquals(AppPolicy.STRATEGY_PROTECTED, byPackage(policies, "com.example.keep").strategy);
        assertEquals(AppPolicy.STRATEGY_IMMEDIATE, byPackage(policies, "com.example.kill").strategy);
    }

    @Test
    public void failSafeInstalledPackageCanNeverBecomeImmediate() {
        AppPolicyLegacyMigrator.LegacySnapshot legacy = base();
        legacy.autoKillEnabled = true;
        legacy.blacklistedApps = set("com.example.system");

        AppPolicy policy = only(AppPolicyLegacyMigrator.planPolicies(
                legacy,
                Collections.emptySet(),
                set("com.example.system"),
                Collections.emptySet(),
                123L));

        assertEquals(AppPolicy.STRATEGY_PROTECTED, policy.strategy);
    }

    @Test
    public void existingExplicitPolicyIsNeverPlannedForOverwrite() {
        AppPolicyLegacyMigrator.LegacySnapshot legacy = base();
        legacy.autoKillEnabled = true;
        legacy.blacklistedApps = set("com.example.app");

        List<AppPolicy> policies = AppPolicyLegacyMigrator.planPolicies(
                legacy,
                set("com.example.app"),
                Collections.emptySet(),
                set("com.example.app"),
                123L);

        assertTrue(policies.isEmpty());
    }

    @Test
    public void restrictionPrecedencePreservesStrongestLegacyType() {
        AppPolicyLegacyMigrator.LegacySnapshot legacy = base();
        legacy.backgroundRestrictedApps = set(
                "com.example.soft", "com.example.medium", "com.example.hard", "com.example.manual");
        legacy.mediumRestrictedApps = set("com.example.medium", "com.example.hard", "com.example.manual");
        legacy.hardRestrictedApps = set("com.example.hard", "com.example.manual");
        legacy.manualRestrictedApps = set("com.example.manual");

        List<AppPolicy> policies = AppPolicyLegacyMigrator.planPolicies(
                legacy,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                123L);

        assertEquals(AppPolicy.RESTRICTION_SOFT,
                byPackage(policies, "com.example.soft").backgroundRestriction);
        assertEquals(AppPolicy.RESTRICTION_MEDIUM,
                byPackage(policies, "com.example.medium").backgroundRestriction);
        assertEquals(AppPolicy.RESTRICTION_HARD,
                byPackage(policies, "com.example.hard").backgroundRestriction);
        assertEquals(AppPolicy.RESTRICTION_MANUAL,
                byPackage(policies, "com.example.manual").backgroundRestriction);
    }

    @Test
    public void immediatePolicyCarriesLegacyKillMethodAndTriggers() {
        AppPolicyLegacyMigrator.LegacySnapshot legacy = base();
        legacy.autoKillEnabled = true;
        legacy.killMethod = AppPolicy.KILL_METHOD_AM_KILL;
        legacy.immediateTriggerMask = AppPolicy.TRIGGER_PERIODIC
                | AppPolicy.TRIGGER_SCREEN_OFF
                | AppPolicy.TRIGGER_APP_LAUNCH;
        legacy.blacklistedApps = set("com.example.app");

        AppPolicy policy = only(AppPolicyLegacyMigrator.planPolicies(
                legacy, set("com.example.app"), Collections.emptySet(),
                Collections.emptySet(), 123L));

        assertEquals(AppPolicy.SOURCE_LEGACY_MIGRATED, policy.source);
        assertEquals(AppPolicy.KILL_METHOD_AM_KILL, policy.killMethod);
        assertEquals(legacy.immediateTriggerMask, policy.triggerMask);
        assertEquals(123L, policy.createdAt);
        assertEquals(123L, policy.updatedAt);
        assertTrue(policy.customized);
    }

    @Test
    public void smartPolicyCarriesProfileDelaysAndBootCleanupOnly() {
        AppPolicyLegacyMigrator.LegacySnapshot legacy = base();
        legacy.smartLifecycleEnabled = true;
        legacy.smartBootCleanup = true;
        legacy.smartStandbyDelayMs = 7_200_000L;
        legacy.smartForceStopDelayMs = 43_200_000L;
        legacy.immediateTriggerMask = AppPolicy.TRIGGER_PERIODIC | AppPolicy.TRIGGER_SCREEN_OFF;
        legacy.blacklistedApps = set("com.example.app");

        AppPolicy policy = only(AppPolicyLegacyMigrator.planPolicies(
                legacy, set("com.example.app"), Collections.emptySet(),
                Collections.emptySet(), 123L));

        assertEquals(AppPolicy.STRATEGY_SMART, policy.strategy);
        assertEquals(7_200_000L, policy.standbyDelayMs);
        assertEquals(43_200_000L, policy.forceStopDelayMs);
        assertEquals(AppPolicy.TRIGGER_BOOT_CLEANUP, policy.triggerMask);
    }

    @Test
    public void invalidPackageNamesAreIgnored() {
        AppPolicyLegacyMigrator.LegacySnapshot legacy = base();
        legacy.autoKillEnabled = true;
        legacy.blacklistedApps = set("valid.package", "bad package;rm");

        List<AppPolicy> policies = AppPolicyLegacyMigrator.planPolicies(
                legacy, set("valid.package"), Collections.emptySet(),
                Collections.emptySet(), 123L);

        assertEquals(1, policies.size());
        assertEquals("valid.package", policies.get(0).packageName);
    }

    @Test
    public void fingerprintIsStableAcrossSetIterationOrder() {
        AppPolicyLegacyMigrator.LegacySnapshot first = base();
        first.autoKillEnabled = true;
        first.blacklistedApps = set("com.example.b", "com.example.a");

        AppPolicyLegacyMigrator.LegacySnapshot same = base();
        same.autoKillEnabled = true;
        same.blacklistedApps = set("com.example.a", "com.example.b");

        assertEquals(
                AppPolicyLegacyMigrator.fingerprintSnapshot(first),
                AppPolicyLegacyMigrator.fingerprintSnapshot(same));
    }

    @Test
    public void fingerprintChangesForOwnershipAndTimingInputs() {
        AppPolicyLegacyMigrator.LegacySnapshot original = base();
        original.autoKillEnabled = true;
        original.activePresetNumber = 0;
        original.sleepFrozenTimerApps = set("com.example.app");

        AppPolicyLegacyMigrator.LegacySnapshot changed = base();
        changed.autoKillEnabled = true;
        changed.activePresetNumber = 1;
        changed.sleepFrozenTimerApps = set("com.example.app");

        assertFalse(AppPolicyLegacyMigrator.fingerprintSnapshot(original).equals(
                AppPolicyLegacyMigrator.fingerprintSnapshot(changed)));

        changed.activePresetNumber = 0;
        changed.smartStandbyDelayMs = original.smartStandbyDelayMs + 1L;
        assertFalse(AppPolicyLegacyMigrator.fingerprintSnapshot(original).equals(
                AppPolicyLegacyMigrator.fingerprintSnapshot(changed)));
    }

    @Test
    public void staleMigrationRowsFallBackButExplicitPoliciesAlwaysWin() {
        AppPolicy migrated = new AppPolicy("com.example.migrated");
        migrated.source = AppPolicy.SOURCE_LEGACY_MIGRATED;

        assertNull(AppPolicyLegacyMigrator.resolveEffectivePolicy(migrated, false));
        assertSame(migrated, AppPolicyLegacyMigrator.resolveEffectivePolicy(migrated, true));

        AppPolicy explicit = new AppPolicy("com.example.explicit");
        explicit.source = AppPolicy.SOURCE_EXPLICIT;

        assertSame(explicit, AppPolicyLegacyMigrator.resolveEffectivePolicy(explicit, false));
        assertSame(explicit, AppPolicyLegacyMigrator.resolveEffectivePolicy(explicit, true));
    }

    private static AppPolicyLegacyMigrator.LegacySnapshot base() {
        return new AppPolicyLegacyMigrator.LegacySnapshot();
    }

    private static Set<String> set(String... values) {
        Set<String> result = new HashSet<>();
        Collections.addAll(result, values);
        return result;
    }

    private static AppPolicy only(List<AppPolicy> policies) {
        assertEquals(1, policies.size());
        return policies.get(0);
    }

    private static AppPolicy byPackage(List<AppPolicy> policies, String packageName) {
        for (AppPolicy policy : policies) {
            if (packageName.equals(policy.packageName)) return policy;
        }
        throw new AssertionError("Missing policy for " + packageName);
    }
}
