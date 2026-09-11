package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SmartLifecycleProtectionPolicyTest {
    private static final String PKG = "com.example.app";

    @Test
    public void everyDumpProtectionReasonIsExplicit() {
        assertEquals("active widget", reason(
                "", "provider=" + PKG + "/.Widget", "", "", "", ""));
        assertEquals("media session", reason(
                "session package=" + PKG, "", "", "", "", ""));
        assertEquals("foreground service", reason(
                "", "", "ServiceRecord{" + PKG + "/.Sync}\n isForeground=true",
                "", "", ""));
        assertEquals("foreground service", reason(
                "", "", "ServiceRecord{" + PKG + "/.Sync}\n foregroundId=42",
                "", "", ""));
        assertEquals("wallpaper", reason(
                "", "", "", "wallpaperComponent=" + PKG + "/.Live", "", ""));
        assertEquals("device policy", reason(
                "", "", "", "", "admin=" + PKG + "/.Receiver", ""));
        assertEquals("VPN/network service", reason(
                "", "", "", "", "", "VPN owner=" + PKG + "/.VpnService"));
    }

    @Test
    public void unrelatedAndPrefixNeighborPackagesDoNotProtectManagedPackage() {
        String neighbor = "com.example.app.extra";
        assertNull(reason(
                "session package=" + neighbor,
                "provider=" + neighbor + "/.Widget",
                "ServiceRecord{" + neighbor + "/.Sync}\n isForeground=true",
                "wallpaperComponent=" + neighbor + "/.Live",
                "admin=" + neighbor + "/.Receiver",
                "VPN owner=" + neighbor + "/.VpnService"));
    }

    @Test
    public void unrelatedVpnMarkerIsNotBorrowedAcrossLargeDumpDistance() {
        String connectivity = "owner=" + PKG + "/.Network\n"
                + "x".repeat(2200)
                + "\nVPN owner=com.other.vpn/.Service";
        assertNull(reason("", "", "", "", "", connectivity));
    }

    @Test
    public void noProtectionSignalReturnsNull() {
        assertNull(reason("", "", "", "", "", ""));
    }

    private static String reason(
            String media,
            String widget,
            String services,
            String wallpaper,
            String devicePolicy,
            String connectivity) {
        return SmartLifecycleProtectionPolicy.getDumpProtectionReason(
                PKG, media, widget, services, wallpaper, devicePolicy, connectivity);
    }
}
