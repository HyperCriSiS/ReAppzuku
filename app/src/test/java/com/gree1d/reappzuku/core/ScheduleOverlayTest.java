package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.db.AppPolicy;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class ScheduleOverlayTest {

    private static AppPolicy policy(int strategy) {
        AppPolicy policy = new AppPolicy("a");
        policy.strategy = strategy;
        return policy;
    }

    @Test
    public void blacklistScheduleTargetsOnlyItsList() {
        ScheduleOverlay overlay = new ScheduleOverlay(AppConstants.KILL_MODE_BLACKLIST,
                Collections.singleton("w"), Collections.singleton("a"));
        assertTrue(overlay.targets("a", null));
        assertFalse(overlay.targets("other", null));
    }

    @Test
    public void aStrategyTheUserChoseWinsOverTheSchedule() {
        ScheduleOverlay overlay = new ScheduleOverlay(AppConstants.KILL_MODE_BLACKLIST,
                Collections.emptySet(), Collections.singleton("a"));
        for (int strategy : new int[] {AppPolicy.STRATEGY_UNMANAGED, AppPolicy.STRATEGY_PROTECTED,
                AppPolicy.STRATEGY_SMART, AppPolicy.STRATEGY_IMMEDIATE}) {
            assertFalse(overlay.targets("a", policy(strategy)));
        }
    }

    @Test
    public void whitelistScheduleTargetsEverythingOutsideItsList() {
        ScheduleOverlay overlay = new ScheduleOverlay(AppConstants.KILL_MODE_WHITELIST,
                new HashSet<>(Arrays.asList("kept")), Collections.emptySet());
        assertTrue(overlay.targets("a", null));
        assertFalse(overlay.targets("kept", null));
    }

    @Test
    public void anAppTheUserProtectedIsNeverTargeted() {
        ScheduleOverlay overlay = new ScheduleOverlay(AppConstants.KILL_MODE_WHITELIST,
                Collections.emptySet(), Collections.emptySet());
        assertFalse(overlay.targets("a", policy(AppPolicy.STRATEGY_PROTECTED)));
    }

    @Test
    public void nullListsMeanEmptyLists() {
        assertFalse(new ScheduleOverlay(AppConstants.KILL_MODE_BLACKLIST, null, null).targets("a", null));
        assertTrue(new ScheduleOverlay(AppConstants.KILL_MODE_WHITELIST, null, null).targets("a", null));
    }

    @Test
    public void activatingAScheduleNoLongerOverwritesTheStoredLists() throws Exception {
        String manager = read("app/src/main/java/com/gree1d/reappzuku/manager/PresetManager.java");
        int start = manager.indexOf("    private void applyPreset(PresetModel model) {");
        String apply = manager.substring(start, manager.indexOf("\n    }\n", start));
        assertFalse(apply.contains("KEY_WHITELISTED_APPS"));
        assertFalse(apply.contains("KEY_BLACKLISTED_APPS"));
        assertFalse(apply.contains("KEY_KILL_MODE"));
        // A schedule activated by an earlier version is still undone correctly.
        assertTrue(manager.contains("if (mainPrefs.contains(KEY_BACKUP_PREFIX + KEY_WHITELISTED_APPS)) {"));
        assertTrue(manager.contains("if (mainPrefs.contains(KEY_BACKUP_PREFIX + KEY_KILL_MODE)) {"));

        String killer = read("app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java");
        int overlay = killer.indexOf("scheduleOverlay.targets(pkg, explicitPolicy)");
        assertTrue(overlay >= 0 && overlay < killer.indexOf("AppPolicyResolver.shouldExecuteImmediate("));
        // The overlay only decides whether an app is wanted; the hidden, protected,
        // foreground and persistent exclusions still follow and can veto it.
        int veto = killer.indexOf("if (!wanted) {");
        assertTrue(overlay < veto);
        assertTrue(veto < killer.indexOf("ProtectedApps.isProtected(pkg, currentKeyboard, currentLauncher)"));
        assertTrue(veto < killer.indexOf("ApplicationInfo.FLAG_PERSISTENT"));
        // While a schedule is active the legacy lists take no part: no fallback, no migrated rows.
        assertTrue(killer.contains("AppPolicyResolver.LegacyState legacyState = scheduleOverlay != null ? null"));
        assertTrue(killer.contains("effectivePolicy.source == AppPolicy.SOURCE_LEGACY_MIGRATED"));
        // Switching between schedules puts back lists an earlier version had swapped.
        assertTrue(manager.contains("} else {\n            restoreListsFromBackup();"));
        assertEquals(1, AppConstants.KILL_MODE_BLACKLIST);
        assertEquals(0, AppConstants.KILL_MODE_WHITELIST);
    }

    private static String read(String relative) throws Exception {
        for (Path candidate : Arrays.asList(
                Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative)).replace("\r\n", "\n");
            }
        }
        throw new java.io.IOException("Could not locate repository root");
    }
}
