package com.gree1d.reappzuku.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Single-process journal. Every store sharing a Backend object must share the
 * same lock and poisoned flag. Android adapters also share this gate by the
 * identity-cached SharedPreferences object. No cross-process file lock, disk
 * fsync/power-loss guarantee, scheduler integration or privileged mutation.
 */
public final class SchedulerRecoveryJournalStore
        implements SchedulerRecoveryTransaction.DurableStore {

    public interface Backend {
        /** null means never written; an empty string is corrupt. */
        String read();
        /** true only after a synchronous complete snapshot write succeeds. */
        boolean writeSync(String encoded);
    }

    /** Value deliberately does not retain its registry's weak key. */
    static final class Gate {
        boolean poisoned;
    }

    private static final Map<Backend, Gate> BACKEND_GATES = new WeakHashMap<>();

    private static Gate gateForBackend(Backend backend) {
        synchronized (BACKEND_GATES) {
            Gate gate = BACKEND_GATES.get(backend);
            if (gate == null) {
                gate = new Gate();
                BACKEND_GATES.put(backend, gate);
            }
            return gate;
        }
    }

    private final Backend backend;
    private final Gate gate;

    public SchedulerRecoveryJournalStore(Backend backend) {
        this(backend, checkedGate(backend));
    }

    private static Gate checkedGate(Backend backend) {
        if (backend == null) throw new IllegalArgumentException("missing backend");
        return gateForBackend(backend);
    }

    /** Allows the Android adapter to bind all wrappers of one prefs object. */
    SchedulerRecoveryJournalStore(Backend backend, Gate sharedGate) {
        if (backend == null || sharedGate == null) {
            throw new IllegalArgumentException("missing journal backend or safety gate");
        }
        this.backend = backend;
        this.gate = sharedGate;
    }

    public Map<String, SchedulerRecoveryTransaction.Record> snapshot() {
        synchronized (gate) {
            return snapshotLocked();
        }
    }

    private Map<String, SchedulerRecoveryTransaction.Record> snapshotLocked() {
        if (gate.poisoned) throw new IllegalStateException("journal storage uncertain");
        try {
            return SchedulerRecoveryJournalCodec.decode(backend.read());
        } catch (RuntimeException failedRead) {
            gate.poisoned = true;
            throw new IllegalStateException("recovery journal is not readable", failedRead);
        }
    }

    @Override public boolean commit(SchedulerRecoveryTransaction.Record record) {
        synchronized (gate) {
            if (gate.poisoned || record == null) return false;
            try {
                Map<String, SchedulerRecoveryTransaction.Record> update =
                        new HashMap<>(snapshotLocked());
                SchedulerRecoveryTransaction.Record previous = update.get(record.packageName);
                if (previous == null) {
                    // A newly captured record may be written as PREPARED/1, or as
                    // APPLYING/2 by beginLift's pre-mutation persistence guard.
                    if (!((record.phase == SchedulerRecoveryTransaction.Phase.PREPARED
                            && record.sequence == 1)
                            || (record.phase == SchedulerRecoveryTransaction.Phase.APPLYING
                            && record.sequence == 2))) {
                        return false;
                    }
                } else if (!validSuccessor(previous, record)) {
                    return false;
                }
                update.put(record.packageName, record);
                return writeLocked(update);
            } catch (RuntimeException invalidOrFailed) {
                gate.poisoned = true;
                return false;
            }
        }
    }

    /** Only completed rows may be pruned. Never discard a pending rollback. */
    public boolean removeResolved(String packageName) {
        synchronized (gate) {
            if (gate.poisoned || packageName == null) return false;
            try {
                Map<String, SchedulerRecoveryTransaction.Record> update =
                        new HashMap<>(snapshotLocked());
                SchedulerRecoveryTransaction.Record record = update.get(packageName);
                if (record == null) return true;
                if (record.phase != SchedulerRecoveryTransaction.Phase.RESOLVED) return false;
                update.remove(packageName);
                return writeLocked(update);
            } catch (RuntimeException invalidOrFailed) {
                gate.poisoned = true;
                return false;
            }
        }
    }

    private boolean writeLocked(Map<String, SchedulerRecoveryTransaction.Record> update) {
        String encoded = SchedulerRecoveryJournalCodec.encode(update);
        // A failed commit() can already have changed SharedPreferences memory.
        if (!backend.writeSync(encoded)) {
            gate.poisoned = true;
            return false;
        }
        return true;
    }

    public boolean isPoisoned() {
        synchronized (gate) {
            return gate.poisoned;
        }
    }

    private static boolean sameOriginal(SchedulerRecoveryTransaction.OriginalRestrictions a,
                                        SchedulerRecoveryTransaction.OriginalRestrictions b) {
        return a.appOpsMask == b.appOpsMask
                && a.standbyBucket == b.standbyBucket
                && a.deviceIdleWhitelisted == b.deviceIdleWhitelisted
                && a.suspended == b.suspended && a.enabled == b.enabled;
    }

    private static boolean oneOwnerChange(SchedulerRecoveryTransaction.Record old,
                                          SchedulerRecoveryTransaction.Record next) {
        return (next.owners.size() == old.owners.size() + 1
                && next.owners.containsAll(old.owners))
                || (next.owners.size() + 1 == old.owners.size()
                && old.owners.containsAll(next.owners));
    }

    /**
     * Prevent invented rollback values, owner rewrites and skipping a durable
     * uncertain phase. REVIEW_REQUIRED has no automatic outgoing transition.
     */
    private static boolean validSuccessor(SchedulerRecoveryTransaction.Record old,
                                          SchedulerRecoveryTransaction.Record next) {
        if (!old.packageName.equals(next.packageName)
                || old.version != next.version
                || old.sequence == Long.MAX_VALUE
                || next.sequence != old.sequence + 1
                || !sameOriginal(old.original, next.original)) {
            return false;
        }
        switch (old.phase) {
            case PREPARED:
                if (next.phase == SchedulerRecoveryTransaction.Phase.PREPARED) {
                    return oneOwnerChange(old, next);
                }
                if (next.phase == SchedulerRecoveryTransaction.Phase.APPLYING) {
                    return old.owners.equals(next.owners);
                }
                return next.phase == SchedulerRecoveryTransaction.Phase.RESOLVED
                        && old.owners.size() == 1 && next.owners.isEmpty();
            case APPLYING:
                if (next.phase == SchedulerRecoveryTransaction.Phase.ACTIVE) {
                    return old.owners.equals(next.owners);
                }
                return next.phase == SchedulerRecoveryTransaction.Phase.REVIEW_REQUIRED
                        && old.owners.size() == 1 && next.owners.isEmpty();
            case ACTIVE:
                if (next.phase == SchedulerRecoveryTransaction.Phase.ACTIVE) {
                    return oneOwnerChange(old, next);
                }
                return next.phase == SchedulerRecoveryTransaction.Phase.RESTORE_REQUIRED
                        && old.owners.size() == 1 && next.owners.isEmpty();
            case RESTORE_REQUIRED:
                return (next.phase == SchedulerRecoveryTransaction.Phase.RESTORING
                        || next.phase == SchedulerRecoveryTransaction.Phase.REVIEW_REQUIRED)
                        && old.owners.isEmpty() && next.owners.isEmpty();
            case RESTORING:
                return next.phase == SchedulerRecoveryTransaction.Phase.RESOLVED
                        && old.owners.isEmpty() && next.owners.isEmpty();
            case REVIEW_REQUIRED:
            case RESOLVED:
            default:
                return false;
        }
    }
}
