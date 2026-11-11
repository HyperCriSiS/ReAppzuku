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
    public void checksRunEightTimesPerShortestStandbyDelay() {
        assertEquals(15, SmartLifecycleWorker.periodMinutes(minutes(120)));
        assertEquals(22, SmartLifecycleWorker.periodMinutes(minutes(180)));
        assertEquals(30, SmartLifecycleWorker.periodMinutes(minutes(240)));
    }

    @Test
    public void periodStaysWithinWorkManagerAndLatenessLimits() {
        // The Balanced preset keeps today's 15 minute rhythm.
        assertEquals(15, SmartLifecycleWorker.periodMinutes(AppPolicy.DEFAULT_SMART_STANDBY_DELAY_MS));
        assertEquals(15, SmartLifecycleWorker.periodMinutes(minutes(30)));
        assertEquals(15, SmartLifecycleWorker.periodMinutes(1));
        assertEquals(60, SmartLifecycleWorker.periodMinutes(minutes(600)));
        assertEquals(60, SmartLifecycleWorker.periodMinutes(Long.MAX_VALUE));
    }

    @Test
    public void legacyBlacklistAppsKeepTheShortestPeriodNextToPolicies() throws Exception {
        String worker = new String(java.nio.file.Files.readAllBytes(locate(
                "app/src/main/java/com/gree1d/reappzuku/service/SmartLifecycleWorker.java")), "UTF-8");
        int legacy = worker.indexOf("? TimeUnit.MINUTES.toMillis(MIN_PERIOD_MINUTES) : shortest;");
        // The legacy check is reached also when policies already gave a delay.
        org.junit.Assert.assertTrue(legacy > 0);
        org.junit.Assert.assertFalse(worker.contains("if (shortest > 0L) return shortest;"));
    }

    private static java.nio.file.Path locate(String relative) throws java.io.IOException {
        for (String prefix : new String[] {"", "../", "../../"}) {
            java.nio.file.Path path = java.nio.file.Paths.get(prefix + relative);
            if (java.nio.file.Files.exists(path)) return path;
        }
        throw new java.io.IOException(relative);
    }
}
