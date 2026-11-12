package com.gree1d.reappzuku.core;

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
 *
 * The gentlest step is the default: put the app on standby once it has been
 * idle, and never stop it. Stopping an app can break alarms and message
 * delivery, and no measurement here can rule that out, so anything firmer is
 * the user's choice when applying.
 */
public final class SuggestionEngine {
    /** The app keeps coming back after being stopped. */
    public static final int REASON_RESTARTS = 1;
    /** The app has not been opened for a long time and still uses battery. */
    public static final int REASON_UNUSED = 2;
    /** The app has not been opened for days and is one of the biggest battery users. */
    public static final int REASON_HEAVY = 3;
    /** The app has not been opened for days and keeps a sensor registered in the background. */
    public static final int REASON_SENSOR = 4;
    /** The app has not been opened for days and still works in the background every day. */
    public static final int REASON_BACKGROUND = 5;

    public static final int WINDOW_DAYS = 7;
    public static final int MAX_SUGGESTIONS = 10;

    static final int MIN_DAYS_WITH_DATA = 3;
    /** Below this much battery drain in the window the shares say little. */
    static final double MIN_DRAIN_PCT = 20;
    static final int MIN_KILLS = 5;
    static final double MIN_RESTART_RATIO = 0.5;
    static final int UNUSED_DAYS = 14;
    static final double UNUSED_MIN_SHARE = 0.02;
    static final int HEAVY_IDLE_DAYS = 3;
    static final double HEAVY_MIN_SHARE = 0.10;
    /** Wakelock or job time per day that is worth a look for an app nobody opens. */
    static final double MIN_BACKGROUND_MINUTES_PER_DAY = 2;

    private SuggestionEngine() {
    }

    /** What is known about one app over the window. */
    public static final class AppFacts {
        public final String packageName;
        /** An {@link AppPolicyListState} status. */
        public final int status;
        /** Part of all power use attributed to apps that went to this app, 0 to 1. */
        public final double batteryShare;
        /** Stops and restarts since the app's current policy took effect. */
        public final int kills;
        public final int relaunches;
        /** Whole days since the app was last opened, or -1 if unknown. */
        public final int daysSinceUsed;
        /** Minutes per day the system counted wakelocks or jobs for the app since the last charge. */
        public final double backgroundMinutesPerDay;
        /** The app has a sensor registered right now. */
        public final boolean holdsSensor;

        public AppFacts(String packageName, int status, double batteryShare,
                int kills, int relaunches, int daysSinceUsed) {
            this(packageName, status, batteryShare, kills, relaunches, daysSinceUsed, 0, false);
        }

        public AppFacts(String packageName, int status, double batteryShare,
                int kills, int relaunches, int daysSinceUsed,
                double backgroundMinutesPerDay, boolean holdsSensor) {
            this.packageName = packageName;
            this.status = status;
            this.batteryShare = batteryShare;
            this.kills = kills;
            this.relaunches = relaunches;
            this.daysSinceUsed = daysSinceUsed;
            this.backgroundMinutesPerDay = backgroundMinutesPerDay;
            this.holdsSensor = holdsSensor;
        }
    }

    public static final class Suggestion {
        public final String packageName;
        public final int reason;
        public final AppFacts facts;

        Suggestion(AppFacts facts, int reason) {
            this.packageName = facts.packageName;
            this.reason = reason;
            this.facts = facts;
        }

        /** Identifies this recommendation for the dismissed list. */
        public String key() {
            return packageName + "|" + reason;
        }
    }

    /** The daily rows of the window, per app. */
    public static final class Usage {
        private final Map<String, List<DailyAppUsage>> rows = new HashMap<>();
        private final Set<Integer> daysWithBattery = new HashSet<>();
        private final double drainPct;
        private double totalBattery;

        /** @param drainPct battery percent points the device lost in the window */
        public Usage(List<DailyAppUsage> appRows, double drainPct) {
            this.drainPct = drainPct;
            for (DailyAppUsage row : appRows) {
                rows.computeIfAbsent(row.packageName, k -> new ArrayList<>()).add(row);
                totalBattery += row.rawBattery;
                if (row.rawBattery > 0) daysWithBattery.add(row.day);
            }
        }

