package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class SecurityProbePolicyTest {
    @Test
    public void externalProbeStaysUnprivilegedAndSeparateFromProduct() throws Exception {
        String settings = readRepositoryFile("settings.gradle");
        String build = readRepositoryFile("securityProbe/build.gradle");
        String manifest = readRepositoryFile("securityProbe/src/main/AndroidManifest.xml");

        assertTrue(settings.contains("include(\":securityProbe\")"));
        assertTrue(build.contains("applicationId = 'com.reappzuku.securityprobe'"));
        assertFalse(build.contains("applicationId = 'com.gree1d.reappzuku'"));
        assertFalse(manifest.contains("<uses-permission"));
        assertFalse(manifest.contains("sharedUserId"));
        assertFalse(manifest.contains("<instrumentation"));
    }

    @Test
    public void api37GateRequiresSeparateUidAndSuccessfulExternalProbe() throws Exception {
        String workflow = readRepositoryFile(".github/workflows/android17-runtime.yml");

        assertTrue(workflow.contains(":securityProbe:assembleDebug"));
        assertTrue(workflow.contains("pm list packages -U"));
        assertTrue(workflow.contains("test \"$app_uid\" != \"$probe_uid\""));
        assertTrue(workflow.contains("RESULT=PASS"));
        assertTrue(workflow.contains("BINDER_EXPOSED="));
        assertTrue(workflow.contains("BOOT_BROADCAST_DENIED"));
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
