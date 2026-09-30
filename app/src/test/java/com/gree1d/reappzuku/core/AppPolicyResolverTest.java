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
    public void explicitImmediateKillMethodOverridesLegacyDefault() {
        AppPolicy explicit = new AppPolicy("com.example.app");
        explicit.killMethod = AppPolicy.KILL_METHOD_AM_KILL;

        assertEquals(AppPolicy.KILL_METHOD_AM_KILL,
                AppPolicyResolver.resolveImmediateKillMethod(
                        explicit, AppPolicy.KILL_METHOD_FORCE_STOP));
    }

    @Test
    public void invalidImmediateKillMethodFailsSafeToForceStop() {
        AppPolicy explicit = new AppPolicy("com.example.app");
        explicit.killMethod = 99;

        assertEquals(AppPolicy.KILL_METHOD_FORCE_STOP,
                AppPolicyResolver.resolveImmediateKillMethod(
                        explicit, AppPolicy.KILL_METHOD_AM_KILL));
        assertEquals(AppPolicy.KILL_METHOD_FORCE_STOP,
                AppPolicyResolver.resolveImmediateKillMethod(null, 99));
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
    public void explicitNonSmartPolicyRemovesLegacySmartOwnership() {
        AppPolicy explicit = new AppPolicy("com.example.app");
        explicit.strategy = AppPolicy.STRATEGY_PROTECTED;

        assertFalse(AppPolicyResolver.shouldExecuteSmart(
                explicit, legacy(true, true, false, false, true), false));
    }

    @Test
    public void explicitSmartPolicyUsesPerAppDelays() {
        AppPolicy explicit = new AppPolicy("com.example.app");
        explicit.strategy = AppPolicy.STRATEGY_SMART;
        explicit.standbyDelayMs = 15_000L;
        explicit.forceStopDelayMs = 45_000L;

        long standby = AppPolicyResolver.resolveSmartStandbyDelayMs(explicit, 60_000L);
        long forceStop = AppPolicyResolver.resolveSmartForceStopDelayMs(
                explicit, 120_000L, standby);

        assertEquals(15_000L, standby);
        assertEquals(45_000L, forceStop);
    }

    @Test
    public void invalidExplicitSmartDelaysUseConservativePolicyDefaults() {
        AppPolicy explicit = new AppPolicy("com.example.app");
        explicit.strategy = AppPolicy.STRATEGY_SMART;
        explicit.standbyDelayMs = -1L;
        explicit.forceStopDelayMs = -1L;

        long standby = AppPolicyResolver.resolveSmartStandbyDelayMs(explicit, 30_000L);
        long forceStop = AppPolicyResolver.resolveSmartForceStopDelayMs(
                explicit, 60_000L, standby);

        assertEquals(AppPolicy.DEFAULT_SMART_STANDBY_DELAY_MS, standby);
        assertEquals(AppPolicy.DEFAULT_SMART_FORCE_STOP_DELAY_MS, forceStop);
    }

    @Test
    public void smartForceStopNeverPrecedesResolvedStandby() {
        AppPolicy explicit = new AppPolicy("com.example.app");
        explicit.strategy = AppPolicy.STRATEGY_SMART;
        explicit.standbyDelayMs = 60_000L;
        explicit.forceStopDelayMs = 10_000L;

        long standby = AppPolicyResolver.resolveSmartStandbyDelayMs(explicit, 30_000L);
        long forceStop = AppPolicyResolver.resolveSmartForceStopDelayMs(
                explicit, 120_000L, standby);

        assertEquals(60_000L, standby);
        assertEquals(60_000L, forceStop);
    }

    @Test
    public void smartDelayFallbackUsesSafeDefaultsForInvalidLegacyValues() {
        assertEquals(AppPolicy.DEFAULT_SMART_STANDBY_DELAY_MS,
                AppPolicyResolver.resolveSmartStandbyDelayMs(null, 0L));
        assertEquals(AppPolicy.DEFAULT_SMART_FORCE_STOP_DELAY_MS,
                AppPolicyResolver.resolveSmartForceStopDelayMs(
                        null, 0L, AppPolicy.DEFAULT_SMART_STANDBY_DELAY_MS));
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
