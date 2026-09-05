package com.gree1d.reappzuku.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PrivilegedShellTest {

    @Test
    public void buildsTypedPackageCommands() {
        assertEquals("am kill com.example.app",
                PrivilegedShell.buildKillCommand("com.example.app", PrivilegedShell.KillMode.KILL));
        assertEquals("am force-stop com.example.app",
                PrivilegedShell.buildKillCommand("com.example.app", PrivilegedShell.KillMode.FORCE_STOP));
        assertEquals("pm uninstall com.example.app",
                PrivilegedShell.buildUninstallCommand("com.example.app"));
        assertEquals("am set-standby-bucket com.example.app 45",
                PrivilegedShell.buildStandbyBucketCommand(
                        "com.example.app", PrivilegedShell.StandbyBucket.RESTRICTED));
        assertEquals("cmd deviceidle whitelist -com.example.app",
                PrivilegedShell.buildDeviceIdleWhitelistCommand(
                        "com.example.app", PrivilegedShell.DeviceIdleWhitelistAction.REMOVE));
    }

    @Test
    public void appOpsUseTypedModesAndStrictOpNames() {
        assertEquals(
                "cmd appops set --user current com.example.app RUN_ANY_IN_BACKGROUND ignore",
                PrivilegedShell.buildAppOpCommand(
                        "com.example.app", "RUN_ANY_IN_BACKGROUND", PrivilegedShell.AppOpMode.IGNORE));
        assertEquals(PrivilegedShell.AppOpMode.DEFAULT,
                PrivilegedShell.AppOpMode.fromShellValue("default"));
        assertThrows(IllegalArgumentException.class,
                () -> PrivilegedShell.AppOpMode.fromShellValue("ignore; id"));
        assertThrows(IllegalArgumentException.class,
                () -> PrivilegedShell.buildAppOpCommand(
                        "com.example.app", "RUN_ANY_IN_BACKGROUND;id", PrivilegedShell.AppOpMode.IGNORE));
    }

    @Test
    public void rejectsPackageInjectionAtEveryMutatingBoundary() {
        String injected = "com.example.app;id";
        assertThrows(IllegalArgumentException.class,
                () -> PrivilegedShell.buildKillCommand(injected, PrivilegedShell.KillMode.FORCE_STOP));
        assertThrows(IllegalArgumentException.class,
                () -> PrivilegedShell.buildUninstallCommand(injected));
        assertThrows(IllegalArgumentException.class,
                () -> PrivilegedShell.buildStandbyBucketCommand(
                        injected, PrivilegedShell.StandbyBucket.RARE));
        assertThrows(IllegalArgumentException.class,
                () -> PrivilegedShell.buildDeviceIdleWhitelistCommand(
                        injected, PrivilegedShell.DeviceIdleWhitelistAction.ADD));
    }

    @Test
    public void killBatchValidatesEveryPackage() {
        assertEquals("am force-stop com.one.app; am force-stop com.two.app",
                PrivilegedShell.buildKillBatchCommand(
                        Arrays.asList("com.one.app", "com.two.app"),
                        PrivilegedShell.KillMode.FORCE_STOP));
        assertThrows(IllegalArgumentException.class,
                () -> PrivilegedShell.buildKillBatchCommand(
                        Arrays.asList("com.one.app", "com.two.app && id"),
                        PrivilegedShell.KillMode.FORCE_STOP));
        assertThrows(IllegalArgumentException.class,
                () -> PrivilegedShell.buildKillBatchCommand(
                        Collections.emptyList(), PrivilegedShell.KillMode.FORCE_STOP));
    }

    @Test
    public void pidKillOnlyAcceptsPositiveNumericProcessIds() {
        assertEquals("kill -9 123 4567",
                PrivilegedShell.buildKillPidsCommand(Arrays.asList("123", "4567")));
        assertThrows(IllegalArgumentException.class,
                () -> PrivilegedShell.buildKillPidsCommand(Arrays.asList("123", "456;id")));
        assertThrows(IllegalArgumentException.class,
                () -> PrivilegedShell.buildKillPidsCommand(Collections.singletonList("1")));
    }

    @Test
    public void legacyMappingsAreClosedSets() {
        assertEquals(PrivilegedShell.KillMode.KILL,
                PrivilegedShell.KillMode.fromAutoKillType(1));
        assertEquals(PrivilegedShell.KillMode.FORCE_STOP,
                PrivilegedShell.KillMode.fromAutoKillType(0));
        assertEquals(PrivilegedShell.StandbyBucket.RARE,
                PrivilegedShell.StandbyBucket.fromLegacyValue(40));
        assertEquals(PrivilegedShell.StandbyBucket.RESTRICTED,
                PrivilegedShell.StandbyBucket.fromLegacyValue(45));
        assertThrows(IllegalArgumentException.class,
                () -> PrivilegedShell.StandbyBucket.fromLegacyValue(999));
    }
}
