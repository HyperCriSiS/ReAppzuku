package com.gree1d.reappzuku.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

/**
 * What one app did on one local calendar day. Kept for 90 days, long after
 * the 24 hour snapshot table has been pruned.
 */
@Entity(
        tableName = "daily_app_usage",
        primaryKeys = {"packageName", "day"},
        indices = {@Index("day")}
)
public class DailyAppUsage {
    @NonNull
    public String packageName = "";

    /** Local calendar day as yyyyMMdd. */
    public int day;

    /**
     * Sum of the raw batterystats power deltas. The unit differs between
     * devices, so this is only meaningful as a share of the day's total.
     */
    public double rawBattery;

    public long cpuMs;

    public int kills;

    public int relaunches;
}
