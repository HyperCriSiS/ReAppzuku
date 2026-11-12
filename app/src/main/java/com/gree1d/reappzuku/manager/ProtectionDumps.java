package com.gree1d.reappzuku.manager;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.db.AppPolicy;

/**
 * One set of system dumps per pass, shared by every engine that must not stop
 * an app the user is actively relying on.
 */
final class ProtectionDumps {
    private static final String SEPARATOR = "==reappzuku-dump==";
    private static final String COMMAND = "dumpsys media_session; echo " + SEPARATOR
            + "; dumpsys appwidget; echo " + SEPARATOR
            + "; dumpsys activity services; echo " + SEPARATOR
            + "; dumpsys wallpaper; echo " + SEPARATOR
            + "; dumpsys device_policy; echo " + SEPARATOR
            + "; dumpsys connectivity";

    private final String[] dumps;
    /** The elapsed realtime when the dumps were taken, to tell a fresh pause from an old one; 0 if unknown. */
    private final long takenAtElapsedMs;

    /** @param output the output of {@link #COMMAND}, or null when the shell failed */
    ProtectionDumps(String output) {
        this(output, 0L);
    }

    ProtectionDumps(String output, long takenAtElapsedMs) {
        String[] parts = output == null ? new String[0] : output.split(SEPARATOR, -1);
        dumps = parts.length == 6 ? parts : null;
        this.takenAtElapsedMs = takenAtElapsedMs;
    }

    /** One shell invocation, so a pass under a short wakelock is not six round trips. */
    static ProtectionDumps collect(ShellManager shell) {
        return new ProtectionDumps(shell.runShellCommandAndGetFullOutput(COMMAND),
                android.os.SystemClock.elapsedRealtime());
    }

    /** @param policy the app's explicit policy, or null when every protection applies */
    String reasonFor(Context context, String pkg, AppPolicy policy) {
        if (isEnabledIn(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, pkg)) {
            return "accessibility service";
        }
        if (isEnabledIn(context, "enabled_notification_listeners", pkg)) {
            return "notification listener";
        }
        return dumpReasonFor(pkg, policy);
    }

    String dumpReasonFor(String pkg, AppPolicy policy) {
        // Without the dumps nothing is known about the app, so it is left alone.
        if (dumps == null || dumps[2].trim().isEmpty()) return "protection state unknown";
        return SmartLifecycleProtectionPolicy.getDumpProtectionReason(
                pkg, dumps[0], dumps[1], dumps[2], dumps[3], dumps[4], dumps[5],
                policy == null || policy.protectMedia,
                policy == null || policy.protectForegroundServices,
                policy == null || policy.protectWidgets,
                takenAtElapsedMs);
    }

    private static boolean isEnabledIn(Context context, String secureKey, String pkg) {
        String raw = Settings.Secure.getString(context.getContentResolver(), secureKey);
        if (raw == null || raw.isEmpty()) return false;
        for (String component : raw.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(component);
            if (cn != null && pkg.equals(cn.getPackageName())) return true;
        }
        return false;
    }
}
