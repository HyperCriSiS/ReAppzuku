package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class SchedulerRecoveryTransactionTest {
    private static final class Disk implements SchedulerRecoveryTransaction.DurableStore {
        SchedulerRecoveryTransaction.Record persisted;
        SchedulerRecoveryTransaction.Record visible;
        int writes;
        int failAt = -1;

        @Override public boolean commit(SchedulerRecoveryTransaction.Record record) {
            writes++;
            // Model Android SharedPreferences: false may still alter in-memory values.
            visible = record;
            if (writes == failAt) return false;
            persisted = record;
            return true;
        }
    }

    private static SchedulerRecoveryTransaction.Record fresh(Long... ids) {
        return SchedulerRecoveryTransaction.prepare(
                "com.example.target", new HashSet<>(Arrays.asList(ids)),
                new SchedulerRecoveryTransaction.OriginalRestrictions(
                        3, 40, false, true, true), 1);
    }

    @Test public void durablePrepareMustSucceedBeforeAnyLift() {
        Disk disk = new Disk();
        disk.failAt = 1;
        AtomicInteger operations = new AtomicInteger();
        assertEquals(SchedulerRecoveryTransaction.Outcome.PERSISTENCE_FAILED,
                SchedulerRecoveryTransaction.beginLift(fresh(11L), disk,
                        () -> { operations.incrementAndGet(); return true; }));
        assertEquals(0, operations.get());
        assertNull(disk.persisted);
        assertEquals(SchedulerRecoveryTransaction.Phase.APPLYING, disk.visible.phase);
    }

    @Test public void successfulLiftPersistsUncertainBeforeActive() {
        Disk disk = new Disk();
        AtomicInteger calls = new AtomicInteger();
        assertEquals(SchedulerRecoveryTransaction.Outcome.COMPLETED,
                SchedulerRecoveryTransaction.beginLift(fresh(11L), disk,
                        () -> { calls.incrementAndGet(); return true; }));
        assertEquals(1, calls.get());
        assertEquals(2, disk.writes);
        assertEquals(SchedulerRecoveryTransaction.Phase.ACTIVE, disk.persisted.phase);
        assertEquals(3, disk.persisted.sequence);
    }

    @Test public void partialLiftNeverClaimsCompletion() {
        Disk disk = new Disk();
        assertEquals(SchedulerRecoveryTransaction.Outcome.OPERATION_UNCERTAIN,
                SchedulerRecoveryTransaction.beginLift(fresh(11L), disk, () -> false));
        assertEquals(SchedulerRecoveryTransaction.Phase.APPLYING, disk.persisted.phase);
        assertEquals(1, disk.writes);
    }

    @Test public void failedPostOperationCommitLeavesRecoveryEvidence() {
        Disk disk = new Disk();
        disk.failAt = 2;
        assertEquals(SchedulerRecoveryTransaction.Outcome.PERSISTENCE_FAILED,
                SchedulerRecoveryTransaction.beginLift(fresh(11L), disk, () -> true));
        assertEquals(SchedulerRecoveryTransaction.Phase.APPLYING, disk.persisted.phase);
        assertEquals(SchedulerRecoveryTransaction.Phase.ACTIVE, disk.visible.phase);
    }

    @Test public void lastOwnerRemovalPreservesOriginalAndRequestsRestore() {
        SchedulerRecoveryTransaction.Record prepared = fresh(11L, 12L);
        Disk disk = new Disk();
        SchedulerRecoveryTransaction.beginLift(prepared, disk, () -> true);
        SchedulerRecoveryTransaction.Record first = disk.persisted.removeOwner(11L);
        assertEquals(SchedulerRecoveryTransaction.Phase.ACTIVE, first.phase);
        assertEquals(Collections.singleton(12L), first.owners);
        SchedulerRecoveryTransaction.Record last = first.removeOwner(12L);
        assertEquals(SchedulerRecoveryTransaction.Phase.RESTORE_REQUIRED, last.phase);
        assertEquals(40, last.original.standbyBucket);
        assertTrue(last.owners.isEmpty());
        assertSame(last, last.removeOwner(12L));
    }

    @Test public void deletingWhileLiftIsUncertainRequiresReview() {
        Disk disk = new Disk();
        SchedulerRecoveryTransaction.beginLift(fresh(11L), disk, () -> false);
        assertEquals(SchedulerRecoveryTransaction.Phase.REVIEW_REQUIRED,
                disk.persisted.removeOwner(11L).phase);
    }

    @Test public void deletingBeforeMutationFinishesWithoutRestore() {
        SchedulerRecoveryTransaction.Record next = fresh(11L).removeOwner(11L);
        assertEquals(SchedulerRecoveryTransaction.Phase.RESOLVED, next.phase);
        assertEquals(3, next.original.appOpsMask);
    }

    @Test public void conflictCannotInvokeRestore() {
        SchedulerRecoveryTransaction.Record restored = activeToRestore();
        Disk disk = new Disk();
        AtomicInteger operations = new AtomicInteger();
        assertEquals(SchedulerRecoveryTransaction.Outcome.CONFLICT_REQUIRES_REVIEW,
                SchedulerRecoveryTransaction.restore(restored, false, disk,
                        () -> { operations.incrementAndGet(); return true; }));
        assertEquals(0, operations.get());
        assertEquals(SchedulerRecoveryTransaction.Phase.REVIEW_REQUIRED, disk.persisted.phase);
    }

    @Test public void failedPreRestoreCommitCannotInvokeRestore() {
        Disk disk = new Disk();
        disk.failAt = 1;
        AtomicInteger operations = new AtomicInteger();
        assertEquals(SchedulerRecoveryTransaction.Outcome.PERSISTENCE_FAILED,
                SchedulerRecoveryTransaction.restore(activeToRestore(), true, disk,
                        () -> { operations.incrementAndGet(); return true; }));
        assertEquals(0, operations.get());
        assertNull(disk.persisted);
    }

    @Test public void partialRestoreKeepsRollbackTupleForLaterInspection() {
        Disk disk = new Disk();
        assertEquals(SchedulerRecoveryTransaction.Outcome.OPERATION_UNCERTAIN,
                SchedulerRecoveryTransaction.restore(activeToRestore(), true, disk, () -> false));
        assertEquals(SchedulerRecoveryTransaction.Phase.RESTORING, disk.persisted.phase);
        assertEquals(40, disk.persisted.original.standbyBucket);
    }

    @Test public void successAndPostRestoreCommitFailureAreDistinct() {
        Disk disk = new Disk();
        assertEquals(SchedulerRecoveryTransaction.Outcome.COMPLETED,
                SchedulerRecoveryTransaction.restore(activeToRestore(), true, disk, () -> true));
        assertEquals(SchedulerRecoveryTransaction.Phase.RESOLVED, disk.persisted.phase);
        Disk failed = new Disk();
        failed.failAt = 2;
        assertEquals(SchedulerRecoveryTransaction.Outcome.PERSISTENCE_FAILED,
                SchedulerRecoveryTransaction.restore(activeToRestore(), true, failed, () -> true));
        assertEquals(SchedulerRecoveryTransaction.Phase.RESTORING, failed.persisted.phase);
    }

    @Test public void ownerAdditionIsIdempotentBoundedAndNeverAllowedDuringRestore() {
        SchedulerRecoveryTransaction.Record record = fresh(1L);
        assertSame(record, record.addOwner(1L));
        SchedulerRecoveryTransaction.Record more = record.addOwner(2L);
        assertEquals(2, more.owners.size());
        assertEquals(1, record.owners.size());
        try {
            activeToRestore().addOwner(13L);
            fail("expected refused owner addition");
        } catch (IllegalStateException expected) {
            // A newly activated schedule must be reconciled separately.
        }
    }

    @Test public void invalidInputFailsClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                SchedulerRecoveryTransaction.prepare("bad;pkg", Collections.singleton(1L),
                        new SchedulerRecoveryTransaction.OriginalRestrictions(0, 30, false, false, true), 1));
        assertThrows(IllegalArgumentException.class, () -> fresh(0L));
        assertThrows(IllegalArgumentException.class, () ->
                new SchedulerRecoveryTransaction.OriginalRestrictions(-1, 30, false, false, true));
        Set<Long> tooMany = new HashSet<>();
        for (long i = 1; i <= 16; i++) tooMany.add(i);
        assertThrows(IllegalArgumentException.class, () ->
                SchedulerRecoveryTransaction.prepare("com.example.pkg", tooMany,
                        new SchedulerRecoveryTransaction.OriginalRestrictions(0, 30, false, false, true), 1));
    }

    @Test public void retriesAreBoundedAndNotImmediatelyRepeated() {
        assertEquals(30_000L, SchedulerRecoveryTransaction.retryDelayMillis(1));
        assertEquals(60_000L, SchedulerRecoveryTransaction.retryDelayMillis(2));
        assertEquals(1_800_000L, SchedulerRecoveryTransaction.retryDelayMillis(100));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryTransaction.retryDelayMillis(0));
    }

    private static SchedulerRecoveryTransaction.Record activeToRestore() {
        Disk disk = new Disk();
        SchedulerRecoveryTransaction.beginLift(fresh(11L), disk, () -> true);
        return disk.persisted.removeOwner(11L);
    }
}
