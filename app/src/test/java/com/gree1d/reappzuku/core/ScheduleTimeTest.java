package com.gree1d.reappzuku.core;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScheduleTimeTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    public void futureTimeStaysOnSameDay() {
        FixedClock clock = fixedUtc(2026, Calendar.SEPTEMBER, 5, 10, 15);
        long result = ScheduleTime.nextDailyOccurrence(clock, 12, 30);
        Calendar out = calendar(result);
        assertEquals(5, out.get(Calendar.DAY_OF_MONTH));
        assertEquals(12, out.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, out.get(Calendar.MINUTE));
    }

    @Test
    public void elapsedTimeMovesToNextDay() {
        FixedClock clock = fixedUtc(2026, Calendar.SEPTEMBER, 5, 10, 15);
        long result = ScheduleTime.nextDailyOccurrence(clock, 9, 30);
        Calendar out = calendar(result);
        assertEquals(6, out.get(Calendar.DAY_OF_MONTH));
        assertEquals(9, out.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, out.get(Calendar.MINUTE));
    }

    @Test
    public void currentMinutesUsesInjectedClock() {
        FixedClock clock = fixedUtc(2026, Calendar.SEPTEMBER, 5, 23, 7);
        assertEquals(23 * 60 + 7, ScheduleTime.currentMinutesOfDay(clock));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidHour() {
        ScheduleTime.nextDailyOccurrence(fixedUtc(2026, Calendar.SEPTEMBER, 5, 10, 15), 24, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidMinute() {
        ScheduleTime.nextDailyOccurrence(fixedUtc(2026, Calendar.SEPTEMBER, 5, 10, 15), 10, 60);
    }


    @Test
    public void berlinSpringGapRunsAtFirstNormalizedTimeAndNextDayIsLocal() {
        // 2026-03-29 02:30 does not exist in Europe/Berlin.
        long before = utcMillis(2026, Calendar.MARCH, 29, 0, 15);
        assertEquals(utcMillis(2026, Calendar.MARCH, 29, 1, 30),
                ScheduleTime.nextDailyOccurrence(zonedClock("Europe/Berlin", before), 2, 30));
        long after = utcMillis(2026, Calendar.MARCH, 29, 1, 31);
        assertEquals(utcMillis(2026, Calendar.MARCH, 30, 0, 30),
                ScheduleTime.nextDailyOccurrence(zonedClock("Europe/Berlin", after), 2, 30));
    }

    @Test
    public void berlinAutumnRepeatedHourRunsOnlyAtFirstOccurrence() {
        long before = utcMillis(2026, Calendar.OCTOBER, 25, 0, 15);
        assertEquals(utcMillis(2026, Calendar.OCTOBER, 25, 0, 30),
                ScheduleTime.nextDailyOccurrence(zonedClock("Europe/Berlin", before), 2, 30));
        // Past the first 02:30 CEST but before 02:30 CET: never repeat today.
        long betweenOccurrences = utcMillis(2026, Calendar.OCTOBER, 25, 1, 15);
        assertEquals(utcMillis(2026, Calendar.OCTOBER, 26, 1, 30),
                ScheduleTime.nextDailyOccurrence(
                        zonedClock("Europe/Berlin", betweenOccurrences), 2, 30));
    }

    @Test
    public void currentZoneControlsNextWallClockOccurrence() {
        long sameInstant = utcMillis(2026, Calendar.JUNE, 1, 8, 0);
        assertEquals(utcMillis(2026, Calendar.JUNE, 1, 9, 30),
                ScheduleTime.nextDailyOccurrence(zonedClock("UTC", sameInstant), 9, 30));
        assertEquals(utcMillis(2026, Calendar.JUNE, 2, 7, 30),
                ScheduleTime.nextDailyOccurrence(zonedClock("Europe/Berlin", sameInstant), 9, 30));
    }

    @Test
    public void halfHourDstOverlapAlsoUsesEarlierInstant() {
        // Lord Howe has a 30-minute DST rollback, not the usual 60-minute shift.
        long before = utcMillis(2026, Calendar.APRIL, 4, 14, 45);
        assertEquals(utcMillis(2026, Calendar.APRIL, 4, 14, 45),
                ScheduleTime.nextDailyOccurrence(zonedClock("Australia/Lord_Howe",
                        utcMillis(2026, Calendar.APRIL, 4, 14, 0)), 1, 45));
    }

    private static long utcMillis(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(UTC);
        c.clear();
        c.set(year, month, day, hour, minute);
        return c.getTimeInMillis();
    }

    private static Clock zonedClock(String zone, long millis) {
        return new Clock() {
            @Override public long currentTimeMillis() { return millis; }
            @Override public Calendar calendarNow() {
                Calendar c = Calendar.getInstance(TimeZone.getTimeZone(zone));
                c.setTimeInMillis(millis);
                return c;
            }
        };
    }

    private static FixedClock fixedUtc(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return new FixedClock(calendar.getTimeInMillis());
    }

    private static Calendar calendar(long millis) {
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.setTimeInMillis(millis);
        return calendar;
    }

    private static final class FixedClock implements Clock {
        private final long millis;

        FixedClock(long millis) {
            this.millis = millis;
        }

        @Override
        public long currentTimeMillis() {
            return millis;
        }

        @Override
        public Calendar calendarNow() {
            Calendar calendar = Calendar.getInstance(UTC);
            calendar.setTimeInMillis(millis);
            return calendar;
        }
    }
}