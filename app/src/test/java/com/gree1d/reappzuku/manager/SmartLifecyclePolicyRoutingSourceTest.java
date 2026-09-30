package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class SmartLifecyclePolicyRoutingSourceTest {
    @Test
    public void managerRoutesOwnershipAndDelaysThroughPolicyResolver() throws Exception {
        String source = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java");

        assertEquals(1, count(source, "AppPolicyResolver.shouldExecuteSmart("));
        assertEquals(1, count(source, "AppPolicyResolver.resolveSmartStandbyDelayMs("));
        assertEquals(1, count(source, "AppPolicyResolver.resolveSmartForceStopDelayMs("));
        assertTrue(source.contains("managed.addAll(explicitPolicies.keySet())"));
    }

    @Test
    public void explicitNonSmartOverridesClearStaleSmartState() throws Exception {
        String source = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java");

        assertTrue(source.contains(
                "if (!AppPolicyResolver.shouldExecuteSmart(explicitPolicy, legacyState, bootPass))"));
        assertTrue(source.contains("clearBackgroundState(pkg);"));
    }

    @Test
    public void managerKeepsExistingFailSafeProtectionLayers() throws Exception {
        String source = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java");

        assertTrue(source.contains("ProtectedApps.isProtected(context, pkg)"));
        assertTrue(source.contains("ApplicationInfo.FLAG_PERSISTENT"));
        assertTrue(source.contains("isEnabledAccessibilityService(pkg)"));
        assertTrue(source.contains("isEnabledNotificationListener(pkg)"));
        assertTrue(source.contains("SmartLifecycleProtectionPolicy.getDumpProtectionReason("));
    }

    @Test
    public void bootWorkerLetsExplicitPolicyOwnBootCleanupDecision() throws Exception {
        String worker = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/service/SmartLifecycleWorker.java");
        String manager = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java");

        assertFalse(worker.contains(
                "if (!prefs.getBoolean(KEY_SMART_BOOT_CLEANUP_ENABLED, true)) return;"));
        assertTrue(manager.contains(
                "if (explicitPolicy == null && !legacyBootCleanupEnabled) continue;"));
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
