package com.gree1d.reappzuku.core;

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

public class CodeQlHardeningPolicyTest {
    @Test
    public void appOpsRelativeTimePatternAvoidsNestedAmbiguousRepetition() throws Exception {
        String source = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/utils/triggers/analyzers/DozeOpsAnalyzer.java");

        assertTrue(source.contains(
                "time=\\\\+([0-9dhms]+(?:\\\\s+[0-9dhms]+)*)\\\\s+ago"));
        assertFalse(source.contains(
                "time=\\\\+([\\\\d]+[\\\\dhms]+(?:\\\\s*[\\\\dhms]+)*)\\\\s+ago"));
    }

    @Test
    public void shizukuUserServiceUsesAbsolutePlatformShell() throws Exception {
        String source = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/shell/ShizukuUserServiceImpl.java");

        String absoluteLaunch = "new String[] { \"/system/bin/sh\", \"-c\", command }";
        assertEquals(2, count(source, absoluteLaunch));
        assertFalse(source.contains("new String[] { \"sh\", \"-c\", command }"));
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
