package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.Test;

/**
 * Source contract for the app's own battery cost: housekeeping must not wake a
 * sleeping device, must not refresh what nobody can see, and must not run
 * privileged process queries for apps that are not going to be killed.
 */
public class BatteryDietSourcePolicyTest {

    private static final String SERVICE =
            "app/src/main/java/com/gree1d/reappzuku/service/ShappkyService.java";
    private static final String AUTO_KILL =
            "app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java";

    @Test
    public void snapshotIsSkippedOnlyWithScreenOffAndNoCharger() {
        assertTrue(BackgroundWorkPolicy.shouldCollectSnapshot(true, false));
        assertTrue(BackgroundWorkPolicy.shouldCollectSnapshot(false, true));
        assertTrue(BackgroundWorkPolicy.shouldCollectSnapshot(true, true));
        assertFalse(BackgroundWorkPolicy.shouldCollectSnapshot(false, false));
    }

    @Test
    public void snapshotAndHeartbeatAlarmsDoNotWakeTheDevice() throws Exception {
        String source = read(SERVICE);
        assertFalse(method(source, "scheduleSnapshotAlarm").contains("RTC_WAKEUP"));
        assertFalse(method(source, "scheduleHeartbeatAlarm").contains("RTC_WAKEUP"));
        // The idle freeze must still fire while the device sleeps.
        assertEquals(2, count(method(source, "scheduleIdleFreezeAlarm"), "RTC_WAKEUP"));
    }

    @Test
    public void skippedSnapshotKeepsTheAlarmChainAlive() throws Exception {
        String source = read(SERVICE);
        int gate = source.indexOf("BackgroundWorkPolicy.shouldCollectSnapshot(");
        int collect = source.indexOf("collectStatsManager.takeSnapshotAsync(");
        assertTrue(gate >= 0 && gate < collect);
        String skipped = source.substring(gate, collect);
        assertTrue(skipped.contains("releaseSnapshotWakeLock();"));
        assertTrue(skipped.contains("scheduleSnapshotAlarm();"));
    }

    @Test
    public void displayLoopsStopWhileTheScreenIsOff() throws Exception {
        String source = read(SERVICE);
        assertEquals(2, count(source, "if (!isRunning || screenOff) return;"));
        assertEquals(1, count(source, "ramNotificationRunnable != this || screenOff"));
        assertEquals(1, count(source,
                "{ramNotificationRunnable, widgetRunnable, rootCheckRunnable}"));
    }

    @Test
    public void periodicKillTimerOnlyRunsWhilePeriodicKillIsEnabled() throws Exception {
        String schedule = method(read(SERVICE), "scheduleNextKill");
        int enabledCheck = schedule.indexOf("KEY_PERIODIC_KILL_ENABLED");
        int post = schedule.indexOf("handler.postDelayed(periodicKillRunnable");
        assertTrue(enabledCheck >= 0 && enabledCheck < post);
        assertTrue(schedule.contains("handler.removeCallbacks(periodicKillRunnable);"));
    }

    @Test
    public void autoKillQueriesPssAndOrphansForKillCandidatesOnly() throws Exception {
        String source = read(AUTO_KILL);
        assertEquals(1, count(source, "shellManager.getProcessMemoryInfo("));
        assertEquals(1, count(source, "shellManager.getProcessMemoryInfo(killPids)"));
        assertEquals(0, count(source, "killOrphanShellProcesses(null)"));
    }

    private static String method(String source, String name) {
        int start = source.indexOf("    private void " + name + "() {");
        assertTrue("missing method " + name, start >= 0);
        return source.substring(start, source.indexOf("\n    }\n", start));
    }

    private static int count(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String read(String relative) throws IOException {
        for (Path candidate : Arrays.asList(
                Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative));
            }
        }
        throw new IOException("Could not locate repository root from "
                + Paths.get("").toAbsolutePath());
    }
}
