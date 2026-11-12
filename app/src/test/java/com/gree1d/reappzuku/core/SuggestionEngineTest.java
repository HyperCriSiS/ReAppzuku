package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.core.SuggestionEngine.AppFacts;
import com.gree1d.reappzuku.core.SuggestionEngine.Suggestion;
import com.gree1d.reappzuku.db.DailyAppUsage;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SuggestionEngineTest {
    private static final int UNMANAGED = AppPolicyListState.STATUS_UNMANAGED;
    private static final int IMMEDIATE = AppPolicyListState.STATUS_IMMEDIATE;
    private static final int RESTARTS = SuggestionEngine.REASON_RESTARTS;
    private static final int UNUSED = SuggestionEngine.REASON_UNUSED;
    private static final int HEAVY = SuggestionEngine.REASON_HEAVY;

    private static AppFacts app(String pkg, int status, double share, int kills, int relaunches,
            int daysSinceUsed) {
        return new AppFacts(pkg, status, share, kills, relaunches, daysSinceUsed);
    }

    private static List<Integer> reasons(int status, double share, int kills, int relaunches,
            int daysSinceUsed) {
        return SuggestionEngine.reasonsFor(app("a", status, share, kills, relaunches, daysSinceUsed));
    }

    private static DailyAppUsage row(String pkg, int day, double battery, int kills, int relaunches) {
        DailyAppUsage row = new DailyAppUsage();
        row.packageName = pkg;
        row.day = day;
        row.rawBattery = battery;
        row.kills = kills;
        row.relaunches = relaunches;
        return row;
    }

    @Test
    public void appThatKeepsRestartingAfterStopsIsFlagged() {
        assertEquals(Collections.singletonList(RESTARTS), reasons(IMMEDIATE, 0, 10, 6, 0));
        assertEquals(Collections.singletonList(RESTARTS), reasons(IMMEDIATE, 0, 5, 3, 0));
        assertEquals(Collections.singletonList(RESTARTS), reasons(IMMEDIATE, 0, 6, 3, 0));
    }

    @Test
    public void fewStopsOrRareRestartsAreNotALoop() {
        assertTrue(reasons(IMMEDIATE, 0, 4, 4, 0).isEmpty());
        assertTrue(reasons(IMMEDIATE, 0, 5, 2, 0).isEmpty());
        // Restarts of an app that is not being stopped by policy are not a policy problem.
        assertTrue(reasons(UNMANAGED, 0, 10, 10, 0).isEmpty());
    }

    @Test
    public void appInDailyUseIsNeverFlaggedForItsBatteryUse() {
        // Its battery goes to what the user asked for; limiting it promises a saving it cannot deliver.
        assertTrue(reasons(UNMANAGED, 0.60, 0, 0, 0).isEmpty());
        assertTrue(reasons(UNMANAGED, 0.60, 0, 0, 2).isEmpty());
    }

    @Test
    public void appNotOpenedForDaysAndAmongTheBiggestUsersIsFlagged() {
        assertEquals(Collections.singletonList(HEAVY), reasons(UNMANAGED, 0.10, 0, 0, 3));
        assertTrue(reasons(UNMANAGED, 0.09, 0, 0, 3).isEmpty());
    }

    @Test
    public void appNotOpenedForTwoWeeksNeedsOnlyASmallShare() {
        assertEquals(Collections.singletonList(UNUSED), reasons(UNMANAGED, 0.02, 0, 0, 14));
        assertTrue(reasons(UNMANAGED, 0.019, 0, 0, 14).isEmpty());
        assertTrue(reasons(UNMANAGED, 0.02, 0, 0, 13).isEmpty());
        assertEquals(Arrays.asList(UNUSED, HEAVY), reasons(UNMANAGED, 0.30, 0, 0, 20));
    }

    @Test
    public void withoutUsageDataNothingIsClaimedAboutBatteryUse() {
        assertTrue(reasons(UNMANAGED, 0.90, 0, 0, -1).isEmpty());
    }

    @Test
    public void managedProtectedAndPendingAppsAreLeftAlone() {
        for (int status : new int[] {AppPolicyListState.STATUS_PROTECTED,
                AppPolicyListState.STATUS_SMART, AppPolicyListState.STATUS_NEEDS_SETUP}) {
            assertTrue(reasons(status, 0.5, 20, 20, 30).isEmpty());
        }
        assertTrue(reasons(IMMEDIATE, 0.5, 0, 0, 30).isEmpty());
    }

    @Test
    public void dismissingOneReasonLeavesTheOtherAndDismissingBothHidesTheApp() {
        List<AppFacts> apps = Collections.singletonList(app("a", UNMANAGED, 0.3, 0, 0, 20));
        assertEquals(UNUSED, SuggestionEngine.suggest(apps, Collections.emptySet()).get(0).reason);
        assertEquals(HEAVY, SuggestionEngine.suggest(
                apps, Collections.singleton("a|" + UNUSED)).get(0).reason);
        assertEquals(UNUSED, SuggestionEngine.suggest(
                apps, Collections.singleton("a|" + HEAVY)).get(0).reason);
        assertTrue(SuggestionEngine.suggest(
                apps, new TreeSet<>(Arrays.asList("a|" + UNUSED, "a|" + HEAVY))).isEmpty());
        assertEquals(1, SuggestionEngine.suggest(apps, Collections.emptySet()).size());
    }

    @Test
    public void restartLoopsComeFirstThenTheLargestBatteryShare() {
        List<Suggestion> result = SuggestionEngine.suggest(Arrays.asList(
                app("small", UNMANAGED, 0.12, 0, 0, 5),
                app("big", UNMANAGED, 0.40, 0, 0, 5),
                app("loop", IMMEDIATE, 0, 8, 8, 0)), Collections.emptySet());
        assertEquals(Arrays.asList("loop", "big", "small"),
                Arrays.asList(result.get(0).packageName, result.get(1).packageName,
                        result.get(2).packageName));
    }

    @Test
    public void batteryShareNeedsSeveralDaysAndRealDrain() {
        List<DailyAppUsage> twoDays = Arrays.asList(
                row("a", 20261109, 30, 0, 0), row("b", 20261109, 70, 0, 0),
                row("a", 20261110, 30, 0, 0));
        assertEquals(0.0, new SuggestionEngine.Usage(twoDays, 50).batteryShare("a"), 0.0);

        List<DailyAppUsage> threeDays = Arrays.asList(
                row("a", 20261108, 10, 0, 0), row("b", 20261108, 40, 0, 0),
                row("a", 20261109, 10, 0, 0), row("b", 20261109, 20, 0, 0),
                row("a", 20261110, 5, 0, 0), row("b", 20261110, 15, 0, 0));
        SuggestionEngine.Usage usage = new SuggestionEngine.Usage(threeDays, 50);
        assertEquals(0.25, usage.batteryShare("a"), 1e-9);
        assertEquals(0.75, usage.batteryShare("b"), 1e-9);
        assertEquals(0.0, usage.batteryShare("unknown"), 0.0);
        // A device that barely lost charge gives one app 100 percent of almost nothing.
        assertEquals(0.0, new SuggestionEngine.Usage(threeDays, 19.9).batteryShare("a"), 0.0);
        assertEquals(0.25, new SuggestionEngine.Usage(threeDays, 20).batteryShare("a"), 1e-9);
    }

    @Test
    public void severalRowsOfOneDayCountAsOneDayAndStopOnlyRowsAsNone() {
        List<DailyAppUsage> rows = Arrays.asList(
                row("a", 20261108, 10, 0, 0), row("b", 20261108, 10, 0, 0),
                row("c", 20261108, 10, 0, 0), row("a", 20261109, 10, 0, 0),
                row("a", 20261110, 0, 3, 1));
        assertEquals(0.0, new SuggestionEngine.Usage(rows, 50).batteryShare("a"), 0.0);
    }

    @Test
    public void stopsAreCountedFromTheGivenDayOnly() {
        SuggestionEngine.Usage usage = new SuggestionEngine.Usage(Arrays.asList(
                row("a", 20261107, 0, 4, 4), row("a", 20261109, 0, 2, 1),
                row("a", 20261110, 0, 1, 0)), 0);
        assertEquals(7, usage.kills("a", 20261104));
        assertEquals(5, usage.relaunches("a", 20261104));
        // The policy changed on the 9th: what happened before says nothing about it.
        assertEquals(3, usage.kills("a", 20261109));
        assertEquals(1, usage.relaunches("a", 20261109));
        assertTrue(usage.packages().contains("a"));
    }

    @Test
    public void daysSinceLastOpenedHandlesUnknownAndFutureValues() {
        long now = 100L * 24 * 60 * 60 * 1000;
        assertEquals(-1, SuggestionLoader.daysSince(null, now));
        assertEquals(-1, SuggestionLoader.daysSince(0L, now));
        assertEquals(0, SuggestionLoader.daysSince(now - 1000, now));
        assertEquals(14, SuggestionLoader.daysSince(now - 14L * 24 * 60 * 60 * 1000, now));
        assertEquals(0, SuggestionLoader.daysSince(now + 5000, now));
    }

    @Test
    public void applyingARecommendationOnlyLeadsToAForceStopAtTheStrictLevel() throws Exception {
        assertEquals(PolicyPresetSeeder.PRESET_MESSENGER, SuggestionLoader.APPLIED_PRESET);
        String seeder = read("app/src/main/java/com/gree1d/reappzuku/core/PolicyPresetSeeder.java");
        int start = seeder.indexOf("preset(PRESET_MESSENGER,");
        String preset = seeder.substring(start, seeder.indexOf("result.add(messenger);", start));
        assertTrue(preset.contains("messenger.forceStopDelayMs = Long.MAX_VALUE;"));
        assertTrue(preset.contains("messenger.bootCleanup = false;"));
        String screen = read("app/src/main/java/com/gree1d/reappzuku/ui/RecommendationsActivity.java");
        // The screen passes the level the user chose; the two lower levels use the preset above.
        assertTrue(screen.contains("SuggestionLoader.apply(getApplicationContext(), suggestion, level);"));
        assertEquals(SuggestionLoader.APPLIED_PRESET, SuggestionLoader.presetFor(SuggestionLoader.LEVEL_GENTLE));
        assertEquals(SuggestionLoader.APPLIED_PRESET, SuggestionLoader.presetFor(SuggestionLoader.LEVEL_BALANCED));
        // A second tap while the change runs must not repeat it.
        assertTrue(screen.contains("apply.setEnabled(false);"));
    }

    @Test
    public void loaderCannotCrashAScreenAndAsksTheSystemOnlyAboutCandidates() throws Exception {
        String loader = read("app/src/main/java/com/gree1d/reappzuku/core/SuggestionLoader.java");
        assertTrue(loader.contains("} catch (RuntimeException e) {"));
        assertTrue(loader.indexOf("SuggestionEngine.suggest(facts, dismissed)")
                < loader.indexOf("isManageable(context, packageManager, suggestion.packageName"));
        assertFalse(loader.contains("ProtectedApps.isProtected(context,"));
        // The stored set is never modified in place; a new set is written.
        assertTrue(loader.contains("putStringSet(KEY_DISMISSED, entries(active))"));
        String main = read("app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java");
        int resume = main.indexOf("    protected void onResume() {");
        String body = main.substring(resume, main.indexOf("\n    }\n", resume));
        assertTrue(body.lastIndexOf("loadBackgroundApps();") < body.indexOf("updateRecommendationsCard();"));
    }

    @Test
    public void screenIsPrivateAndReachableFromSettingsAndTheMainList() throws Exception {
        String manifest = read("app/src/main/AndroidManifest.xml");
        int entry = manifest.indexOf("com.gree1d.reappzuku.ui.RecommendationsActivity\"");
        assertTrue(entry >= 0);
        assertTrue(manifest.substring(entry, manifest.indexOf("/>", entry))
                .contains("android:exported=\"false\""));
        assertTrue(read("app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivity.java")
                .contains("startActivity(new Intent(this, RecommendationsActivity.class))"));
        assertTrue(read("app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java").contains(
                "binding.recommendationsCard.setVisibility(count > 0 ? View.VISIBLE : View.GONE);"));
    }

    @Test
    public void everyLocaleHasTheSameStringsWithTheSamePlaceholders() throws Exception {
        List<String> expected = entries(read("app/src/main/res/values/strings_recommendations.xml"));
        assertEquals(17, expected.size());
        for (String locale : new String[] {"es", "ru", "uk", "zh-rCN"}) {
            assertEquals(locale, expected, entries(read(
                    "app/src/main/res/values-" + locale + "/strings_recommendations.xml")));
        }
    }

    /** Each string as "name:placeholders", so a locale that drops a format argument is caught. */
    private static List<String> entries(String xml) {
        List<String> entries = new ArrayList<>();
        Matcher string = Pattern.compile("<string name=\"([^\"]+)\">(.*?)</string>").matcher(xml);
        while (string.find()) {
            Set<String> placeholders = new TreeSet<>();
            Matcher placeholder = Pattern.compile("%\\d\\$d|%%").matcher(string.group(2));
            while (placeholder.find()) placeholders.add(placeholder.group());
            entries.add(string.group(1) + ":" + placeholders);
        }
        Collections.sort(entries);
        return entries;
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
