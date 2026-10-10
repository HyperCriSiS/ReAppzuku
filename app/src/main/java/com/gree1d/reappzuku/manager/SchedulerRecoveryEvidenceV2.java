package com.gree1d.reappzuku.manager;

import com.gree1d.reappzuku.core.PackageNameValidator;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Version-2 lossless serialization boundary for the fields of a COMPLETE
 * SchedulerRestrictionObservation. Evidence only: it is NOT verified platform
 * rollback state, and has no privileged-operation or scheduler entry point.
 * Package UID is captured, but signer/reinstall, UID AppOps precedence and
 * write-time read consistency have not been proven.
 */
public final class SchedulerRecoveryEvidenceV2 {
    public static final int VERSION = 2;
    public static final int MAX_APP_OPS = 16;
    public static final int MAX_OWNERS = 15;

    public enum Phase { OBSERVED, REVIEW_REQUIRED }

    public final String packageName;
    public final int uid;
    public final int enabledSetting;
    public final boolean suspended;
    public final int standbyBucket;
    public final SchedulerRestrictionObservation.WhitelistKind whitelistKind;
    public final Map<String, SchedulerRestrictionObservation.AppOpMode> appOps;
    public final Set<Long> owners;
    public final long capturedAtMillis;
    public final long sequence;
    public final Phase phase;

    private SchedulerRecoveryEvidenceV2(String packageName, int uid,
            int enabledSetting, boolean suspended, int standbyBucket,
            SchedulerRestrictionObservation.WhitelistKind whitelistKind,
            Map<String, SchedulerRestrictionObservation.AppOpMode> appOps,
            Set<Long> owners, long capturedAtMillis, long sequence, Phase phase) {
        if (!PackageNameValidator.isValid(packageName) || uid <= 0
                || enabledSetting < 0 || enabledSetting > 4
                || !knownBucket(standbyBucket) || whitelistKind == null
                || phase == null || capturedAtMillis <= 0
                || sequence != (phase == Phase.OBSERVED ? 1 : 2)) {
            throw new IllegalArgumentException("invalid evidence identity or state");
        }
        if (owners == null || owners.isEmpty() || owners.size() > MAX_OWNERS
                || appOps == null || appOps.size() > MAX_APP_OPS) {
            throw new IllegalArgumentException("invalid evidence collection bounds");
        }
        for (Long id : owners) {
            if (id == null || id <= 0) throw new IllegalArgumentException("invalid owner");
        }
        Map<String, SchedulerRestrictionObservation.AppOpMode> modes = new TreeMap<>();
        for (Map.Entry<String, SchedulerRestrictionObservation.AppOpMode> mode
                : appOps.entrySet()) {
            if (mode.getKey() == null
                    || !mode.getKey().matches("[A-Z][A-Z0-9_]{0,63}")
                    || mode.getValue() == null) {
                throw new IllegalArgumentException("invalid explicit AppOp");
            }
            modes.put(mode.getKey(), mode.getValue());
        }
        this.packageName = packageName;
        this.uid = uid;
        this.enabledSetting = enabledSetting;
        this.suspended = suspended;
        this.standbyBucket = standbyBucket;
        this.whitelistKind = whitelistKind;
        this.appOps = Collections.unmodifiableMap(modes);
        this.owners = Collections.unmodifiableSet(new HashSet<>(owners));
        this.capturedAtMillis = capturedAtMillis;
        this.sequence = sequence;
        this.phase = phase;
    }

    /** Refuse partial/failed reads; no fabricated defaults or v1 conversion. */
    public static SchedulerRecoveryEvidenceV2 fromObservation(
            SchedulerRestrictionObservation.Result observation,
            Set<Long> owners, long capturedAtMillis) {
        if (observation == null || !observation.complete() || observation.snapshot == null) {
            throw new IllegalArgumentException("complete original observation required");
        }
        SchedulerRestrictionObservation.Snapshot s = observation.snapshot;
        return rehydrate(s.packageName, s.uid, s.enabledSetting, s.suspended,
                s.standbyBucket, s.whitelistKind, s.appOps, owners,
                capturedAtMillis, 1, Phase.OBSERVED);
    }

    static SchedulerRecoveryEvidenceV2 rehydrate(String packageName, int uid,
            int enabledSetting, boolean suspended, int standbyBucket,
            SchedulerRestrictionObservation.WhitelistKind whitelistKind,
            Map<String, SchedulerRestrictionObservation.AppOpMode> appOps,
            Set<Long> owners, long capturedAtMillis, long sequence, Phase phase) {
        return new SchedulerRecoveryEvidenceV2(packageName, uid, enabledSetting,
                suspended, standbyBucket, whitelistKind, appOps,
                owners, capturedAtMillis, sequence, phase);
    }

    public SchedulerRecoveryEvidenceV2 requireManualReview() {
        if (phase != Phase.OBSERVED) {
            throw new IllegalStateException("already review-only");
        }
        return rehydrate(packageName, uid, enabledSetting, suspended,
                standbyBucket, whitelistKind, appOps, owners,
                capturedAtMillis, 2, Phase.REVIEW_REQUIRED);
    }

    static boolean sameOriginal(SchedulerRecoveryEvidenceV2 a,
                                SchedulerRecoveryEvidenceV2 b) {
        return a.packageName.equals(b.packageName) && a.uid == b.uid
                && a.enabledSetting == b.enabledSetting && a.suspended == b.suspended
                && a.standbyBucket == b.standbyBucket && a.whitelistKind == b.whitelistKind
                && a.appOps.equals(b.appOps) && a.owners.equals(b.owners)
                && a.capturedAtMillis == b.capturedAtMillis;
    }

    private static boolean knownBucket(int bucket) {
        return bucket == 5 || bucket == 10 || bucket == 20 || bucket == 30
                || bucket == 40 || bucket == 45 || bucket == 50;
    }
}
