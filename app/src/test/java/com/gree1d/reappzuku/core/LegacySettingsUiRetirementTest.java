package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class LegacySettingsUiRetirementTest {
    @Test
    public void obsoleteListAndSmartControlsAreNotReachableFromSettings() throws Exception {
        String settings = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivity.java");
        String dialogs = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivityDialogs.java");
        String layout = readRepositoryFile(
                "app/src/main/res/layout/activity_settings.xml");

        assertFalse(settings.contains("showWhitelistDialog"));
        assertFalse(settings.contains("showBlacklistDialog"));
        assertFalse(settings.contains("showKillModeDialog"));
        assertFalse(settings.contains("switchSmartLifecycle"));
        assertFalse(settings.contains("layoutSmartLifecycle"));
        assertFalse(dialogs.contains("showWhitelistDialog"));
        assertFalse(dialogs.contains("showBlacklistDialog"));
        assertFalse(dialogs.contains("showKillModeDialog"));

        assertFalse(layout.contains("layout_kill_mode"));
        assertFalse(layout.contains("layout_whitelist"));
        assertFalse(layout.contains("layout_blacklist"));
        assertFalse(layout.contains("switch_smart_lifecycle"));
        assertFalse(layout.contains("layout_smart_lifecycle_apps"));
        assertFalse(layout.contains("layout_smart_lifecycle_profile"));
        assertFalse(layout.contains("switch_smart_boot_cleanup"));
    }

    @Test
    public void retiredUiStateRemainsAvailableForMigrationAndRestoreCompatibility() throws Exception {
        String keys = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/PreferenceKeys.java");
        String migrator = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/AppPolicyLegacyMigrator.java");
        String worker = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/service/SmartLifecycleWorker.java");
        String backgroundPolicy = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/BackgroundWorkPolicy.java");

        assertTrue(keys.contains("KEY_WHITELISTED_APPS"));
        assertTrue(keys.contains("KEY_BLACKLISTED_APPS"));
        assertTrue(keys.contains("KEY_KILL_MODE"));
        assertTrue(keys.contains("KEY_SMART_LIFECYCLE_ENABLED"));
        assertTrue(keys.contains("KEY_SMART_LIFECYCLE_PROFILE"));
        assertTrue(keys.contains("KEY_SMART_BOOT_CLEANUP_ENABLED"));

        assertTrue(migrator.contains("case KEY_WHITELISTED_APPS:"));
        assertTrue(migrator.contains("case KEY_BLACKLISTED_APPS:"));
        assertTrue(migrator.contains("case KEY_KILL_MODE:"));
        assertTrue(migrator.contains("case KEY_SMART_LIFECYCLE_ENABLED:"));

        assertFalse(worker.contains("getBoolean(KEY_SMART_LIFECYCLE_ENABLED"));
        assertFalse(backgroundPolicy.contains("prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED"));
    }

    private static String readRepositoryFile(String relative) throws IOException {
        List<Path> candidates = Arrays.asList(
                Paths.get(relative),
                Paths.get("..", relative),
                Paths.get("..", "..", relative));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
        }
        throw new IOException("Could not locate repository file: " + relative
                + " from " + Paths.get("").toAbsolutePath());
    }
}
