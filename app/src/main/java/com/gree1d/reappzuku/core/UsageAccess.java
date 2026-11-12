package com.gree1d.reappzuku.core;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.util.HashMap;
import java.util.Map;

/**
 * Usage statistics for this app's own decisions. Access comes from the
 * GET_USAGE_STATS AppOp, which the privileged shell grants to this package.
 * The manifest declares PACKAGE_USAGE_STATS, without which Android 16 ignores
 * the AppOp change (seen on a device running API 36).
 */
public final class UsageAccess {
    private static final String OP = "GET_USAGE_STATS";
    // A year reaches the yearly buckets, so rarely used apps still have an entry.
    private static final long WINDOW_MS = 365L * 24 * 60 * 60 * 1000;

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
     * @return the last time each package was used, visible or running a
     *         foreground service, or null when usage data is not available
     */
    public static Map<String, Long> lastUsedByPackage(Context context, long now) {
        return lastUse(context, now, true);
    }

    /**
     * Like {@link #lastUsedByPackage} but without foreground service time: the
     * last time the user had the app on screen. An app that restarted itself
     * into a foreground service has not been opened.
     */
    public static Map<String, Long> lastOpenedByPackage(Context context, long now) {
        return lastUse(context, now, false);
    }

    // The call is only made after isGranted() confirmed the AppOp, which is what the system checks.
    @android.annotation.SuppressLint("MissingPermission")
    private static Map<String, Long> lastUse(Context context, long now, boolean withBackgroundWork) {
        try {
            if (!isGranted(context)) return null;
            UsageStatsManager manager =
                    (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (manager == null) return null;
            Map<String, Long> lastUsed = new HashMap<>();
            for (Map.Entry<String, UsageStats> entry
                    : manager.queryAndAggregateUsageStats(now - WINDOW_MS, now).entrySet()) {
                UsageStats stats = entry.getValue();
                long used = stats.getLastTimeUsed();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Background playback and similar work is use, although no activity resumes.
                    used = Math.max(used, stats.getLastTimeVisible());
                    if (withBackgroundWork) {
                        used = Math.max(used, stats.getLastTimeForegroundServiceUsed());
                    }
                }
                lastUsed.put(entry.getKey(), used);
            }
            return lastUsed;
        } catch (RuntimeException e) {
            // A failing usage service must not stop Smart Lifecycle; passes run in full instead.
            return null;
        }
    }
}
