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
    public void clockStartsAtTheLastUse() {
        assertEquals(NOW - 3 * HOUR, SmartIdleClock.resolveSince(0L, NOW - 3 * HOUR, NOW));
        assertEquals(NOW - HOUR, SmartIdleClock.resolveSince(NOW - 3 * HOUR, NOW - HOUR, NOW));
        assertEquals(NOW - HOUR, SmartIdleClock.resolveSince(NOW - HOUR, NOW - 3 * HOUR, NOW));
    }

    @Test
    public void withoutAnyInformationTheClockStartsNow() {
        assertEquals(NOW, SmartIdleClock.resolveSince(0L, 0L, NOW));
    }

    @Test
    public void valuesFromTheFutureAreIgnored() {
        assertEquals(NOW, SmartIdleClock.resolveSince(NOW + HOUR, 0L, NOW));
        assertEquals(NOW - HOUR, SmartIdleClock.resolveSince(NOW + HOUR, NOW - HOUR, NOW));
        assertEquals(NOW - HOUR, SmartIdleClock.resolveSince(NOW - HOUR, NOW + HOUR, NOW));
    }

    @Test
    public void unknownAppIsDueOnceSoAPassCanStartItsClock() {
        assertTrue(SmartIdleClock.isDue(0L, 0L, NOW, STANDBY, FORCE_STOP, false));
    }

    @Test
    public void nothingIsDueBeforeTheStandbyDelay() {
        assertFalse(SmartIdleClock.isDue(NOW - STANDBY + 1, 0L, NOW, STANDBY, FORCE_STOP, false));
        assertTrue(SmartIdleClock.isDue(NOW - STANDBY, 0L, NOW, STANDBY, FORCE_STOP, false));
    }

    @Test
    public void appliedStandbyIsNotDueAgainUntilForceStop() {
        assertFalse(SmartIdleClock.isDue(NOW - 2 * HOUR, 0L, NOW, STANDBY, FORCE_STOP, true));
        assertTrue(SmartIdleClock.isDue(NOW - FORCE_STOP, 0L, NOW, STANDBY, FORCE_STOP, true));
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
        // Without usage access every pass still runs in full and asks for the grant.
        assertTrue(source.contains("if (lastUsed == null) UsageAccess.grant(context, privilegedShell);"));
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
