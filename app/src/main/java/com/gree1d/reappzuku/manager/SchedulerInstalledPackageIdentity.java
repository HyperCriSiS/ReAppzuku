package com.gree1d.reappzuku.manager;

import com.gree1d.reappzuku.core.PackageNameValidator;

/**
 * Diagnostic identity double-read only. A matched observation is NOT proof of
 * lifetime identity and must never authorize a privileged operation.
 */
public final class SchedulerInstalledPackageIdentity {
    private SchedulerInstalledPackageIdentity() {}

    public enum Status { MATCHING_READS, INVALID_INPUT, UNREADABLE, CHANGED }

    public interface Source {
        Snapshot read(String packageName);
    }

    public static final class Snapshot {
        public final String packageName;
        public final int uid;
        public final int userId;
        public final String currentSignerSha256;
        public final long versionCode;
        public final long firstInstallTime;
        public final long lastUpdateTime;
        public final boolean onlyVisiblePackageForUid;

        public Snapshot(String packageName, int uid, int userId,
                String currentSignerSha256, long versionCode,
                long firstInstallTime, long lastUpdateTime,
                boolean onlyVisiblePackageForUid) {
            this.packageName = packageName;
            this.uid = uid;
            this.userId = userId;
            this.currentSignerSha256 = currentSignerSha256;
            this.versionCode = versionCode;
            this.firstInstallTime = firstInstallTime;
            this.lastUpdateTime = lastUpdateTime;
            this.onlyVisiblePackageForUid = onlyVisiblePackageForUid;
        }

        boolean validFor(String requested) {
            return requested.equals(packageName) && uid > 0 && userId >= 0
                    && currentSignerSha256 != null
                    && currentSignerSha256.matches("[0-9a-f]{64}")
                    && versionCode >= 0 && firstInstallTime > 0
                    && lastUpdateTime >= firstInstallTime
                    && onlyVisiblePackageForUid;
        }

        boolean sameAs(Snapshot other) {
            return other != null && packageName.equals(other.packageName)
                    && uid == other.uid && userId == other.userId
                    && currentSignerSha256.equals(other.currentSignerSha256)
                    && versionCode == other.versionCode
                    && firstInstallTime == other.firstInstallTime
                    && lastUpdateTime == other.lastUpdateTime
                    && onlyVisiblePackageForUid == other.onlyVisiblePackageForUid;
        }
    }

    public static final class Result {
        public final Status status;
        public final Snapshot snapshot;
        private Result(Status status, Snapshot snapshot) {
            this.status = status;
            this.snapshot = snapshot;
        }
        public boolean matched() { return status == Status.MATCHING_READS; }
    }

    private static Result failed(Status status) { return new Result(status, null); }

    /** Two independent reads detect observed reinstall/UID/signing/update drift. */
    public static Result captureStable(String packageName, Source source) {
        if (!PackageNameValidator.isValid(packageName) || source == null) {
            return failed(Status.INVALID_INPUT);
        }
        try {
            Snapshot first = source.read(packageName);
            Snapshot second = source.read(packageName);
            if (first == null || second == null
                    || !first.validFor(packageName) || !second.validFor(packageName)) {
                return failed(Status.UNREADABLE);
            }
            if (!first.sameAs(second)) return failed(Status.CHANGED);
            return new Result(Status.MATCHING_READS, second);
        } catch (RuntimeException failure) {
            return failed(Status.UNREADABLE);
        }
    }

    /**
     * Check a previously captured identity against TWO new reads. A match is
     * diagnostic only; an immediate reinstall after this call remains possible.
     */
    public static Result checkAgain(Snapshot expected, Source source) {
        if (expected == null || !PackageNameValidator.isValid(expected.packageName)
                || source == null) return failed(Status.INVALID_INPUT);
        Result current = captureStable(expected.packageName, source);
        if (!current.matched()) return current;
        if (!expected.validFor(expected.packageName)
                || !expected.sameAs(current.snapshot)) return failed(Status.CHANGED);
        return current;
    }
}
