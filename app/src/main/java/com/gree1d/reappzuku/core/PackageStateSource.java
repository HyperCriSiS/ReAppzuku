package com.gree1d.reappzuku.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only source for running Android application processes consumed by UI/state collection.
 * Shell execution is isolated here while parsing remains deterministic and JVM-testable.
 */
public final class PackageStateSource {
    static final String PID_NAME_COMMAND = "ps -A -o pid,name";
    static final String PID_RSS_NAME_COMMAND = "ps -A -o pid,rss,name";

    public static final class ProcessSample {
        public final String packageName;
        public final int pid;
        public final long rssKb;

        ProcessSample(String packageName, int pid, long rssKb) {
            this.packageName = packageName;
            this.pid = pid;
            this.rssKb = rssKb;
        }
    }

    public static final class Snapshot {
        public final boolean available;
        public final List<ProcessSample> samples;

        private Snapshot(boolean available, List<ProcessSample> samples) {
            this.available = available;
            this.samples = Collections.unmodifiableList(samples);
        }
    }

    private final ShellManager shellManager;

    public PackageStateSource(ShellManager shellManager) {
        if (shellManager == null) {
            throw new NullPointerException("shellManager");
        }
        this.shellManager = shellManager;
    }

    public Snapshot readRunningProcesses() {
        String output = shellManager.runShellCommandAndGetFullOutput(PID_NAME_COMMAND);
        return snapshot(output, false);
    }

    public Snapshot readRunningProcessesWithRss() {
        String output = shellManager.runShellCommandAndGetFullOutput(PID_RSS_NAME_COMMAND);
        return snapshot(output, true);
    }

    static List<ProcessSample> parsePidName(String output) {
        return parse(output, false);
    }

    static List<ProcessSample> parsePidRssName(String output) {
        return parse(output, true);
    }

    private static Snapshot snapshot(String output, boolean withRss) {
        if (output == null) {
            return new Snapshot(false, Collections.emptyList());
        }
        return new Snapshot(true, parse(output, withRss));
    }

    private static List<ProcessSample> parse(String output, boolean withRss) {
        if (output == null || output.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcessSample> result = new ArrayList<>();
        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("\\s+", withRss ? 3 : 2);
            if (parts.length < (withRss ? 3 : 2)) continue;

            try {
                int pid = Integer.parseInt(parts[0]);
                if (pid <= 1) continue;
                long rssKb = withRss ? Long.parseLong(parts[1]) : 0L;
                if (rssKb < 0L) continue;
                String packageName = basePackageName(parts[withRss ? 2 : 1].trim());
                if (!PackageNameValidator.isValid(packageName)) continue;
                result.add(new ProcessSample(packageName, pid, rssKb));
            } catch (NumberFormatException ignored) {
                // Headers and malformed rows are intentionally ignored.
            }
        }
        return result;
    }

    private static String basePackageName(String rawProcessName) {
        if (rawProcessName == null) return null;
        int remoteSeparator = rawProcessName.indexOf(':');
        return remoteSeparator > 0
                ? rawProcessName.substring(0, remoteSeparator)
                : rawProcessName;
    }
}
