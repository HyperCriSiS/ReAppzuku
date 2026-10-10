package com.gree1d.reappzuku.manager;

import com.gree1d.reappzuku.core.PackageNameValidator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * A strictly read-only observation, NOT a rollback instruction. In particular
 * SchedulerRecoveryTransaction.OriginalRestrictions only has an AppOps ignore
 * bitmask and boolean enabled/whitelisted fields. It cannot encode whether
 * the observed mode was ALLOW vs DEFAULT, a default vs explicitly enabled
 * package, or a system vs user DeviceIdle whitelist entry.
 *
 * Never truncate this richer evidence into that older tuple or run an operation
 * because capture succeeded. A future versioned journal and real backend
 * verification must establish exact restoration first.
 */
public final class SchedulerRestrictionObservation {
    private SchedulerRestrictionObservation() {}

    public enum Status { COMPLETE, INVALID_INPUT, BACKEND_NOT_READY, UNREADABLE, UNSUPPORTED_VALUE }
    public enum AppOpMode { ALLOW, IGNORE, DEFAULT, FOREGROUND, ERRORED, ASK }
    public enum WhitelistKind { NONE, USER, SYSTEM, BOTH }

    public interface Source {
        Integer uid(String packageName);
        Integer enabledSetting(String packageName);
        Boolean suspended(String packageName);
        Integer standbyBucket(String packageName);
        WhitelistKind whitelistKind(String packageName);
        String appOpMode(String packageName, String appOpName);
    }

    public static final class Snapshot {
        public final String packageName;
        public final int uid;
        /** Raw PackageManager COMPONENT_ENABLED_STATE_* value, not a boolean. */
        public final int enabledSetting;
        public final boolean suspended;
        public final int standbyBucket;
        public final WhitelistKind whitelistKind;
        public final Map<String, AppOpMode> appOps;

        private Snapshot(String packageName, int uid, int enabledSetting, boolean suspended,
                         int standbyBucket, WhitelistKind whitelistKind,
                         Map<String, AppOpMode> appOps) {
            this.packageName = packageName;
            this.uid = uid;
            this.enabledSetting = enabledSetting;
            this.suspended = suspended;
            this.standbyBucket = standbyBucket;
            this.whitelistKind = whitelistKind;
            this.appOps = Collections.unmodifiableMap(new LinkedHashMap<>(appOps));
        }
    }

    public static final class Result {
        public final Status status;
        public final Snapshot snapshot;

        private Result(Status status, Snapshot snapshot) {
            this.status = status;
            this.snapshot = snapshot;
        }

        public boolean complete() {
            return status == Status.COMPLETE;
        }
    }

    public static Result notReady() {
        return failed(Status.BACKEND_NOT_READY);
    }

    private static Result failed(Status reason) {
        return new Result(reason, null);
    }

    /**
     * Every source observation must be explicitly known. No null/empty/unknown
     * reading is interpreted as a platform default. A failed read returns no
     * partial snapshot; callers must never use this to authorize mutation.
     */
    public static Result capture(String packageName, String[] supportedOps, Source source) {
        if (!PackageNameValidator.isValid(packageName) || source == null
                || supportedOps == null
                || supportedOps.length > 16) {
            return failed(Status.INVALID_INPUT);
        }
        Set<String> seen = new HashSet<>();
        for (String op : supportedOps) {
            if (op == null || !op.matches("[A-Z][A-Z0-9_]{0,63}")
                    || !seen.add(op)) {
                return failed(Status.INVALID_INPUT);
            }
        }
        try {
            Integer uid = source.uid(packageName);
            Integer enabled = source.enabledSetting(packageName);
            Boolean suspended = source.suspended(packageName);
            Integer bucket = source.standbyBucket(packageName);
            WhitelistKind whitelist = source.whitelistKind(packageName);
            if (uid == null || enabled == null || suspended == null
                    || bucket == null || whitelist == null) {
                return failed(Status.UNREADABLE);
            }
            if (uid <= 0 || enabled < 0 || enabled > 4
                    || !knownBucket(bucket)) {
                return failed(Status.UNSUPPORTED_VALUE);
            }
            Map<String, AppOpMode> modes = new LinkedHashMap<>();
            for (String op : supportedOps) {
                String rawMode = source.appOpMode(packageName, op);
                if (rawMode == null) return failed(Status.UNREADABLE);
                AppOpMode mode = parseMode(rawMode);
                if (mode == null) return failed(Status.UNSUPPORTED_VALUE);
                modes.put(op, mode);
            }
            return new Result(Status.COMPLETE, new Snapshot(packageName, uid,
                    enabled, suspended, bucket, whitelist, modes));
        } catch (RuntimeException failure) {
            return failed(Status.UNREADABLE);
        }
    }

    private static boolean knownBucket(int bucket) {
        return bucket == 5 || bucket == 10 || bucket == 20 || bucket == 30
                || bucket == 40 || bucket == 45 || bucket == 50;
    }

    private static AppOpMode parseMode(String rawMode) {
        // Do not trim: accidental whitespace or an appended shell warning is
        // not an explicitly observed mode.
        switch (rawMode) {
            case "allow": return AppOpMode.ALLOW;
            case "ignore": return AppOpMode.IGNORE;
            case "default": return AppOpMode.DEFAULT;
            case "foreground": return AppOpMode.FOREGROUND;
            case "errored": return AppOpMode.ERRORED;
            case "ask": return AppOpMode.ASK;
            default: return null;
        }
    }
}