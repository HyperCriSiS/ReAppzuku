package com.gree1d.reappzuku.manager;

import java.util.regex.Pattern;

/**
 * Conservative parser for the human-readable Android "cmd appops get" output.
 * Uid mode takes precedence over package mode when explicitly reported.
 * This diagnostic does NOT establish absence of an unreported UID override,
 * and neither it nor v2 evidence authorizes a rollback command.
 */
public final class SchedulerAppOpsDiagnostic {
    private SchedulerAppOpsDiagnostic() {}

    public static final class Result {
        public final String explicitPackageMode;
        public final String explicitUidMode;
        public final String observedEffectiveMode;
        public final boolean uidOverridesPackage;

        private Result(String pkg, String uid) {
            explicitPackageMode = pkg;
            explicitUidMode = uid;
            observedEffectiveMode = uid != null ? uid : pkg;
            uidOverridesPackage = uid != null;
        }
    }

    private static final Pattern OP = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern MODE =
            Pattern.compile("(allow|ignore|default|foreground|errored|deny|ask)");

    /**
     * No operations / Default mode does not prove an explicitly stored
     * original mode. Duplicate, unexpected or unfamiliar lines are ambiguous.
     * A per-operation query must not be treated as successful if it emits
     * a warning, unrelated op, malformed UID row or unsupported shell text.
     */
    public static Result parse(String output, String operation) {
        if (operation == null || !OP.matcher(operation).matches()
                || output == null || output.isEmpty() || output.length() > 8192) {
            return null;
        }
        String pkg = null;
        String uid = null;
        boolean bad = false;
        String target = operation + ":";
        for (String line : output.split("\\r?\\n")) {
            String item = line.trim();
            boolean uidRow = item.startsWith("Uid mode:");
            if (uidRow) item = item.substring("Uid mode:".length()).trim();

            // A targeted query has at most one package and one UID row for
            // this operation. Do not silently discard any other output:
            // text from a failed/unsupported command is not usable evidence.
            if (!item.startsWith(target)) {
                return null;
            }

            String details = item.substring(target.length()).trim();
            String[] components = details.split(";", -1);
            String candidate = components[0].trim();
            if (!MODE.matcher(candidate).matches()) { bad = true; continue; }
            // Only known Android diagnostic suffixes are accepted. Arbitrary
            // suffixes must never turn a warning into apparently valid evidence.
            for (int i = 1; i < components.length; i++) {
                String detail = components[i].trim();
                if (!(detail.startsWith("time=")
                        || detail.startsWith("rejectTime=")
                        || detail.startsWith("duration="))
                        || detail.substring(detail.indexOf('=') + 1).trim().isEmpty()) {
                    bad = true;
                }
            }
            if (uidRow) {
                if (uid != null) bad = true;
                uid = candidate;
            } else {
                if (pkg != null) bad = true;
                pkg = candidate;
            }
        }
        if (bad || (pkg == null && uid == null)) return null;
        return new Result(pkg, uid);
    }

    /** Legacy package-only consumers MUST reject a present UID override. */
    public static String packageOnly(String output, String operation) {
        Result parsed = parse(output, operation);
        return parsed == null || parsed.explicitUidMode != null
                ? null : parsed.explicitPackageMode;
    }
}
