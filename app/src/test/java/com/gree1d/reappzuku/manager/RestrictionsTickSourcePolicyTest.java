package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Guards the production tick wiring, not just the pure marker set policy.
 * No Android device, app data, privileged backend or clock change is used.
 */
public final class RestrictionsTickSourcePolicyTest {
    private static String schedulerSource() throws IOException {
        String relative = "app/src/main/java/com/gree1d/reappzuku/manager/RestrictionsScheduler.java";
        for (Path path : Arrays.asList(Paths.get(relative),
                Paths.get("..", relative), Paths.get("..", "..", relative))) {
            if (Files.isRegularFile(path)) return Files.readString(path);
        }
        throw new IOException("Missing source: " + relative);
    }

    private static String normalTick(String source) {
        int start = source.indexOf("public void tick() {");
        int end = source.indexOf("private boolean setAppBucketActive(", start);
        assertTrue("Missing normal tick", start >= 0 && end > start);
        return source.substring(start, end);
    }

    @Test
    public void failedOperationsCannotBePreemptivelyMarkedAsCompleted() throws Exception {
        String tick = normalTick(schedulerSource());
        assertFalse(tick.contains("saveTempProtectedPackages(shouldBeProtected)"));
        assertTrue(tick.contains("successful = \"ok\".equals(outcome) || \"skipped\".equals(outcome)"));
        assertTrue(tick.contains("successful = privilegedShell.applyPackageStateBlocking(pkg, action)"));
        assertTrue(tick.contains("successful = setAppBucketActive(pkg)"));
        assertTrue(tick.contains("successful = stopApp(pkg, forceStop)"));
        assertTrue(tick.contains("if (successful) succeededActivations.add(pkg)"));
        assertTrue(tick.contains("if (successful) succeededDeactivations.add(pkg)"));
        assertTrue(tick.contains("RestrictionsTickMarkerPolicy.afterCompletedTransitions("));
        assertTrue(tick.indexOf("RestrictionsTickMarkerPolicy.afterCompletedTransitions(")
                < tick.indexOf("putStringSet(KEY_TEMP_PROTECTED, completed).commit()"));
    }

    @Test
    public void missingHistoryIsNotGuessedAndNextAlarmAlwaysRearmed() throws Exception {
        String source = schedulerSource();
        String tick = normalTick(source);
        assertTrue(tick.contains("if (entry == null) continue;"));
        assertTrue(tick.contains("} finally {\n                // Keep future boundaries armed"));
        assertTrue(tick.contains("scheduleNext();"));
        assertTrue(source.contains("return privilegedShell.stopPackage(packageName, mode).succeeded()"));
    }
}
