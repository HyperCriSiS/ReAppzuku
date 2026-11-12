package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class WronglyAllowedOpsRepairTest {

    @Test
    public void liftingARestrictionGoesBackToTheDefaultsAndGrantsNothing() throws Exception {
        String manager = read("app/src/main/java/com/gree1d/reappzuku/manager/BackgroundAppManager.java");
        // Found on an Android 16 device: every app whose restriction had been lifted, or that
        // had got a new level, was exempt from power restrictions and from suspension.
        assertFalse(manager.contains("applyAllHardOps(packageName, \"allow\")"));
        assertFalse(manager.contains("applyMediumOps(packageName, \"allow\")"));
        assertFalse(manager.contains("applyManualOps(packageName, opsMask, \"allow\")"));
        assertEquals(3, manager.split(java.util.regex.Pattern.quote("applyAllHardOps(packageName, \"default\")"), -1).length - 1);
    }

    @Test
    public void theRepairReadsThePackagesOfTheMarkingOperation() {
        // The output of "cmd appops query-op" on that device.
        assertEquals(Arrays.asList("com.airbnb.android", "com.twitter.android", "com.philips.ph.homecare"),
                WronglyAllowedOpsRepair.packages(
                        "com.airbnb.android\ncom.twitter.android\r\n  com.philips.ph.homecare\n"));
        assertTrue(WronglyAllowedOpsRepair.packages("").isEmpty());
        assertTrue(WronglyAllowedOpsRepair.packages(null).isEmpty());
        // An error text is not a package.
        assertTrue(WronglyAllowedOpsRepair.packages(
                "ERROR: Unknown operation string: SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS\nNo operations.").isEmpty());
    }

    @Test
    public void theRepairOnlyRemovesWhatNoInstalledAppHoldsByItself() throws Exception {
        assertEquals("SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS", WronglyAllowedOpsRepair.MARKER_OP);
        assertEquals(Arrays.asList("SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS",
                "SYSTEM_EXEMPT_FROM_SUSPENSION", "ACCESS_NOTIFICATIONS", "RUN_USER_INITIATED_JOBS"),
                Arrays.asList(WronglyAllowedOpsRepair.OPS));
        String repair = read("app/src/main/java/com/gree1d/reappzuku/manager/WronglyAllowedOpsRepair.java");
        // Usage access and exact alarms may be the user's own grants; the background
        // restriction itself is the app's current level. None of them is reset here.
        for (String kept : new String[] {"\"GET_USAGE_STATS\"", "\"SCHEDULE_EXACT_ALARM\"",
                "\"RUN_ANY_IN_BACKGROUND\"", "\"RUN_IN_BACKGROUND\"", "\"WAKE_LOCK\""}) {
            assertFalse(kept, repair.contains(kept));
        }
        // The system gives the exemption to some of its own apps, and an owner may do so on purpose.
        assertTrue(repair.contains("if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;"));
        int managed = repair.indexOf("if (managed(context)) {");
        assertTrue(managed >= 0 && managed < repair.indexOf("cmd appops query-op --user current"));
        assertTrue(repair.contains("PrivilegedShell.AppOpMode.DEFAULT"));
        assertFalse(repair.contains("AppOpMode.ALLOW"));
        // Done only when every operation was set; no answer from the shell means: try again.
        assertTrue(repair.contains("if (complete) prefs.edit().putBoolean(KEY_DONE, true).apply();"));
        assertTrue(repair.contains("if (marked == null || marked.startsWith(\"ERROR:\")) return;"));
    }

    @Test
    public void theRepairReachesUsersWithoutAnyRestrictionLeft() throws Exception {
        assertTrue(read("app/src/main/java/com/gree1d/reappzuku/core/SuggestionLoader.java")
                .contains("WronglyAllowedOpsRepair.runOnce(context, shell);"));
        assertTrue(read("app/src/main/java/com/gree1d/reappzuku/manager/BackgroundAppManager.java")
                .contains("WronglyAllowedOpsRepair.runOnce(context, shellManager);"));
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
