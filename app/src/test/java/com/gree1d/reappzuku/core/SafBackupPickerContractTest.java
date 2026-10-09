package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Guard the intentional one-shot DocumentsUI picker contract. This is a
 * source-level guard, not evidence of OS-granted long-lived URI permissions.
 */
public final class SafBackupPickerContractTest {
    @Test
    public void backupPickersStayOneShotWithoutImplicitPersistableGrantAcquisition()
            throws IOException {
        String source = source("app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivity.java");
        assertTrue(source.contains("new ActivityResultContracts.CreateDocument("));
        assertTrue(source.contains("new ActivityResultContracts.OpenDocument("));
        assertFalse(source.contains("takePersistableUriPermission("));
    }

    private static String source(String path) throws IOException {
        for (Path candidate : new Path[]{Paths.get(path), Paths.get("..", path),
                Paths.get("..", "..", path)}) {
            if (Files.isRegularFile(candidate)) return Files.readString(candidate);
        }
        throw new IOException("Missing source contract file: " + path);
    }
}
