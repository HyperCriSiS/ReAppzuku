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
    public void eachPolicySwitchDisablesOnlyItsOwnProtection() {
        String media = "session package=" + PKG;
        String widget = "provider=" + PKG + "/.Widget";
        String services = "ServiceRecord{" + PKG + "/.Sync}\n isForeground=true";

        assertEquals("media session", SmartLifecycleProtectionPolicy.getDumpProtectionReason(
                PKG, media, widget, services, "", "", "", true, true, false));
        assertEquals("foreground service", SmartLifecycleProtectionPolicy.getDumpProtectionReason(
                PKG, media, widget, services, "", "", "", false, true, false));
        assertNull(SmartLifecycleProtectionPolicy.getDumpProtectionReason(
                PKG, media, widget, services, "", "", "", false, false, false));
        assertEquals("wallpaper", SmartLifecycleProtectionPolicy.getDumpProtectionReason(
                PKG, "", "", "", "wallpaperComponent=" + PKG + "/.Live", "", "",
                false, false, false));
    }

    @Test
    public void installedButUnplacedWidgetProviderDoesNotProtect() {
        String provider = "ProviderId{user:0, app:10123, cmp:ComponentInfo{" + PKG + "/.Widget}}";
        String installedOnly = "Providers:\n  [0] provider " + provider
                + "\n\nWidgets:\n\nHosts:\n  [0] hostId=HostId{user:0, pkg:com.launcher}\n";
        assertNull(reason("", installedOnly, "", "", "", ""));

        String placed = "Providers:\n  [0] provider " + provider
                + "\n\nWidgets:\n  [0] id=7\n    provider=" + provider
                + "\n\nHosts:\n  [0] hostId=HostId{user:0, pkg:com.launcher}\n";
        assertEquals("active widget", reason("", placed, "", "", "", ""));
    }

    @Test
    public void notVpnCapabilityOfAnOrdinaryNetworkDoesNotProtect() {
        String connectivity = "NetworkRequest [ REQUEST id=7, [ Capabilities: INTERNET&NOT_VPN"
                + " RequestorUid: 10123 RequestorPkg: " + PKG + " ] ]";
        assertNull(reason("", "", "", "", "", connectivity));
        assertEquals("VPN/network service", reason("", "", "", "", "",
                "Transports: VPN Capabilities: INTERNET OwnerPkg: " + PKG + " "));
    }

    @Test
    public void widgetHostWithoutPlacedWidgetIsNotProtected() {
        String hostOnly = "Providers:\n\nWidgets:\n\nHosts:\n  [0] hostId=HostId{user:0, pkg:"
                + PKG + "}\n";
        assertNull(reason("", hostOnly, "", "", "", ""));
        assertEquals("active widget", reason("", "Widgets:\n provider=" + PKG + "/.W", "", "", "", ""));
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
