package com.gree1d.reappzuku.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProtectedAppsTest {

    @Test
    public void protectsCriticalAospAndPrivilegeBackends() {
        assertTrue(ProtectedApps.isProtected("com.gree1d.reappzuku", null, null));
        assertTrue(ProtectedApps.isProtected("com.android.systemui", null, null));
        assertTrue(ProtectedApps.isProtected("com.android.settings", null, null));
        assertTrue(ProtectedApps.isProtected("com.android.phone", null, null));
        assertTrue(ProtectedApps.isProtected("com.android.permissioncontroller", null, null));
        assertTrue(ProtectedApps.isProtected("com.android.networkstack", null, null));
        assertTrue(ProtectedApps.isProtected("moe.shizuku.privileged.api", null, null));
    }

    @Test
    public void protectsCurrentKeyboardAndLauncher() {
        assertTrue(ProtectedApps.isProtected(
                "org.example.keyboard", "org.example.keyboard", "org.example.launcher"));
        assertTrue(ProtectedApps.isProtected(
                "org.example.launcher", "org.example.keyboard", "org.example.launcher"));
    }

    @Test
    public void doesNotProtectPackageNameNeighborsOrNull() {
        assertFalse(ProtectedApps.isProtected("com.android.systemui.helper", null, null));
        assertFalse(ProtectedApps.isProtected("moe.shizuku.privileged.api.fake", null, null));
        assertFalse(ProtectedApps.isProtected(null, "org.example.keyboard", "org.example.launcher"));
    }
}
