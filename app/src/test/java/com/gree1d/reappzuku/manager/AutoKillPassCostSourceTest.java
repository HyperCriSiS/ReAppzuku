package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class AutoKillPassCostSourceTest {

    @Test
    public void aPassWithoutWantedAppsMakesNoActivityDumpOrPackageLookup() throws Exception {
        String killer = read("app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java");
        int filter = killer.indexOf("List<String> toKill = runningPackages.stream()");
        int veto = killer.indexOf("if (!wanted) {", filter);
        // The activity dump is taken lazily inside the filter, after the policy veto.
        assertEquals(1, count(killer, "\"dumpsys activity activities\""));
        assertTrue(veto > filter && veto < killer.indexOf("\"dumpsys activity activities\""));
        assertTrue(veto < killer.indexOf("pm.getApplicationInfo(pkg, 0)", filter));
        // Building the running set asks the package manager nothing.
        String running = killer.substring(killer.indexOf("for (Map.Entry<String, List<Integer>> entry : pidsByPackage.entrySet())"), filter);
        assertTrue(running.indexOf("getApplicationInfo") > running.indexOf("if (runningPackages.isEmpty())"));
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
        return n;
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
