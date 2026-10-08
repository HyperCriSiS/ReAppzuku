package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;

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
 * Two separate CI-driven instrumentation sessions with a real intervening
 * background-process kill (ActivityManager 'am kill', not 'am force-stop').
 * The CI validates the PID disappears before relaunching the app. Only the
 * unique periodic new-app reconciliation work is examined, never executed.
 */
public final class Api24Api37ProcessRestartInstrumentationTest {
    private static final String KEY_UUID = "expected_uuid";
    private static final String KEY_CREATED = "created_by_test";

    @Test
    public void snapshotPersistedPeriodicWorkBeforeProcessDeath() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WorkManager wm = WorkManager.getInstance(context);
        Set<UUID> before = activeIds(wm);
        assertTrue("Pre-existing reconciliation work cannot have duplicates",
                before.size() <= 1);

        NewAppInstallMonitor.schedulePeriodic(context);
        UUID uuid = awaitSingleActive(wm);
        if (!before.isEmpty()) {
            assertEquals("KEEP must retain the existing work identity",
                    before.iterator().next(), uuid);
        }

        Bundle result = new Bundle();
        result.putString("REAPPZUKU_RECOVERY_ID", uuid.toString());
        result.putString("REAPPZUKU_RECOVERY_CREATED",
                before.isEmpty() ? "yes" : "no");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, result);
    }

    @Test
    public void verifySamePersistedWorkAfterActualProcessRestart() throws Exception {
        Bundle inputs = InstrumentationRegistry.getArguments();
        String expected = inputs.getString(KEY_UUID);
        String created = inputs.getString(KEY_CREATED);
        assertNotNull("Workflow must pass the pre-kill UUID", expected);
        assertTrue("Workflow must pass ownership flag",
                "yes".equals(created) || "no".equals(created));

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WorkManager wm = WorkManager.getInstance(context);
        try {
            // Read the persisted unique-work index before any test-triggered reschedule.
            assertEquals(UUID.fromString(expected), awaitSingleActive(wm));
            // Application startup and repeated reconciliation must be idempotent.
            NewAppInstallMonitor.schedulePeriodic(context);
            assertEquals(UUID.fromString(expected), awaitSingleActive(wm));
        } finally {
            // Never cancel work already belonging to the app before the test.
            if ("yes".equals(created)) {
                wm.cancelUniqueWork(NewAppInstallMonitor.PERIODIC_WORK)
                        .getResult().get(15, TimeUnit.SECONDS);
            }
        }
    }

    private static UUID awaitSingleActive(WorkManager wm) throws Exception {
        long deadline = System.currentTimeMillis() + 10000L;
        Set<UUID> ids;
        do {
            ids = activeIds(wm);
            if (ids.size() == 1) return ids.iterator().next();
            assertTrue("More than one active reconciliation worker", ids.size() < 2);
            Thread.sleep(80L);
        } while (System.currentTimeMillis() < deadline);
        assertEquals("Reconciliation work must survive in WorkManager storage",
                1, ids.size());
        return ids.iterator().next();
    }

    private static Set<UUID> activeIds(WorkManager wm) throws Exception {
        List<WorkInfo> infos = wm.getWorkInfosForUniqueWork(
                NewAppInstallMonitor.PERIODIC_WORK).get(15, TimeUnit.SECONDS);
        assertNotNull(infos);
        Set<UUID> ids = new HashSet<>();
        for (WorkInfo info : infos) {
            if (!info.getState().isFinished()) ids.add(info.getId());
        }
        return ids;
    }
}
