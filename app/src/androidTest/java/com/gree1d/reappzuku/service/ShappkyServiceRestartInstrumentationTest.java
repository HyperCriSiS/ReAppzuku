package com.gree1d.reappzuku.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.core.BackgroundWorkPolicy;
import com.gree1d.reappzuku.core.PreferenceKeys;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ShappkyServiceRestartInstrumentationTest {
    private Context targetContext;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = targetContext.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        assertTrue(prefs.edit().clear().commit());
    }

    @Test
    public void staleRestartAlarmIsIgnoredAfterAutoKillWasPersistentlyDisabled() {
        assertTrue(prefs.edit()
                .putBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false)
                .commit());
        assertFalse(BackgroundWorkPolicy.shouldRunForegroundService(targetContext));

        RecordingContext context = new RecordingContext(targetContext);
        new ShappkyService.RestartReceiver().onReceive(
                context,
                new Intent(targetContext, ShappkyService.RestartReceiver.class));

        assertEquals(0, context.serviceStarts);
    }

    @Test
    public void freshRestartReceiverHonorsPersistedAutoKillEnabledState() {
        assertTrue(prefs.edit()
                .putBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, true)
                .commit());
        assertTrue(BackgroundWorkPolicy.shouldRunForegroundService(targetContext));

        RecordingContext context = new RecordingContext(targetContext);
        new ShappkyService.RestartReceiver().onReceive(
                context,
                new Intent(targetContext, ShappkyService.RestartReceiver.class));

        assertEquals(1, context.serviceStarts);
        assertEquals(ShappkyService.class.getName(), context.lastServiceClassName);
    }

    private static final class RecordingContext extends ContextWrapper {
        int serviceStarts;
        String lastServiceClassName;

        RecordingContext(Context base) {
            super(base);
        }

        @Override
        public ComponentName startService(Intent service) {
            record(service);
            return service.getComponent();
        }

        @Override
        public ComponentName startForegroundService(Intent service) {
            record(service);
            return service.getComponent();
        }

        private void record(Intent service) {
            serviceStarts++;
            ComponentName component = service.getComponent();
            lastServiceClassName = component == null ? null : component.getClassName();
        }
    }
}
