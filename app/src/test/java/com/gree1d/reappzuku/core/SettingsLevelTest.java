package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsLevelTest {

    @Test
    public void higherLevelsShowEverythingOfTheLowerOnes() {
        assertTrue(SettingsLevel.shows(SettingsLevel.SIMPLE, SettingsLevel.SIMPLE));
        assertFalse(SettingsLevel.shows(SettingsLevel.SIMPLE, SettingsLevel.ADVANCED));
        assertTrue(SettingsLevel.shows(SettingsLevel.ADVANCED, SettingsLevel.ADVANCED));
        assertFalse(SettingsLevel.shows(SettingsLevel.ADVANCED, SettingsLevel.EXPERT));
        assertTrue(SettingsLevel.shows(SettingsLevel.EXPERT, SettingsLevel.EXPERT));
    }

    @Test
    public void unknownStoredValuesShowEverything() {
        assertEquals(SettingsLevel.EXPERT, SettingsLevel.sanitize(-1));
        assertEquals(SettingsLevel.EXPERT, SettingsLevel.sanitize(7));
        assertTrue(SettingsLevel.shows(42, SettingsLevel.EXPERT));
    }

    @Test
    public void aFreshInstallStartsSimpleAndAnUpdateKeepsEverything() throws Exception {
        assertEquals(SettingsLevel.SIMPLE, SettingsLevel.initial(1000L, 1000L));
        assertEquals(SettingsLevel.EXPERT, SettingsLevel.initial(1000L, 2000L));
        String settings = read("app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivity.java");
        // The first answer is stored: a later update must not switch a new user to Expert.
        int undecided = settings.indexOf("if (!sharedPreferences.contains(KEY_SETTINGS_LEVEL)) {");
        assertTrue(undecided >= 0 && settings.indexOf(
                "sharedPreferences.edit().putInt(KEY_SETTINGS_LEVEL, level).apply();", undecided) > undecided);
    }

    @Test
    public void nobodyLosesARowByUpdatingAndBackupStaysReachable() throws Exception {
        String settings = read("app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivity.java");
        assertTrue(settings.contains("sharedPreferences.getInt(KEY_SETTINGS_LEVEL, SettingsLevel.EXPERT)"));
        int start = settings.indexOf("private void applySettingsLevel() {");
        String body = settings.substring(start, settings.indexOf("\n    }\n", start));
        // Rows needed to recover or to understand the device are never hidden.
        for (String always : new String[] {"layoutBackupRestore", "layoutHelp", "layoutRecommendations",
                "layoutFirstRun", "layoutNewAppSetup", "switchAutoKill"}) {
            assertFalse(always, body.contains(always));
        }
    }

    @Test
    public void everyLocaleHasThreeLevels() throws Exception {
        for (String dir : new String[] {"values", "values-es", "values-ru", "values-uk", "values-zh-rCN"}) {
            String xml = read("app/src/main/res/" + dir + "/strings_settings_level.xml");
            Matcher items = Pattern.compile("<item>").matcher(xml);
            int count = 0;
            while (items.find()) count++;
            assertEquals(dir, 3, count);
            assertTrue(dir, xml.contains("name=\"settings_level_title\""));
        }
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
