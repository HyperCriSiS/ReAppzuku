package com.gree1d.reappzuku.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Single-process synchronous, append-only v2 evidence journal. A failed/throwing
 * backend can already have changed its memory image. All adapters sharing the
 * underlying backend or SharedPreferences instance must then fail closed.
 *
 * No cross-process CAS, power-loss atomicity, automatic recovery, manual
 * deletion, retry scheduling or privileged operation is implemented.
 */
public final class SchedulerRecoveryEvidenceStoreV2 {
    public interface Backend {
        /** Null means an unwritten v2 file; empty/malformed data is corruption. */
        String read();
        /** Must acknowledge only a synchronous complete write. */
        boolean writeSync(String encoded);
    }

    static final class Gate {
        boolean poisoned;
    }

    private static final Map<Backend, Gate> GATES = new WeakHashMap<>();

    private static Gate gateFor(Backend backend) {
        if (backend == null) throw new IllegalArgumentException("null backend");
        synchronized (GATES) {
            Gate gate = GATES.get(backend);
            if (gate == null) {
                gate = new Gate();
                GATES.put(backend, gate);
            }
            return gate;
        }
    }

    private final Backend backend;
    private final Gate gate;

    public SchedulerRecoveryEvidenceStoreV2(Backend backend) {
        this(backend, gateFor(backend));
    }

    SchedulerRecoveryEvidenceStoreV2(Backend backend, Gate gate) {
        if (backend == null || gate == null) {
            throw new IllegalArgumentException("missing store or safety gate");
        }
        this.backend = backend;
        this.gate = gate;
    }

    public Map<String, SchedulerRecoveryEvidenceV2> snapshot() {
        synchronized (gate) {
            return snapshotLocked();
        }
    }

    private Map<String, SchedulerRecoveryEvidenceV2> snapshotLocked() {
        if (gate.poisoned) throw new IllegalStateException("v2 journal persistence uncertain");
        try {
            return SchedulerRecoveryEvidenceCodecV2.decode(backend.read());
        } catch (RuntimeException failure) {
            gate.poisoned = true;
            throw new IllegalStateException("v2 journal unreadable; do not overwrite", failure);
        }
    }

    /** First capture only; never replace a possibly relevant original. */
    public boolean commitCaptured(SchedulerRecoveryEvidenceV2 record) {
        synchronized (gate) {
            if (gate.poisoned || record == null
                    || record.phase != SchedulerRecoveryEvidenceV2.Phase.OBSERVED
                    || record.sequence != 1) return false;
            Map<String, SchedulerRecoveryEvidenceV2> update;
            try {
                update = new HashMap<>(snapshotLocked());
            } catch (RuntimeException failure) {
                return false; // snapshotLocked already poisoned gate
            }
            if (update.containsKey(record.packageName)) return false;
            update.put(record.packageName, record);
            return writeLocked(update);
        }
    }

    /** Only monotonic change is a conservative, terminal manual-review marker. */
    public boolean requireManualReview(SchedulerRecoveryEvidenceV2 record) {
        synchronized (gate) {
            if (gate.poisoned || record == null
                    || record.phase != SchedulerRecoveryEvidenceV2.Phase.REVIEW_REQUIRED
                    || record.sequence != 2) return false;
            Map<String, SchedulerRecoveryEvidenceV2> update;
            try {
                update = new HashMap<>(snapshotLocked());
            } catch (RuntimeException failure) {
                return false;
            }
            SchedulerRecoveryEvidenceV2 prior = update.get(record.packageName);
            if (prior == null || prior.phase != SchedulerRecoveryEvidenceV2.Phase.OBSERVED
                    || prior.sequence != 1
                    || !SchedulerRecoveryEvidenceV2.sameOriginal(prior, record)) {
                return false;
            }
            update.put(record.packageName, record);
            return writeLocked(update);
        }
    }

    private boolean writeLocked(Map<String, SchedulerRecoveryEvidenceV2> update) {
        final String value;
        try {
            value = SchedulerRecoveryEvidenceCodecV2.encode(update);
        } catch (RuntimeException invalid) {
            return false; // no I/O was attempted
        }
        try {
            if (!backend.writeSync(value)) {
                gate.poisoned = true;
                return false;
            }
            return true;
        } catch (RuntimeException failure) {
            gate.poisoned = true;
            return false;
        }
    }

    public boolean isPoisoned() {
        synchronized (gate) {
            return gate.poisoned;
        }
    }
}
