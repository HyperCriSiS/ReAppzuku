package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Collections;
import java.util.Map;

public final class SchedulerRecoveryEvidenceStoreV2Test {
    private static final String PKG = "com.example.original";

    private static SchedulerRecoveryEvidenceV2 evidence(String pkg, int uid) {
        return SchedulerRecoveryEvidenceV2.rehydrate(pkg, uid, 0, false, 40,
                SchedulerRestrictionObservation.WhitelistKind.USER,
                Collections.singletonMap("WAKE_LOCK", SchedulerRestrictionObservation.AppOpMode.DEFAULT),
                Collections.singleton(42L), 1781000000000L, 1,
                SchedulerRecoveryEvidenceV2.Phase.OBSERVED);
    }

    private static final class MemoryBackend implements SchedulerRecoveryEvidenceStoreV2.Backend {
        String durable;
        String visible;
        int writes;
        boolean failNext;
        boolean throwNext;

        @Override public String read() { return visible; }

        @Override public boolean writeSync(String encoded) {
            writes++;
            visible = encoded; // even commit(false) may mutate SharedPreferences memory
            if (throwNext) {
                throwNext = false;
                throw new IllegalStateException("I/O unexpectedly interrupted");
            }
            if (failNext) {
                failNext = false;
                return false;
            }
            durable = encoded;
            return true;
        }

        MemoryBackend newProcess() {
            MemoryBackend other = new MemoryBackend();
            other.durable = durable;
            other.visible = durable;
            return other;
        }
    }

    @Test public void durableObservationPreservesOriginalAndNeverOverwritesIt() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryEvidenceStoreV2 store = new SchedulerRecoveryEvidenceStoreV2(backend);
        SchedulerRecoveryEvidenceV2 original = evidence(PKG, 10421);
        assertTrue(store.commitCaptured(original));
        assertEquals(1, backend.writes);
        assertFalse(store.commitCaptured(original));
        assertFalse(store.commitCaptured(evidence(PKG, 20111)));
        assertEquals(1, backend.writes);
        SchedulerRecoveryEvidenceV2 row =
                new SchedulerRecoveryEvidenceStoreV2(backend.newProcess()).snapshot().get(PKG);
        assertEquals(10421, row.uid);
        assertEquals(SchedulerRestrictionObservation.AppOpMode.DEFAULT,
                row.appOps.get("WAKE_LOCK"));
        assertFalse(store.isPoisoned()); // stale/invented write is not a storage failure
    }

    @Test public void reviewTransitionIsMonotonicAndDoesNotChangeEvidence() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryEvidenceStoreV2 store = new SchedulerRecoveryEvidenceStoreV2(backend);
        SchedulerRecoveryEvidenceV2 first = evidence(PKG, 10421);
        assertTrue(store.commitCaptured(first));
        assertTrue(store.requireManualReview(first.requireManualReview()));
        assertFalse(store.requireManualReview(first.requireManualReview()));
        assertFalse(store.commitCaptured(first));
        Map<String, SchedulerRecoveryEvidenceV2> snapshot =
                new SchedulerRecoveryEvidenceStoreV2(backend.newProcess()).snapshot();
        assertEquals(SchedulerRecoveryEvidenceV2.Phase.REVIEW_REQUIRED, snapshot.get(PKG).phase);
        assertTrue(SchedulerRecoveryEvidenceV2.sameOriginal(first, snapshot.get(PKG)));
        assertEquals(2, backend.writes);
    }

    @Test public void uncertainFirstCommitPoisonsAllAdaptersOfBackendObject() {
        MemoryBackend backend = new MemoryBackend();
        backend.failNext = true;
        SchedulerRecoveryEvidenceStoreV2 one = new SchedulerRecoveryEvidenceStoreV2(backend);
        SchedulerRecoveryEvidenceStoreV2 sibling = new SchedulerRecoveryEvidenceStoreV2(backend);
        assertFalse(one.commitCaptured(evidence(PKG, 10421)));
        assertTrue(one.isPoisoned());
        assertTrue(sibling.isPoisoned());
        assertThrows(IllegalStateException.class, sibling::snapshot);
        assertTrue(new SchedulerRecoveryEvidenceStoreV2(backend).isPoisoned());
        assertFalse(new SchedulerRecoveryEvidenceStoreV2(backend)
                .commitCaptured(evidence("com.example.another", 10500)));
        assertEquals(1, backend.writes);
        assertTrue(new SchedulerRecoveryEvidenceStoreV2(backend.newProcess()).snapshot().isEmpty());
    }

    @Test public void failedReviewWriteLeavesDurableObservationIntact() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryEvidenceStoreV2 store = new SchedulerRecoveryEvidenceStoreV2(backend);
        SchedulerRecoveryEvidenceV2 captured = evidence(PKG, 10421);
        assertTrue(store.commitCaptured(captured));
        backend.throwNext = true;
        assertFalse(store.requireManualReview(captured.requireManualReview()));
        assertTrue(store.isPoisoned());
        SchedulerRecoveryEvidenceV2 persisted =
                new SchedulerRecoveryEvidenceStoreV2(backend.newProcess()).snapshot().get(PKG);
        assertEquals(SchedulerRecoveryEvidenceV2.Phase.OBSERVED, persisted.phase);
        assertEquals(1, persisted.sequence);
    }

    @Test public void corruptAndLegacyJournalNeverSilentlyStartEmpty() {
        MemoryBackend backend = new MemoryBackend();
        backend.durable = backend.visible = "corrupt";
        SchedulerRecoveryEvidenceStoreV2 store = new SchedulerRecoveryEvidenceStoreV2(backend);
        assertFalse(store.commitCaptured(evidence(PKG, 10421)));
        assertTrue(store.isPoisoned());
        assertEquals(0, backend.writes);
        assertThrows(IllegalStateException.class, store::snapshot);

        MemoryBackend legacy = new MemoryBackend();
        legacy.durable = legacy.visible = SchedulerRecoveryJournalCodec.encode(Collections.emptyMap());
        SchedulerRecoveryEvidenceStoreV2 reader = new SchedulerRecoveryEvidenceStoreV2(legacy);
        assertThrows(IllegalStateException.class, reader::snapshot);
        assertFalse(reader.commitCaptured(evidence(PKG, 10421)));
        assertEquals(0, legacy.writes);
    }

    @Test public void recordsForDifferentPackagesCoexistAndRemainAppendOnly() {
        MemoryBackend backend = new MemoryBackend();
        SchedulerRecoveryEvidenceStoreV2 store = new SchedulerRecoveryEvidenceStoreV2(backend);
        assertTrue(store.commitCaptured(evidence(PKG, 10421)));
        assertTrue(store.commitCaptured(evidence("com.example.another", 10500)));
        assertEquals(2, store.snapshot().size());
        assertEquals(2, new SchedulerRecoveryEvidenceStoreV2(backend.newProcess()).snapshot().size());
    }
}
