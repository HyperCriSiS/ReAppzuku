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
    @Test
    public void packageAddedIsRuntimeRegisteredNotManifestExported() throws Exception {
        String manifest = readRepositoryFile("app/src/main/AndroidManifest.xml");
        String receiver = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/PackageAddedReceiver.java");
        String app = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/App.java");

        assertFalse(manifest.contains("PackageAddedReceiver"));
        assertFalse(manifest.contains("android.intent.action.PACKAGE_ADDED"));
        assertTrue(receiver.contains("new IntentFilter(Intent.ACTION_PACKAGE_ADDED)"));
        assertTrue(receiver.contains("filter.addDataScheme(\"package\")"));
        assertTrue(receiver.contains("Context.RECEIVER_EXPORTED"));
        assertTrue(app.contains("PackageAddedReceiver.register(this)"));
    }

    @Test
    public void packageReceiverNeverLaunchesActivityOverForegroundApp() throws Exception {
        String receiver = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/PackageAddedReceiver.java");
        assertTrue(receiver.contains("Intent.EXTRA_REPLACING"));
        assertTrue(receiver.contains("goAsync()"));
        assertTrue(receiver.contains("NewAppDiscoveryWorker.handleObservedPackageAdded"));
        assertFalse(receiver.contains("startActivity("));
    }

    @Test
    public void periodicDiscoveryCatchesMissedInstallsByInstallIdentity() throws Exception {
        String worker = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/NewAppDiscoveryWorker.java");
        assertTrue(worker.contains("PeriodicWorkRequest"));
        assertTrue(worker.contains("15, TimeUnit.MINUTES"));
        assertTrue(worker.contains("getInstalledPackages(0)"));
        assertTrue(worker.contains("firstInstallTime"));
        assertTrue(worker.contains("KEY_NEW_APP_DISCOVERY_SNAPSHOT"));
        assertTrue(worker.contains("NewAppSetupCoordinator.handlePackageAdded"));
    }

    @Test
    public void askAndLeaveAreExplicitlyUnmanagedSafetyPaths() throws Exception {
        String coordinator = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/NewAppSetupCoordinator.java");
        assertTrue(coordinator.contains("ensureExplicitUnmanaged"));
        assertTrue(coordinator.contains("ACTION_QUEUE_UNMANAGED"));
        assertTrue(coordinator.contains("ACTION_LEAVE_UNMANAGED"));
        assertTrue(coordinator.contains("AppPolicy.STRATEGY_UNMANAGED"));
    }

    @Test
    public void migrationOwnedPolicyDoesNotCaptureAReinstall() throws Exception {
        String coordinator = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/NewAppSetupCoordinator.java");
        assertTrue(coordinator.contains("existing.source == AppPolicy.SOURCE_EXPLICIT"));
        assertTrue(coordinator.contains("current.source == AppPolicy.SOURCE_EXPLICIT"));
    }

    @Test
    public void startupMigratesThenDiscoversThenReplaysPendingQueue() throws Exception {
        String app = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/App.java");
        int migration = app.indexOf("AppPolicyLegacyMigrator.migrateIfNeeded(this)");
        int discovery = app.indexOf("NewAppDiscoveryWorker.reconcileNow(this)");
        int schedule = app.indexOf("NewAppDiscoveryWorker.schedulePeriodic(this)");
        int replay = app.indexOf("NewAppSetupCoordinator.replayPending(this)");
        assertTrue(migration >= 0);
        assertTrue(discovery > migration);
        assertTrue(schedule > discovery);
        assertTrue(replay > discovery);
    }

    private static String readRepositoryFile(String relative) throws IOException {
        List<Path> candidates = Arrays.asList(
                Paths.get(relative),
                Paths.get("..", relative),
                Paths.get("..", "..", relative));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return Files.readString(candidate);
        }
        throw new IOException("Could not locate repository file: " + relative
                + " from " + Paths.get("").toAbsolutePath());
    }
}
