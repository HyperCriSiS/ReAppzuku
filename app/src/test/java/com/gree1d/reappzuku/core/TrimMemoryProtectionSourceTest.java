package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class TrimMemoryProtectionSourceTest {

    @Test
    public void memoryTrimSkipsAppsWithTheProtectedStrategy() throws Exception {
        for (String file : new String[] {
                "app/src/main/java/com/gree1d/reappzuku/manager/RamKillShortcutManager.java",
                "app/src/main/java/com/gree1d/reappzuku/service/AppLaunchAccessibilityService.java"}) {
            String source = read(file);
            assertTrue(file, source.contains("ProtectedApps.withProtectedStrategy("));
            assertTrue(file, source.contains("if (protectedByStrategy.contains("));
        }
        assertTrue(read("app/src/main/java/com/gree1d/reappzuku/core/ProtectedApps.java")
                .contains(".getByStrategy(AppPolicy.STRATEGY_PROTECTED)"));
        // A database failure leaves the trim running with the legacy checks.
        assertTrue(read("app/src/main/java/com/gree1d/reappzuku/core/ProtectedApps.java")
                .contains("A database failure must not crash a memory trim"));
    }

    private static String read(String relative) throws Exception {
        for (Path candidate : Arrays.asList(Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative)).replace("\r\n", "\n");
            }
        }
        throw new java.io.IOException("Could not locate repository root");
    }
}
