package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.manager.BackgroundAppManager.RestrictionType;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RestrictionDeltaPolicyTest {

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test
    public void unchangedAppIsNotTouched() {
        String hard = RestrictionDeltaPolicy.signature(RestrictionType.HARD, 1, 0, false);
        assertFalse(RestrictionDeltaPolicy.needsApply(hard, hard));
        assertTrue(RestrictionDeltaPolicy.needsApply(null, hard));
        assertTrue(RestrictionDeltaPolicy.needsApply(
                RestrictionDeltaPolicy.signature(RestrictionType.SOFT, 1, 0, false), hard));
    }

    @Test
    public void manualDetailsOnlyMatterForManualRestrictions() {
        assertEquals(
                RestrictionDeltaPolicy.signature(RestrictionType.MEDIUM, 1, 0, false),
                RestrictionDeltaPolicy.signature(RestrictionType.MEDIUM, 7, 40, true));

        String manual = RestrictionDeltaPolicy.signature(RestrictionType.MANUAL, 1, 40, false);
        assertNotEquals(manual, RestrictionDeltaPolicy.signature(RestrictionType.MANUAL, 3, 40, false));
        assertNotEquals(manual, RestrictionDeltaPolicy.signature(RestrictionType.MANUAL, 1, 45, false));
        assertNotEquals(manual, RestrictionDeltaPolicy.signature(RestrictionType.MANUAL, 1, 40, true));
    }

    @Test
    public void onlyOwnRestrictionsAreLifted() {
        Set<String> lift = RestrictionDeltaPolicy.packagesToLift(
                set("previous.kept", "previous.removed"),
                set("applied.kept", "applied.orphan"),
                set("external.removed"),
                set("previous.kept", "applied.kept", "new.app"));
        assertEquals(set("previous.removed", "applied.orphan", "external.removed"), lift);
    }

    @Test
    public void nothingIsLiftedWhenNothingWasOwned() {
        assertTrue(RestrictionDeltaPolicy.packagesToLift(
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                set("new.app")).isEmpty());
    }

    @Test
    public void everyLevelHasItsOwnSignature() {
        Set<String> signatures = new HashSet<>();
        for (RestrictionType type : RestrictionType.values()) {
            signatures.add(RestrictionDeltaPolicy.signature(type, 1, 0, false));
        }
        assertEquals(RestrictionType.values().length, signatures.size());
    }

    @Test
    public void onlyACompleteApplyIsRemembered() {
        assertTrue(RestrictionDeltaPolicy.applySucceeded(new int[] {3, 0}, true, true));
        assertFalse(RestrictionDeltaPolicy.applySucceeded(new int[] {3, 1}, true, true));
        assertFalse(RestrictionDeltaPolicy.applySucceeded(new int[] {0, 0}, true, true));
        assertFalse(RestrictionDeltaPolicy.applySucceeded(new int[] {3, 0}, false, true));
        assertFalse(RestrictionDeltaPolicy.applySucceeded(new int[] {3, 0}, true, false));
    }

    @Test
    public void failedLiftOfAnInstalledAppIsRetried() {
        assertFalse(RestrictionDeltaPolicy.liftCompleted(new int[] {0, 5}, true));
        assertTrue(RestrictionDeltaPolicy.liftCompleted(new int[] {2, 3}, true));
        assertTrue(RestrictionDeltaPolicy.liftCompleted(new int[] {0, 5}, false));
    }

    @Test
    public void untrackedFirstPassAlsoLiftsThePreviousSetAndExplicitRemovals() {
        assertEquals(set("live.orphan", "previous.removed", "explicit.removed"),
                RestrictionDeltaPolicy.packagesToLiftUntracked(
                        set("live.orphan", "kept"), set("previous.removed", "kept"),
                        set("explicit.removed"), set("kept")));
    }

    @Test
    public void explicitRequestsBypassTheUnchangedSkip() throws Exception {
        String source = readManager();
        int start = source.indexOf("    public void setBackgroundRestricted(");
        String body = source.substring(start, source.indexOf("\n    }\n", start));
        assertTrue(body.indexOf("targetPackages.add(packageName);")
                < body.indexOf("saveAppliedRestrictionSignature(packageName, null);"));
        // Tracking starts only after the live state was read successfully.
        assertTrue(source.contains("tracked = live.querySucceeded;"));
        // A queued watchdog repair re-checks the current set per app.
        assertTrue(source.contains("if (!getBackgroundRestrictedApps().contains(pkg)) continue;"));
    }

    @Test
    public void editorShowsTheLevelOfTheRestrictionSets() throws Exception {
        String editor = read("app/src/main/java/com/gree1d/reappzuku/ui/AppPolicyEditorActivity.java");
        assertTrue(editor.contains("BackgroundAppManager.storedRestrictionLevel(this, packageName);"));
        String dao = read("app/src/main/java/com/gree1d/reappzuku/db/AppPolicyDao.java");
        assertTrue(dao.contains("UPDATE app_policy SET backgroundRestriction = :level, updatedAt = :now"));
        assertTrue(dao.contains("AND source = 0"));
        String setup = read("app/src/main/java/com/gree1d/reappzuku/core/NewAppSetupCoordinator.java");
        assertTrue(setup.contains("policy.backgroundRestriction, true);"));
    }

    @Test
    public void userZeroBlockIsCutOutOfTheRightPackage() {
        String dump = "Verifiers:\n  none\n\nPackages:\n"
                + "  Package [com.other] (1):\n    User 0: ceDataInode=1 suspended=true enabled=0\n"
                + "  Package [com.example.app] (2):\n    userId=10123\n"
                + "    User 0: ceDataInode=2 suspended=false enabled=3\n      gids=[3003]\n"
                + "    User 10: ceDataInode=3 suspended=true enabled=0\n"
                + "  Package [com.example.app.extra] (3):\n    User 0: suspended=true enabled=0\n";
        String block = RestrictionsWatchdogManager.userZeroBlock(dump, "com.example.app");
        assertTrue(block.contains("suspended=false enabled=3"));
        assertTrue(block.contains("gids=[3003]"));
        assertFalse(block.contains("User 10:"));
        assertFalse(block.contains("com.example.app.extra"));
        assertEquals(null, RestrictionsWatchdogManager.userZeroBlock(dump, "com.missing"));
    }

    @Test
    public void policyLevelAndRestrictionTypeRoundTrip() {
        assertEquals(0, RestrictionDeltaPolicy.policyLevel(false, RestrictionType.HARD));
        assertEquals(null, RestrictionDeltaPolicy.typeForLevel(0));
        assertEquals(null, RestrictionDeltaPolicy.typeForLevel(99));
        for (RestrictionType type : RestrictionType.values()) {
            int level = RestrictionDeltaPolicy.policyLevel(true, type);
            assertTrue(level > 0);
            assertEquals(type, RestrictionDeltaPolicy.typeForLevel(level));
        }
    }

    @Test
    public void policyWritersExecuteTheStoredRestrictionLevel() throws Exception {
        String editor = read("app/src/main/java/com/gree1d/reappzuku/ui/AppPolicyEditorActivity.java");
        assertTrue(editor.indexOf("database.appPolicyDao().upsert(saved);")
                < editor.indexOf("BackgroundAppManager.applyPolicyRestriction("));
        String setup = read("app/src/main/java/com/gree1d/reappzuku/core/NewAppSetupCoordinator.java");
        assertTrue(setup.contains("BackgroundAppManager.applyPolicyRestriction("));
        // Legacy screens funnel through the apply pass, which updates explicit policies.
        assertTrue(readManager().contains(
                "shellExecutor.execute(this::syncExplicitPoliciesWithRestrictionSets);"));
    }

    @Test
    public void applyPassSkipsUnchangedAppsBeforeTouchingThem() throws Exception {
        String source = readManager();
        int start = source.indexOf("    private void applyBackgroundRestriction(Set<String> targetPackages,");
        String body = source.substring(start,
                source.indexOf("    public void reapplySavedBackgroundRestrictions(", start));
        int skip = body.indexOf("if (!RestrictionDeltaPolicy.needsApply(");
        int allow = body.indexOf("applyAllHardOps(packageName, \"default\");", skip);
        int stop = body.indexOf("stopAfterRestriction(packageName);");
        assertTrue(skip >= 0 && skip < allow && skip < stop);
        assertTrue(body.contains("RestrictionDeltaPolicy.packagesToLift("));
    }

    private static String readManager() throws Exception {
        return read("app/src/main/java/com/gree1d/reappzuku/manager/BackgroundAppManager.java");
    }

    private static String read(String relative) throws Exception {
        for (Path candidate : Arrays.asList(
                Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative));
            }
        }
        throw new java.io.IOException("Could not locate repository root");
    }
}
