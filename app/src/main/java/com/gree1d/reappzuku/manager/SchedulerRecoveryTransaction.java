package com.gree1d.reappzuku.manager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure, Android-independent model for future Restrictions Scheduler rollback.
 *
 * NOT wired to the live scheduler. The caller must serialize changes by package,
 * capture every supported original restriction BEFORE preparing a record, and
 * provide a durable store. No root/Shizuku command is executed by this class.
 */
public final class SchedulerRecoveryTransaction {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_OWNERS = 15;
    public static final int MAX_PACKAGE_LENGTH = 255;

    private static final Pattern PACKAGE_NAME =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    private static final long BASE_RETRY_MILLIS = 30_000L;
    private static final long MAX_RETRY_MILLIS = 30L * 60_000L;

    private SchedulerRecoveryTransaction() {}

    public enum Phase {
        PREPARED, APPLYING, ACTIVE, RESTORE_REQUIRED, RESTORING, REVIEW_REQUIRED, RESOLVED
    }

    public enum Outcome {
        COMPLETED, PERSISTENCE_FAILED, OPERATION_UNCERTAIN, CONFLICT_REQUIRES_REVIEW
    }

    /**
     * Captured values are evidence, not guessed defaults. These fields form an
     * initial typed schema; integration must prove that each one can be safely
     * read/restored on supported Android versions before invoking operations.
     */
    public static final class OriginalRestrictions {
        public final int appOpsMask;
        public final int standbyBucket;
        public final boolean deviceIdleWhitelisted;
        public final boolean suspended;
        public final boolean enabled;

        public OriginalRestrictions(int appOpsMask, int standbyBucket,
                                    boolean deviceIdleWhitelisted,
                                    boolean suspended, boolean enabled) {
            if (appOpsMask < 0 || standbyBucket < 0 || standbyBucket > 1000) {
                throw new IllegalArgumentException("invalid captured restriction value");
            }
            this.appOpsMask = appOpsMask;
            this.standbyBucket = standbyBucket;
            this.deviceIdleWhitelisted = deviceIdleWhitelisted;
            this.suspended = suspended;
            this.enabled = enabled;
        }
    }

    public static final class Record {
        public final int version;
        public final String packageName;
        public final OriginalRestrictions original;
        public final Set<Long> owners;
        public final Phase phase;
        public final long sequence;

        private Record(String packageName, OriginalRestrictions original, Set<Long> owners,
                       Phase phase, long sequence) {
            if (packageName == null || packageName.length() > MAX_PACKAGE_LENGTH
                    || !PACKAGE_NAME.matcher(packageName).matches()) {
                throw new IllegalArgumentException("invalid package name");
            }
            if (original == null || phase == null || sequence < 1) {
                throw new IllegalArgumentException("missing recovery data");
            }
            if (owners == null || owners.size() > MAX_OWNERS) {
                throw new IllegalArgumentException("invalid schedule owner count");
            }
            for (Long id : owners) {
                if (id == null || id <= 0) {
                    throw new IllegalArgumentException("invalid schedule owner");
                }
            }
            if (owners.isEmpty() && (phase == Phase.PREPARED || phase == Phase.ACTIVE
                    || phase == Phase.APPLYING)) {
                throw new IllegalArgumentException("live recovery record has no owners");
            }
            if (!owners.isEmpty() && (phase == Phase.RESTORE_REQUIRED
                    || phase == Phase.RESTORING || phase == Phase.RESOLVED)) {
                throw new IllegalArgumentException("restore phase still has schedule owners");
            }
            this.version = FORMAT_VERSION;
            this.packageName = packageName;
            this.original = original;
            this.owners = Collections.unmodifiableSet(new HashSet<>(owners));
            this.phase = phase;
            this.sequence = sequence;
        }

        private Record next(Set<Long> owners, Phase phase) {
            if (sequence == Long.MAX_VALUE) {
                throw new IllegalStateException("recovery sequence exhausted");
            }
            return new Record(packageName, original, owners, phase, sequence + 1);
        }

