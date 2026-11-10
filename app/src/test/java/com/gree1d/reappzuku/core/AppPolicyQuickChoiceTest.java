package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.PolicyPreset;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class AppPolicyQuickChoiceTest {
    private static final String PKG = "com.example.app";

    private static PolicyPreset balanced() {
        PolicyPreset preset = new PolicyPreset("balanced");
        preset.id = PolicyPresetSeeder.PRESET_BALANCED;
        preset.strategy = AppPolicy.STRATEGY_SMART;
        preset.standbyDelayMs = 2 * 60 * 60_000L;
        preset.forceStopDelayMs = 8 * 60 * 60_000L;
        preset.triggerMask = AppPolicy.TRIGGER_BOOT_CLEANUP;
        return preset;
    }

    @Test
    public void newSmartPolicyTakesItsDefaultsFromThePreset() {
        AppPolicy policy = AppPolicyEditorModel.withStrategy(
                null, PKG, AppPolicy.STRATEGY_SMART, balanced(), 100L);
        assertEquals(AppPolicy.STRATEGY_SMART, policy.strategy);
        assertEquals(AppPolicy.SOURCE_EXPLICIT, policy.source);
        assertEquals(2 * 60 * 60_000L, policy.standbyDelayMs);
        assertEquals(Long.valueOf(PolicyPresetSeeder.PRESET_BALANCED), policy.presetId);
        assertFalse(policy.customized);
    }

    @Test
    public void newImmediatePolicyRunsOnEveryTrigger() {
        AppPolicy policy = AppPolicyEditorModel.withStrategy(
                null, PKG, AppPolicy.STRATEGY_IMMEDIATE, balanced(), 100L);
        assertEquals(AppPolicy.STRATEGY_IMMEDIATE, policy.strategy);
        assertEquals(AppPolicyEditorModel.IMMEDIATE_TRIGGER_BITS, policy.triggerMask);
        assertTrue(AppPolicyResolver.shouldExecuteImmediate(policy, null, AppPolicy.TRIGGER_PERIODIC));
    }

    @Test
    public void newProtectedOrUnmanagedPolicyHasNoTriggers() {
        for (int strategy : new int[] {AppPolicy.STRATEGY_PROTECTED, AppPolicy.STRATEGY_UNMANAGED}) {
            AppPolicy policy = AppPolicyEditorModel.withStrategy(null, PKG, strategy, balanced(), 100L);
            assertEquals(strategy, policy.strategy);
            assertEquals(0L, policy.triggerMask);
            assertNull(policy.presetId);
        }
    }

    @Test
    public void existingPolicyKeepsEverythingButTheStrategy() {
        AppPolicy existing = new AppPolicy(PKG);
        existing.strategy = AppPolicy.STRATEGY_SMART;
        existing.source = AppPolicy.SOURCE_LEGACY_MIGRATED;
        existing.presetId = 7L;
        existing.standbyDelayMs = 30 * 60_000L;
        existing.forceStopDelayMs = 90 * 60_000L;
        existing.protectMedia = false;
        existing.triggerMask = AppPolicy.TRIGGER_SCREEN_OFF;
        existing.createdAt = 5L;

        AppPolicy policy = AppPolicyEditorModel.withStrategy(
                existing, PKG, AppPolicy.STRATEGY_IMMEDIATE, balanced(), 100L);
        assertEquals(AppPolicy.STRATEGY_IMMEDIATE, policy.strategy);
        // A migrated row becomes the user's own, so the migrator no longer replaces it.
        assertEquals(AppPolicy.SOURCE_EXPLICIT, policy.source);
        assertEquals(30 * 60_000L, policy.standbyDelayMs);
        assertFalse(policy.protectMedia);
        assertEquals(AppPolicy.TRIGGER_SCREEN_OFF, policy.triggerMask);
        assertEquals(5L, policy.createdAt);
        assertEquals(100L, policy.updatedAt);
        assertTrue(policy.customized);
    }

    @Test
    public void existingPolicyWithoutTriggersGetsThemWhenItBecomesImmediate() {
        AppPolicy existing = new AppPolicy(PKG);
        existing.strategy = AppPolicy.STRATEGY_PROTECTED;
        AppPolicy policy = AppPolicyEditorModel.withStrategy(
                existing, PKG, AppPolicy.STRATEGY_IMMEDIATE, null, 100L);
        assertEquals(AppPolicyEditorModel.IMMEDIATE_TRIGGER_BITS, policy.triggerMask);
    }

    @Test
    public void bootOnlyTriggersDoNotCountForImmediate() {
        AppPolicy existing = new AppPolicy(PKG);
        existing.strategy = AppPolicy.STRATEGY_SMART;
        existing.presetId = PolicyPresetSeeder.PRESET_BALANCED;
        existing.triggerMask = AppPolicy.TRIGGER_BOOT_CLEANUP;
        AppPolicy policy = AppPolicyEditorModel.withStrategy(
                existing, PKG, AppPolicy.STRATEGY_IMMEDIATE, balanced(), 100L);
        for (long trigger : new long[] {AppPolicy.TRIGGER_PERIODIC, AppPolicy.TRIGGER_SCREEN_OFF,
                AppPolicy.TRIGGER_RAM_THRESHOLD, AppPolicy.TRIGGER_HARDWARE_EVENT,
                AppPolicy.TRIGGER_APP_LAUNCH}) {
            assertTrue(AppPolicyResolver.shouldExecuteImmediate(policy, null, trigger));
        }
        assertTrue((policy.triggerMask & AppPolicy.TRIGGER_BOOT_CLEANUP) != 0L);
    }

    @Test
    public void newAppPlaceholderIsTreatedLikeNoPolicy() {
        AppPolicy placeholder = AppPolicyEditorModel.defaultPolicy(PKG, 5L);
        placeholder.customized = true;
        AppPolicy policy = AppPolicyEditorModel.withStrategy(
                placeholder, PKG, AppPolicy.STRATEGY_SMART, balanced(), 100L);
        assertEquals(Long.valueOf(PolicyPresetSeeder.PRESET_BALANCED), policy.presetId);
        assertTrue(AppPolicyResolver.shouldExecuteSmart(policy, null, true));
        assertEquals(5L, policy.createdAt);
    }

    @Test
    public void policyWithoutPresetCountsAsCustomized() {
        assertTrue(AppPolicyEditorModel.withStrategy(
                null, PKG, AppPolicy.STRATEGY_PROTECTED, balanced(), 100L).customized);
    }

    @Test
    public void lockFollowsTheEffectivePolicyNotTheLegacyWhitelist() throws Exception {
        assertTrue(AppPolicyListState.locksRow(AppPolicyListState.STATUS_PROTECTED, false));
        assertFalse(AppPolicyListState.locksRow(AppPolicyListState.STATUS_PROTECTED, true));
        assertFalse(AppPolicyListState.locksRow(AppPolicyListState.STATUS_IMMEDIATE, false));
        assertFalse(AppPolicyListState.locksRow(AppPolicyListState.STATUS_UNMANAGED, false));
        String main = read("app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java");
        assertTrue(main.contains(
                "app.setWhitelisted(AppPolicyListState.locksRow(app.getPolicyListStatus(), failSafe));"));
        // A reload requested during a running load is not dropped.
        assertTrue(main.contains("reloadRequested = true;"));
    }

    @Test
    public void strategyRowHandlesStatusesWithoutAStrategy() throws Exception {
        String row = read("app/src/main/java/com/gree1d/reappzuku/ui/PolicyStrategyRow.java");
        assertTrue(row.contains("int[] current = {status < names.length ? status : -1};"));
        assertTrue(row.contains("row.setVisibility(View.GONE);"));
        String detail = read("app/src/main/java/com/gree1d/reappzuku/ui/AppResourceDetailActivity.java");
        assertTrue(detail.contains("getSupportFragmentManager().isStateSaved()"));
        assertTrue(detail.contains("if (sheetLoading) return;"));
    }

    @Test
    public void emptyWhitelistModeNoLongerBlocksAutomation() throws Exception {
        String settings = read("app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivity.java");
        assertFalse(settings.contains("dialog_unsafe_whitelist"));
        assertTrue(settings.contains("putInt(KEY_KILL_MODE, DEFAULT_KILL_MODE)"));
    }

    @Test
    public void quickChoiceRunsTheSameFollowUpsAsTheEditor() throws Exception {
        String source = read("app/src/main/java/com/gree1d/reappzuku/core/AppPolicyQuickChoice.java");
        int upsert = source.indexOf("database.appPolicyDao().upsert(policy);");
        int restriction = source.indexOf("BackgroundAppManager.storedRestrictionLevel(context, packageName)");
        int reconcile = source.indexOf("SmartLifecycleWorker.reconcilePeriodic(context);");
        int pending = source.indexOf("NewAppSetupStore.removePending(context, packageName);");
        int cancel = source.indexOf("NewAppSetupNotifier.cancel(context, packageName);");
        assertTrue(restriction >= 0 && restriction < upsert);
        assertTrue(upsert < reconcile && reconcile < pending && pending < cancel);
    }

    @Test
    public void optionSheetsNoLongerWriteTheLegacyLists() throws Exception {
        for (String file : new String[] {
                "ui/AppOptionsBottomSheet.java", "ui/StatsAppOptionsBottomSheet.java",
                "ui/MainActivity.java", "ui/AppResourceDetailActivity.java"}) {
            String source = read("app/src/main/java/com/gree1d/reappzuku/" + file);
            assertFalse(file, source.contains("saveWhitelistedApps("));
            assertFalse(file, source.contains("saveBlacklistedApps("));
            assertFalse(file, source.contains("onToggleWhitelist"));
            assertFalse(file, source.contains("onToggleBlacklist"));
        }
        for (String layout : new String[] {
                "fragment_app_options_bottom_sheet.xml", "fragment_stats_options_bottom_sheet.xml"}) {
            String xml = read("app/src/main/res/layout/" + layout);
            assertTrue(layout, xml.contains("@+id/sheet_item_strategy"));
            assertTrue(layout, xml.contains("@+id/sheet_strategy_value"));
            assertFalse(layout, xml.contains("whitelist"));
            assertFalse(layout, xml.contains("blacklist"));
        }
        assertTrue(read("app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java")
                .contains("AppPolicyQuickChoice.apply(getApplicationContext(), packageName, strategy);"));
    }

    private static String read(String relative) throws Exception {
        for (Path candidate : Arrays.asList(
                Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative));
            }
        }
        throw new java.io.IOException("Could not locate repository root");
    }
}
