package com.gree1d.reappzuku.core;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Pure local wall-clock scheduling shared by Automation Schedules and Restrictions Scheduler.
 * Recompute from the current time zone rather than adding a fixed 24-hour duration.
 */
public final class ScheduleTime {
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private ScheduleTime() {}

    public static long nextDailyOccurrence(Clock clock, int hour, int minute) {
        requireClock(clock);
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("hour out of range: " + hour);
        if (minute < 0 || minute > 59) throw new IllegalArgumentException("minute out of range: " + minute);

        long now = clock.currentTimeMillis();
        Calendar localDay = clock.calendarNow();
        localDay.setTimeInMillis(now);
        long candidate = firstOccurrenceOnLocalDay(localDay, hour, minute);
        if (candidate > now) return candidate;

        // A repeated autumn hour has only one scheduled occurrence per local date.
        localDay.add(Calendar.DAY_OF_YEAR, 1);
        return firstOccurrenceOnLocalDay(localDay, hour, minute);
    }

    /**
     * Resolve the earliest real instant with the requested clock reading.
     * A missing spring-forward time uses Calendar's forward normalization, e.g.
     * 02:30 becomes 03:30 on a day that jumps from 01:59 to 03:00.
     * A repeated fall-back time fires once, at the earlier of its two occurrences.
     */
    private static long firstOccurrenceOnLocalDay(Calendar day, int hour, int minute) {
        Calendar candidate = (Calendar) day.clone();
        candidate.clear();
        candidate.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH),
                day.get(Calendar.DAY_OF_MONTH), hour, minute, 0);
        candidate.set(Calendar.MILLISECOND, 0);
        long result = candidate.getTimeInMillis();

        TimeZone zone = day.getTimeZone();
        int earlierOffset = zone.getOffset(result - DAY_MS);
        int laterOffset = zone.getOffset(result + DAY_MS);
        int shift = Math.abs(earlierOffset - laterOffset);
        if (shift != 0) {
            long earlierInstant = result - shift;
            Calendar earlier = (Calendar) day.clone();
            earlier.setTimeInMillis(earlierInstant);
            if (earlier.get(Calendar.ERA) == day.get(Calendar.ERA)
                    && earlier.get(Calendar.YEAR) == day.get(Calendar.YEAR)
                    && earlier.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
                    && earlier.get(Calendar.HOUR_OF_DAY) == hour
                    && earlier.get(Calendar.MINUTE) == minute) {
                result = earlierInstant;
            }
        }
        return result;
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
