package com.gree1d.reappzuku.utils.triggers.analyzers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure text parsing for the small subset of ActivityManager dumps consumed by
 * {@link ProcessAnalyzer}. Keeping this Android-free makes format drift across
 * Android releases and OEM builds fixture-testable on the JVM.
 */
final class ProcessDumpParser {

    private static final Pattern ADJ_PATTERN = Pattern.compile("\\badj=([-+]?\\d+)");
    private static final Pattern CUR_STATE_PATTERN =
            Pattern.compile("\\bcurProcState=([A-Za-z0-9_]+)");
    private static final Pattern SET_STATE_PATTERN =
            Pattern.compile("\\bsetProcState=([A-Za-z0-9_]+)");
    private static final Pattern SERVICE_COMPONENT_PATTERN = Pattern.compile(
            "ServiceRecord\\{[^}\\r\\n]*\\s([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)\\s*\\}");
    private static final Pattern ANY_PROCESS_RECORD_PATTERN = Pattern.compile(
            "ProcessRecord\\{[^}\\r\\n]*\\s(?:\\d+:)?"
                    + "([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*)"
                    + "(?::[A-Za-z0-9_.-]+)?/");

    private ProcessDumpParser() {
    }

    static ProcessStateSnapshot parseProcessState(String output, String packageName) {
        if (output == null || output.trim().isEmpty() || packageName == null || packageName.isEmpty()) {
            return null;
        }

        Pattern targetHeader = processRecordPattern(packageName);
        boolean inTargetBlock = false;
        int adj = Integer.MAX_VALUE;
        String procState = null;
        boolean persistent = false;

        for (String line : output.split("\\r?\\n")) {
            boolean targetHeaderLine = isTopLevelProcessHeader(line)
                    && targetHeader.matcher(line).find();
            if (targetHeaderLine) {
                inTargetBlock = true;
            } else if (inTargetBlock && isTopLevelProcessHeader(line)) {
                break;
            }

            if (!inTargetBlock) {
                continue;
            }

            Matcher adjMatcher = ADJ_PATTERN.matcher(line);
            if (adjMatcher.find() && adj == Integer.MAX_VALUE) {
                try {
                    adj = Integer.parseInt(adjMatcher.group(1));
                } catch (NumberFormatException ignored) {
                    // Treat malformed OEM output as absent rather than crashing analysis.
                }
            }

            if (procState == null) {
                Matcher stateMatcher = CUR_STATE_PATTERN.matcher(line);
                if (stateMatcher.find()) {
                    procState = stateMatcher.group(1);
                } else {
                    stateMatcher = SET_STATE_PATTERN.matcher(line);
                    if (stateMatcher.find()) {
                        procState = stateMatcher.group(1);
                    }
                }
            }

            if (line.contains("persistent=true")) {
                persistent = true;
            }
        }

        if (!inTargetBlock || (procState == null && adj == Integer.MAX_VALUE && !persistent)) {
            return null;
        }
        return new ProcessStateSnapshot(adj, procState, persistent);
    }

    static String extractProcessRecordPackage(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = ANY_PROCESS_RECORD_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    static boolean isServiceRecordForPackage(String line, String packageName) {
        String component = extractServiceComponent(line);
        if (component == null || packageName == null) {
            return false;
        }
        int slash = component.indexOf('/');
        return slash > 0 && packageName.equals(component.substring(0, slash));
    }

    static boolean isServiceRecordLine(String line) {
        return extractServiceComponent(line) != null;
    }

    static String extractServiceShortName(String line, String packageName) {
        String component = extractServiceComponent(line);
        if (component == null) {
            return null;
        }
        int slash = component.indexOf('/');
        if (slash < 0 || slash == component.length() - 1) {
            return component;
        }

        String cls = component.substring(slash + 1);
        if (cls.startsWith(".")) {
            return cls.substring(1);
        }
        if (packageName != null && cls.startsWith(packageName + ".")) {
            return cls.substring(packageName.length() + 1);
        }
        return cls;
    }

    private static Pattern processRecordPattern(String packageName) {
        return Pattern.compile(
                "ProcessRecord\\{[^}\\r\\n]*\\s(?:\\d+:)?"
                        + Pattern.quote(packageName)
                        + "(?::[A-Za-z0-9_.-]+)?/");
    }

    private static boolean isTopLevelProcessHeader(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("ProcessRecord{")
                || (trimmed.startsWith("*APP*") && trimmed.contains("ProcessRecord{"));
    }

    private static String extractServiceComponent(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = SERVICE_COMPONENT_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    static final class ProcessStateSnapshot {
        final int adj;
        final String procState;
        final boolean persistent;

        ProcessStateSnapshot(int adj, String procState, boolean persistent) {
            this.adj = adj;
            this.procState = procState;
            this.persistent = persistent;
        }
    }
}
