package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KillIntervalPolicyTest {

    @Test
    public void aLockedScreenWaitsAtLeastFiveMinutesBetweenPasses() {
        assertEquals(18_000, KillIntervalPolicy.serviceDelayMs(18_000, false));
        assertEquals(300_000, KillIntervalPolicy.serviceDelayMs(18_000, true));
        int longest = AppConstants.KILL_INTERVALS_MS[AppConstants.KILL_INTERVALS_MS.length - 1];
        assertEquals(Math.max(longest, 300_000), KillIntervalPolicy.serviceDelayMs(longest, true));
    }

    @Test
    public void theServiceLoopUsesTheScreenAwareDelay() throws Exception {
        String service = new String(java.nio.file.Files.readAllBytes(locateService()), "UTF-8")
                .replace("\r\n", "\n");
        org.junit.Assert.assertTrue(service.contains("KillIntervalPolicy.serviceDelayMs("));
        org.junit.Assert.assertTrue(service.contains("case \"SCREEN_ON\":\n                syncDisplayLoopsWithScreen();\n"
                + "                // Back to the chosen interval instead of waiting out the screen-off delay.\n"
                + "                scheduleNextKill();"));
    }

    private static java.nio.file.Path locateService() throws java.io.IOException {
        String relative = "app/src/main/java/com/gree1d/reappzuku/service/ShappkyService.java";
        for (String prefix : new String[] {"", "../", "../../"}) {
            java.nio.file.Path path = java.nio.file.Paths.get(prefix + relative);
            if (java.nio.file.Files.exists(path)) return path;
        }
        throw new java.io.IOException(relative);
    }

    @Test
    public void storedIntervalsPassClampUnchanged() {
        for (int interval : AppConstants.KILL_INTERVALS_MS) {
            assertEquals(interval, KillIntervalPolicy.clampServiceIntervalMs(interval));
        }
    }

    @Test
    public void legacyMinuteScaleFallbackCannotSpinTheServiceLoop() {
        // Older preset backup/restore paths wrote 15 into the millisecond key.
        assertEquals(KillIntervalPolicy.MIN_INTERVAL_MS,
                KillIntervalPolicy.clampServiceIntervalMs(15));
        assertEquals(KillIntervalPolicy.MIN_INTERVAL_MS,
                KillIntervalPolicy.clampServiceIntervalMs(0));
        assertEquals(KillIntervalPolicy.MIN_INTERVAL_MS,
                KillIntervalPolicy.clampServiceIntervalMs(-1));
    }

    @Test
    public void oversizedIntervalIsClampedToLargestSupportedValue() {
        assertEquals(KillIntervalPolicy.MAX_INTERVAL_MS,
                KillIntervalPolicy.clampServiceIntervalMs(Integer.MAX_VALUE));
    }

    @Test
    public void workerIntervalConvertsMillisecondsToMinutes() {
        // 18000 ms must become the 15-minute WorkManager floor, never 18000 minutes.
        assertEquals(15L, KillIntervalPolicy.workerIntervalMinutes(18_000));
        assertEquals(15L, KillIntervalPolicy.workerIntervalMinutes(300_000));
        assertEquals(15L, KillIntervalPolicy.workerIntervalMinutes(15));
        assertEquals(15L, KillIntervalPolicy.workerIntervalMinutes(Integer.MAX_VALUE));
    }

    @Test
    public void defaultIntervalIsWithinSupportedRange() {
        assertEquals(AppConstants.DEFAULT_KILL_INTERVAL_MS,
                KillIntervalPolicy.clampServiceIntervalMs(AppConstants.DEFAULT_KILL_INTERVAL_MS));
    }
}
