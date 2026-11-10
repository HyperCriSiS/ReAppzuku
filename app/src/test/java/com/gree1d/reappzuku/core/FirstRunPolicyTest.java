package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FirstRunPolicyTest {

    @Test
    public void setupIsOfferedOnAFreshInstallOnly() {
        assertTrue(FirstRunPolicy.shouldOffer(false, 1_000L, 1_000L));
        // An update keeps a working configuration; nobody is interrupted.
        assertFalse(FirstRunPolicy.shouldOffer(false, 1_000L, 2_000L));
        assertFalse(FirstRunPolicy.shouldOffer(true, 1_000L, 1_000L));
    }

    @Test
    public void askKeepsTheBehaviorWithoutSetup() {
        assertEquals(NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL,
                FirstRunPolicy.newAppMode(FirstRunPolicy.CHOICE_ASK));
    }

    @Test
    public void gentleAppliesThePresetThatNeverStopsAnApp() {
        assertEquals(NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET,
                FirstRunPolicy.newAppMode(FirstRunPolicy.CHOICE_GENTLE));
        assertEquals(PolicyPresetSeeder.PRESET_MESSENGER,
                FirstRunPolicy.defaultPreset(FirstRunPolicy.CHOICE_GENTLE));
        assertEquals(PolicyPresetSeeder.PRESET_BALANCED,
                FirstRunPolicy.defaultPreset(FirstRunPolicy.CHOICE_BALANCED));
    }

    @Test
    public void screenOpensOnTheStoredState() {
        for (int choice : new int[] {FirstRunPolicy.CHOICE_ASK, FirstRunPolicy.CHOICE_GENTLE,
                FirstRunPolicy.CHOICE_BALANCED}) {
            assertEquals(choice, FirstRunPolicy.choiceFor(
                    FirstRunPolicy.newAppMode(choice), FirstRunPolicy.defaultPreset(choice)));
        }
        // "Leave unmanaged" and presets chosen elsewhere have no option of their own here.
        assertEquals(FirstRunPolicy.CHOICE_ASK, FirstRunPolicy.choiceFor(
                NewAppSetupPolicy.MODE_LEAVE_UNMANAGED, PolicyPresetSeeder.PRESET_BALANCED));
        assertEquals(FirstRunPolicy.CHOICE_BALANCED, FirstRunPolicy.choiceFor(
                NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET, PolicyPresetSeeder.PRESET_AGGRESSIVE));
    }

    @Test
    public void setupNeverOpensByItselfAndNeverSwitchesAutomationOn() throws Exception {
        String main = read("app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java");
        int start = main.indexOf("private void updateFirstRunCard() {");
        String card = main.substring(start, main.indexOf("\n    }\n", start));
        // The only way into the screen from here is the user's tap.
        assertEquals(1, count(main, "new Intent(this, FirstRunActivity.class)"));
        assertTrue(card.contains("binding.firstRunCard.setOnClickListener(v ->"));

        String screen = read("app/src/main/java/com/gree1d/reappzuku/ui/FirstRunActivity.java");
        assertFalse(screen.contains("KEY_AUTO_KILL_ENABLED"));
        assertFalse(screen.contains("ShappkyService"));
        assertTrue(screen.contains("NewAppSetupCoordinator.replayPending(getApplicationContext())"));

        String manifest = read("app/src/main/AndroidManifest.xml");
        int entry = manifest.indexOf("com.gree1d.reappzuku.ui.FirstRunActivity\"");
        assertTrue(manifest.substring(entry, manifest.indexOf("/>", entry))
                .contains("android:exported=\"false\""));
    }

    @Test
    public void everyLocaleHasTheSameSetupStrings() throws Exception {
        List<String> expected = names(read("app/src/main/res/values/strings_first_run.xml"));
        assertEquals(11, expected.size());
        for (String locale : new String[] {"es", "ru", "uk", "zh-rCN"}) {
            assertEquals(locale, expected, names(read(
                    "app/src/main/res/values-" + locale + "/strings_first_run.xml")));
        }
    }

    private static List<String> names(String xml) {
        List<String> names = new ArrayList<>();
        Matcher matcher = Pattern.compile("<string name=\"([^\"]+)\"").matcher(xml);
        while (matcher.find()) names.add(matcher.group(1));
        Collections.sort(names);
        return names;
    }

    private static int count(String value, String needle) {
        int count = 0;
        for (int at = value.indexOf(needle); at >= 0; at = value.indexOf(needle, at + 1)) count++;
        return count;
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
