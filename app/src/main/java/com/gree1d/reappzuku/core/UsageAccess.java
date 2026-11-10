package com.gree1d.reappzuku.core;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import java.util.HashMap;
import java.util.Map;

/**
 * Usage statistics for this app's own decisions. Access comes from the
 * GET_USAGE_STATS AppOp, which the privileged shell grants to this package.
 * The manifest deliberately does not request PACKAGE_USAGE_STATS.
 */
public final class UsageAccess {
    private static final String OP = "GET_USAGE_STATS";
    private static final long WINDOW_MS = 7L * 24 * 60 * 60 * 1000;

    private UsageAccess() {
    }

    @SuppressWarnings("deprecation") // unsafeCheckOpNoThrow needs API 29; minSdk is 24.
    public static boolean isGranted(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        return appOps != null && appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /** Takes effect for the next query. */
    public static void grant(Context context, PrivilegedShell shell) {
        shell.setAppOp(context.getPackageName(), OP, PrivilegedShell.AppOpMode.ALLOW);
    }

    /**
     * @return the last time each package was used in the past week, or null
     *         when usage access is not granted
     */
    // The call is only made after isGranted() confirmed the AppOp, which is what the system checks.
    @android.annotation.SuppressLint("MissingPermission")
    public static Map<String, Long> lastUsedByPackage(Context context, long now) {
        if (!isGranted(context)) return null;
        UsageStatsManager manager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) return null;
        Map<String, Long> lastUsed = new HashMap<>();
        for (Map.Entry<String, UsageStats> entry
                : manager.queryAndAggregateUsageStats(now - WINDOW_MS, now).entrySet()) {
            lastUsed.put(entry.getKey(), entry.getValue().getLastTimeUsed());
        }
        return lastUsed;
    }
}
