package com.gree1d.reappzuku.manager;

import com.gree1d.reappzuku.core.PackageNameValidator;
import java.util.regex.Pattern;

/**
 * Independent, in-memory, read-only package/UID AppOps evidence.
 *
 * This is NOT a durable RZJ2/v1 schema, NOT proof of original ABSENT entries,
 * and never authorizes privileged restoration. It only compares the reported
 * layer data and an installed identity across a finite observation window.
 */
public final class SchedulerAppOpsDualScopeDiagnostic {
    private SchedulerAppOpsDualScopeDiagnostic() {}

    public enum Status {
        CONSISTENT_DIAGNOSTIC_ONLY, INVALID_INPUT, IDENTITY_UNKNOWN,
        IDENTITY_CHANGED, QUERY_UNKNOWN, QUERY_CHANGED, CONTRADICTORY_SCOPES
    }

    public enum Presence { EXPLICIT, NOT_REPORTED }

    public interface Source {
        String readPackage(String packageName, String operation);
        String readUid(int uid, String operation);
    }

    /** Pre-bound shell adapters must be checked against installed identity. */
    public interface IdentityBoundSource extends Source {
        boolean matchesBoundIdentity(SchedulerInstalledPackageIdentity.Snapshot identity);
    }

    static boolean matchesSourceIdentity(
            Source source, SchedulerInstalledPackageIdentity.Snapshot identity) {
        return !(source instanceof IdentityBoundSource)
                || ((IdentityBoundSource) source).matchesBoundIdentity(identity);
    }

    public static final class Layer {
        public final Presence presence;
        /** Only populated for an explicitly reported record. */
        public final String mode;
        /** Fallback platform hint, NEVER an explicit mode or restoration input. */
        public final String defaultHint;

        private Layer(Presence presence, String mode, String defaultHint) {
            this.presence = presence;
            this.mode = mode;
            this.defaultHint = defaultHint;
        }
    }

    public static final class Result {
        public final Status status;
        public final SchedulerInstalledPackageIdentity.Snapshot identity;
        public final Layer packageLayer;
        public final Layer uidLayer;
        /** A matched finite observation is never rollback authorization. */
        public boolean authorizesRestore() { return false; }
        public boolean consistentDiagnosticOnly() {
            return status == Status.CONSISTENT_DIAGNOSTIC_ONLY;
        }

        private Result(Status status,
                SchedulerInstalledPackageIdentity.Snapshot identity,
                Layer packageLayer, Layer uidLayer) {
            this.status = status;
            this.identity = identity;
            this.packageLayer = packageLayer;
            this.uidLayer = uidLayer;
        }
    }

    private static final Pattern OP = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern MODE =
            Pattern.compile("allow|ignore|default|foreground|errored|deny|ask");

    private static Result fail(Status status) {
        return new Result(status, null, null, null);
    }

    static final class Parsed {
        final Layer pkg;
        final Layer uid;
        Parsed(Layer pkg, Layer uid) { this.pkg = pkg; this.uid = uid; }
    }

    /** NOT_REPORTED is intentionally not named ABSENT. */
    private static Layer notReported(String hint) {
        return new Layer(Presence.NOT_REPORTED, null, hint);
    }

    private static Layer explicit(String mode) {
        return new Layer(Presence.EXPLICIT, mode, null);
    }

