package com.gree1d.reappzuku.core;

import com.gree1d.reappzuku.db.DailyAppUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** One app's days out of the long-term history, for the trend chart. */
public final class DailyTrend {
    public static final int DAYS = 30;

    /** One day of the app; share is 0 to 1, or negative when the day has no battery data. */
    public static final class Day {
        public final int day;
        public final double share;
        public final int kills;
        public final int relaunches;

        Day(int day, double share, int kills, int relaunches) {
            this.day = day;
            this.share = share;
            this.kills = kills;
            this.relaunches = relaunches;
        }
    }

    public final List<Day> days;
    public final int kills;
    public final int relaunches;

    private DailyTrend(List<Day> days, int kills, int relaunches) {
        this.days = days;
        this.kills = kills;
        this.relaunches = relaunches;
    }

    /**
     * @param rows every app's rows of the window; the app's daily share is
     *             taken against all apps of the same day, as the recommendations do
     */
    public static DailyTrend of(List<DailyAppUsage> rows, String packageName) {
        Map<Integer, double[]> totals = new TreeMap<>();
        Map<Integer, DailyAppUsage> own = new TreeMap<>();
        for (DailyAppUsage row : rows) {
            totals.computeIfAbsent(row.day, k -> new double[1])[0] += row.rawBattery;
            if (packageName.equals(row.packageName)) own.put(row.day, row);
        }
        List<Day> days = new ArrayList<>();
        int kills = 0;
        int relaunches = 0;
        for (Map.Entry<Integer, double[]> entry : totals.entrySet()) {
            DailyAppUsage row = own.get(entry.getKey());
            double total = entry.getValue()[0];
            double share = total <= 0 ? -1 : (row == null ? 0 : row.rawBattery / total);
            int dayKills = row == null ? 0 : row.kills;
            int dayRelaunches = row == null ? 0 : row.relaunches;
            days.add(new Day(entry.getKey(), share, dayKills, dayRelaunches));
            kills += dayKills;
            relaunches += dayRelaunches;
        }
        return new DailyTrend(days, kills, relaunches);
    }

    /** Whether there is anything to draw. */
    public boolean hasBatteryData() {
        for (Day day : days) if (day.share >= 0) return true;
        return false;
    }
}
