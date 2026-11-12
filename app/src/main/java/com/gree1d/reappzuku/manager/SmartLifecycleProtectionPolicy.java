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
    private static final java.util.regex.Pattern PLAYBACK_STATE =
            java.util.regex.Pattern.compile("state=PlaybackState \\{state=[A-Z_]*\\((\\d+)\\)");
    private static final java.util.regex.Pattern PLAYBACK_UPDATED =
            java.util.regex.Pattern.compile("\\bupdated=(\\d+)");
    /**
     * A paused player stays protected this long. A phone call or a spoken
     * direction pauses playback for a moment and it resumes afterwards.
     */
    static final long PAUSE_GRACE_MS = 5 * 60_000L;

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

    /** Without a clock: a paused player counts as playing. */
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
        return getDumpProtectionReason(packageName, mediaDump, widgetDump, servicesDump,
                wallpaperDump, devicePolicyDump, connectivityDump,
                protectMedia, protectForegroundServices, protectWidgets, 0L);
    }

    /**
     * The three flags are the per-app switches from the policy editor.
     *
     * @param nowElapsedMs the elapsed realtime when the dumps were taken, or 0 if unknown
     */
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
            boolean protectWidgets,
            long nowElapsedMs) {
        if (protectWidgets
                && PackageTextMatcher.containsExactPackage(placedWidgets(widgetDump), packageName)) {
            return "active widget";
        }
        if (protectMedia && playsMedia(mediaDump, packageName, nowElapsedMs)) {
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
        if (runsVpnService(servicesDump, packageName) || ownsVpn(connectivityDump, packageName)) {
            return "VPN/network service";
        }
        return null;
    }

    /**
     * Whether the package plays media now, or paused it within
     * {@link #PAUSE_GRACE_MS}. A media session alone is not playback: players
     * keep one for as long as their process lives, and that kept every app
     * that had ever played something out of reach.
     *
     * In "dumpsys media_session" each session has a line "package=name" and,
     * further down, "state=PlaybackState {state=PLAYING(3), ..., updated=ms".
     * A dump without any such state line is in a layout this does not know;
     * then any mention of the package counts, as it did before.
     */
    static boolean playsMedia(String mediaDump, String packageName, long nowElapsedMs) {
        if (mediaDump == null) return false;
        boolean anyState = false;
        boolean playing = false;
        String owner = null;
        for (String line : mediaDump.split("\n")) {
            String text = line.trim();
            if (text.startsWith("package=")) {
                owner = text.substring("package=".length()).trim();
                continue;
            }
            java.util.regex.Matcher state = PLAYBACK_STATE.matcher(text);
            if (!state.find()) continue;
            anyState = true;
            if (!packageName.equals(owner)) continue;
            switch (Integer.parseInt(state.group(1))) {
                case 3: // playing
                case 4: // fast forwarding
                case 5: // rewinding
                case 6: // buffering
                case 8: // connecting
                case 9: // skipping to previous
                case 10: // skipping to next
                case 11: // skipping to queue item
                    playing = true;
                    break;
                case 2: // paused
                    java.util.regex.Matcher updated = PLAYBACK_UPDATED.matcher(text);
                    if (nowElapsedMs <= 0 || !updated.find()
                            || nowElapsedMs - Long.parseLong(updated.group(1)) < PAUSE_GRACE_MS) {
                        playing = true;
                    }
                    break;
                default: // none, stopped, error
                    break;
            }
        }
        return anyState ? playing : PackageTextMatcher.containsExactPackage(mediaDump, packageName);
    }

    /**
     * A ServiceRecord of the package that runs in the foreground. Each record
     * is checked on its own: one long dump area would let a package borrow the
     * foreground flag of the next app's service.
     */
    static boolean runsForegroundService(String servicesDump, String packageName) {
        for (String record : serviceRecords(servicesDump, packageName)) {
            if (record.contains("isForeground=true")) return true;
            // The id of the notification stays after the service left the foreground, so
            // it only counts in a record that does not say whether it is in the foreground.
            if (!record.contains("isForeground=") && FOREGROUND_ID.matcher(record).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A running VpnService of the package. dumpsys connectivity names a VPN's
     * owner only by uid, so the bound service is the signal that works on
     * API 24 to 37.
     */
    static boolean runsVpnService(String servicesDump, String packageName) {
        for (String record : serviceRecords(servicesDump, packageName)) {
            if (record.contains("permission=android.permission.BIND_VPN_SERVICE")
                    && record.contains("app=ProcessRecord{")) {
                return true;
            }
        }
        return false;
    }

    /** The ServiceRecord blocks whose header names the package. */
    private static java.util.List<String> serviceRecords(String servicesDump, String packageName) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (servicesDump == null) return result;
        String[] records = servicesDump.split("ServiceRecord[{]", -1);
        for (int i = 1; i < records.length; i++) {
            String record = records[i];
            int lineEnd = record.indexOf('\n');
            String header = lineEnd < 0 ? record : record.substring(0, lineEnd);
            if (PackageTextMatcher.containsExactPackage(header, packageName)) result.add(record);
        }
        return result;
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
            // Another user's admin section ends the list even when it is indented.
            boolean adminSection = devicePolicyDump.startsWith("Enabled Device Admins",
                    end + 1 + leadingSpaces(devicePolicyDump, end + 1));
            if (adminSection || (next != ' ' && next != '\t' && next != '\n' && next != '\r')) {
                return devicePolicyDump.substring(0, start) + devicePolicyDump.substring(end);
            }
            end++;
        }
    }

    private static int leadingSpaces(String text, int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) == ' ') i++;
        return i - from;
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
