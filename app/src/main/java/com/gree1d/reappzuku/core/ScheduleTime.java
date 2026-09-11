package com.gree1d.reappzuku.core;

import java.util.Calendar;

/** Pure daily-schedule calculations shared by preset and restriction scheduling. */
public final class ScheduleTime {
    private ScheduleTime() {}

    public static long nextDailyOccurrence(Clock clock, int hour, int minute) {
        requireClock(clock);
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("hour out of range: " + hour);
        if (minute < 0 || minute > 59) throw new IllegalArgumentException("minute out of range: " + minute);

        Calendar calendar = clock.calendarNow();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= clock.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return calendar.getTimeInMillis();
    }

    public static int currentMinutesOfDay(Clock clock) {
        requireClock(clock);
        Calendar now = clock.calendarNow();
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    private static void requireClock(Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock == null");
    }
}
