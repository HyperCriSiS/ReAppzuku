package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.db.AppPolicy;

import org.junit.Test;

public class AppPolicyListStateTest {
    private static AppPolicy policy(String pkg, int strategy, int source) {
        AppPolicy policy = new AppPolicy(pkg);
        policy.strategy = strategy;
        policy.source = source;
        return policy;
    }

    private static AppPolicyResolver.LegacyState legacy(
            boolean autoKill, boolean smart, boolean whitelistMode,
            boolean inWhitelist, boolean inBlacklist) {
        return new AppPolicyResolver.LegacyState(
                autoKill, smart, whitelistMode, inWhitelist, inBlacklist);
    }

    @Test
    public void explicitPolicyWinsForListStatus() {
        AppPolicy explicit = policy("com.example.app", AppPolicy.STRATEGY_SMART,
                AppPolicy.SOURCE_EXPLICIT);
        assertEquals(AppPolicyListState.STATUS_SMART,
                AppPolicyListState.resolveStatus(
                        explicit, false,
                        legacy(true, false, false, false, true),
                        false, false));
    }

    @Test
    public void staleMigrationOwnedRowFallsBackToLiveLegacy() {
        AppPolicy migrated = policy("com.example.app", AppPolicy.STRATEGY_SMART,
                AppPolicy.SOURCE_LEGACY_MIGRATED);
        assertEquals(AppPolicyListState.STATUS_IMMEDIATE,
                AppPolicyListState.resolveStatus(
                        migrated, false,
                        legacy(true, false, false, false, true),
                        false, false));
    }

    @Test
    public void currentSnapshotDisablesLegacyFallbackWithoutPolicy() {
        assertEquals(AppPolicyListState.STATUS_UNMANAGED,
                AppPolicyListState.resolveStatus(
                        null, true,
                        legacy(true, true, false, false, true),
                        false, false));
    }

    @Test
    public void needsSetupAndFailSafeHaveDeterministicPriority() {
        assertEquals(AppPolicyListState.STATUS_NEEDS_SETUP,
                AppPolicyListState.resolveStatus(
                        null, true, null, true, false));
        assertEquals(AppPolicyListState.STATUS_PROTECTED,
                AppPolicyListState.resolveStatus(
                        null, true, null, true, true));
    }

    @Test
    public void managedFilterIsSmartOrImmediateOnly() {
        assertTrue(AppPolicyListState.matchesFilter(
                AppPolicyListState.FILTER_MANAGED, AppPolicyListState.STATUS_SMART));
        assertTrue(AppPolicyListState.matchesFilter(
                AppPolicyListState.FILTER_MANAGED, AppPolicyListState.STATUS_IMMEDIATE));
        assertFalse(AppPolicyListState.matchesFilter(
                AppPolicyListState.FILTER_MANAGED, AppPolicyListState.STATUS_PROTECTED));
        assertFalse(AppPolicyListState.matchesFilter(
                AppPolicyListState.FILTER_MANAGED, AppPolicyListState.STATUS_NEEDS_SETUP));
    }

    @Test
    public void filtersUseOrSemanticsAndNoFilterShowsAll() {
        int mask = AppPolicyListState.FILTER_PROTECTED
                | AppPolicyListState.FILTER_NEEDS_SETUP;
        assertTrue(AppPolicyListState.matchesFilter(
                mask, AppPolicyListState.STATUS_PROTECTED));
        assertTrue(AppPolicyListState.matchesFilter(
                mask, AppPolicyListState.STATUS_NEEDS_SETUP));
        assertFalse(AppPolicyListState.matchesFilter(
                mask, AppPolicyListState.STATUS_SMART));
        assertTrue(AppPolicyListState.matchesFilter(
                AppPolicyListState.FILTER_NONE, AppPolicyListState.STATUS_UNMANAGED));
    }
}
