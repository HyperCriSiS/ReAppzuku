package com.gree1d.reappzuku.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.core.PreferenceKeys;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class AccessibilityRuntimeInstrumentationTest {
    private static final long SERVICE_TIMEOUT_MS = 15_000L;
    private static final long EVENT_TIMEOUT_MS = 15_000L;
    private static final String OP_BIND_ACCESSIBILITY_SERVICE = "BIND_ACCESSIBILITY_SERVICE";
    private static final String OP_ACCESS_RESTRICTED_SETTINGS = "ACCESS_RESTRICTED_SETTINGS";

    private Instrumentation instrumentation;
    private Context targetContext;
    private SharedPreferences prefs;

    private String originalEnabledServices;
    private String originalAccessibilityEnabled;
    private String originalBindAccessibilityMode;
    private String originalRestrictedSettingsMode;

    private boolean hadTriggerEnabled;
    private boolean originalTriggerEnabled;
    private boolean hadAutoKillEnabled;
    private boolean originalAutoKillEnabled;

    private String observedPackage;
    private String foregroundKey;
    private String backgroundSinceKey;
    private boolean hadForegroundTimestamp;
    private long originalForegroundTimestamp;
    private boolean hadBackgroundSince;
    private long originalBackgroundSince;

    @Before
    public void setUp() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        targetContext = instrumentation.getTargetContext();
        prefs = targetContext.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);

        originalEnabledServices = shell("settings get secure enabled_accessibility_services");
        originalAccessibilityEnabled = shell("settings get secure accessibility_enabled");
        originalBindAccessibilityMode = readAppOpMode(OP_BIND_ACCESSIBILITY_SERVICE);
        originalRestrictedSettingsMode = readAppOpMode(OP_ACCESS_RESTRICTED_SETTINGS);

        hadTriggerEnabled = prefs.contains(PreferenceKeys.KEY_APP_LAUNCH_TRIGGER_ENABLED);
        originalTriggerEnabled = prefs.getBoolean(
                PreferenceKeys.KEY_APP_LAUNCH_TRIGGER_ENABLED, false);
        hadAutoKillEnabled = prefs.contains(PreferenceKeys.KEY_AUTO_KILL_ENABLED);
        originalAutoKillEnabled = prefs.getBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false);

        // Runtime evidence must remain non-destructive: the accessibility service may observe
        // a real foreground transition, but it must not be allowed to invoke AutoKill.
        assertTrue(prefs.edit()
                .putBoolean(PreferenceKeys.KEY_APP_LAUNCH_TRIGGER_ENABLED, false)
                .putBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false)
                .commit());

        Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo resolved = targetContext.getPackageManager().resolveActivity(
                settingsIntent, PackageManager.MATCH_DEFAULT_ONLY);
        assertNotNull("System Settings activity must resolve", resolved);
        assertNotNull("Resolved Settings activity info must exist", resolved.activityInfo);
        observedPackage = resolved.activityInfo.packageName;
        assertNotNull("Resolved Settings package must exist", observedPackage);

        foregroundKey = PreferenceKeys.KEY_SMART_LAST_FOREGROUND_PREFIX + observedPackage;
        backgroundSinceKey = PreferenceKeys.KEY_SMART_BACKGROUND_SINCE_PREFIX + observedPackage;
        hadForegroundTimestamp = prefs.contains(foregroundKey);
        originalForegroundTimestamp = prefs.getLong(foregroundKey, 0L);
        hadBackgroundSince = prefs.contains(backgroundSinceKey);
        originalBackgroundSince = prefs.getLong(backgroundSinceKey, 0L);
        assertTrue(prefs.edit().remove(foregroundKey).commit());
    }

    @After
    public void tearDown() throws Exception {
        restoreSecureSetting("enabled_accessibility_services", originalEnabledServices);
        restoreSecureSetting("accessibility_enabled", originalAccessibilityEnabled);
        restoreAppOp(OP_BIND_ACCESSIBILITY_SERVICE, originalBindAccessibilityMode);
        restoreAppOp(OP_ACCESS_RESTRICTED_SETTINGS, originalRestrictedSettingsMode);

        SharedPreferences.Editor editor = prefs.edit();
        if (hadTriggerEnabled) {
            editor.putBoolean(PreferenceKeys.KEY_APP_LAUNCH_TRIGGER_ENABLED, originalTriggerEnabled);
        } else {
            editor.remove(PreferenceKeys.KEY_APP_LAUNCH_TRIGGER_ENABLED);
        }
        if (hadAutoKillEnabled) {
            editor.putBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, originalAutoKillEnabled);
        } else {
            editor.remove(PreferenceKeys.KEY_AUTO_KILL_ENABLED);
        }
        if (hadForegroundTimestamp) {
            editor.putLong(foregroundKey, originalForegroundTimestamp);
        } else if (foregroundKey != null) {
            editor.remove(foregroundKey);
        }
        if (hadBackgroundSince) {
            editor.putLong(backgroundSinceKey, originalBackgroundSince);
        } else if (backgroundSinceKey != null) {
            editor.remove(backgroundSinceKey);
        }
        assertTrue(editor.commit());
    }

    @Test
    public void realWindowStateEventBindsMinimalServiceAndRecordsForegroundWithoutAutoKill()
            throws Exception {
        ComponentName serviceComponent = new ComponentName(
                targetContext, AppLaunchAccessibilityService.class);
        String flattened = serviceComponent.flattenToString();
        String packageName = targetContext.getPackageName();

        // adb-installed debug APKs are treated as sideloaded apps on current Android releases.
        // Simulate the state after the user has explicitly allowed the restricted Accessibility
        // capability; this test is about service runtime behavior, not the Settings confirmation UI.
        shell("appops set --user 0 " + packageName + " "
                + OP_BIND_ACCESSIBILITY_SERVICE + " allow");
        shell("appops set --user 0 " + packageName + " "
                + OP_ACCESS_RESTRICTED_SETTINGS + " allow");

        String currentServices = normalizeSettingValue(originalEnabledServices);
        String enabledServices = containsComponent(currentServices, flattened)
                ? currentServices
                : currentServices.isEmpty() ? flattened : currentServices + ":" + flattened;

        shell("settings put secure enabled_accessibility_services " + shellQuote(enabledServices));
        shell("settings put secure accessibility_enabled 1");

        AccessibilityServiceInfo serviceInfo = waitForEnabledService(serviceComponent);
        assertNotNull("Accessibility service did not bind", serviceInfo);
        assertEquals("Runtime event scope must stay window-state-only",
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, serviceInfo.eventTypes);
        assertEquals("Runtime feedback type must stay generic",
                AccessibilityServiceInfo.FEEDBACK_GENERIC, serviceInfo.feedbackType);
        assertEquals("Service must not request accessibility flags", 0, serviceInfo.flags);
        assertEquals("Service must not gain view-tree retrieval capability", 0,
                serviceInfo.getCapabilities()
                        & AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT);

        long launchStartedAt = System.currentTimeMillis();
        Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        targetContext.startActivity(settingsIntent);

        waitUntil(EVENT_TIMEOUT_MS, () ->
                prefs.getLong(foregroundKey, 0L) >= launchStartedAt);

        long recorded = prefs.getLong(foregroundKey, 0L);
        assertTrue("Real Settings foreground event was not recorded", recorded >= launchStartedAt);
        assertFalse("Disabled app-launch trigger must remain disabled",
                prefs.getBoolean(PreferenceKeys.KEY_APP_LAUNCH_TRIGGER_ENABLED, false));
        assertFalse("Disabled AutoKill must remain disabled",
                prefs.getBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false));
        assertFalse("Foreground observation should clear stale Smart Lifecycle background state",
                prefs.contains(backgroundSinceKey));
    }

    private AccessibilityServiceInfo waitForEnabledService(ComponentName component)
            throws Exception {
        AccessibilityManager manager = (AccessibilityManager)
                targetContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
        assertNotNull(manager);

        final AccessibilityServiceInfo[] found = new AccessibilityServiceInfo[1];
        waitUntil(SERVICE_TIMEOUT_MS, () -> {
            List<AccessibilityServiceInfo> enabled = manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo info : enabled) {
                if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) {
                    continue;
                }
                ComponentName candidate = new ComponentName(
                        info.getResolveInfo().serviceInfo.packageName,
                        info.getResolveInfo().serviceInfo.name);
                if (component.equals(candidate)) {
                    found[0] = info;
                    return true;
                }
            }
            return false;
        });
        return found[0];
    }

    private void waitUntil(long timeoutMs, Condition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (condition.isTrue()) return;
            Thread.sleep(100L);
        }
        assertTrue("Timed out waiting for runtime accessibility condition", condition.isTrue());
    }

    private String readAppOpMode(String op) throws Exception {
        String output = shell("appops get --user 0 " + targetContext.getPackageName() + " " + op);
        String prefix = op + ":";
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(prefix)) continue;
            String value = trimmed.substring(prefix.length()).trim();
            int separator = value.indexOf(';');
            if (separator >= 0) value = value.substring(0, separator).trim();
            int whitespace = value.indexOf(' ');
            if (whitespace >= 0) value = value.substring(0, whitespace).trim();
            if (!value.isEmpty()) return value;
        }
        return "default";
    }

    private void restoreAppOp(String op, String originalMode) throws Exception {
        if (targetContext == null || originalMode == null || originalMode.isEmpty()) return;
        if (!originalMode.matches("[a-z_]+")) {
            throw new AssertionError("Unexpected saved AppOp mode for " + op + ": " + originalMode);
        }
        shell("appops set --user 0 " + targetContext.getPackageName()
                + " " + op + " " + originalMode);
    }

    private void restoreSecureSetting(String key, String originalValue) throws Exception {
        if (targetContext == null) return;
        String normalized = normalizeSettingValue(originalValue);
        if (normalized.isEmpty()) {
            shell("settings delete secure " + key);
        } else {
            shell("settings put secure " + key + " " + shellQuote(normalized));
        }
    }

    private static boolean containsComponent(String enabledServices, String component) {
        if (enabledServices == null || enabledServices.isEmpty()) return false;
        for (String candidate : enabledServices.split(":")) {
            if (component.equals(candidate)) return true;
        }
        return false;
    }

    private static String normalizeSettingValue(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return "null".equals(trimmed) ? "" : trimmed;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private String shell(String command) throws Exception {
        UiAutomation automation = instrumentation.getUiAutomation();
        ParcelFileDescriptor descriptor = automation.executeShellCommand(command);
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name()).trim();
        }
    }

    private interface Condition {
        boolean isTrue() throws Exception;
    }
}
