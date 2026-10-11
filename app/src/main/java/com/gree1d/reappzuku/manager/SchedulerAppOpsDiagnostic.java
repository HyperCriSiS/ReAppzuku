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
     * original mode. Duplicate or unfamiliar target rows are ambiguous.
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
            if (item.equals("No operations.") || item.startsWith("Error:")
                    || item.startsWith("Unknown ") || item.startsWith("Default mode:")) {
                bad = true;
            }
            if (!item.startsWith(target)) {
                // Unrecognized UID rows must not be accidentally treated as a
                // harmless absence of a UID override on a targeted query.
                if (uidRow) bad = true;
                continue;
            }
            String candidate = item.substring(target.length()).trim();
            int extra = candidate.indexOf(';');
            if (extra >= 0) candidate = candidate.substring(0, extra).trim();
            // Reject running/access metadata without a known delimiter.
            if (!MODE.matcher(candidate).matches()) { bad = true; continue; }
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
