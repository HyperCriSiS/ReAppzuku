package com.gree1d.reappzuku.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.core.PreferenceKeys;
import com.gree1d.reappzuku.service.ShappkyService;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class ShortcutAbuseInstrumentationTest {
    private static final String ARG_ABUSE_PROBE = "shortcutAbuseProbe";
    private static final String EXTRA_TOKEN = "com.gree1d.reappzuku.extra.SHORTCUT_TOKEN";
    private static final String COMPONENT =
            "com.gree1d.reappzuku/com.gree1d.reappzuku.utils.KillShortcutActivity";

    private Context targetContext;

    @Before
    public void requireExplicitAbuseProbeAndStopService() throws Exception {
        Assume.assumeTrue(
                "Exported shortcut abuse probe is only run by the guarded security lane",
                "true".equals(InstrumentationRegistry.getArguments().getString(ARG_ABUSE_PROBE)));
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        targetContext.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false).commit();
        targetContext.stopService(new Intent(targetContext, ShappkyService.class));
        android.os.SystemClock.sleep(400L);
        assertServiceNotRunning();
    }

    @Test
    public void externalSecureActionWithoutTokenCannotStartPrivilegedService() throws Exception {
        String output = shell("am start -W -n " + COMPONENT
                + " -a " + ShortcutAuth.ACTION_RAM_KILL_SECURE);
        assertTrue("External explicit launch should resolve the exported activity",
                output.contains("Status: ok") || output.contains("Activity:"));
        android.os.SystemClock.sleep(700L);
        assertServiceNotRunning();
    }

    @Test
    public void externalSecureActionWithForgedTokenCannotStartPrivilegedService() throws Exception {
        String output = shell("am start -W -n " + COMPONENT
                + " -a " + ShortcutAuth.ACTION_RAM_KILL_SECURE
                + " --es " + EXTRA_TOKEN + " definitely-not-the-install-secret");
        assertTrue("External explicit launch should resolve the exported activity",
                output.contains("Status: ok") || output.contains("Activity:"));
        android.os.SystemClock.sleep(700L);
        assertServiceNotRunning();
    }

    private void assertServiceNotRunning() throws Exception {
        String services = shell("dumpsys activity services " + targetContext.getPackageName());
        assertFalse("Unauthenticated external intent must not start ShappkyService",
                services.contains("ShappkyService"));
    }

    private String shell(String command) throws Exception {
        ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        try (FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            descriptor.close();
        }
    }
}