        public Set<String> packages() {
            return rows.keySet();
        }

        /**
         * The app's part of everything attributed to apps, system apps
         * included. 0 until there are enough days and enough drain to compare
         * apps fairly.
         */
        /** Enough days and enough drain to compare apps fairly. */
        public boolean hasEnoughData() {
            return daysWithBattery.size() >= MIN_DAYS_WITH_DATA && drainPct >= MIN_DRAIN_PCT
                    && totalBattery > 0;
        }

        public double batteryShare(String packageName) {
            if (!hasEnoughData()) return 0;
            double sum = 0;
            for (DailyAppUsage row : rowsOf(packageName)) sum += row.rawBattery;
            return sum / totalBattery;
        }

        public int kills(String packageName, int fromDay) {
            int sum = 0;
            for (DailyAppUsage row : rowsOf(packageName)) if (row.day >= fromDay) sum += row.kills;
            return sum;
        }

        public int relaunches(String packageName, int fromDay) {
            int sum = 0;
            for (DailyAppUsage row : rowsOf(packageName)) if (row.day >= fromDay) sum += row.relaunches;
            return sum;
        }

        private List<DailyAppUsage> rowsOf(String packageName) {
            List<DailyAppUsage> list = rows.get(packageName);
            return list == null ? new ArrayList<>() : list;
        }
    }

    /**
     * @param dismissed keys of recommendations the user does not want to see again
     * @return one recommendation per app at most, the most useful first, not capped
     */
    public static List<Suggestion> suggest(List<AppFacts> apps, Set<String> dismissed) {
        List<Suggestion> result = new ArrayList<>();
        for (AppFacts app : apps) {
            for (int reason : reasonsFor(app)) {
                Suggestion suggestion = new Suggestion(app, reason);
                if (!dismissed.contains(suggestion.key())) {
                    result.add(suggestion);
                    break;
                }
            }
        }
        result.sort((a, b) -> Double.compare(weight(b), weight(a)));
        return result;
    }

    /** Every reason that applies to the app, the strongest first. */
    static List<Integer> reasonsFor(AppFacts app) {
        List<Integer> reasons = new ArrayList<>();
        // Stopping an app that restarts itself costs more than leaving it idle.
        if (app.status == AppPolicyListState.STATUS_IMMEDIATE
                && app.kills >= MIN_KILLS
                && app.relaunches >= app.kills * MIN_RESTART_RATIO) {
            reasons.add(REASON_RESTARTS);
        }
        // An app in daily use spends its battery on what the user asked for.
        // Only an app that has not been opened for days is worth a look.
        if (app.status == AppPolicyListState.STATUS_UNMANAGED) {
            if (app.daysSinceUsed >= UNUSED_DAYS && app.batteryShare >= UNUSED_MIN_SHARE) {
                reasons.add(REASON_UNUSED);
            }
            if (app.daysSinceUsed >= HEAVY_IDLE_DAYS && app.batteryShare >= HEAVY_MIN_SHARE) {
                reasons.add(REASON_HEAVY);
            }
            // What the system itself counted needs no days of own measurements.
            if (app.daysSinceUsed >= HEAVY_IDLE_DAYS) {
                if (app.backgroundMinutesPerDay >= MIN_BACKGROUND_MINUTES_PER_DAY) {
                    reasons.add(REASON_BACKGROUND);
                }
                if (app.holdsSensor) reasons.add(REASON_SENSOR);
            }
        }
        return reasons;
    }

    private static double weight(Suggestion suggestion) {
        // A restart loop is certain waste; the others are ranked by how much is at stake.
        switch (suggestion.reason) {
            case REASON_RESTARTS:
                return 1 + suggestion.facts.relaunches;
            case REASON_BACKGROUND:
            case REASON_SENSOR:
                // Below every restart loop, and comparable with a battery share:
                // an hour per day counts like the whole measured power use.
                return Math.min(1, suggestion.facts.backgroundMinutesPerDay / 60
                        + (suggestion.facts.holdsSensor ? 0.05 : 0));
            default:
                return suggestion.facts.batteryShare;
        }
    }
}
