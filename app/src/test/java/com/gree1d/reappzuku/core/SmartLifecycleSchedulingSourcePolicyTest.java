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

public class SmartLifecycleSchedulingSourcePolicyTest {
    @Test
    public void periodicWorkIsReconciledFromEffectiveSmartOwnership() throws Exception {
        String worker = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/service/SmartLifecycleWorker.java");
        String app = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/App.java");
        String editor = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/ui/AppPolicyEditorActivity.java");
        String newApp = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/NewAppSetupCoordinator.java");
        String backup = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/BackupManager.java");

        assertTrue(worker.contains("public static void reconcilePeriodic(Context context)"));
        assertTrue(worker.contains("AppPolicyLegacyMigrator.resolveEffectivePolicy"));
        assertTrue(worker.contains("effective.strategy == AppPolicy.STRATEGY_SMART"));
        assertTrue(worker.contains("KEY_BLACKLISTED_APPS"));

        int bootStart = worker.indexOf("public static void scheduleAfterBoot");
        int cancelStart = worker.indexOf("public static void cancel", bootStart);
        assertTrue(bootStart >= 0 && cancelStart > bootStart);
        assertFalse(worker.substring(bootStart, cancelStart)
                .contains("schedulePeriodic(context);"));

        assertTrue(app.contains("SmartLifecycleWorker.reconcilePeriodic(this);"));
        assertTrue(editor.contains("SmartLifecycleWorker.reconcilePeriodic(this);"));
        assertTrue(newApp.contains("SmartLifecycleWorker.reconcilePeriodic(context);"));
        assertTrue(backup.contains("SmartLifecycleWorker.reconcilePeriodic(context);"));
    }

    @Test
    public void noSmartOwnershipReturnsBeforeShellResolution() throws Exception {
        String manager = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java");
        int emptyCheck = manager.indexOf("if (smartManaged.isEmpty())");
        int shellCheck = manager.indexOf("shellManager.resolveAnyShellPermission()");
        assertTrue(emptyCheck >= 0);
        assertTrue(shellCheck > emptyCheck);
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
