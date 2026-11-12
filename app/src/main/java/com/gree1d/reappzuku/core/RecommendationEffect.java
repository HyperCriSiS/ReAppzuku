package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.WorkerThread;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.DailyDeviceUsage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * What an applied recommendation changed: the app's part of all measured app
 * power use in the week before against the week after. A change without a
 * clear effect is offered for undo.
 */
public final class RecommendationEffect {
    public static final int VERDICT_HELPED = 1;
    public static final int VERDICT_NO_CLEAR_EFFECT = 2;
    public static final int VERDICT_UNKNOWN = 3;

    /** The share must drop by at least this part of its old value to count as an effect. */
    static final double MIN_RELATIVE_DROP = 0.3;
    /** Days after the change before it is measured, plus the day of the change itself. */
    static final int MEASURE_DAYS = SuggestionEngine.WINDOW_DAYS;
    /** An evaluated entry stays this many days after it became ready, then it is dropped. */
    static final int SHOW_DAYS = 30;

    private static final String PREFS = "recommendations";
    private static final String KEY_APPLIED = "applied";

    public final String packageName;
    public final long appliedAt;
    /** The {@link AppPolicyListState} status before the change, which undo restores. */
    public final int previousStatus;
    /** The background restriction level the change set, or -1 if it set none. */
    public final int appliedRestriction;
    public final double shareBefore;
    public final double shareAfter;
    public final int verdict;

    RecommendationEffect(Applied applied, double shareBefore, double shareAfter, int verdict) {
        this.packageName = applied.packageName;
        this.appliedAt = applied.appliedAt;
        this.previousStatus = applied.previousStatus;
        this.appliedRestriction = applied.appliedRestriction;
        this.shareBefore = shareBefore;
        this.shareAfter = shareAfter;
        this.verdict = verdict;
    }

    static int verdict(double before, double after, boolean known) {
        if (!known || before <= 0) return VERDICT_UNKNOWN;
        return after <= before * (1 - MIN_RELATIVE_DROP) ? VERDICT_HELPED : VERDICT_NO_CLEAR_EFFECT;
    }

    /** One applied recommendation as stored. */
    static final class Applied {
        final String packageName;
        final long appliedAt;
        final int previousStatus;
        /** The restriction level the change set; -1 if none, as in entries of earlier versions. */
        final int appliedRestriction;

        Applied(String packageName, long appliedAt, int previousStatus) {
            this(packageName, appliedAt, previousStatus, -1);
        }

        Applied(String packageName, long appliedAt, int previousStatus, int appliedRestriction) {
            this.packageName = packageName;
            this.appliedAt = appliedAt;
            this.previousStatus = previousStatus;
            this.appliedRestriction = appliedRestriction;
        }

        String encode() {
            return packageName + "|" + appliedAt + "|" + previousStatus
                    + (appliedRestriction < 0 ? "" : "|" + appliedRestriction);
        }

        /** @return null for a malformed entry */
        static Applied decode(String value) {
            String[] parts = value.split("\\|");
            if (parts.length != 3 && parts.length != 4) return null;
            try {
                return new Applied(parts[0], Long.parseLong(parts[1]), Integer.parseInt(parts[2]),
                        parts.length == 4 ? Integer.parseInt(parts[3]) : -1);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** The day the after window ends is the last day that has to be complete. */
    /**
     * Undo lifts a restriction only if the recommendation set it and it is
     * still the level in place. One the user changed by hand since is theirs.
     */
    public static boolean shouldLiftRestriction(int appliedRestriction, int currentRestriction) {
        return appliedRestriction > 0 && appliedRestriction == currentRestriction;
    }

    static boolean isReady(long appliedAt, long now, TimeZone zone) {
        return DailyUsageMath.dayKey(now, zone) > DailyUsageMath.dayKey(appliedAt, zone, -MEASURE_DAYS);
    }

    static boolean isExpired(long appliedAt, long now, TimeZone zone) {
        return DailyUsageMath.dayKey(now, zone)
                > DailyUsageMath.dayKey(appliedAt, zone, -(MEASURE_DAYS + SHOW_DAYS));
    }

    public static void recordApplied(Context context, String packageName, int previousStatus,
            int appliedRestriction) {
        Set<String> entries = new HashSet<>(stored(context));
        // A newer change of the same app replaces the older measurement.
        entries.removeIf(entry -> entry.startsWith(packageName + "|"));
        entries.add(new Applied(packageName, System.currentTimeMillis(), previousStatus,
                appliedRestriction).encode());
        prefs(context).edit().putStringSet(KEY_APPLIED, entries).apply();
    }

    public static void forget(Context context, String packageName) {
        Set<String> entries = new HashSet<>(stored(context));
        entries.removeIf(entry -> entry.startsWith(packageName + "|"));
        prefs(context).edit().putStringSet(KEY_APPLIED, entries).apply();
    }

    /** Evaluated changes whose after week is complete, newest first. */
    @WorkerThread
    public static List<RecommendationEffect> loadReady(Context context) {
        try {
            return loadReadyUnguarded(context);
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    private static List<RecommendationEffect> loadReadyUnguarded(Context context) {
        long now = System.currentTimeMillis();
        TimeZone zone = TimeZone.getDefault();
        AppDatabase database = AppDatabase.getInstance(context);
        List<RecommendationEffect> result = new ArrayList<>();
        Set<String> expired = new HashSet<>();
        for (String value : stored(context)) {
            Applied applied = Applied.decode(value);
            if (applied == null || isExpired(applied.appliedAt, now, zone)) {
                expired.add(value);
                continue;
            }
            if (!isReady(applied.appliedAt, now, zone)) continue;
            SuggestionEngine.Usage before = window(database,
                    DailyUsageMath.dayKey(applied.appliedAt, zone, MEASURE_DAYS),
                    DailyUsageMath.dayKey(applied.appliedAt, zone, 1));
            SuggestionEngine.Usage after = window(database,
                    DailyUsageMath.dayKey(applied.appliedAt, zone, -1),
                    DailyUsageMath.dayKey(applied.appliedAt, zone, -MEASURE_DAYS));
            double shareBefore = before.batteryShare(applied.packageName);
            double shareAfter = after.batteryShare(applied.packageName);
            result.add(new RecommendationEffect(applied, shareBefore, shareAfter,
                    verdict(shareBefore, shareAfter, before.hasEnoughData() && after.hasEnoughData())));
        }
        if (!expired.isEmpty()) {
            Set<String> kept = new HashSet<>(stored(context));
            kept.removeAll(expired);
            prefs(context).edit().putStringSet(KEY_APPLIED, kept).apply();
        }
        result.sort((a, b) -> Long.compare(b.appliedAt, a.appliedAt));
        return result;
    }

    private static SuggestionEngine.Usage window(AppDatabase database, int fromDay, int toDay) {
        double drain = 0;
        for (DailyDeviceUsage day : database.dailyUsageDao().getDeviceUsageBetween(fromDay, toDay)) {
            drain += day.drainPct;
        }
        return new SuggestionEngine.Usage(
                database.dailyUsageDao().getAppUsageBetween(fromDay, toDay), drain);
    }

    private static Set<String> stored(Context context) {
        return prefs(context).getStringSet(KEY_APPLIED, Collections.emptySet());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
