package com.gree1d.reappzuku.manager;

import com.gree1d.reappzuku.core.PackageNameValidator;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Read-only, in-memory coherence check across a bounded list of AppOps.
 *
 * A complete two-pass observation is useful DIAGNOSTIC evidence only.
 * The Android shell is not atomic: neither this nor an individual read
 * authorizes an automatic privileged restore, RZJ2 conversion, or an assertion
 * about originally absent stored AppOps records.
 */
public final class SchedulerAppOpsCatalogDiagnostic {
    private SchedulerAppOpsCatalogDiagnostic() {}

    public enum Status {
        CONSISTENT_DIAGNOSTIC_ONLY, INVALID_INPUT,
        IDENTITY_UNKNOWN, IDENTITY_CHANGED,
        QUERY_UNKNOWN, QUERY_CHANGED, CONTRADICTORY_SCOPES
    }

    public static final class OperationEvidence {
        public final SchedulerAppOpsDualScopeDiagnostic.Layer packageLayer;
        public final SchedulerAppOpsDualScopeDiagnostic.Layer uidLayer;

        private OperationEvidence(SchedulerAppOpsDualScopeDiagnostic.Layer pkg,
                SchedulerAppOpsDualScopeDiagnostic.Layer uid) {
            packageLayer = pkg;
            uidLayer = uid;
        }
    }

    public static final class Result {
        public final Status status;
        public final SchedulerInstalledPackageIdentity.Snapshot identity;
        public final Map<String, OperationEvidence> operations;

        private Result(Status status, SchedulerInstalledPackageIdentity.Snapshot identity,
                Map<String, OperationEvidence> operations) {
            this.status = status;
            this.identity = identity;
            this.operations = operations;
        }

        public boolean consistentDiagnosticOnly() {
            return status == Status.CONSISTENT_DIAGNOSTIC_ONLY;
        }

        /** Even complete matching diagnostic reads CANNOT authorize rollback. */
        public boolean authorizesRestore() {
            return false;
        }
    }

    private static final Pattern OP = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private static Result fail(Status reason) {
        return new Result(reason, null, Collections.emptyMap());
    }

    private static Status identityStatus(SchedulerInstalledPackageIdentity.Status status) {
        return status == SchedulerInstalledPackageIdentity.Status.CHANGED
                ? Status.IDENTITY_CHANGED : Status.IDENTITY_UNKNOWN;
    }

    /**
     * Whole-pass ordering matters: reads all operations once, then all again.
     * A changing value in any operation invalidates the ENTIRE result.
     *
     * Both scopes use the same source as the single-operation diagnostic;
     * no command execution, persistence or recovery is performed here.
     */
    public static Result capture(String packageName, String[] supportedOps,
            SchedulerInstalledPackageIdentity.Source identitySource,
            SchedulerAppOpsDualScopeDiagnostic.Source shellSource) {
        if (!PackageNameValidator.isValid(packageName) || supportedOps == null
                || supportedOps.length == 0 || supportedOps.length > 16
                || identitySource == null || shellSource == null) {
            return fail(Status.INVALID_INPUT);
        }
        final String[] catalog = supportedOps.clone();
        Set<String> unique = new HashSet<>();
        for (String op : catalog) {
            if (op == null || !OP.matcher(op).matches() || !unique.add(op)) {
                return fail(Status.INVALID_INPUT);
            }
        }

        try {
            SchedulerInstalledPackageIdentity.Result initial =
                    SchedulerInstalledPackageIdentity.captureStable(packageName, identitySource);
            if (!initial.matched()) return fail(identityStatus(initial.status));

            String[][] packageRows = new String[2][catalog.length];
            String[][] uidRows = new String[2][catalog.length];
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < catalog.length; i++) {
                    packageRows[pass][i] = shellSource.readPackage(packageName, catalog[i]);
                    uidRows[pass][i] = shellSource.readUid(initial.snapshot.uid, catalog[i]);
                    if (packageRows[pass][i] == null || uidRows[pass][i] == null) {
                        return fail(Status.QUERY_UNKNOWN);
                    }
                }
            }

            for (int i = 0; i < catalog.length; i++) {
                if (!packageRows[0][i].equals(packageRows[1][i])
                        || !uidRows[0][i].equals(uidRows[1][i])) {
                    return fail(Status.QUERY_CHANGED);
                }
            }

            Map<String, OperationEvidence> complete = new LinkedHashMap<>();
            for (int i = 0; i < catalog.length; i++) {
                SchedulerAppOpsDualScopeDiagnostic.Parsed pkg =
                        SchedulerAppOpsDualScopeDiagnostic.parse(packageRows[0][i], catalog[i]);
                SchedulerAppOpsDualScopeDiagnostic.Parsed uid =
                        SchedulerAppOpsDualScopeDiagnostic.parse(uidRows[0][i], catalog[i]);
                if (pkg == null || uid == null) return fail(Status.QUERY_UNKNOWN);
                if (uid.pkg.presence == SchedulerAppOpsDualScopeDiagnostic.Presence.EXPLICIT) {
                    return fail(Status.CONTRADICTORY_SCOPES);
                }
                if (pkg.uid.presence == SchedulerAppOpsDualScopeDiagnostic.Presence.EXPLICIT
                        && (uid.uid.presence
                                != SchedulerAppOpsDualScopeDiagnostic.Presence.EXPLICIT
                            || !pkg.uid.mode.equals(uid.uid.mode))) {
                    return fail(Status.CONTRADICTORY_SCOPES);
                }
                complete.put(catalog[i], new OperationEvidence(pkg.pkg, uid.uid));
            }

            SchedulerInstalledPackageIdentity.Result last =
                    SchedulerInstalledPackageIdentity.checkAgain(initial.snapshot, identitySource);
            if (!last.matched()) return fail(identityStatus(last.status));

            return new Result(Status.CONSISTENT_DIAGNOSTIC_ONLY, last.snapshot,
                    Collections.unmodifiableMap(complete));
        } catch (RuntimeException failure) {
            return fail(Status.QUERY_UNKNOWN);
        }
    }
}
