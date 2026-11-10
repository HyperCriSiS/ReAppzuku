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
        int allow = body.indexOf("applyAllHardOps(packageName, \"allow\");", skip);
        int stop = body.indexOf("privilegedShell.forceStopPackage(packageName);");
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
