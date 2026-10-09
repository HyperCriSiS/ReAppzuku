package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

/**
 * Non-privileged, read-only checks of the installed (merged) manifest.
 * Uses PackageManager rather than parsing the source manifest so library
 * manifest merges cannot silently widen the exported component surface.
 */
public class ExportedComponentManifestInstrumentationTest {
    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    // ShizukuWakeReceiver is intentionally runtime-disabled in On-demand mode.
    // Include disabled manifest entries to audit their declared export boundary.
    private ComponentInfo receiver(String name) throws Exception {
        Context context = context();
        return context.getPackageManager().getReceiverInfo(
                new ComponentName(context.getPackageName(), name),
                PackageManager.MATCH_DISABLED_COMPONENTS);
    }

    private ActivityInfo activity(String name) throws Exception {
        Context context = context();
        return context.getPackageManager().getActivityInfo(
                new android.content.ComponentName(context.getPackageName(), name), 0);
    }

    private ServiceInfo service(String name) throws Exception {
        Context context = context();
        return context.getPackageManager().getServiceInfo(
                new android.content.ComponentName(context.getPackageName(), name), 0);
    }

    private ProviderInfo provider(String name) throws Exception {
        Context context = context();
        return context.getPackageManager().getProviderInfo(
                new ComponentName(context.getPackageName(), name),
                PackageManager.MATCH_DISABLED_COMPONENTS);
    }

    @Test
    public void widgetAndShizukuBootstrapKeepTheirReviewedBoundaries() throws Exception {
        assertTrue(receiver("com.gree1d.reappzuku.utils.AppzukuWidgetReceiver").exported);

        ProviderInfo shizuku = provider("rikka.shizuku.ShizukuProvider");
        assertTrue(shizuku.exported);
        assertEquals(context().getPackageName() + ".shizuku", shizuku.authority);
        assertEquals("android.permission.INTERACT_ACROSS_USERS_FULL",
                shizuku.permission);
    }

    @Test
    public void policyAndSettingsActivitiesCannotBeLaunchedByForeignApps() throws Exception {
        String ui = "com.gree1d.reappzuku.ui.";
        assertFalse(activity(ui + "AppPolicyEditorActivity").exported);
        assertFalse(activity(ui + "NewAppSetupSettingsActivity").exported);
        assertFalse(activity(ui + "SettingsActivity").exported);
        assertFalse(activity(ui + "StatisticsActivity").exported);
        assertFalse(activity(ui + "PresetSettingsActivity").exported);
        assertTrue(activity(ui + "MainActivity").exported);
        assertTrue(activity("com.gree1d.reappzuku.utils.KillShortcutActivity").exported);
    }

    @Test
    public void platformBoundExportedServicesRequireTheirSignaturePermission() throws Exception {
        ServiceInfo accessibility = service(
                "com.gree1d.reappzuku.service.AppLaunchAccessibilityService");
        assertTrue(accessibility.exported);
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",
                accessibility.permission);
        for (String name : new String[] {
                "com.gree1d.reappzuku.utils.ShappkyQuickTile",
                "com.gree1d.reappzuku.utils.ShappkyBackgroundKillTile"
        }) {
            ServiceInfo tile = service(name);
            assertTrue(tile.exported);
            assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE",
                    tile.permission);
        }
        assertFalse(service("com.gree1d.reappzuku.service.ShappkyService").exported);
    }

    @Test
    public void internalTriggerReceiversStayPrivate() throws Exception {
        assertFalse(receiver("com.gree1d.reappzuku.service.KillTriggerReceiver").exported);
        assertFalse(receiver("com.gree1d.reappzuku.core.ShizukuWakeReceiver").exported);
        assertFalse(receiver("com.gree1d.reappzuku.service.ShappkyService$RestartReceiver").exported);
        assertFalse(receiver("com.gree1d.reappzuku.service.CollectStatsReceiver").exported);
        assertFalse(receiver("com.gree1d.reappzuku.manager.RestrictionsScheduler$SchedulerReceiver").exported);
        assertFalse(receiver("com.gree1d.reappzuku.manager.PresetManager$PresetReceiver").exported);
        assertTrue(receiver("com.gree1d.reappzuku.core.BootReceiver").exported);
        assertTrue(receiver("com.gree1d.reappzuku.core.PackageAddedReceiver").exported);
    }
}