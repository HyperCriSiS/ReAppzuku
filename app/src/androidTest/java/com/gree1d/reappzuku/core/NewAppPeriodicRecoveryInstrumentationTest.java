package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Non-privileged API-24/API-37 integration regression. Exercise the real
 * WorkManager persisted unique-work index, not package installation or workers.
 * Restore ownership: only cancel work when this test had to create it.
 */
public class NewAppPeriodicRecoveryInstrumentationTest {
    @Test
    public void repeatedSchedulingPreservesSingleDurablePeriodicWork() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WorkManager workManager = WorkManager.getInstance(context);
        Set<UUID> before = activeIds(workManager);
        assertTrue("Pre-existing periodic reconciliation must already be unique",
                before.size() <= 1);
        try {
            NewAppInstallMonitor.schedulePeriodic(context);
            Set<UUID> afterFirst = awaitSingleActive(workManager);
            NewAppInstallMonitor.schedulePeriodic(context);
            NewAppInstallMonitor.schedulePeriodic(context);
            Set<UUID> afterDuplicates = awaitSingleActive(workManager);

            assertEquals("KEEP must not replace a running/pending work request",
                    afterFirst, afterDuplicates);
            if (!before.isEmpty()) {
                assertEquals("Startup restoration must preserve the existing request",
                        before, afterDuplicates);
            }
        } finally {
            if (before.isEmpty()) {
                workManager.cancelUniqueWork(NewAppInstallMonitor.PERIODIC_WORK)
                        .getResult().get(15, TimeUnit.SECONDS);
            }
        }
    }

    private static Set<UUID> awaitSingleActive(WorkManager workManager) throws Exception {
        long deadline = System.currentTimeMillis() + 8000L;
        Set<UUID> ids;
        do {
            ids = activeIds(workManager);
            if (ids.size() == 1) return ids;
            Thread.sleep(80L);
        } while (System.currentTimeMillis() < deadline);
        assertEquals("Exactly one unfinished periodic task must remain", 1, ids.size());
        return ids;
    }

    private static Set<UUID> activeIds(WorkManager workManager) throws Exception {
        List<WorkInfo> entries = workManager
                .getWorkInfosForUniqueWork(NewAppInstallMonitor.PERIODIC_WORK)
                .get(15, TimeUnit.SECONDS);
        assertNotNull(entries);
        Set<UUID> ids = new HashSet<>();
        for (WorkInfo info : entries) {
            if (!info.getState().isFinished()) ids.add(info.getId());
        }
        return ids;
    }
}
