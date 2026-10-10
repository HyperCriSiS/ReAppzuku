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

        void restart() { visible = durable; }
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
        backend.restart();
        assertEquals(SchedulerRecoveryTransaction.Phase.ACTIVE,
                new SchedulerRecoveryJournalStore(backend).snapshot().get("com.example.one").phase);
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
        backend.restart();
        assertEquals(SchedulerRecoveryTransaction.Phase.APPLYING,
                new SchedulerRecoveryJournalStore(backend).snapshot().get("com.example.one").phase);
    }

    @Test public void throwingBackendPoisonsStoreAndPreservesLastDurableValue() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryJournalStore store = new SchedulerRecoveryJournalStore(backend);
        assertTrue(store.commit(prepared("com.example.one")));
        backend.throwNext = true;
        assertFalse(store.commit(prepared("com.example.one").addOwner(2L)));
        assertTrue(store.isPoisoned());
        backend.restart();
        assertEquals(Collections.singleton(1L),
                new SchedulerRecoveryJournalStore(backend).snapshot().get("com.example.one").owners);
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
        backend.restart();
        assertEquals(SchedulerRecoveryTransaction.Phase.RESOLVED,
                new SchedulerRecoveryJournalStore(backend).snapshot().get("com.example.one").phase);
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
