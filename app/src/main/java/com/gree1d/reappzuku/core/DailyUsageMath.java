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
     * The increase of a cumulative counter between two snapshots. A lower
     * current value means the counter was reset in between; what it counted
     * since the reset is then all that is known.
     */
    public static double counterDelta(double previous, double current) {
        return current >= previous ? current - previous : current;
    }

    public static long counterDelta(long previous, long current) {
        return current >= previous ? current - previous : current;
    }

    /** Battery percent points lost between two snapshots; charging and unknown levels count as none. */
    public static double levelDrop(int previousLevel, int currentLevel) {
        if (previousLevel <= 0 || currentLevel <= 0) return 0;
        return Math.max(0, previousLevel - currentLevel);
    }
}
