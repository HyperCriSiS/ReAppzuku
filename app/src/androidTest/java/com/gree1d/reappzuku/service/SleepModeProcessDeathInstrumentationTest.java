package com.gree1d.reappzuku.service;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.core.PreferenceKeys;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class SleepModeProcessDeathInstrumentationTest {
    private Context targetContext;
    private SharedPreferences prefs;
    private String targetPackage;

    @Before
    public void setUp() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Guarded runtime probe only",
                "true".equals(arguments.getString("sleepModeProcessDeathProbe")));

        targetPackage = arguments.getString("targetPackage");
        Assume.assumeTrue("A disposable target package is required",
                targetPackage != null && !targetPackage.trim().isEmpty());

        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = targetContext.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    @Test
    public void configureTimerFreezeAndStartIdleFreeze() {
        Set<String> timerApps = new HashSet<>();
        timerApps.add(targetPackage);

        assertTrue(prefs.edit()
                .clear()
                .putBoolean(PreferenceKeys.KEY_SLEEP_MODE_ENABLED, true)
                .putStringSet(PreferenceKeys.KEY_SLEEP_MODE_APPS, timerApps)
                .putStringSet(PreferenceKeys.KEY_SLEEP_MODE_APPS_PERMANENT, Collections.emptySet())
                .putStringSet(PreferenceKeys.KEY_SLEEP_MODE_APPS_FROZEN, Collections.emptySet())
                .putStringSet(PreferenceKeys.KEY_SLEEP_MODE_APPS_SUSPEND_METHOD, Collections.emptySet())
                .commit());

        startServiceAction("IDLE_FREEZE");
    }

    @Test
    public void startRecoveryWithPersistedOwnedFreeze() {
        Set<String> owned = prefs.getStringSet(
                PreferenceKeys.KEY_SLEEP_MODE_APPS_FROZEN, Collections.emptySet());
        assertTrue("Owned timer-freeze marker must survive process death",
                owned != null && owned.contains(targetPackage));
        assertTrue("Sleep Mode must remain enabled across process death",
                prefs.getBoolean(PreferenceKeys.KEY_SLEEP_MODE_ENABLED, false));

        startServiceAction("SCREEN_ON");
    }

    private void startServiceAction(String action) {
        Intent intent = new Intent(targetContext, ShappkyService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            targetContext.startForegroundService(intent);
        } else {
            targetContext.startService(intent);
        }
    }
}
