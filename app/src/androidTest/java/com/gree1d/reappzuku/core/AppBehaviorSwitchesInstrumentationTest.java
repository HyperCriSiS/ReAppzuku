package com.gree1d.reappzuku.core;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.ui.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class AppBehaviorSwitchesInstrumentationTest {
    private static final String RESTRICTION_SCHEDULES = "restrictions_schedules";
    private static final String[] KEYS = {
            KEY_PREVENT_SHIZUKU_AUTOSTART, KEY_EXIT_ON_BACK,
            KEY_AUTO_KILL_ENABLED, KEY_SLEEP_MODE_ENABLED, KEY_ACTIVE_PRESET,
            RESTRICTION_SCHEDULES
    };

    @Test
    public void threeSwitchesRemainConsistentAndRestoreAfterAutomationBlocker() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        SharedPreferences prefs =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        Map<String, ?> previous = new HashMap<>(prefs.getAll());
        Activity activity = null;
        try {
            assertTrue(prefs.edit()
                    .putBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, false)
                    .putBoolean(KEY_EXIT_ON_BACK, false)
                    .putBoolean(KEY_AUTO_KILL_ENABLED, false)
                    .putBoolean(KEY_SLEEP_MODE_ENABLED, false)
                    .putInt(KEY_ACTIVE_PRESET, 0)
                    .putString(RESTRICTION_SCHEDULES, "[]")
                    .commit());

            Intent launch = new Intent(context, SettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = instrumentation.startActivitySync(launch);
            instrumentation.waitForIdleSync();

            MaterialSwitch master = activity.findViewById(R.id.switch_on_demand_mode);
            MaterialSwitch prevent = activity.findViewById(R.id.switch_prevent_shizuku_autostart);
            MaterialSwitch exit = activity.findViewById(R.id.switch_exit_on_back);
            assertFalse(master.isChecked());
            assertFalse(prevent.isChecked());
            assertFalse(exit.isChecked());

            instrumentation.runOnMainSync(master::performClick);
            instrumentation.waitForIdleSync();
            assertTrue(master.isChecked());
            assertTrue(prevent.isChecked());
            assertTrue(exit.isChecked());
            assertTrue(prefs.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, false));
            assertTrue(prefs.getBoolean(KEY_EXIT_ON_BACK, false));

            instrumentation.runOnMainSync(prevent::performClick);
            instrumentation.waitForIdleSync();
            assertFalse(master.isChecked());
            assertFalse(prevent.isChecked());
            assertTrue(exit.isChecked());
            assertFalse(prefs.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, true));
            assertTrue(prefs.getBoolean(KEY_EXIT_ON_BACK, false));

            instrumentation.runOnMainSync(master::performClick);
            instrumentation.waitForIdleSync();
            assertTrue(master.isChecked());
            assertTrue(prevent.isChecked());
            assertTrue(exit.isChecked());

            // Simulate a schedule/automation preference change while Settings is open.
            // The effective switches are paused, but requested choices must survive.
            assertTrue(prefs.edit().putBoolean(KEY_AUTO_KILL_ENABLED, true).commit());
            instrumentation.waitForIdleSync();
            assertFalse(master.isEnabled());
            assertFalse(prevent.isEnabled());
            assertFalse(exit.isEnabled());
            assertFalse(master.isChecked());
            assertFalse(prevent.isChecked());
            assertFalse(exit.isChecked());
            assertTrue(prefs.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, false));
            assertTrue(prefs.getBoolean(KEY_EXIT_ON_BACK, false));
            assertTrue(BackgroundWorkPolicy.requiresBackgroundContinuity(context));
            assertFalse(BackgroundWorkPolicy.shouldPreventShizukuAutoStart(context));
            assertTrue(receiverEnabled(context));

            assertTrue(prefs.edit().putBoolean(KEY_AUTO_KILL_ENABLED, false).commit());
            instrumentation.waitForIdleSync();
            assertTrue(master.isEnabled());
            assertTrue(prevent.isEnabled());
            assertTrue(exit.isEnabled());
            assertTrue(master.isChecked());
            assertTrue(prevent.isChecked());
            assertTrue(exit.isChecked());
            assertTrue(BackgroundWorkPolicy.shouldPreventShizukuAutoStart(context));
            assertFalse(receiverEnabled(context));
        } finally {
            if (activity != null) {
                Activity toFinish = activity;
                instrumentation.runOnMainSync(toFinish::finish);
                instrumentation.waitForIdleSync();
            }
            SharedPreferences.Editor restore = prefs.edit();
            for (String key : KEYS) {
                if (!previous.containsKey(key)) {
                    restore.remove(key);
                } else {
                    Object value = previous.get(key);
                    if (value instanceof Boolean) {
                        restore.putBoolean(key, (Boolean) value);
                    } else if (value instanceof Integer) {
                        restore.putInt(key, (Integer) value);
                    } else if (value instanceof String) {
                        restore.putString(key, (String) value);
                    }
                }
            }
            assertTrue(restore.commit());
            BackgroundWorkPolicy.enforceCompatibleBehavior(context);
        }
    }

    private static boolean receiverEnabled(Context context) {
        ComponentName receiver = new ComponentName(context, ShizukuWakeReceiver.class);
        return context.getPackageManager().getComponentEnabledSetting(receiver)
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
}
