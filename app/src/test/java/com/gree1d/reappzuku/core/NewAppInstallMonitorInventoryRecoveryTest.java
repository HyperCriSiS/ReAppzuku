package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Inject failures and concurrent package observations into the same pure
 * inventory merge used after live PACKAGE_ADDED / periodic reconciliation.
 * No package manager, worker execution, root, or app preferences are touched.
 */
public class NewAppInstallMonitorInventoryRecoveryTest {
    private static Set<String> set(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    @Test
    public void failedPackageStaysUnrecordedAndRetriesOnNextPass() {
        Set<String> known = set("old.app");
        Set<String> installed = set("old.app", "new.app");
        assertEquals(set("new.app"),
                NewAppInstallMonitor.findNewPackages(known, installed));

        // The handler threw: no successful package is recorded.
        Set<String> afterFailed = NewAppInstallMonitor.mergeKnownAfterPass(
                known, known, Collections.emptySet(), installed);
        assertFalse(afterFailed.contains("new.app"));
        assertEquals(set("new.app"),
                NewAppInstallMonitor.findNewPackages(afterFailed, installed));

        Set<String> recovered = NewAppInstallMonitor.mergeKnownAfterPass(
                afterFailed, afterFailed, set("new.app"), installed);
        assertEquals(installed, recovered);
        assertTrue(NewAppInstallMonitor.findNewPackages(recovered, installed).isEmpty());
    }

    @Test
    public void concurrentBroadcastRecordingSurvivesStaleReconcileSnapshot() {
        Set<String> initial = set("old.app");
        Set<String> installed = set("old.app", "a.app", "b.app");
        Set<String> latestFromLiveReceiver = set("old.app", "b.app");
        Set<String> merged = NewAppInstallMonitor.mergeKnownAfterPass(
                latestFromLiveReceiver, initial, set("a.app"), installed);
        assertEquals(installed, merged);
        assertTrue(NewAppInstallMonitor.findNewPackages(merged, installed).isEmpty());

        // Repeating the same receiver/reconciliation event must be idempotent.
        assertEquals(merged, NewAppInstallMonitor.mergeKnownAfterPass(
                merged, initial, set("a.app", "b.app"), installed));
    }

    @Test
    public void uninstalledEntriesArePrunedEvenWhenKnownOrCompleted() {
        Set<String> known = set("removed.app", "old.app");
        Set<String> current = set("old.app", "new.app");
        Set<String> latest = set("old.app", "removed.app", "new.app");
        assertEquals(current, NewAppInstallMonitor.mergeKnownAfterPass(
                latest, known, set("removed.app", "new.app"), current));
    }

    @Test
    public void mergeDoesNotMutateCallerSetsAndEmptyBatchRemainsStable() {
        Set<String> latest = set("old.app");
        Set<String> known = set("old.app");
        Set<String> completed = set("new.app");
        Set<String> installed = set("old.app", "new.app");
        assertEquals(installed, NewAppInstallMonitor.mergeKnownAfterPass(
                latest, known, completed, installed));
        assertEquals(set("old.app"), latest);
        assertEquals(set("old.app"), known);
        assertEquals(set("new.app"), completed);
        assertEquals(set("old.app", "new.app"), installed);
        assertEquals(Collections.emptySet(), NewAppInstallMonitor.mergeKnownAfterPass(
                null, Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet()));
    }
}
