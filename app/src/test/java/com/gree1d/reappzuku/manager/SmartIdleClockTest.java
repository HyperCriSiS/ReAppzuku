package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class SmartIdleClockTest {
    private static final long HOUR = 60 * 60_000L;
    private static final long NOW = 100 * HOUR;
    private static final long STANDBY = HOUR;
    private static final long FORCE_STOP = 6 * HOUR;

    @Test
    public void clockStartsAtFirstSightNotAtAnOldUse() {
        // An app unused for days must not be stopped by the first pass that sees it.
        assertEquals(NOW, SmartIdleClock.resolveSince(0L, NOW - 50 * HOUR, NOW));
        assertEquals(NOW, SmartIdleClock.resolveSince(0L, 0L, NOW));
    }

    @Test
    public void laterUseMovesTheClockForwardOnly() {
        assertEquals(NOW - HOUR, SmartIdleClock.resolveSince(NOW - 3 * HOUR, NOW - HOUR, NOW));
        assertEquals(NOW - HOUR, SmartIdleClock.resolveSince(NOW - HOUR, NOW - 3 * HOUR, NOW));
        assertEquals(NOW - HOUR, SmartIdleClock.resolveSince(NOW - HOUR, 0L, NOW));
    }

    @Test
    public void useStampedSlightlyAheadCountsAsNow() {
        // Discarding it would fall back to the old start and treat a just used app as idle.
        assertEquals(NOW, SmartIdleClock.resolveSince(NOW - 5 * HOUR, NOW + 1, NOW));
        assertFalse(SmartIdleClock.isDue(NOW - 5 * HOUR, NOW + 1, NOW, STANDBY, FORCE_STOP, false));
    }

    @Test
    public void clockThatNeverStartedOrLiesInTheFutureIsAlwaysDue() {
        assertTrue(SmartIdleClock.isDue(0L, 0L, NOW, STANDBY, FORCE_STOP, false));
        assertTrue(SmartIdleClock.isDue(0L, NOW - HOUR, NOW, STANDBY, FORCE_STOP, true));
        assertTrue(SmartIdleClock.isDue(NOW + HOUR, 0L, NOW, STANDBY, FORCE_STOP, true));
        assertEquals(NOW, SmartIdleClock.resolveSince(NOW + HOUR, NOW - HOUR, NOW));
    }

    @Test
    public void nothingIsDueBeforeTheStandbyDelay() {
        assertFalse(SmartIdleClock.isDue(NOW - STANDBY + 1, 0L, NOW, STANDBY, FORCE_STOP, false));
        assertTrue(SmartIdleClock.isDue(NOW - STANDBY, 0L, NOW, STANDBY, FORCE_STOP, false));
    }

    @Test
    public void appliedStandbyIsNotDueAgainUntilForceStop() {
        assertFalse(SmartIdleClock.isDue(NOW - 2 * HOUR, 0L, NOW, STANDBY, FORCE_STOP, true));
        assertFalse(SmartIdleClock.isDue(NOW - FORCE_STOP + 1, 0L, NOW, STANDBY, FORCE_STOP, true));
        assertTrue(SmartIdleClock.isDue(NOW - FORCE_STOP, 0L, NOW, STANDBY, FORCE_STOP, true));
        assertTrue(SmartIdleClock.isDue(NOW - FORCE_STOP, 0L, NOW, FORCE_STOP, FORCE_STOP, false));
    }

    @Test
    public void useAfterTheClockStartRestartsItAndMakesStandbyDueAgain() {
        long stored = NOW - 5 * HOUR;
        // Used 30 minutes ago: the five idle hours no longer count.
        assertFalse(SmartIdleClock.isDue(stored, NOW - HOUR / 2, NOW, STANDBY, FORCE_STOP, true));
        // Used two hours ago: standby was undone by the system and is due again.
        assertTrue(SmartIdleClock.isDue(stored, NOW - 2 * HOUR, NOW, STANDBY, FORCE_STOP, true));
        assertTrue(SmartIdleClock.usedSinceClockStart(stored, NOW - 2 * HOUR));
        assertFalse(SmartIdleClock.usedSinceClockStart(stored, stored - 1));
        assertFalse(SmartIdleClock.usedSinceClockStart(0L, NOW));
    }

    @Test
    public void passChecksForDueWorkBeforeAnyShellCommand() throws Exception {
        String source = read("app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java");
        int usage = source.indexOf("UsageAccess.lastUsedByPackage(context, now)");
        int gate = source.indexOf("return true; // nothing due");
        int shell = source.indexOf("shellManager.resolveAnyShellPermission()");
        assertTrue(usage >= 0 && usage < gate && gate < shell);
        // The boot pass always runs in full.
        assertTrue(source.contains("if (usageKnown && !bootPass) {"));
        // An app without a usage entry is observed on every pass, as before.
        assertTrue(source.contains("!lastUsed.containsKey(pkg)"));
        // Without usage access every pass still runs in full and asks for the grant.
        assertTrue(source.contains("if (!usageKnown) UsageAccess.grant(context, privilegedShell);"));
        // State written with usage data is not trusted once the data is gone.
        assertTrue(source.contains("prefs.getBoolean(KEY_SMART_USAGE_CLOCK, false) != usageKnown"));
    }

    @Test
    public void foregroundEventsOnlyRecordTheUseAndNeverMoveTheClockBack() throws Exception {
        String source = read("app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java");
        int start = source.indexOf("public static void recordForeground(");
        String body = source.substring(start, source.indexOf("\n    }\n", start));
        assertTrue(body.contains("putLong(KEY_SMART_LAST_FOREGROUND_PREFIX + packageName"));
        assertFalse(body.contains(".remove("));
    }

    @Test
    public void usageQueryFailuresFallBackToFullPasses() throws Exception {
        String source = read("app/src/main/java/com/gree1d/reappzuku/core/UsageAccess.java");
        assertTrue(source.contains("} catch (RuntimeException e) {"));
        assertTrue(source.contains("getLastTimeForegroundServiceUsed()"));
    }

    private static String read(String relative) throws Exception {
        for (Path candidate : Arrays.asList(
                Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative));
            }
        }
        throw new java.io.IOException("Could not locate repository root");
    }
}
