package com.gree1d.reappzuku.core;

import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.DailyAppUsage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns what the device measured about each app into recommendations. Pure
 * rules over plain numbers: nothing here touches Android, the database or a
 * shell, and nothing is changed until the user applies a recommendation.
 */
public final class SuggestionEngine {
    /** The app keeps coming back after being stopped. */
    public static final int REASON_RESTARTS = 1;
    /** The app has not been opened for a long time and still uses battery. */
    public static final int REASON_UNUSED = 2;
    /** The app is one of the biggest battery users and nothing manages it. */
    public static final int REASON_HEAVY = 3;

    public static final int WINDOW_DAYS = 7;
    public static final int MAX_SUGGESTIONS = 10;

    static final int MIN_DAYS_WITH_DATA = 3;
    static final int MIN_KILLS = 5;
    static final double MIN_RESTART_RATIO = 0.5;
    static final int UNUSED_DAYS = 14;
    static final double UNUSED_MIN_SHARE = 0.02;
    static final double HEAVY_MIN_SHARE = 0.10;

    private SuggestionEngine() {
    }

    /** What is known about one app over the window. */
    public static final class AppFacts {
        public final String packageName;
        /** An {@link AppPolicyListState} status. */
        public final int status;
        /** Part of all measured battery use that went to this app, 0 to 1. */
        public final double batteryShare;
        public final int kills;
        public final int relaunches;
        /** Whole days since the app was last opened, or -1 if unknown. */
        public final int daysSinceUsed;

        public AppFacts(String packageName, int status, double batteryShare,
                int kills, int relaunches, int daysSinceUsed) {
            this.packageName = packageName;
            this.status = status;
            this.batteryShare = batteryShare;
            this.kills = kills;
            this.relaunches = relaunches;
            this.daysSinceUsed = daysSinceUsed;
        }
    }

    public static final class Suggestion {
        public final String packageName;
        public final int reason;
        /** The {@link AppPolicy} strategy the user is offered. */
        public final int strategy;
        public final AppFacts facts;

        Suggestion(AppFacts facts, int reason, int strategy) {
            this.packageName = facts.packageName;
            this.reason = reason;
            this.strategy = strategy;
            this.facts = facts;
        }

        /** Identifies this recommendation for the dismissed list. */
        public String key() {
            return packageName + "|" + reason;
        }
    }

    /** Sums the daily rows of the window per app. */
    public static final class Usage {
        final Map<String, double[]> battery = new HashMap<>();
        final Map<String, int[]> counts = new HashMap<>();
        final Set<Integer> daysWithBattery = new HashSet<>();
        double totalBattery;

        public Usage(List<DailyAppUsage> rows) {
            for (DailyAppUsage row : rows) {
                battery.computeIfAbsent(row.packageName, k -> new double[1])[0] += row.rawBattery;
                int[] count = counts.computeIfAbsent(row.packageName, k -> new int[2]);
                count[0] += row.kills;
                count[1] += row.relaunches;
                totalBattery += row.rawBattery;
                if (row.rawBattery > 0) daysWithBattery.add(row.day);
            }
        }

        public Set<String> packages() {
            return counts.keySet();
        }

        /** 0 until enough days have battery data to compare apps fairly. */
        public double batteryShare(String packageName) {
            if (daysWithBattery.size() < MIN_DAYS_WITH_DATA || totalBattery <= 0) return 0;
            double[] value = battery.get(packageName);
            return value == null ? 0 : value[0] / totalBattery;
        }

        public int kills(String packageName) {
            int[] count = counts.get(packageName);
            return count == null ? 0 : count[0];
        }

        public int relaunches(String packageName) {
            int[] count = counts.get(packageName);
            return count == null ? 0 : count[1];
        }
    }

    /**
     * @param dismissed keys of recommendations the user does not want to see again
     * @return at most {@link #MAX_SUGGESTIONS}, the most useful first, one per app
     */
    public static List<Suggestion> suggest(List<AppFacts> apps, Set<String> dismissed) {
        List<Suggestion> result = new ArrayList<>();
        for (AppFacts app : apps) {
            Suggestion suggestion = suggestFor(app);
            if (suggestion != null && !dismissed.contains(suggestion.key())) result.add(suggestion);
        }
        result.sort((a, b) -> Double.compare(weight(b), weight(a)));
        return result.size() > MAX_SUGGESTIONS ? new ArrayList<>(result.subList(0, MAX_SUGGESTIONS)) : result;
    }

    static Suggestion suggestFor(AppFacts app) {
        // Stopping an app that restarts itself costs more than leaving it idle.
        // Smart lets it run and only limits it once it has been idle.
        if (app.status == AppPolicyListState.STATUS_IMMEDIATE
                && app.kills >= MIN_KILLS
                && app.relaunches >= app.kills * MIN_RESTART_RATIO) {
            return new Suggestion(app, REASON_RESTARTS, AppPolicy.STRATEGY_SMART);
        }
        if (app.status != AppPolicyListState.STATUS_UNMANAGED) return null;
        if (app.daysSinceUsed >= UNUSED_DAYS && app.batteryShare >= UNUSED_MIN_SHARE) {
            return new Suggestion(app, REASON_UNUSED, AppPolicy.STRATEGY_SMART);
        }
        if (app.batteryShare >= HEAVY_MIN_SHARE) {
            return new Suggestion(app, REASON_HEAVY, AppPolicy.STRATEGY_SMART);
        }
        return null;
    }

    private static double weight(Suggestion suggestion) {
        // A restart loop is certain waste; the other two are ranked by how much battery is at stake.
        return suggestion.reason == REASON_RESTARTS
                ? 1 + suggestion.facts.relaunches
                : suggestion.facts.batteryShare;
    }
}
