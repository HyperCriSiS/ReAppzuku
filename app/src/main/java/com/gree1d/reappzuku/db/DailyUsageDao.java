package com.gree1d.reappzuku.db;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface DailyUsageDao {

    @Query("INSERT OR IGNORE INTO daily_app_usage "
            + "(packageName, day, rawBattery, cpuMs, kills, relaunches) "
            + "VALUES (:packageName, :day, 0, 0, 0, 0)")
    void ensureAppRow(String packageName, int day);

    @Query("UPDATE daily_app_usage SET rawBattery = rawBattery + :rawBattery, "
            + "cpuMs = cpuMs + :cpuMs, kills = kills + :kills, relaunches = relaunches + :relaunches "
            + "WHERE packageName = :packageName AND day = :day")
    void addToAppRow(String packageName, int day, double rawBattery, long cpuMs,
            int kills, int relaunches);

    /** Adds the given amounts to the app's row for the day, creating the row if needed. */
    @Transaction
    default void accumulate(String packageName, int day, double rawBattery, long cpuMs,
            int kills, int relaunches) {
        ensureAppRow(packageName, day);
        addToAppRow(packageName, day, rawBattery, cpuMs, kills, relaunches);
    }

    @Query("INSERT OR IGNORE INTO daily_device_usage (day, drainPct) VALUES (:day, 0)")
    void ensureDeviceRow(int day);

    @Query("UPDATE daily_device_usage SET drainPct = drainPct + :drainPct WHERE day = :day")
    void addToDeviceRow(int day, double drainPct);

    @Transaction
    default void accumulateDrain(int day, double drainPct) {
        ensureDeviceRow(day);
        addToDeviceRow(day, drainPct);
    }

    @Query("SELECT * FROM daily_app_usage WHERE day >= :fromDay ORDER BY day, packageName")
    List<DailyAppUsage> getAppUsageSince(int fromDay);

    @Query("SELECT * FROM daily_device_usage WHERE day >= :fromDay ORDER BY day")
    List<DailyDeviceUsage> getDeviceUsageSince(int fromDay);

    @Query("DELETE FROM daily_app_usage WHERE day < :cutoffDay")
    void deleteAppUsageBefore(int cutoffDay);

    @Query("DELETE FROM daily_device_usage WHERE day < :cutoffDay")
    void deleteDeviceUsageBefore(int cutoffDay);
}
