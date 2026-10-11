package com.gree1d.reappzuku.manager;

import com.gree1d.reappzuku.core.PackageNameValidator;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Read-only diagnostic for a short, bounded observation window.
 *
 * It double-reads the installed identity, captures the complete requested
 * restriction tuple twice, then double-reads identity again. ANY missing,
 * unsupported, contradictory or changing observation fails closed.
 *
 * CONSISTENT_DIAGNOSTIC is NOT rollback authority. A package may be replaced
 * immediately after the final read, a shared UID may be invisible, and
 * package/UID AppOps absence cannot be reconstructed from legacy RZJ2/v1.
 * No scheduler/privileged caller is connected to this class.
 */
public final class SchedulerRestrictionConsistencyDiagnostic {
    private SchedulerRestrictionConsistencyDiagnostic() {}

    public enum Status {
        CONSISTENT_DIAGNOSTIC,
        INVALID_INPUT,
        IDENTITY_UNKNOWN,
        IDENTITY_CHANGED,
        RESTRICTIONS_UNKNOWN,
        RESTRICTIONS_CHANGED,
        UID_MISMATCH
    }

    public static final class Result {
        public final Status status;
        /** Null unless both identity and the two observations were consistent. */
        public final SchedulerInstalledPackageIdentity.Snapshot identity;
        /** Null on every failure, including any incomplete read. */
        public final SchedulerRestrictionObservation.Snapshot restrictions;

        private Result(Status status,
                SchedulerInstalledPackageIdentity.Snapshot identity,
                SchedulerRestrictionObservation.Snapshot restrictions) {
            this.status = status;
            this.identity = identity;
            this.restrictions = restrictions;
        }

        public boolean consistentDiagnosticOnly() {
            return status == Status.CONSISTENT_DIAGNOSTIC;
        }
    }

    private static Result failed(Status status) {
        return new Result(status, null, null);
    }

    private static boolean validOps(String[] ops) {
        if (ops == null || ops.length == 0 || ops.length > 16) return false;
        Set<String> seen = new HashSet<>();
        for (String op : ops) {
            if (op == null || !op.matches("[A-Z][A-Z0-9_]{0,63}")
                    || !seen.add(op)) return false;
        }
        return true;
    }

    private static Status identityFailure(SchedulerInstalledPackageIdentity.Status status) {
        return status == SchedulerInstalledPackageIdentity.Status.CHANGED
                ? Status.IDENTITY_CHANGED : Status.IDENTITY_UNKNOWN;
    }

    private static boolean equalRestrictions(
            SchedulerRestrictionObservation.Snapshot left,
            SchedulerRestrictionObservation.Snapshot right) {
        if (left == null || right == null) return false;
        Map<String, SchedulerRestrictionObservation.AppOpMode> a = left.appOps;
        Map<String, SchedulerRestrictionObservation.AppOpMode> b = right.appOps;
        return left.packageName.equals(right.packageName)
                && left.uid == right.uid
                && left.enabledSetting == right.enabledSetting
                && left.suspended == right.suspended
                && left.standbyBucket == right.standbyBucket
                && left.whitelistKind == right.whitelistKind
                && a.equals(b);
    }

    /**
     * Pure, bounded read protocol. Identity reads: four; restriction captures:
     * two. A failure never returns a partial identity or restriction snapshot.
     * The supportedOps catalog must contain at least one specific operation;
     * an empty catalog is not evidence that AppOps are safely restorable.
     */
    public static Result capture(String packageName, String[] supportedOps,
            SchedulerInstalledPackageIdentity.Source identitySource,
            SchedulerRestrictionObservation.Source restrictionSource) {
        if (!PackageNameValidator.isValid(packageName)
                || !validOps(supportedOps)
                || identitySource == null || restrictionSource == null) {
            return failed(Status.INVALID_INPUT);
        }

        try {
            SchedulerInstalledPackageIdentity.Result initial =
                    SchedulerInstalledPackageIdentity.captureStable(packageName, identitySource);
            if (!initial.matched()) return failed(identityFailure(initial.status));

            SchedulerRestrictionObservation.Result first =
                    SchedulerRestrictionObservation.capture(packageName, supportedOps,
                            restrictionSource);
            if (!first.complete()) return failed(Status.RESTRICTIONS_UNKNOWN);

            SchedulerRestrictionObservation.Result second =
                    SchedulerRestrictionObservation.capture(packageName, supportedOps,
                            restrictionSource);
            if (!second.complete()) return failed(Status.RESTRICTIONS_UNKNOWN);

            SchedulerInstalledPackageIdentity.Result finalIdentity =
                    SchedulerInstalledPackageIdentity.checkAgain(
                            initial.snapshot, identitySource);
            if (!finalIdentity.matched()) {
                return failed(identityFailure(finalIdentity.status));
            }

            if (first.snapshot.uid != initial.snapshot.uid
                    || second.snapshot.uid != finalIdentity.snapshot.uid) {
                return failed(Status.UID_MISMATCH);
            }
            if (!equalRestrictions(first.snapshot, second.snapshot)) {
                return failed(Status.RESTRICTIONS_CHANGED);
            }
            // This result is deliberately NOT encoded as a recovery journal.
            return new Result(Status.CONSISTENT_DIAGNOSTIC,
                    finalIdentity.snapshot, second.snapshot);
        } catch (RuntimeException failure) {
            return failed(Status.RESTRICTIONS_UNKNOWN);
        }
    }
}
