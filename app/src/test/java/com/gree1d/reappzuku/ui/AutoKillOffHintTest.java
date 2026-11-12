package com.gree1d.reappzuku.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.db.AppPolicy;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class AutoKillOffHintTest {

    @Test
    public void immediateIsPointedOutUntilTheServiceAndATriggerAreOn() {
        assertTrue(AutoKillOffHint.needed(AppPolicy.STRATEGY_IMMEDIATE, false, false, 0));
        // Seen on a device: the service ran, no trigger was enabled, and the app was never stopped.
        assertTrue(AutoKillOffHint.needed(AppPolicy.STRATEGY_IMMEDIATE, true, false, 0));
        assertTrue(AutoKillOffHint.needed(AppPolicy.STRATEGY_IMMEDIATE, false, true, 0));
    }

    @Test
    public void nothingIsSaidWhenTheChoiceTakesEffect() {
        assertFalse(AutoKillOffHint.needed(AppPolicy.STRATEGY_IMMEDIATE, true, true, 0));
        // An active Automation Schedule brings its own Auto-Kill settings.
        assertFalse(AutoKillOffHint.needed(AppPolicy.STRATEGY_IMMEDIATE, false, false, 2));
        // The other strategies do not depend on Auto-Kill.
        for (int strategy : new int[] {AppPolicy.STRATEGY_UNMANAGED,
                AppPolicy.STRATEGY_PROTECTED, AppPolicy.STRATEGY_SMART}) {
            assertFalse(AutoKillOffHint.needed(strategy, false, false, 0));
        }
    }

    @Test
    public void everyAutoKillTriggerCounts() {
        assertEquals(11, AutoKillOffHint.TRIGGER_KEYS.length);
        assertTrue(java.util.Arrays.asList(AutoKillOffHint.TRIGGER_KEYS).containsAll(java.util.Arrays.asList(
                "periodicKillEnabled", "killOnScreenOff", "ramThresholdEnabled", "app_launch_trigger_enabled")));
    }

    @Test
    public void everyPlaceThatSavesAStrategyShowsTheHint() throws Exception {
        assertTrue(read("MainActivity.java").contains(
                "if (!AutoKillOffHint.show(MainActivity.this, strategy, null)) {"));
        assertTrue(read("AppResourceDetailActivity.java").contains(
                "if (!AutoKillOffHint.show(AppResourceDetailActivity.this, chosen, null)) {"));
        // The editor closes only after the hint was read.
        assertTrue(read("AppPolicyEditorActivity.java").contains(
                "AutoKillOffHint.show(this, saved.strategy, this::finish);"));
        // The hint reads the same two settings the Auto-Kill pass checks.
        String hint = read("AutoKillOffHint.java");
        assertTrue(hint.contains("prefs.getBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false)"));
        assertTrue(hint.contains("prefs.getInt(PreferenceKeys.KEY_ACTIVE_PRESET, 0)"));
    }

    private static String read(String file) throws Exception {
        String relative = "app/src/main/java/com/gree1d/reappzuku/ui/" + file;
        for (Path candidate : Arrays.asList(Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative)).replace("\r\n", "\n");
            }
        }
        throw new java.io.IOException("Could not locate repository root");
    }
}
