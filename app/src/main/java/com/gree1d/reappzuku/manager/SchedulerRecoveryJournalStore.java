package com.gree1d.reappzuku.manager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Single-process, single-writer crash-recovery journal boundary. A backend must
 * acknowledge ONLY synchronous durable writes. Failed writes poison this store
 * instance because SharedPreferences can update memory even on commit(false).
 *
 * No multi-process CAS, flash power-loss atomicity, scheduler integration or
 * privileged operations are claimed.
 */
public final class SchedulerRecoveryJournalStore
        implements SchedulerRecoveryTransaction.DurableStore {

    public interface Backend {
        /** null means the journal has never been written, not an empty string. */
        String read();
        /** Must synchronously commit the entire encoded snapshot. */
        boolean writeSync(String encoded);
    }

    private final Backend backend;
    private boolean poisoned;

    public SchedulerRecoveryJournalStore(Backend backend) {
        if (backend == null) throw new IllegalArgumentException("missing backend");
        this.backend = backend;
    }

    public synchronized Map<String, SchedulerRecoveryTransaction.Record> snapshot() {
        if (poisoned) throw new IllegalStateException("journal write uncertain; reopen storage");
        try {
            return SchedulerRecoveryJournalCodec.decode(backend.read());
        } catch (RuntimeException failure) {
            poisoned = true;
            throw new IllegalStateException("recovery journal is not readable", failure);
        }
    }

    @Override public synchronized boolean commit(SchedulerRecoveryTransaction.Record record) {
        if (poisoned || record == null) return false;
        try {
            Map<String, SchedulerRecoveryTransaction.Record> update =
                    new HashMap<>(snapshot());
            SchedulerRecoveryTransaction.Record previous = update.get(record.packageName);
            if (previous == null) {
                // The first durable phase may be APPLYING (beginLift's pre-op guard).
                if (record.phase != SchedulerRecoveryTransaction.Phase.PREPARED
                        && record.phase != SchedulerRecoveryTransaction.Phase.APPLYING) {
                    return false;
                }
            } else if (previous.sequence == Long.MAX_VALUE
                    || record.sequence != previous.sequence + 1) {
                // No stale replays, revision rollback or same-sequence overwrite.
                return false;
            }
            update.put(record.packageName, record);
            return write(update);
        } catch (RuntimeException invalidOrFailed) {
            // In particular a false/throwing backend can have changed memory.
            poisoned = true;
            return false;
        }
    }

    /** Only successfully resolved rows can be discarded. */
    public synchronized boolean removeResolved(String packageName) {
        if (poisoned || packageName == null) return false;
        try {
            Map<String, SchedulerRecoveryTransaction.Record> update =
                    new HashMap<>(snapshot());
            SchedulerRecoveryTransaction.Record record = update.get(packageName);
            if (record == null) return true;
            if (record.phase != SchedulerRecoveryTransaction.Phase.RESOLVED) return false;
            update.remove(packageName);
            return write(update);
        } catch (RuntimeException invalidOrFailed) {
            poisoned = true;
            return false;
        }
    }

    private boolean write(Map<String, SchedulerRecoveryTransaction.Record> update) {
        String encoded = SchedulerRecoveryJournalCodec.encode(update);
        // A false result may already have changed a SharedPreferences in-memory map.
        if (!backend.writeSync(encoded)) {
            poisoned = true;
            return false;
        }
        return true;
    }

    public synchronized boolean isPoisoned() {
        return poisoned;
    }
}
