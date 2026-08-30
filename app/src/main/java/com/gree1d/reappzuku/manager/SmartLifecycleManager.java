package com.gree1d.reappzuku.manager;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.ProtectedApps;
import com.gree1d.reappzuku.core.ShellManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;

/**
 * Conservative lifecycle management inspired by Brevent's "standby first,
 * force-stop later" model, but using ReAppzuku/Shizuku as the privilege layer.
 *
 * Only packages explicitly placed in ReAppzuku's blacklist are managed. This
 * intentionally does not inherit whitelist-mode semantics, because doing so
 * could silently target almost every installed application.
 */
public final class SmartLifecycleManager {
    private static final String TAG = "SmartLifecycleManager";

    public static final int PROFILE_GENTLE = 0;
    public static final int PROFILE_BALANCED = 1;
    public static final int PROFILE_AGGRESSIVE = 2;

    private static final long MINUTE = 60_000L;
    private static final Pattern RESUMED_PACKAGE = Pattern.compile(
            "(?:mResumedActivity|topResumedActivity|mCurrentFocus).*?\\s([A-Za-z0-9_.$]+)/(?:[A-Za-z0-9_.$]+)");

    private final Context context;
    private final ShellManager shellManager;
    private final SharedPreferences prefs;
    private final PackageManager packageManager;

    public SmartLifecycleManager(Context context, ShellManager shellManager) {
        this.context = context.getApplicationContext();
        this.shellManager = shellManager;
        this.prefs = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        this.packageManager = this.context.getPackageManager();
    }

