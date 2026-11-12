package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WronglyAllowedOpsRepairTest {

    // ---- lifting ----

    @Test
    public void liftingARestrictionNeverSetsAnOperationToAllow() throws Exception {
        String manager = read("app/src/main/java/com/gree1d/reappzuku/manager/BackgroundAppManager.java");
        // Found on an Android 16 device: every app whose restriction had been lifted, or that
        // had got a new level, was exempt from power restrictions and from suspension.
        assertFalse(manager.contains("\"allow\");"));
        assertFalse(manager.contains("AppOpMode.ALLOW"));
        int start = manager.indexOf("    private void applyBackgroundRestriction(Set<String> targetPackages,");
        String body = manager.substring(start,
                manager.indexOf("    public void reapplySavedBackgroundRestrictions(", start));
        assertEquals(2, body.split(java.util.regex.Pattern.quote("liftRestrictedOps(packageName);"), -1).length - 1);
    }

    @Test
    public void onlyOperationsThatAreRestrictedNowAreSetBack() {
        // What "cmd appops get" printed on that device, one operation per line.
        String output = String.join("\n",
                "RUN_ANY_IN_BACKGROUND=RUN_ANY_IN_BACKGROUND: ignore ",
                "RUN_IN_BACKGROUND=RUN_IN_BACKGROUND: ignore; rejectTime=+2m3s ago ",
                "GET_USAGE_STATS=GET_USAGE_STATS: allow ",
                "WAKE_LOCK=WAKE_LOCK: allow; time=+150d22h58m55s788ms ago; duration=+1ms ",
                "SCHEDULE_EXACT_ALARM=No operations. ",
                "ACCESS_NOTIFICATIONS=ACCESS_NOTIFICATIONS: default ",
                "START_FOREGROUND=Uid mode: START_FOREGROUND: ignore ",
                "INTERACT_ACROSS_PROFILES=Uid mode: INTERACT_ACROSS_PROFILES: allow INTERACT_ACROSS_PROFILES: ignore ",
                "garbage");
        // A usage access the user granted is "allow" and is left alone. A uid mode is the
        // system's own bookkeeping; only the package's setting counts.
        assertEquals(Arrays.asList("RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND", "INTERACT_ACROSS_PROFILES"),
                RestrictionDeltaPolicy.opsInMode(output, "ignore"));
        assertEquals(Arrays.asList("GET_USAGE_STATS", "WAKE_LOCK"),
                RestrictionDeltaPolicy.opsInMode(output, "allow"));
        assertTrue(RestrictionDeltaPolicy.opsInMode(null, "ignore").isEmpty());
        assertTrue(RestrictionDeltaPolicy.opsInMode("", "ignore").isEmpty());
    }

    @Test
    public void aLiftWithNothingLeftToResetCountsAsDone() throws Exception {
        String manager = read("app/src/main/java/com/gree1d/reappzuku/manager/BackgroundAppManager.java");
        // The callers treat zero set operations as a failed lift.
        assertTrue(manager.contains("return new int[]{fail == 0 ? Math.max(ok, 1) : ok, fail};"));
        // No answer from the shell is a failure, not an empty lift.
        assertTrue(manager.contains("if (output == null) return new int[]{0, 1};"));
    }

    // ---- repairing what earlier versions left ----

    // The query output on the device, with a restricted app that only holds the suspension exemption.
    private static final String QUERY = String.join("\n",
            "---SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS---",
            "com.airbnb.android",
            "com.twitter.android\r",
            "---SYSTEM_EXEMPT_FROM_SUSPENSION---",
            "com.airbnb.android",
            "com.example.unmarked",
            "---ACCESS_NOTIFICATIONS---",
            "ERROR: something on stderr",
            "com.airbnb.android",
            "com.twitter.android",
            "---RUN_USER_INITIATED_JOBS---");

    @Test
    public void theQueryAsksForEveryOperationOnce() {
        String command = WronglyAllowedOpsRepair.queryCommand();
        for (String op : WronglyAllowedOpsRepair.OPS) {
            assertEquals(1, command.split(java.util.regex.Pattern.quote(
                    "cmd appops query-op --user current " + op + " allow;"), -1).length - 1);
        }
    }

    @Test
    public void anOperationIsOnlyResetWhereItIsAllowed() {
        Map<String, List<String>> allowed = WronglyAllowedOpsRepair.allowedByOp(QUERY);
        assertEquals(Arrays.asList("SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS",
                "SYSTEM_EXEMPT_FROM_SUSPENSION", "ACCESS_NOTIFICATIONS"),
                WronglyAllowedOpsRepair.opsToReset(allowed, "com.airbnb.android"));
        // A Manual restriction of this app may hold the suspension exemption at "ignore":
        // the app is then not in that list and the operation stays as it is.
        assertEquals(Arrays.asList("SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS", "ACCESS_NOTIFICATIONS"),
                WronglyAllowedOpsRepair.opsToReset(allowed, "com.twitter.android"));
        // Without the marker nothing is touched, whatever else the app is allowed.
        assertTrue(WronglyAllowedOpsRepair.opsToReset(allowed, "com.example.unmarked").isEmpty());
        assertTrue(WronglyAllowedOpsRepair.opsToReset(allowed, "com.example.other").isEmpty());
    }

    @Test
    public void anUnansweredMarkerIsNotAnEmptyList() {
        // The shell said nothing: the repair is asked for again instead of marked as done.
        assertFalse(WronglyAllowedOpsRepair.allowedByOp(null)
                .containsKey(WronglyAllowedOpsRepair.MARKER_OP));
        assertFalse(WronglyAllowedOpsRepair.allowedByOp("ERROR: su: not found")
                .containsKey(WronglyAllowedOpsRepair.MARKER_OP));
        // Answered and empty: nothing to repair.
        assertEquals(Collections.emptyList(), WronglyAllowedOpsRepair.allowedByOp(
                "---SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS---\n").get(WronglyAllowedOpsRepair.MARKER_OP));
    }

    @Test
    public void theRepairLeavesTheUsersOwnGrantsAndTheSystemsAppsAlone() throws Exception {
        assertEquals(Arrays.asList("SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS",
                "SYSTEM_EXEMPT_FROM_SUSPENSION", "ACCESS_NOTIFICATIONS", "RUN_USER_INITIATED_JOBS"),
                Arrays.asList(WronglyAllowedOpsRepair.OPS));
        String repair = read("app/src/main/java/com/gree1d/reappzuku/manager/WronglyAllowedOpsRepair.java");
        for (String kept : new String[] {"\"GET_USAGE_STATS\"", "\"SCHEDULE_EXACT_ALARM\"",
                "\"RUN_ANY_IN_BACKGROUND\"", "\"RUN_IN_BACKGROUND\"", "\"WAKE_LOCK\""}) {
            assertFalse(kept, repair.contains(kept));
        }
        assertTrue(repair.contains("if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;"));
        int managed = repair.indexOf("if (managed(context)) {");
        assertTrue(managed >= 0 && managed < repair.indexOf("runShellCommandAndGetFullOutput(queryCommand())"));
        assertFalse(repair.contains("AppOpMode.ALLOW"));
        // One repair at a time, done only when every operation was set, logged only when one was.
        assertTrue(repair.contains("public static synchronized void runOnce("));
        assertTrue(repair.contains("if (complete) prefs.edit().putBoolean(KEY_DONE, true).apply();"));
        assertTrue(repair.contains("if (reset > 0) {"));
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
