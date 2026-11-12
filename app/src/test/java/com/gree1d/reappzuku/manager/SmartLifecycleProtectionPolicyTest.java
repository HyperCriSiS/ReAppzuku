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

    @Test
    public void android16SuspensionCandidateListIsNoDeviceAdmin() {
        // dumpsys device_policy on Android 16 lists nearly every app here.
        String policy = "Device state cache:\n  Device provisioned: true\n\n"
                + "PersonalAppsSuspensionHelper\n  critical packages: empty\n"
                + "  Packages subject to suspension: 2 apps\n    0: " + PKG + "\n    1: com.other.app\n"
                + "\nEncryption Status: per-user\n";
        assertNull(reason("", "", "", "", policy, ""));
        assertEquals("device policy", reason("", "", "", "",
                policy + "Enabled Device Admins (User 0):\n  admin=" + PKG + "/.Receiver\n", ""));
    }

    @Test
    public void aRequestNextToSystemUiListeningForVpnsIsNoVpn() {
        String connectivity = "  uid/pid:10135/3093 callbackRequest: 48 [NetworkRequest [ LISTEN id=48,"
                + " [ Transports: VPN RequestorUid: 10135 RequestorPkg: com.android.systemui ] ]]\n"
                + "      NetworkRequest [ REQUEST id=371, [ Capabilities: INTERNET&NOT_VCN_MANAGED"
                + " Uid: 10244 RequestorUid: 10244 RequestorPkg: " + PKG + " ] ]\n";
        assertNull(reason("", "", "", "", "", connectivity));
        // The app that asked for VPN networks does not run one either.
        assertNull(SmartLifecycleProtectionPolicy.getDumpProtectionReason("com.android.systemui",
                "", "", "", "", "", connectivity, true, true, true));
    }

    @Test
    public void aForegroundFlagBelongsToItsOwnServiceRecord() {
        String services = "  * ServiceRecord{1 u0 " + PKG + "/.Sync}\n    isForeground=false foregroundId=0\n"
                + "  * ServiceRecord{2 u0 com.other.app/.Player}\n    isForeground=true foregroundId=7\n";
        assertNull(reason("", "", services, "", "", ""));
        assertEquals("foreground service", SmartLifecycleProtectionPolicy.getDumpProtectionReason(
                "com.other.app", "", "", services, "", "", "", true, true, true));
    }

    @Test
    public void aRunningVpnServiceIsProtectedThroughItsServiceRecord() {
        String services = "  * ServiceRecord{3 u0 " + PKG + "/.Tunnel}\n"
                + "    intent={act=android.net.VpnService cmp=" + PKG + "/.Tunnel}\n"
                + "    permission=android.permission.BIND_VPN_SERVICE\n"
                + "    app=ProcessRecord{45d1bfc 23792:" + PKG + "/u0a172}\n"
                + "    isForeground=false foregroundId=0\n";
        assertEquals("VPN/network service", reason("", "", services, "", "", ""));
        // A declared but not running VPN service is no VPN.
        assertNull(reason("", "", services.replace("app=ProcessRecord{", "app=null "), "", "", ""));
    }

    @Test
    public void anotherUsersAdminAfterTheSuspensionListStaysProtected() {
        String policy = "Device state cache:\n\nPersonalAppsSuspensionHelper\n  Packages subject to suspension: 1 apps\n"
                + "    0: com.other.app\n  Enabled Device Admins (User 10, provisioningState: 3):\n"
                + "    admin=" + PKG + "/.Receiver\n";
        assertEquals("device policy", reason("", "", "", "", policy, ""));
        assertNull(SmartLifecycleProtectionPolicy.getDumpProtectionReason("com.other.app",
                "", "", "", "", policy, "", true, true, true));
    }

    @Test
    public void theWatchdogAndThePassShareTheForegroundServiceCheck() throws Exception {
        String root = java.nio.file.Files.isDirectory(java.nio.file.Paths.get("app")) ? "" : "../";
        String watchdog = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(root
                + "app/src/main/java/com/gree1d/reappzuku/manager/RestrictionsWatchdogManager.java")), "UTF-8");
        org.junit.Assert.assertTrue(watchdog.contains("SmartLifecycleProtectionPolicy.runsForegroundService(servicesDump, packageName)"));
        String killer = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(root
                + "app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java")), "UTF-8");
        // A root shell reports a failed dumpsys as text; that is no dump either.
        org.junit.Assert.assertTrue(killer.contains("activityDump[0].startsWith(\"ERROR:\")"));
    }
}
