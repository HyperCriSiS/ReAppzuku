package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecommendationEffectTest {
    private static final TimeZone BERLIN = TimeZone.getTimeZone("Europe/Berlin");

    private static long at(int year, int month, int day, int hour) {
        Calendar calendar = Calendar.getInstance(BERLIN);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, 0);
        return calendar.getTimeInMillis();
    }

    @Test
    public void aDropOfAtLeastAThirdCountsAsHelping() {
        assertEquals(RecommendationEffect.VERDICT_HELPED, RecommendationEffect.verdict(0.30, 0.21, true));
        assertEquals(RecommendationEffect.VERDICT_HELPED, RecommendationEffect.verdict(0.30, 0.0, true));
        assertEquals(RecommendationEffect.VERDICT_NO_CLEAR_EFFECT,
                RecommendationEffect.verdict(0.30, 0.22, true));
        assertEquals(RecommendationEffect.VERDICT_NO_CLEAR_EFFECT,
                RecommendationEffect.verdict(0.30, 0.40, true));
    }

    @Test
    public void withoutDataOrABaselineNothingIsClaimed() {
        assertEquals(RecommendationEffect.VERDICT_UNKNOWN, RecommendationEffect.verdict(0.30, 0.10, false));
        // An app recommended for its restarts may have had no measured battery share before.
        assertEquals(RecommendationEffect.VERDICT_UNKNOWN, RecommendationEffect.verdict(0.0, 0.0, true));
    }

    @Test
    public void theAfterWeekMustBeCompleteBeforeMeasuring() {
        long applied = at(2026, 11, 3, 18);
        // Applied on the 3rd: the after week is the 4th to the 10th.
        assertFalse(RecommendationEffect.isReady(applied, at(2026, 11, 10, 23), BERLIN));
        assertTrue(RecommendationEffect.isReady(applied, at(2026, 11, 11, 0), BERLIN));
        assertFalse(RecommendationEffect.isExpired(applied, at(2026, 12, 10, 12), BERLIN));
        assertTrue(RecommendationEffect.isExpired(applied, at(2026, 12, 11, 12), BERLIN));
    }

    @Test
    public void storedEntriesRoundTripAndMalformedOnesAreRejected() {
        RecommendationEffect.Applied applied = RecommendationEffect.Applied.decode(
                new RecommendationEffect.Applied("com.example.app", 1234L, 3).encode());
        assertEquals("com.example.app", applied.packageName);
        assertEquals(1234L, applied.appliedAt);
        assertEquals(3, applied.previousStatus);
        assertNull(RecommendationEffect.Applied.decode("com.example.app|x|3"));
        assertNull(RecommendationEffect.Applied.decode("com.example.app|1"));
    }

    @Test
    public void undoRestoresTheStrategyTheAppHadBefore() throws Exception {
        // Undo passes the stored list status as a strategy, which only works while the numbers agree.
        assertEquals(com.gree1d.reappzuku.db.AppPolicy.STRATEGY_UNMANAGED, AppPolicyListState.STATUS_UNMANAGED);
        assertEquals(com.gree1d.reappzuku.db.AppPolicy.STRATEGY_PROTECTED, AppPolicyListState.STATUS_PROTECTED);
        assertEquals(com.gree1d.reappzuku.db.AppPolicy.STRATEGY_SMART, AppPolicyListState.STATUS_SMART);
        assertEquals(com.gree1d.reappzuku.db.AppPolicy.STRATEGY_IMMEDIATE, AppPolicyListState.STATUS_IMMEDIATE);

        String screen = read("app/src/main/java/com/gree1d/reappzuku/ui/RecommendationsActivity.java");
        assertTrue(screen.contains("SuggestionLoader.apply(getApplicationContext(), suggestion, level);"));
        String loader = read("app/src/main/java/com/gree1d/reappzuku/core/SuggestionLoader.java");
        int record = loader.indexOf("RecommendationEffect.recordApplied(context,");
        assertTrue(record >= 0 && record < loader.indexOf("AppPolicyQuickChoice.applyPreset(context,"));
        assertTrue(screen.contains("effect.verdict == RecommendationEffect.VERDICT_NO_CLEAR_EFFECT"));
    }

    @Test
    public void everyLocaleHasTheSameStringsWithTheSamePlaceholders() throws Exception {
        List<String> expected = entries(read("app/src/main/res/values/strings_recommendation_effects.xml"));
        assertEquals(6, expected.size());
        for (String locale : new String[] {"es", "ru", "uk", "zh-rCN"}) {
            assertEquals(locale, expected, entries(read(
                    "app/src/main/res/values-" + locale + "/strings_recommendation_effects.xml")));
        }
    }

    private static List<String> entries(String xml) {
        List<String> entries = new ArrayList<>();
        Matcher string = Pattern.compile("<string name=\"([^\"]+)\">(.*?)</string>").matcher(xml);
        while (string.find()) {
            TreeSet<String> placeholders = new TreeSet<>();
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
