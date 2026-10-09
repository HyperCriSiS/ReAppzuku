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

    @Test
    public void packageLocalExceptionDoesNotSuppressOtherPackagesAndTriggersRetry() {
        Set<String> known = set("old.app");
        Set<String> installed = set("old.app", "good.app", "transient.app", "other.app");
        Set<String> added = NewAppInstallMonitor.findNewPackages(known, installed);
        Set<String> invoked = new HashSet<>();

        NewAppInstallMonitor.PackagePass first = NewAppInstallMonitor.processNewPackages(
                added, name -> {
                    invoked.add(name);
                    if ("transient.app".equals(name)) {
                        throw new IllegalStateException("simulated package-handler exception");
                    }
                });
        assertEquals("Every candidate must get a chance even after an exception", added, invoked);
        assertEquals(1, first.failedCount);
        assertEquals(set("good.app", "other.app"), first.completed);

        Set<String> durable = NewAppInstallMonitor.mergeKnownAfterPass(
                known, known, first.completed, installed);
        assertEquals(set("old.app", "good.app", "other.app"), durable);
        assertEquals(set("transient.app"),
                NewAppInstallMonitor.findNewPackages(durable, installed));

        Set<String> retried = new HashSet<>();
        NewAppInstallMonitor.PackagePass second = NewAppInstallMonitor.processNewPackages(
                NewAppInstallMonitor.findNewPackages(durable, installed), retried::add);
        assertEquals(set("transient.app"), retried);
        assertEquals(0, second.failedCount);
        assertEquals(set("transient.app"), second.completed);
        assertEquals(installed, NewAppInstallMonitor.mergeKnownAfterPass(
                durable, durable, second.completed, installed));
    }

    @Test
    public void everyFailedHandlerStaysEligibleForRetryAndInputsRemainUnchanged() {
        Set<String> candidates = set("first.app", "second.app");
        Set<String> observed = new HashSet<>();
        NewAppInstallMonitor.PackagePass result = NewAppInstallMonitor.processNewPackages(
                candidates, name -> {
                    observed.add(name);
                    throw new IllegalArgumentException("transient test failure");
                });
        assertEquals(candidates, observed);
        assertEquals(candidates, set("first.app", "second.app"));
        assertEquals(2, result.failedCount);
        assertTrue(result.completed.isEmpty());
        assertEquals(candidates, NewAppInstallMonitor.findNewPackages(
                Collections.emptySet(), candidates));
    }

    @Test
    public void successfulOrEmptyPackagePassNeedsNoRetry() {
        NewAppInstallMonitor.PackagePass empty = NewAppInstallMonitor.processNewPackages(
                Collections.emptySet(), name -> {
                    throw new AssertionError("Handler must not run for empty input");
                });
        assertEquals(0, empty.failedCount);
        assertTrue(empty.completed.isEmpty());

        Set<String> candidates = set("single.app");
        NewAppInstallMonitor.PackagePass success = NewAppInstallMonitor.processNewPackages(
                candidates, name -> assertEquals("single.app", name));
        assertEquals(0, success.failedCount);
        assertEquals(candidates, success.completed);
    }

}