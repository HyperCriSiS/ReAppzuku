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
