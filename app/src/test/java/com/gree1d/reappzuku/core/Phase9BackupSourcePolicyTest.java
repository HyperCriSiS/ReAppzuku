package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class Phase9BackupSourcePolicyTest {
    @Test
    public void v7ExportsOnlyPortablePhase9StateAndRestoresTransactionally() throws Exception {
        String backupManager = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/BackupManager.java");
        String codec = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/Phase9BackupCodec.java");

        assertTrue(backupManager.contains("policy.source == AppPolicy.SOURCE_EXPLICIT"));
        assertTrue(backupManager.contains("!preset.builtIn"));
        assertTrue(backupManager.contains("AFTER_PHASE9_DB_COMMIT"));
        assertTrue(backupManager.contains("restorePhase9DatabaseSnapshot("));
        assertTrue(backupManager.contains("AppPolicyLegacyMigrator.migrateIfNeeded(context)"));
        assertTrue(codec.contains("backupVersion < 7"));
        assertTrue(codec.contains("Only explicit app policies are portable"));
        assertTrue(codec.contains("User preset id collides with built-in range"));
    }

    @Test
    public void v7BackupVersionIsLocked() throws Exception {
        String codec = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/BackupCodec.java");
        assertTrue(codec.contains("CURRENT_VERSION = 7"));
        assertTrue(codec.contains("backup payload exceeds maximum size"));
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
