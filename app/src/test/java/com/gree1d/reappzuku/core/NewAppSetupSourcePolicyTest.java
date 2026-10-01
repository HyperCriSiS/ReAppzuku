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

public class NewAppSetupSourcePolicyTest {
    @Test public void manifestReceivesPackageAddedWithPackageScheme() throws Exception {
        String manifest = readRepositoryFile("app/src/main/AndroidManifest.xml");
        assertTrue(manifest.contains("android.intent.action.PACKAGE_ADDED"));
        assertTrue(manifest.contains("android:scheme=\"package\""));
        assertTrue(manifest.contains("PackageAddedReceiver"));
    }
    @Test public void packageReceiverNeverLaunchesActivityOverForegroundApp() throws Exception {
        String receiver = readRepositoryFile("app/src/main/java/com/gree1d/reappzuku/core/PackageAddedReceiver.java");
        assertTrue(receiver.contains("Intent.EXTRA_REPLACING"));
        assertTrue(receiver.contains("goAsync()"));
        assertFalse(receiver.contains("startActivity("));
    }
    @Test public void askAndLeaveAreExplicitlyUnmanagedSafetyPaths() throws Exception {
        String coordinator = readRepositoryFile("app/src/main/java/com/gree1d/reappzuku/core/NewAppSetupCoordinator.java");
        assertTrue(coordinator.contains("ensureExplicitUnmanaged"));
        assertTrue(coordinator.contains("ACTION_QUEUE_UNMANAGED"));
        assertTrue(coordinator.contains("ACTION_LEAVE_UNMANAGED"));
        assertTrue(coordinator.contains("AppPolicy.STRATEGY_UNMANAGED"));
    }

    @Test public void migrationOwnedPolicyDoesNotCaptureAReinstall() throws Exception {
        String coordinator = readRepositoryFile("app/src/main/java/com/gree1d/reappzuku/core/NewAppSetupCoordinator.java");
        assertTrue(coordinator.contains("existing.source == AppPolicy.SOURCE_EXPLICIT"));
        assertTrue(coordinator.contains("current.source == AppPolicy.SOURCE_EXPLICIT"));
    }
    @Test public void pendingQueueReplaysAfterLegacyMigration() throws Exception {
        String app = readRepositoryFile("app/src/main/java/com/gree1d/reappzuku/core/App.java");
        int migration = app.indexOf("AppPolicyLegacyMigrator.migrateIfNeeded(this)");
        int replay = app.indexOf("NewAppSetupCoordinator.replayPending(this)");
        assertTrue(migration >= 0);
        assertTrue(replay > migration);
    }
    private static String readRepositoryFile(String relative) throws IOException {
        List<Path> candidates = Arrays.asList(Paths.get(relative), Paths.get("..", relative), Paths.get("..", "..", relative));
        for (Path candidate : candidates) if (Files.isRegularFile(candidate)) return Files.readString(candidate);
        throw new IOException("Could not locate repository file: " + relative + " from " + Paths.get("").toAbsolutePath());
    }
}