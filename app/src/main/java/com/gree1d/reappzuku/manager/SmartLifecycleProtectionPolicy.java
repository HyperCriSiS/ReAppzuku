package com.gree1d.reappzuku.manager;

/**
 * Pure dump-based Smart Lifecycle protection policy.
 *
 * Android Settings-backed protections (accessibility service / notification listener) stay in
 * SmartLifecycleManager because they require a ContentResolver. Text-dump decisions live here so
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
        if (PackageTextMatcher.containsExactPackage(widgetDump, packageName)) {
            return "active widget";
        }
        if (PackageTextMatcher.containsExactPackage(mediaDump, packageName)) {
            return "media session";
        }
        if (PackageTextMatcher.containsMarkerNearPackage(
                servicesDump, packageName, "isForeground=true", ASSOCIATION_RADIUS)
                || PackageTextMatcher.containsMarkerNearPackage(
                        servicesDump, packageName, "foregroundId=", ASSOCIATION_RADIUS)) {
            return "foreground service";
        }
        if (PackageTextMatcher.containsExactPackage(wallpaperDump, packageName)) {
            return "wallpaper";
        }
        if (PackageTextMatcher.containsExactPackage(devicePolicyDump, packageName)) {
            return "device policy";
        }
        if (PackageTextMatcher.containsMarkerNearPackage(
                connectivityDump, packageName, "VPN", ASSOCIATION_RADIUS)) {
            return "VPN/network service";
        }
        return null;
    }
}