    public static void recordForeground(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) return;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putLong(KEY_SMART_LAST_FOREGROUND_PREFIX + packageName, System.currentTimeMillis())
                .remove(KEY_SMART_BACKGROUND_SINCE_PREFIX + packageName)
                .apply();
    }

    public static int getBootGraceMinutes(SharedPreferences prefs) {
        switch (prefs.getInt(KEY_SMART_LIFECYCLE_PROFILE, PROFILE_BALANCED)) {
            case PROFILE_GENTLE: return 10;
            case PROFILE_AGGRESSIVE: return 2;
            default: return 5;
        }
    }

    public static int getStandbyDelayMinutes(SharedPreferences prefs) {
        switch (prefs.getInt(KEY_SMART_LIFECYCLE_PROFILE, PROFILE_BALANCED)) {
            case PROFILE_GENTLE: return 120;
            case PROFILE_AGGRESSIVE: return 30;
            default: return 60;
        }
    }

    public static int getForceStopDelayMinutes(SharedPreferences prefs) {
        switch (prefs.getInt(KEY_SMART_LIFECYCLE_PROFILE, PROFILE_BALANCED)) {
            case PROFILE_GENTLE: return 720;
            case PROFILE_AGGRESSIVE: return 120;
            default: return 360;
        }
    }

    public void runPass(boolean bootPass) {
        if (!prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false)) return;
        if (!shellManager.resolveAnyShellPermission()) {
            AppDebugManager.w(Category.AUTO_KILL_BASE, TAG + ": no shell permission, skipping pass");
            return;
        }

        Set<String> managed = new HashSet<>(prefs.getStringSet(KEY_BLACKLISTED_APPS, Collections.emptySet()));
        if (managed.isEmpty()) {
            AppDebugManager.d(Category.AUTO_KILL_BASE, TAG + ": blacklist empty, nothing to manage");
            return;
        }

        String currentForeground = getCurrentForegroundPackage();
        if (currentForeground != null) recordForeground(context, currentForeground);

        Set<String> running = getRunningPackages();
        String mediaDump = safeShell("dumpsys media_session");
        String widgetDump = safeShell("dumpsys appwidget");
        String servicesDump = safeShell("dumpsys activity services");
        String wallpaperDump = safeShell("dumpsys wallpaper");
        String devicePolicyDump = safeShell("dumpsys device_policy");
        String connectivityDump = safeShell("dumpsys connectivity");

        long now = System.currentTimeMillis();
        long bootEpoch = prefs.getLong(KEY_SMART_BOOT_EPOCH_MS, 0L);
        long standbyDelay = getStandbyDelayMinutes(prefs) * MINUTE;
        long forceStopDelay = getForceStopDelayMinutes(prefs) * MINUTE;

        for (String pkg : managed) {
            if (!isEligiblePackage(pkg)) continue;

            if (pkg.equals(currentForeground)) {
                clearBackgroundState(pkg);
                continue;
            }

            if (!running.contains(pkg)) {
                clearBackgroundState(pkg);
                continue;
            }

            String protectionReason = getProtectionReason(pkg, mediaDump, widgetDump, servicesDump,
                    wallpaperDump, devicePolicyDump, connectivityDump);
            if (protectionReason != null) {
                AppDebugManager.d(Category.AUTO_KILL_BASE,
                        TAG + ": SKIP " + pkg + " (" + protectionReason + ")");
                clearBackgroundState(pkg);
                continue;
            }

            if (bootPass) {
                if (!prefs.getBoolean(KEY_SMART_BOOT_CLEANUP_ENABLED, true)) continue;
                long lastForeground = prefs.getLong(KEY_SMART_LAST_FOREGROUND_PREFIX + pkg, 0L);
                if (bootEpoch > 0 && lastForeground >= bootEpoch) {
                    AppDebugManager.d(Category.AUTO_KILL_BASE,
                            TAG + ": boot cleanup SKIP " + pkg + " (used since boot)");
                    continue;
                }
                forceStop(pkg, "boot cleanup");
                clearBackgroundState(pkg);
                continue;
            }

            String sinceKey = KEY_SMART_BACKGROUND_SINCE_PREFIX + pkg;
            long backgroundSince = prefs.getLong(sinceKey, 0L);
            if (backgroundSince <= 0L || backgroundSince > now) {
                prefs.edit().putLong(sinceKey, now).apply();
                AppDebugManager.d(Category.AUTO_KILL_BASE,
                        TAG + ": observing background app " + pkg);
                continue;
            }

            long idle = now - backgroundSince;
            if (idle >= standbyDelay && !prefs.getBoolean(KEY_SMART_STANDBY_APPLIED_PREFIX + pkg, false)) {
                if (setStandby(pkg)) {
                    prefs.edit().putBoolean(KEY_SMART_STANDBY_APPLIED_PREFIX + pkg, true).apply();
                }
            }

            if (idle >= forceStopDelay) {
                forceStop(pkg, "inactive " + (idle / MINUTE) + " min");
                clearBackgroundState(pkg);
            }
        }
    }

    private boolean isEligiblePackage(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(context.getPackageName())) return false;
        if (ProtectedApps.isProtected(context, pkg)) return false;
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(pkg, 0);
            return (info.flags & ApplicationInfo.FLAG_PERSISTENT) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String getProtectionReason(String pkg, String mediaDump, String widgetDump,
            String servicesDump, String wallpaperDump, String devicePolicyDump, String connectivityDump) {
        if (isEnabledAccessibilityService(pkg)) return "accessibility service";
        if (isEnabledNotificationListener(pkg)) return "notification listener";
        if (containsPackage(widgetDump, pkg)) return "active widget";
        if (containsPackage(mediaDump, pkg)) return "media session";
        if (containsPackageNear(servicesDump, pkg, "isForeground=true", 1800)
                || containsPackageNear(servicesDump, pkg, "foregroundId=", 1800)) return "foreground service";
        if (containsPackage(wallpaperDump, pkg)) return "wallpaper";
        if (containsPackage(devicePolicyDump, pkg)) return "device policy";
        if (containsPackage(connectivityDump, pkg) && connectivityDump.contains("VPN")) return "VPN/network service";
        return null;
    }

    private boolean isEnabledAccessibilityService(String pkg) {
        String raw = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (raw == null || raw.isEmpty()) return false;
        for (String component : raw.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(component);
            if (cn != null && pkg.equals(cn.getPackageName())) return true;
        }
        return false;
    }

    private boolean isEnabledNotificationListener(String pkg) {
        String raw = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        if (raw == null || raw.isEmpty()) return false;
        for (String component : raw.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(component);
            if (cn != null && pkg.equals(cn.getPackageName())) return true;
        }
        return false;
    }

    private String getCurrentForegroundPackage() {
        String activities = safeShell("dumpsys activity activities");
        Matcher matcher = RESUMED_PACKAGE.matcher(activities);
        if (matcher.find()) return matcher.group(1);

        String window = safeShell("dumpsys window windows");
        matcher = RESUMED_PACKAGE.matcher(window);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Set<String> getRunningPackages() {
        Set<String> result = new HashSet<>();
        String output = safeShell("ps -A -o NAME");
        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String name = line.trim();
                if (name.isEmpty() || "NAME".equalsIgnoreCase(name)) continue;
                int colon = name.indexOf(':');
                if (colon > 0) name = name.substring(0, colon);
                if (name.contains(".")) result.add(name);
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    private boolean setStandby(String pkg) {
        boolean ok = shellManager.runShellCommandBlocking("am set-standby-bucket " + pkg + " rare");
        AppDebugManager.d(Category.AUTO_KILL_BASE,
                TAG + ": " + (ok ? "STANDBY " : "STANDBY FAILED ") + pkg);
        return ok;
    }

    private void forceStop(String pkg, String reason) {
        boolean ok = shellManager.runShellCommandBlocking("am force-stop " + pkg);
        AppDebugManager.d(Category.AUTO_KILL_BASE,
                TAG + ": " + (ok ? "FORCE-STOP " : "FORCE-STOP FAILED ") + pkg + " (" + reason + ")");
    }

    private void clearBackgroundState(String pkg) {
        prefs.edit()
                .remove(KEY_SMART_BACKGROUND_SINCE_PREFIX + pkg)
                .remove(KEY_SMART_STANDBY_APPLIED_PREFIX + pkg)
                .apply();
    }

    private String safeShell(String command) {
        String output = shellManager.runShellCommandAndGetFullOutput(command);
        return output == null ? "" : output;
    }

    private static boolean containsPackage(String text, String pkg) {
        return text != null && !text.isEmpty() && text.contains(pkg);
    }

    private static boolean containsPackageNear(String text, String pkg, String marker, int radius) {
        if (text == null || text.isEmpty()) return false;
        int index = text.indexOf(pkg);
        while (index >= 0) {
            int start = Math.max(0, index - radius);
            int end = Math.min(text.length(), index + pkg.length() + radius);
            if (text.substring(start, end).contains(marker)) return true;
            index = text.indexOf(pkg, index + pkg.length());
        }
        return false;
    }
}
