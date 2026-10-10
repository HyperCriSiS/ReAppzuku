package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import org.junit.Test;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class SchedulerRecoveryJournalStoreTest {
    private static final class MemoryBackend implements SchedulerRecoveryJournalStore.Backend {
        String durable;
        String visible;
        int writes;
        boolean failNext;
        boolean throwNext;

        @Override public String read() { return visible; }

        @Override public boolean writeSync(String encoded) {
            writes++;
            visible = encoded; // SharedPreferences may already update its memory.
            if (throwNext) {
                throwNext = false;
                throw new IllegalStateException("device storage lost");
            }
            if (failNext) {
                failNext = false;
                return false;
            }
            durable = encoded;
            return true;
        }

        MemoryBackend freshProcess() {
            MemoryBackend fresh = new MemoryBackend();
            fresh.durable = durable;
            fresh.visible = durable;
            return fresh;
        }
    }

    private static SchedulerRecoveryTransaction.Record prepared(String pkg) {
        return SchedulerRecoveryTransaction.prepare(pkg, Collections.singleton(1L),
                new SchedulerRecoveryTransaction.OriginalRestrictions(
                        5, 40, true, false, true), 1);
    }

    @Test public void liftIsDurablyVisibleToFreshStoreBeforeOperation() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore store = new SchedulerRecoveryJournalStore(backend);
        AtomicInteger calls = new AtomicInteger();
        assertEquals(SchedulerRecoveryTransaction.Outcome.COMPLETED,
                SchedulerRecoveryTransaction.beginLift(prepared("com.example.one"), store, () -> {
                    calls.incrementAndGet();
                    assertEquals(SchedulerRecoveryTransaction.Phase.APPLYING,
                            new SchedulerRecoveryJournalStore(backend).snapshot()
                                    .get("com.example.one").phase);
                    return true;
                }));
        assertEquals(1, calls.get());
        MemoryBackend restarted = backend.freshProcess();
        assertEquals(SchedulerRecoveryTransaction.Phase.ACTIVE,
                new SchedulerRecoveryJournalStore(restarted).snapshot().get("com.example.one").phase);
    }

    @Test public void failedFirstCommitPreventsOperationAndPoisonStopsFollowingWrites() {
        MemoryBackend backend = new MemoryBackend();
        backend.failNext = true;
        SchedulerRecoveryJournalStore store = new SchedulerRecoveryJournalStore(backend);
        AtomicInteger calls = new AtomicInteger();
        assertEquals(SchedulerRecoveryTransaction.Outcome.PERSISTENCE_FAILED,
                SchedulerRecoveryTransaction.beginLift(prepared("com.example.one"), store, () -> {
                    calls.incrementAndGet();
                    return true;
                }));
        assertEquals(0, calls.get());
        assertTrue(store.isPoisoned());
        assertThrows(IllegalStateException.class, store::snapshot);
        assertFalse(store.commit(prepared("com.example.two")));
        assertEquals(1, backend.writes);
        backend.restart();
        assertTrue(new SchedulerRecoveryJournalStore(backend).snapshot().isEmpty());
    }

    @Test public void uncertainFinalCommitRemainsApplyingOnRestart() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore store = new SchedulerRecoveryJournalStore(backend);
        final AtomicInteger operations = new AtomicInteger();
        // First commit is valid, second is reported unsuccessful.
        assertEquals(SchedulerRecoveryTransaction.Outcome.PERSISTENCE_FAILED,
                SchedulerRecoveryTransaction.beginLift(prepared("com.example.one"), record -> {
                    if (record.phase == SchedulerRecoveryTransaction.Phase.ACTIVE) backend.failNext = true;
                    return store.commit(record);
                }, () -> { operations.incrementAndGet(); return true; }));
        assertEquals(1, operations.get());
        assertTrue(store.isPoisoned());
        MemoryBackend restarted = backend.freshProcess();
        assertEquals(SchedulerRecoveryTransaction.Phase.APPLYING,
                new SchedulerRecoveryJournalStore(restarted).snapshot().get("com.example.one").phase);
    }

    @Test public void throwingBackendPoisonsStoreAndPreservesLastDurableValue() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore store = new SchedulerRecoveryJournalStore(backend);
        assertTrue(store.commit(prepared("com.example.one")));
        backend.throwNext = true;
        assertFalse(store.commit(prepared("com.example.one").addOwner(2L)));
        assertTrue(store.isPoisoned());
        MemoryBackend restarted = backend.freshProcess();
        assertEquals(Collections.singleton(1L),
                new SchedulerRecoveryJournalStore(restarted).snapshot().get("com.example.one").owners);
    }

    @Test public void staleOrSameSequenceCannotReplaceExistingEntry() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore store = new SchedulerRecoveryJournalStore(backend);
        SchedulerRecoveryTransaction.Record first = prepared("com.example.one");
        assertTrue(store.commit(first));
        assertFalse(store.commit(first));
        assertTrue(store.commit(first.addOwner(2L)));
        assertFalse(store.commit(first));
        assertEquals(2, store.snapshot().get("com.example.one").owners.size());
        assertFalse(store.isPoisoned()); // rejected stale writes aren't I/O failures
    }

    @Test public void corruptedJournalNeverGetsOverwritten() {
        MemoryBackend backend = new MemoryBackend();
        backend.visible = "corrupt content";
        backend.durable = backend.visible;
        SchedulerRecoveryJournalStore store = new SchedulerRecoveryJournalStore(backend);
        assertThrows(IllegalStateException.class, store::snapshot);
        assertTrue(store.isPoisoned());
        assertFalse(store.commit(prepared("com.example.one")));
        assertEquals(0, backend.writes);
    }

    @Test public void onlyResolvedRecordMayBeRemovedAndOthersSurvive() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore store = new SchedulerRecoveryJournalStore(backend);
        SchedulerRecoveryTransaction.Record unresolved = prepared("com.example.one");
        assertTrue(store.commit(unresolved));
        assertTrue(store.commit(prepared("com.example.two")));
        assertFalse(store.removeResolved("com.example.one"));
        SchedulerRecoveryTransaction.Record completed = unresolved.removeOwner(1L);
        assertTrue(store.commit(completed));
        assertTrue(store.removeResolved("com.example.one"));
        assertTrue(store.removeResolved("com.example.one"));
        backend.restart();
        Map<String, SchedulerRecoveryTransaction.Record> restored =
                new SchedulerRecoveryJournalStore(backend).snapshot();
        assertFalse(restored.containsKey("com.example.one"));
        assertTrue(restored.containsKey("com.example.two"));
    }

    @Test public void failedRemovalPoisonedAndDurableRecordSurvivesRestart() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore store = new SchedulerRecoveryJournalStore(backend);
        SchedulerRecoveryTransaction.Record one = prepared("com.example.one");
        assertTrue(store.commit(one));
        assertTrue(store.commit(one.removeOwner(1)));
        backend.failNext = true;
        assertFalse(store.removeResolved("com.example.one"));
        assertTrue(store.isPoisoned());
        MemoryBackend restarted = backend.freshProcess();
        assertEquals(SchedulerRecoveryTransaction.Phase.RESOLVED,
                new SchedulerRecoveryJournalStore(restarted).snapshot().get("com.example.one").phase);
    }

    @Test public void aFailedCommitPoisonsOtherExistingAndFutureStoreInstances() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore before = new SchedulerRecoveryJournalStore(backend);
        SchedulerRecoveryJournalStore sibling = new SchedulerRecoveryJournalStore(backend);
        backend.failNext = true;
        assertFalse(before.commit(prepared("com.example.one")));
        assertTrue(sibling.isPoisoned());
        assertThrows(IllegalStateException.class, sibling::snapshot);
        SchedulerRecoveryJournalStore after = new SchedulerRecoveryJournalStore(backend);
        assertTrue(after.isPoisoned());
        assertFalse(after.commit(prepared("com.example.two")));
        assertEquals(1, backend.writes);
        assertTrue(new SchedulerRecoveryJournalStore(backend.freshProcess()).snapshot().isEmpty());
    }

    @Test public void poisonedSnapshotBlocksNewStoreEvenWhenMemoryLooksValid() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore writer = new SchedulerRecoveryJournalStore(backend);
        assertTrue(writer.commit(prepared("com.example.one")));
        backend.failNext = true;
        assertFalse(writer.commit(prepared("com.example.one").addOwner(2L)));
        assertThrows(IllegalStateException.class,
                () -> new SchedulerRecoveryJournalStore(backend).snapshot());
        assertEquals(Collections.singleton(1L), new SchedulerRecoveryJournalStore(
                backend.freshProcess()).snapshot().get("com.example.one").owners);
    }

    @Test public void transitioningMustNotRewriteOriginallyCapturedRestrictions() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore journal = new SchedulerRecoveryJournalStore(backend);
        SchedulerRecoveryTransaction.Record captured = prepared("com.example.one");
        assertTrue(journal.commit(captured));
        SchedulerRecoveryTransaction.Record forged = SchedulerRecoveryTransaction.rehydrate(
                captured.version, captured.packageName,
                new SchedulerRecoveryTransaction.OriginalRestrictions(999, 40, true, false, true),
                captured.owners, SchedulerRecoveryTransaction.Phase.APPLYING, 2);
        assertFalse(journal.commit(forged));
        assertEquals(5, journal.snapshot().get(captured.packageName).original.appOpsMask);
        assertFalse(journal.isPoisoned());
    }

    @Test public void unconfirmedPhaseSkippingCannotFalselyClaimCompletion() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore journal = new SchedulerRecoveryJournalStore(backend);
        SchedulerRecoveryTransaction.Record base = prepared("com.example.one");
        assertTrue(journal.commit(base));
        SchedulerRecoveryTransaction.Record forged = SchedulerRecoveryTransaction.rehydrate(
                base.version, base.packageName, base.original,
                base.owners, SchedulerRecoveryTransaction.Phase.ACTIVE, 2);
        assertFalse(journal.commit(forged));
        assertEquals(SchedulerRecoveryTransaction.Phase.PREPARED,
                journal.snapshot().get(base.packageName).phase);
    }

    @Test public void equalSizeOwnerSubstitutionIsForbidden() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore journal = new SchedulerRecoveryJournalStore(backend);
        SchedulerRecoveryTransaction.Record base = prepared("com.example.one");
        assertTrue(journal.commit(base));
        SchedulerRecoveryTransaction.Record forged = SchedulerRecoveryTransaction.rehydrate(
                base.version, base.packageName, base.original, Collections.singleton(2L),
                SchedulerRecoveryTransaction.Phase.PREPARED, 2);
        assertFalse(journal.commit(forged));
        assertEquals(Collections.singleton(1L), journal.snapshot().get(base.packageName).owners);
    }

    @Test public void manualReviewAndResolvedRowsCannotResumeAutomation() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore journal = new SchedulerRecoveryJournalStore(backend);
        SchedulerRecoveryTransaction.Record prepared = prepared("com.example.one");
        assertTrue(journal.commit(prepared));
        SchedulerRecoveryTransaction.Record applying = SchedulerRecoveryTransaction.rehydrate(
                prepared.version, prepared.packageName, prepared.original, prepared.owners,
                SchedulerRecoveryTransaction.Phase.APPLYING, 2);
        assertTrue(journal.commit(applying));
        SchedulerRecoveryTransaction.Record review = applying.removeOwner(1L);
        assertTrue(journal.commit(review));
        SchedulerRecoveryTransaction.Record forged = SchedulerRecoveryTransaction.rehydrate(
                review.version, review.packageName, review.original, Collections.emptySet(),
                SchedulerRecoveryTransaction.Phase.RESOLVED, review.sequence + 1);
        assertFalse(journal.commit(forged));
        assertEquals(SchedulerRecoveryTransaction.Phase.REVIEW_REQUIRED,
                journal.snapshot().get(prepared.packageName).phase);
    }

    @Test public void firstRecordCannotInventAnAdvancedRevision() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore journal = new SchedulerRecoveryJournalStore(backend);
        SchedulerRecoveryTransaction.Record one = prepared("com.example.one");
        assertFalse(journal.commit(SchedulerRecoveryTransaction.rehydrate(
                one.version, one.packageName, one.original, one.owners,
                SchedulerRecoveryTransaction.Phase.PREPARED, 900)));
        assertEquals(0, backend.writes);
    }

    @Test public void validOverlappingOwnerAndRestorePhasesStillWork() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore journal = new SchedulerRecoveryJournalStore(backend);
        SchedulerRecoveryTransaction.Record base = prepared("com.example.one");
        assertTrue(journal.commit(base));
        SchedulerRecoveryTransaction.Record extra = base.addOwner(2L);
        assertTrue(journal.commit(extra));
        assertEquals(SchedulerRecoveryTransaction.Outcome.COMPLETED,
                SchedulerRecoveryTransaction.beginLift(extra, journal, () -> true));
        SchedulerRecoveryTransaction.Record active = journal.snapshot().get(base.packageName);
        assertTrue(journal.commit(active.removeOwner(1L)));
        SchedulerRecoveryTransaction.Record remaining = journal.snapshot().get(base.packageName);
        assertEquals(SchedulerRecoveryTransaction.Phase.ACTIVE, remaining.phase);
        assertTrue(journal.commit(remaining.removeOwner(2L)));
        SchedulerRecoveryTransaction.Record restore = journal.snapshot().get(base.packageName);
        assertEquals(SchedulerRecoveryTransaction.Outcome.COMPLETED,
                SchedulerRecoveryTransaction.restore(restore, true, journal, () -> true));
        assertEquals(SchedulerRecoveryTransaction.Phase.RESOLVED,
                journal.snapshot().get(base.packageName).phase);
    }

    @Test public void corruptStateFailsBeforeAnyExternalOperation() {
        MemoryBackend backend = new MemoryBackend();
        backend.durable = "not valid";
        backend.visible = backend.durable;
        SchedulerRecoveryJournalStore store = new SchedulerRecoveryJournalStore(backend);
        AtomicInteger calls = new AtomicInteger();
        assertEquals(SchedulerRecoveryTransaction.Outcome.PERSISTENCE_FAILED,
                SchedulerRecoveryTransaction.beginLift(prepared("com.example.one"), store,
                        () -> { calls.incrementAndGet(); return true; }));
        assertEquals(0, calls.get());
    }
}