package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.db.DailyAppUsage;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class DailyTrendTest {

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
    public void shareIsTakenAgainstAllAppsOfTheSameDay() {
        DailyTrend trend = DailyTrend.of(Arrays.asList(
                row("a", 20261109, 25, 1, 0), row("b", 20261109, 75, 0, 0),
                row("b", 20261110, 40, 0, 0),
                row("a", 20261111, 10, 2, 1), row("b", 20261111, 10, 0, 0)), "a");
        assertEquals(3, trend.days.size());
        assertEquals(20261109, trend.days.get(0).day);
        assertEquals(0.25, trend.days.get(0).share, 1e-9);
        // The app has no row on the 10th: no use, while other apps used battery.
        assertEquals(0.0, trend.days.get(1).share, 0.0);
        assertEquals(0.5, trend.days.get(2).share, 1e-9);
        assertEquals(3, trend.kills);
        assertEquals(1, trend.relaunches);
        assertTrue(trend.hasBatteryData());
    }

    @Test
    public void dayWithoutBatteryDataIsMarkedNotZero() {
        DailyTrend trend = DailyTrend.of(Collections.singletonList(
                row("a", 20261110, 0, 4, 2)), "a");
        assertTrue(trend.days.get(0).share < 0);
        assertFalse(trend.hasBatteryData());
        assertEquals(4, trend.kills);
    }

    @Test
    public void daysAreInCalendarOrder() {
        DailyTrend trend = DailyTrend.of(Arrays.asList(
                row("a", 20270101, 1, 0, 0), row("a", 20261231, 1, 0, 0)), "a");
        assertEquals(20261231, trend.days.get(0).day);
        assertEquals(20270101, trend.days.get(1).day);
    }
}