    static Parsed parse(String raw, String operation) {
        if (raw == null || raw.length() == 0 || raw.length() > 4096
                || raw.indexOf('\r') >= 0 || raw.indexOf('\u0000') >= 0) {
            return null;
        }
        // AOSP shell output often ends with one newline. Do not silently
        // discard other whitespace, errors, or extra lines.
        if (raw.endsWith("\n")) raw = raw.substring(0, raw.length() - 1);

        if ("No operations.".equals(raw)) {
            return new Parsed(notReported(null), notReported(null));
        }
        String prefix = "No operations.\nDefault mode: ";
        if (raw.startsWith(prefix)) {
            String hint = raw.substring(prefix.length());
            if (!MODE.matcher(hint).matches()) return null;
            // This fallback hint is not an explicit stored mode in EITHER layer.
            return new Parsed(notReported(hint), notReported(hint));
        }
        SchedulerAppOpsDiagnostic.Result result =
                SchedulerAppOpsDiagnostic.parse(raw, operation);
        if (result == null) return null;
        return new Parsed(
                result.explicitPackageMode == null ? notReported(null)
                        : explicit(result.explicitPackageMode),
                result.explicitUidMode == null ? notReported(null)
                        : explicit(result.explicitUidMode));
    }

    /**
     * Captures two reads per scope, with two PackageManager identity reads
     * before and after. Source only supplies raw diagnostic text and cannot
     * commit, save or restore an AppOp.
     */
    public static Result capture(String packageName, String operation,
            SchedulerInstalledPackageIdentity.Source identitySource, Source source) {
        if (!PackageNameValidator.isValid(packageName)
                || operation == null || !OP.matcher(operation).matches()
                || identitySource == null || source == null) {
            return fail(Status.INVALID_INPUT);
        }
        try {
            SchedulerInstalledPackageIdentity.Result original =
                    SchedulerInstalledPackageIdentity.captureStable(packageName, identitySource);
            if (!original.matched()) {
                return fail(original.status == SchedulerInstalledPackageIdentity.Status.CHANGED
                        ? Status.IDENTITY_CHANGED : Status.IDENTITY_UNKNOWN);
            }

            // Same numeric UID after reinstall is not the same installed identity.
            if (!matchesSourceIdentity(source, original.snapshot)) {
                return fail(Status.IDENTITY_CHANGED);
            }
            String pkg1 = source.readPackage(packageName, operation);
            String pkg2 = source.readPackage(packageName, operation);
            String uid1 = source.readUid(original.snapshot.uid, operation);
            String uid2 = source.readUid(original.snapshot.uid, operation);
            if (pkg1 == null || pkg2 == null || uid1 == null || uid2 == null) {
                return fail(Status.QUERY_UNKNOWN);
            }
            if (!pkg1.equals(pkg2) || !uid1.equals(uid2)) {
                return fail(Status.QUERY_CHANGED);
            }
            Parsed packageQuery = parse(pkg1, operation);
            Parsed uidQuery = parse(uid1, operation);
            if (packageQuery == null || uidQuery == null) {
                return fail(Status.QUERY_UNKNOWN);
            }

            // A numeric UID query is never proof of a package-specific mode.
            if (uidQuery.pkg.presence == Presence.EXPLICIT) {
                return fail(Status.CONTRADICTORY_SCOPES);
            }
            // If the package query also exposed a UID row, it must agree with
            // the separately queried UID layer, never silently take priority.
            if (packageQuery.uid.presence == Presence.EXPLICIT) {
                if (uidQuery.uid.presence != Presence.EXPLICIT
                        || !packageQuery.uid.mode.equals(uidQuery.uid.mode)) {
                    return fail(Status.CONTRADICTORY_SCOPES);
                }
            }

            SchedulerInstalledPackageIdentity.Result checked =
                    SchedulerInstalledPackageIdentity.checkAgain(
                            original.snapshot, identitySource);
            if (!checked.matched()) {
                return fail(checked.status == SchedulerInstalledPackageIdentity.Status.CHANGED
                        ? Status.IDENTITY_CHANGED : Status.IDENTITY_UNKNOWN);
            }

            if (!matchesSourceIdentity(source, checked.snapshot)) {
                return fail(Status.IDENTITY_CHANGED);
            }
            return new Result(Status.CONSISTENT_DIAGNOSTIC_ONLY,
                    checked.snapshot, packageQuery.pkg, uidQuery.uid);
        } catch (RuntimeException failure) {
            return fail(Status.QUERY_UNKNOWN);
        }
    }
}
