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
    private final String media;
    private final String widget;
    private final String services;
    private final String wallpaper;
    private final String devicePolicy;
    private final String connectivity;

    private ProtectionDumps(ShellManager shell) {
        media = dump(shell, "dumpsys media_session");
        widget = dump(shell, "dumpsys appwidget");
        services = dump(shell, "dumpsys activity services");
        wallpaper = dump(shell, "dumpsys wallpaper");
        devicePolicy = dump(shell, "dumpsys device_policy");
        connectivity = dump(shell, "dumpsys connectivity");
    }

    static ProtectionDumps collect(ShellManager shell) {
        return new ProtectionDumps(shell);
    }

    /** @param policy the app's explicit policy, or null when every protection applies */
    String reasonFor(Context context, String pkg, AppPolicy policy) {
        if (isEnabledIn(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, pkg)) {
            return "accessibility service";
        }
        if (isEnabledIn(context, "enabled_notification_listeners", pkg)) {
            return "notification listener";
        }
        return SmartLifecycleProtectionPolicy.getDumpProtectionReason(
                pkg, media, widget, services, wallpaper, devicePolicy, connectivity,
                policy == null || policy.protectMedia,
                policy == null || policy.protectForegroundServices,
                policy == null || policy.protectWidgets);
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

    private static String dump(ShellManager shell, String command) {
        String output = shell.runShellCommandAndGetFullOutput(command);
        return output == null ? "" : output;
    }
}
