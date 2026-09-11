package com.gree1d.reappzuku.core;

/**
 * Pure process-name classification for the split Shizuku provider topology.
 */
public final class ProcessTopology {
    private ProcessTopology() {}

    public static boolean isShizukuProviderProcess(String packageName, String processName) {
        return packageName != null
                && processName != null
                && (packageName + ":shizuku").equals(processName);
    }
}
