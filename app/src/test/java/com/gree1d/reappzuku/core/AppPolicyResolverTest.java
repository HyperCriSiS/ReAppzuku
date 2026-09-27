package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.db.AppPolicy;

import org.junit.Test;

public class AppPolicyResolverTest {
    @Test
    public void explicitPolicyAlwaysWins() {
        AppPolicy explicit = new AppPolicy("com.example.app");
        explicit.strategy = AppPolicy.STRATEGY_PROTECTED;

        int strategy = AppPolicyResolver.resolveStrategy(explicit,
                legacy(true, true, false, false, true));

        assertEquals(AppPolicy.STRATEGY_PROTECTED, strategy);
    }

    @Test
    public void smartOwnsLegacyBlacklistBeforeImmediateKill() {
        int strategy = AppPolicyResolver.resolveStrategy(null,
                legacy(true, true, false, false, true));

        assertEquals(AppPolicy.STRATEGY_SMART, strategy);
    }

    @Test
    public void blacklistModeTargetsOnlyListedApps() {
        assertEquals(AppPolicy.STRATEGY_IMMEDIATE,
                AppPolicyResolver.resolveStrategy(null,
                        legacy(true, false, false, false, true)));
        assertEquals(AppPolicy.STRATEGY_UNMANAGED,
                AppPolicyResolver.resolveStrategy(null,
                        legacy(true, false, false, false, false)));
    }

    @Test
    public void whitelistModeProtectsListedAppsAndTargetsOthers() {
        assertEquals(AppPolicy.STRATEGY_PROTECTED,
                AppPolicyResolver.resolveStrategy(null,
                        legacy(true, false, true, true, false)));
        assertEquals(AppPolicy.STRATEGY_IMMEDIATE,
                AppPolicyResolver.resolveStrategy(null,
                        legacy(true, false, true, false, false)));
    }

    @Test
    public void disabledLegacyAutomationLeavesAppUnmanaged() {
        assertEquals(AppPolicy.STRATEGY_UNMANAGED,
                AppPolicyResolver.resolveStrategy(null,
                        legacy(false, false, false, false, true)));
    }

    @Test
    public void explicitImmediatePolicyRequiresMatchingTrigger() {
        AppPolicy explicit = new AppPolicy("com.example.app");
        explicit.strategy = AppPolicy.STRATEGY_IMMEDIATE;
        explicit.triggerMask = AppPolicy.TRIGGER_SCREEN_OFF;

        AppPolicyResolver.LegacyState legacy =
                legacy(true, false, false, false, true);

        assertTrue(AppPolicyResolver.shouldExecuteImmediate(
                explicit, legacy, AppPolicy.TRIGGER_SCREEN_OFF));
        assertFalse(AppPolicyResolver.shouldExecuteImmediate(
                explicit, legacy, AppPolicy.TRIGGER_PERIODIC));
    }

    @Test
    public void legacyImmediateRoutingKeepsGlobalTriggerCompatibility() {
        assertTrue(AppPolicyResolver.shouldExecuteImmediate(
                null,
                legacy(true, false, false, false, true),
                AppPolicy.TRIGGER_PERIODIC));
    }

    @Test
    public void explicitSmartBootPassRequiresBootCleanupTrigger() {
        AppPolicy explicit = new AppPolicy("com.example.app");
        explicit.strategy = AppPolicy.STRATEGY_SMART;
        explicit.bootCleanup = true;
        explicit.triggerMask = AppPolicy.TRIGGER_BOOT_CLEANUP;

        assertTrue(AppPolicyResolver.shouldExecuteSmart(
                explicit, legacy(false, false, false, false, false), true));

        explicit.triggerMask = 0L;
        assertFalse(AppPolicyResolver.shouldExecuteSmart(
                explicit, legacy(false, false, false, false, false), true));
    }

    @Test
    public void managedStrategyOnlyIncludesSmartAndImmediate() {
        assertTrue(AppPolicyResolver.isManagedStrategy(AppPolicy.STRATEGY_SMART));
        assertTrue(AppPolicyResolver.isManagedStrategy(AppPolicy.STRATEGY_IMMEDIATE));
        assertFalse(AppPolicyResolver.isManagedStrategy(AppPolicy.STRATEGY_PROTECTED));
        assertFalse(AppPolicyResolver.isManagedStrategy(AppPolicy.STRATEGY_UNMANAGED));
    }

    private static AppPolicyResolver.LegacyState legacy(boolean autoKill,
                                                         boolean smart,
                                                         boolean whitelistMode,
                                                         boolean inWhitelist,
                                                         boolean inBlacklist) {
        return new AppPolicyResolver.LegacyState(
                autoKill, smart, whitelistMode, inWhitelist, inBlacklist);
    }
}
