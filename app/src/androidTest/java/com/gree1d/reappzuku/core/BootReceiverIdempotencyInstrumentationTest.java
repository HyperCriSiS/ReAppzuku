package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class BootReceiverIdempotencyInstrumentationTest {
    private static final String AUTO_KILL_WORK = "AutoKillWorker";
    private static final String SMART_PERIODIC_WORK = "SmartLifecyclePeriodic";
    private static final String SMART_BOOT_WORK = "SmartLifecycleBootCleanup";

    private Context targetContext;
    private SharedPreferences prefs;
    private WorkManager workManager;

    @Before
    public void setUp() throws Exception {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = targetContext.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        assertTrue(prefs.edit().clear().commit());
        workManager = WorkManager.getInstance(targetContext);
        cancelAndAwait(AUTO_KILL_WORK);
        cancelAndAwait(SMART_PERIODIC_WORK);
        cancelAndAwait(SMART_BOOT_WORK);
    }

    @After
    public void tearDown() throws Exception {
        cancelAndAwait(AUTO_KILL_WORK);
        cancelAndAwait(SMART_PERIODIC_WORK);
        cancelAndAwait(SMART_BOOT_WORK);
        prefs.edit().clear().commit();
    }

    @Test
    public void repeatedBootReconciliationKeepsSingleActiveUniqueWorkers() throws Exception {
        assertTrue(prefs.edit()
                .putBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, true)
                .putBoolean(PreferenceKeys.KEY_SMART_LIFECYCLE_ENABLED, true)
                .putBoolean(PreferenceKeys.KEY_SMART_BOOT_CLEANUP_ENABLED, true)
                .commit());

        RecordingContext context = new RecordingContext(targetContext);
        BootReceiver receiver = new BootReceiver();
        Intent boot = new Intent(Intent.ACTION_BOOT_COMPLETED);

        receiver.onReceive(context, boot);
        receiver.onReceive(context, boot);

        assertEquals(2, context.serviceStarts);
        assertEquals(1, activeWorkCount(AUTO_KILL_WORK));
        assertEquals(1, activeWorkCount(SMART_PERIODIC_WORK));
        assertEquals(1, activeWorkCount(SMART_BOOT_WORK));
    }

    private int activeWorkCount(String uniqueName) throws Exception {
        List<WorkInfo> infos = workManager.getWorkInfosForUniqueWork(uniqueName)
                .get(10, TimeUnit.SECONDS);
        int active = 0;
        for (WorkInfo info : infos) {
            if (!info.getState().isFinished()) active++;
        }
        return active;
    }

    private void cancelAndAwait(String uniqueName) throws Exception {
        workManager.cancelUniqueWork(uniqueName).getResult().get(10, TimeUnit.SECONDS);
    }

    private static final class RecordingContext extends ContextWrapper {
        int serviceStarts;

        RecordingContext(Context base) {
            super(base);
        }

        @Override
        public ComponentName startService(Intent service) {
            serviceStarts++;
            return service.getComponent();
        }

        @Override
        public ComponentName startForegroundService(Intent service) {
            serviceStarts++;
            return service.getComponent();
        }
    }
}
