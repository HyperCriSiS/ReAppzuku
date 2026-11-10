package com.gree1d.reappzuku.manager;

/**
 * Pure dump-based Smart Lifecycle protection policy.
 *
 * Android Settings-backed protections (accessibility service / notification listener) stay in
 * ProtectionDumps because they require a ContentResolver. Text-dump decisions live here so
 * they can be exhaustively regression-tested without Android runtime dependencies.
 */
final class SmartLifecycleProtectionPolicy {
    private static final int ASSOCIATION_RADIUS = 1800;

    private SmartLifecycleProtectionPolicy() {
    }

    static String getDumpProtectionReason(
            String packageName,
            String mediaDump,
            String widgetDump,
            String servicesDump,
            String wallpaperDump,
            String devicePolicyDump,
            String connectivityDump) {
        return getDumpProtectionReason(packageName, mediaDump, widgetDump, servicesDump,
                wallpaperDump, devicePolicyDump, connectivityDump, true, true, true);
    }

    /** The three flags are the per-app switches from the policy editor. */
    static String getDumpProtectionReason(
            String packageName,
            String mediaDump,
            String widgetDump,
            String servicesDump,
            String wallpaperDump,
            String devicePolicyDump,
            String connectivityDump,
            boolean protectMedia,
            boolean protectForegroundServices,
            boolean protectWidgets) {
        if (protectWidgets
                && PackageTextMatcher.containsExactPackage(placedWidgets(widgetDump), packageName)) {
            return "active widget";
        }
        if (protectMedia && PackageTextMatcher.containsExactPackage(mediaDump, packageName)) {
            return "media session";
        }
        if (protectForegroundServices
                && (PackageTextMatcher.containsMarkerNearPackage(
                        servicesDump, packageName, "isForeground=true", ASSOCIATION_RADIUS)
                || PackageTextMatcher.containsMarkerNearPackage(
                        servicesDump, packageName, "foregroundId=", ASSOCIATION_RADIUS))) {
            return "foreground service";
        }
        if (PackageTextMatcher.containsExactPackage(wallpaperDump, packageName)) {
            return "wallpaper";
        }
        if (PackageTextMatcher.containsExactPackage(devicePolicyDump, packageName)) {
            return "device policy";
        }
        // Ordinary networks carry the NOT_VPN capability, which is not a VPN signal.
        if (PackageTextMatcher.containsMarkerNearPackage(
                connectivityDump == null ? null : connectivityDump.replace("NOT_VPN", ""),
                packageName, "VPN", ASSOCIATION_RADIUS)) {
            return "VPN/network service";
        }
        return null;
    }

    /**
     * The appwidget dump also lists every installed provider. Only the
     * "Widgets:" section names widgets that are placed on a host. A dump
     * without that section is used whole.
     */
    private static String placedWidgets(String widgetDump) {
        if (widgetDump == null) return null;
        int start = widgetDump.indexOf("\nWidgets:");
        if (start < 0) return widgetDump;
        int end = widgetDump.indexOf("\nHosts:", start);
        return end < 0 ? widgetDump.substring(start) : widgetDump.substring(start, end);
    }
}
