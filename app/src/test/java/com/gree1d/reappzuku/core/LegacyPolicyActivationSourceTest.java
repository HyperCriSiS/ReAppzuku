package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class LegacyPolicyActivationSourceTest {
    @Test
    public void bothExecutionEnginesFilterMigrationOwnedRowsBySnapshotFreshness() throws Exception {
        String autoKill = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java");
        String smart = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java");

        assertEquals(1, count(autoKill, "AppPolicyLegacyMigrator.isMigrationSnapshotCurrent("));
        assertEquals(1, count(autoKill, "AppPolicyLegacyMigrator.resolveEffectivePolicy("));
        assertEquals(1, count(smart, "AppPolicyLegacyMigrator.isMigrationSnapshotCurrent("));
        assertEquals(1, count(smart, "AppPolicyLegacyMigrator.resolveEffectivePolicy("));
    }

    @Test
    public void currentMigrationDisablesLegacyFallbackInBothExecutionEngines() throws Exception {
        String autoKill = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java");
        String smart = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java");

        assertEquals(1, count(autoKill, "migrationSnapshotCurrent ? null : legacyState"));
        assertEquals(1, count(smart, "migrationSnapshotCurrent ? null : legacyState"));
    }

    @Test
    public void applicationReconcilesLegacyPoliciesOnNormalProcessStart() throws Exception {
        String source = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/App.java");

        assertEquals(1, count(source, "AppPolicyLegacyMigrator.migrateIfNeeded(this)"));
        int executor = source.indexOf("executor.execute(() -> {");
        int migration = source.indexOf("AppPolicyLegacyMigrator.migrateIfNeeded(this)");
        int replay = source.indexOf("NewAppSetupCoordinator.replayPending(this)");
        assertTrue(executor >= 0);
        assertTrue(migration > executor);
        assertTrue(replay > migration);
    }

    @Test
    public void migrationReplacesOnlyRowsItOwns() throws Exception {
        String source = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/AppPolicyLegacyMigrator.java");

        assertTrue(source.contains(
                "policy.source != AppPolicy.SOURCE_LEGACY_MIGRATED"));
        assertTrue(source.contains(
                "policyDao.deleteBySource(AppPolicy.SOURCE_LEGACY_MIGRATED)"));
        assertTrue(source.contains(
                "policy.source = AppPolicy.SOURCE_LEGACY_MIGRATED"));
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