        /** Owner addition during a restoration must be reconciled externally. */
        public Record addOwner(long id) {
            if (id <= 0) throw new IllegalArgumentException("invalid owner");
            if (owners.contains(id)) return this;
            if (phase != Phase.PREPARED && phase != Phase.ACTIVE) {
                throw new IllegalStateException("cannot add owner to uncertain/restoring record");
            }
            Set<Long> next = new HashSet<>(owners);
            next.add(id);
            return next(next, phase);
        }

        /** Never discard the captured original tuple on last-owner deletion. */
        public Record removeOwner(long id) {
            if (!owners.contains(id)) return this;
            Set<Long> next = new HashSet<>(owners);
            next.remove(id);
            if (!next.isEmpty()) return next(next, phase);
            if (phase == Phase.PREPARED) return next(next, Phase.RESOLVED);
            if (phase == Phase.ACTIVE) return next(next, Phase.RESTORE_REQUIRED);
            // APPLYING is indeterminate: mutation may have happened before a crash.
            return next(next, Phase.REVIEW_REQUIRED);
        }
    }

    public interface DurableStore {
        /** true ONLY after the complete record is persisted, not just visible in memory. */
        boolean commit(Record record);
    }

    public interface Operation {
        /** false may mean partially applied; never assume rollback from this value. */
        boolean execute();
    }

    public static Record prepare(String packageName, Set<Long> owners,
                                 OriginalRestrictions original, long sequence) {
        return new Record(packageName, original, owners, Phase.PREPARED, sequence);
    }

    /**
     * Commit APPLYING before any external operation. Failed/uncertain execution
     * leaves durable APPLYING data for explicit state inspection, never blind replay.
     */
    public static Outcome beginLift(Record record, DurableStore store, Operation lift) {
        require(record, Phase.PREPARED, store, lift);
        Record applying = record.next(record.owners, Phase.APPLYING);
        if (!commitSafely(store, applying)) return Outcome.PERSISTENCE_FAILED;
        if (!executeSafely(lift)) return Outcome.OPERATION_UNCERTAIN;
        return commitSafely(store, applying.next(applying.owners, Phase.ACTIVE))
                ? Outcome.COMPLETED : Outcome.PERSISTENCE_FAILED;
    }

    /**
     * The caller must first observe the actual live package restrictions and
     * confirm no user change or overlapping schedule conflicts with restoration.
     * False never executes the restore operation.
     */
    public static Outcome restore(Record record, boolean actualStateMatches,
                                  DurableStore store, Operation operation) {
        require(record, Phase.RESTORE_REQUIRED, store, operation);
        if (!actualStateMatches) {
            return commitSafely(store, record.next(record.owners, Phase.REVIEW_REQUIRED))
                    ? Outcome.CONFLICT_REQUIRES_REVIEW : Outcome.PERSISTENCE_FAILED;
        }
        Record restoring = record.next(record.owners, Phase.RESTORING);
        if (!commitSafely(store, restoring)) return Outcome.PERSISTENCE_FAILED;
        if (!executeSafely(operation)) return Outcome.OPERATION_UNCERTAIN;
        return commitSafely(store, restoring.next(restoring.owners, Phase.RESOLVED))
                ? Outcome.COMPLETED : Outcome.PERSISTENCE_FAILED;
    }

    public static long retryDelayMillis(int failedAttempts) {
        if (failedAttempts < 1) throw new IllegalArgumentException("attempts must be positive");
        long delay = BASE_RETRY_MILLIS;
        for (int i = 1; i < failedAttempts && delay < MAX_RETRY_MILLIS; i++) {
            delay = Math.min(MAX_RETRY_MILLIS, delay * 2);
        }
        return delay;
    }

    // Runtime failures have the same indeterminate effects as boolean failures.
    // In particular a failed persistence attempt may already change the visible map.
    private static boolean commitSafely(DurableStore store, Record record) {
        try {
            return store.commit(record);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean executeSafely(Operation operation) {
        try {
            return operation.execute();
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static void require(Record record, Phase phase, DurableStore store, Operation op) {
        if (record == null || record.phase != phase || store == null || op == null) {
            throw new IllegalArgumentException("invalid recovery transition");
        }
    }
}
