package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.core.SuggestionEngine.AppFacts;
import com.gree1d.reappzuku.core.SuggestionEngine.Suggestion;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.DailyAppUsage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SuggestionEngineTest {
    private static final int UNMANAGED = AppPolicyListState.STATUS_UNMANAGED;
    private static final int IMMEDIATE = AppPolicyListState.STATUS_IMMEDIATE;

    private static AppFacts app(String pkg, int status, double share, int kills, int relaunches,
            int daysSinceUsed) {
        return new AppFacts(pkg, status, share, kills, relaunches, daysSinceUsed);
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
    public void appThatKeepsRestartingAfterKillsIsMovedToSmart() {
        Suggestion suggestion = SuggestionEngine.suggestFor(app("a", IMMEDIATE, 0, 10, 6, 0));
        assertEquals(SuggestionEngine.REASON_RESTARTS, suggestion.reason);
        assertEquals(AppPolicy.STRATEGY_SMART, suggestion.strategy);
    }

    @Test
    public void fewKillsOrRareRestartsAreNotALoop() {
        assertNull(SuggestionEngine.suggestFor(app("a", IMMEDIATE, 0, 4, 4, 0)));
        assertNull(SuggestionEngine.suggestFor(app("a", IMMEDIATE, 0, 10, 4, 0)));
        // Restarts of an app that is not being stopped by policy are not this app's doing.
        assertNull(SuggestionEngine.suggestFor(app("a", UNMANAGED, 0, 10, 10, 0)));
    }

    @Test
    public void unusedAppThatStillUsesBatteryIsSuggested() {
        Suggestion suggestion = SuggestionEngine.suggestFor(app("a", UNMANAGED, 0.03, 0, 0, 20));
        assertEquals(SuggestionEngine.REASON_UNUSED, suggestion.reason);
        assertNull(SuggestionEngine.suggestFor(app("a", UNMANAGED, 0.01, 0, 0, 20)));
        assertNull(SuggestionEngine.suggestFor(app("a", UNMANAGED, 0.03, 0, 0, 13)));
        // Without usage data nothing is claimed about when the app was opened.
        assertNull(SuggestionEngine.suggestFor(app("a", UNMANAGED, 0.03, 0, 0, -1)));
    }

    @Test
    public void biggestUnmanagedBatteryUsersAreSuggested() {
        assertEquals(SuggestionEngine.REASON_HEAVY,
                SuggestionEngine.suggestFor(app("a", UNMANAGED, 0.10, 0, 0, 0)).reason);
        assertNull(SuggestionEngine.suggestFor(app("a", UNMANAGED, 0.09, 0, 0, 0)));
    }

    @Test
    public void managedProtectedAndPendingAppsAreLeftAlone() {
        for (int status : new int[] {AppPolicyListState.STATUS_PROTECTED,
                AppPolicyListState.STATUS_SMART, AppPolicyListState.STATUS_NEEDS_SETUP}) {
            assertNull(SuggestionEngine.suggestFor(app("a", status, 0.5, 20, 20, 30)));
        }
        assertNull(SuggestionEngine.suggestFor(app("a", IMMEDIATE, 0.5, 0, 0, 30)));
    }

    @Test
    public void dismissedRecommendationsStayHiddenButOtherReasonsCanAppear() {
        AppFacts heavy = app("a", UNMANAGED, 0.2, 0, 0, 0);
        String key = SuggestionEngine.suggestFor(heavy).key();
        assertTrue(SuggestionEngine.suggest(
                Collections.singletonList(heavy), Collections.singleton(key)).isEmpty());
        assertEquals(1, SuggestionEngine.suggest(Collections.singletonList(heavy),
                Collections.singleton("a|" + SuggestionEngine.REASON_UNUSED)).size());
    }

    @Test
    public void restartLoopsComeFirstThenTheLargestBatteryShare() {
        List<Suggestion> result = SuggestionEngine.suggest(Arrays.asList(
                app("small", UNMANAGED, 0.12, 0, 0, 0),
                app("big", UNMANAGED, 0.40, 0, 0, 0),
                app("loop", IMMEDIATE, 0, 8, 8, 0)), Collections.emptySet());
        assertEquals(Arrays.asList("loop", "big", "small"),
                Arrays.asList(result.get(0).packageName, result.get(1).packageName,
                        result.get(2).packageName));
    }

    @Test
    public void listIsCapped() {
        List<AppFacts> apps = new ArrayList<>();
        for (int i = 0; i < 25; i++) apps.add(app("p" + i, UNMANAGED, 0.2, 0, 0, 0));
        assertEquals(SuggestionEngine.MAX_SUGGESTIONS,
                SuggestionEngine.suggest(apps, Collections.emptySet()).size());
    }

    @Test
    public void batteryShareNeedsSeveralDaysOfData() {
        SuggestionEngine.Usage twoDays = new SuggestionEngine.Usage(Arrays.asList(
                row("a", 20261109, 30, 0, 0), row("b", 20261109, 70, 0, 0),
                row("a", 20261110, 30, 0, 0)));
        assertEquals(0.0, twoDays.batteryShare("a"), 0.0);

        SuggestionEngine.Usage threeDays = new SuggestionEngine.Usage(Arrays.asList(
                row("a", 20261108, 10, 1, 0), row("b", 20261108, 40, 0, 0),
                row("a", 20261109, 10, 2, 1), row("b", 20261109, 20, 0, 0),
                row("a", 20261110, 5, 0, 0), row("b", 20261110, 15, 0, 0)));
        assertEquals(0.25, threeDays.batteryShare("a"), 1e-9);
        assertEquals(0.75, threeDays.batteryShare("b"), 1e-9);
        assertEquals(0.0, threeDays.batteryShare("unknown"), 0.0);
        assertEquals(3, threeDays.kills("a"));
        assertEquals(1, threeDays.relaunches("a"));
    }

    @Test
    public void killCountsDoNotNeedBatteryData() {
        SuggestionEngine.Usage usage = new SuggestionEngine.Usage(
                Collections.singletonList(row("a", 20261110, 0, 6, 5)));
        assertEquals(6, usage.kills("a"));
        assertTrue(usage.packages().contains("a"));
    }
}
