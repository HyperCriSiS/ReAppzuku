package com.gree1d.reappzuku.manager;

/**
 * Pure dump-based Smart Lifecycle protection policy.
 *
 * Android Settings-backed protections (accessibility service / notification listener) stay in
 * ProtectionDumps because they require a ContentResolver. Text-dump decisions live here so
 * they can be exhaustively regression-tested without Android runtime dependencies.
 */
final class SmartLifecycleProtectionPolicy {
    private static final java.util.regex.Pattern FOREGROUND_ID =
            java.util.regex.Pattern.compile("foregroundId=[1-9]");

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
        if (protectForegroundServices && runsForegroundService(servicesDump, packageName)) {
            return "foreground service";
        }
        if (PackageTextMatcher.containsExactPackage(wallpaperDump, packageName)) {
            return "wallpaper";
        }
        if (PackageTextMatcher.containsExactPackage(adminState(devicePolicyDump), packageName)) {
            return "device policy";
        }
        if (ownsVpn(connectivityDump, packageName)) {
            return "VPN/network service";
        }
        return null;
    }

    /**
     * A ServiceRecord of the package that runs in the foreground. Each record
     * is checked on its own: one long dump area would let a package borrow the
     * foreground flag of the next app's service.
     */
    static boolean runsForegroundService(String servicesDump, String packageName) {
        if (servicesDump == null) return false;
        String[] records = servicesDump.split("ServiceRecord[{]", -1);
        for (int i = 1; i < records.length; i++) {
            String record = records[i];
            int lineEnd = record.indexOf('\n');
            String header = lineEnd < 0 ? record : record.substring(0, lineEnd);
            if (!PackageTextMatcher.containsExactPackage(header, packageName)) continue;
            if (record.contains("isForeground=true")
                    || FOREGROUND_ID.matcher(record).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The package and a VPN transport on the same line. Ordinary networks carry
     * the NOT_VPN capability, and a request line names the app that asked for a
     * network (SystemUI listens for VPNs), not the app that runs one; a whole
     * dump area around the package would borrow both.
     */
    static boolean ownsVpn(String connectivityDump, String packageName) {
        if (connectivityDump == null) return false;
        for (String line : connectivityDump.split("\n")) {
            if (line.contains("RequestorPkg: " + packageName)) continue;
            if (line.replace("NOT_VPN", "").contains("VPN")
                    && PackageTextMatcher.containsExactPackage(line, packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Android 16 appends "PersonalAppsSuspensionHelper", which lists every app
     * that could be suspended (nearly all of them) and names no admin. That
     * section, up to the next unindented line, is left out.
     */
    static String adminState(String devicePolicyDump) {
        if (devicePolicyDump == null) return null;
        int start = devicePolicyDump.indexOf("\nPersonalAppsSuspensionHelper");
        if (start < 0) return devicePolicyDump;
        int end = start + 1;
        while (true) {
            end = devicePolicyDump.indexOf('\n', end);
            if (end < 0 || end + 1 >= devicePolicyDump.length()) {
                return devicePolicyDump.substring(0, start);
            }
            char next = devicePolicyDump.charAt(end + 1);
            if (next != ' ' && next != '\t' && next != '\n' && next != '\r') {
                return devicePolicyDump.substring(0, start) + devicePolicyDump.substring(end);
            }
            end++;
        }
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
