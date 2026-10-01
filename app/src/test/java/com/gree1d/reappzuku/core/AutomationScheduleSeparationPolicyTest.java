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

public class AutomationScheduleSeparationPolicyTest {
    @Test
    public void legacyStorageIdentifiersRemainStableForUpgradeCompatibility() throws Exception {
        String manager = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/PresetManager.java");

        assertTrue(manager.contains("\"preset_1_prefs\""));
        assertTrue(manager.contains("\"preset_2_prefs\""));
        assertTrue(manager.contains("\"com.gree1d.reappzuku.PRESET_ACTIVATE\""));
        assertTrue(manager.contains("\"com.gree1d.reappzuku.PRESET_DEACTIVATE\""));
        assertTrue(manager.contains("KEY_BACKUP_PREFIX = \"preset_backup_\""));
    }

    @Test
    public void scheduleUiUsesDistinctTerminologyFromPolicyPresets() throws Exception {
        String dialogs = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivityDialogs.java");
        String settingsLayout = readRepositoryFile(
                "app/src/main/res/layout/activity_settings.xml");
        String editor = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/ui/PresetSettingsActivity.java");

        assertTrue(dialogs.contains("AutomationScheduleManager"));
        assertTrue(dialogs.contains("R.string.automation_schedules_title"));
        assertFalse(dialogs.contains("R.string.settings_presets_title"));
        assertTrue(settingsLayout.contains("@string/automation_schedules_title"));
        assertFalse(settingsLayout.contains("@string/settings_presets_title"));
        assertTrue(editor.contains("R.string.automation_schedule_title"));
        assertFalse(editor.contains("R.string.preset_title"));
    }

    @Test
    public void roomPolicyPresetsRemainTimeWindowFree() throws Exception {
        String policyPreset = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/db/PolicyPreset.java");

        assertTrue(policyPreset.contains("tableName = \"policy_preset\""));
        assertFalse(policyPreset.contains("startHour"));
        assertFalse(policyPreset.contains("endHour"));
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
