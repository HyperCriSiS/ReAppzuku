package com.gree1d.reappzuku.core;

import java.util.Calendar;
import java.util.TimeZone;

/** Pure arithmetic of the long-term usage history. */
public final class DailyUsageMath {
    public static final int RETENTION_DAYS = 90;

    private DailyUsageMath() {
    }

    /** The local calendar day of a moment as yyyyMMdd. */
    public static int dayKey(long timeMs, TimeZone zone) {
        return dayKey(timeMs, zone, 0);
    }

    /**
     * The day {@code daysBack} calendar days before the moment. Calendar
     * arithmetic, so a daylight saving change does not shift the result.
     */
    public static int dayKey(long timeMs, TimeZone zone, int daysBack) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(timeMs);
        calendar.add(Calendar.DAY_OF_YEAR, -daysBack);
        return calendar.get(Calendar.YEAR) * 10000
                + (calendar.get(Calendar.MONTH) + 1) * 100
                + calendar.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * What one app added to a cumulative counter between two snapshot batches.
     *
     * @param previous the app's value in the previous batch, or null if it had no row there
     * @param reset    the counters of the whole batch started over in between,
     *                 so the current value is what was counted since
     * @param cap      the most the app can have added in that time; anything
     *                 above it is an artifact and must not be booked
     */
    public static double usageDelta(Double previous, double current, boolean reset, double cap) {
        double delta;
        if (reset || previous == null) {
            delta = current;
        } else {
            // A single app's value going down without a batch reset is a
            // re-attribution, for example between apps of one shared UID.
            delta = Math.max(0, current - previous);
        }
        return Math.max(0, Math.min(delta, cap));
    }

    /** The batch counters only ever grow, so a lower total means they were reset. */
    public static boolean wasReset(double previousTotal, double currentTotal) {
        return currentTotal < previousTotal;
    }

    /**
     * Whether the time between two batches can be booked at all. A previous
     * batch from the future (clock set back) or more than a day old is only
     * good as the next baseline.
     */
    public static boolean isBookableGap(long previousMs, long nowMs) {
        long gap = nowMs - previousMs;
        return gap > 0 && gap <= 24L * 60 * 60 * 1000;
    }

    /** Battery percent points lost between two snapshots; charging and unknown levels count as none. */
    public static double levelDrop(int previousLevel, int currentLevel) {
        if (previousLevel <= 0 || currentLevel <= 0) return 0;
        return Math.max(0, previousLevel - currentLevel);
    }
}
