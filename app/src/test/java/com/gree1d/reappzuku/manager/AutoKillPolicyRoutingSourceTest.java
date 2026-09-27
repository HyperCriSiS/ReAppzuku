package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class AutoKillPolicyRoutingSourceTest {
    @Test
    public void autoKillManagerRoutesOwnershipAndKillMethodThroughPolicyResolver() throws Exception {
        String source = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java");

        assertEquals(1, count(source, "AppPolicyResolver.shouldExecuteImmediate("));
        assertEquals(1, count(source, "AppPolicyResolver.resolveImmediateKillMethod("));
    }

    @Test
    public void periodicWorkerCarriesPeriodicOrRamTrigger() throws Exception {
        String source = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/service/AutoKillWorker.java");

        assertTrue(source.contains("AppPolicy.TRIGGER_PERIODIC"));
        assertTrue(source.contains("AppPolicy.TRIGGER_RAM_THRESHOLD"));
        assertTrue(source.contains("performAutoKill(latch::countDown, source, trigger)"));
    }

    @Test
    public void serviceScreenOffAndPeriodicPathsCarryConcreteTriggers() throws Exception {
        String source = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/service/ShappkyService.java");

        assertEquals(2, count(source, "AppPolicy.TRIGGER_SCREEN_OFF"));
        assertEquals(1, count(source, "AppPolicy.TRIGGER_PERIODIC"));
        assertEquals(1, count(source, "AppPolicy.TRIGGER_RAM_THRESHOLD"));
    }

    @Test
    public void hardwareAndAppLaunchPathsCarryConcreteTriggers() throws Exception {
        String hardware = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/service/HardwareEventReceiver.java");
        String appLaunch = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/service/AppLaunchAccessibilityService.java");

        assertEquals(1, count(hardware, "AppPolicy.TRIGGER_HARDWARE_EVENT"));
        assertEquals(1, count(appLaunch, "AppPolicy.TRIGGER_APP_LAUNCH"));
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

    private static String readRepositoryFile(String relative) throws IOException {
        List<Path> candidates = Arrays.asList(
                Paths.get(relative),
                Paths.get("..", relative),
                Paths.get("..", "..", relative));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
        }
        throw new IOException("Could not locate repository file: " + relative
                + " from " + Paths.get("").toAbsolutePath());
    }
}
