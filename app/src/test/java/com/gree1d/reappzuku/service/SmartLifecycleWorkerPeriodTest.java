package com.gree1d.reappzuku.service;

import static org.junit.Assert.assertEquals;

import com.gree1d.reappzuku.db.AppPolicy;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class SmartLifecycleWorkerPeriodTest {

    private static long minutes(long value) {
        return TimeUnit.MINUTES.toMillis(value);
    }

    @Test
    public void checksRunFourTimesPerShortestStandbyDelay() {
        assertEquals(30, SmartLifecycleWorker.periodMinutes(minutes(120)));
        assertEquals(45, SmartLifecycleWorker.periodMinutes(minutes(180)));
    }

    @Test
    public void periodStaysWithinWorkManagerAndLatenessLimits() {
        // The Balanced preset keeps today's 15 minute rhythm.
        assertEquals(15, SmartLifecycleWorker.periodMinutes(AppPolicy.DEFAULT_SMART_STANDBY_DELAY_MS));
        assertEquals(15, SmartLifecycleWorker.periodMinutes(minutes(30)));
        assertEquals(15, SmartLifecycleWorker.periodMinutes(1));
        // Messenger: standby after 4 hours, checked hourly.
        assertEquals(60, SmartLifecycleWorker.periodMinutes(minutes(240)));
        assertEquals(60, SmartLifecycleWorker.periodMinutes(Long.MAX_VALUE / 2));
    }
}
