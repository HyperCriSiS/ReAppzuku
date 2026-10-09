package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.gree1d.reappzuku.service.NewAppInstallReconcileWorker;

import org.junit.Assume;
import org.junit.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Dedicated disposable-emulator proof that the real production Worker executes
 * reconciliation, retries after a debug-only post-pass fault and ultimately
 * completes after an actual main-process SIGKILL and restart. No user policy,
 * inventory or preference is manually altered by these tests.
 */
public final class Api24Api37RealReconcileWorkerRetryInstrumentationTest {
    private static final String CI_MODE = "ci_real_reconcile_retry_mode";
    private static final String UNIQUE = "ReAppzuku_CI_ActualReconcileRetry";
    private static final String INPUT_KEY = "reappzuku_ci_real_reconcile_retry";
    private static final String OUTPUT_KEY = "reappzuku_ci_real_reconcile_completed";
    private static final String EXPECTED_ID = "expected_uuid";

    @Test
    public void startActualWorkerAndObserveFirstRetry() throws Exception {
        requireDedicatedCi();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WorkManager wm = WorkManager.getInstance(context);
        List<WorkInfo> prior = wm.getWorkInfosForUniqueWork(UNIQUE)
                .get(15, TimeUnit.SECONDS);
        assertTrue("Test-only unique WorkManager slot must be unused", prior.isEmpty());

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                NewAppInstallReconcileWorker.class)
                .setInputData(new Data.Builder()
                        .putString(INPUT_KEY, "post_reconcile_once").build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build();
        wm.enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.KEEP, request)
                .getResult().get(15, TimeUnit.SECONDS);
        try {
            awaitFirstRetry(wm, request.getId());
            Bundle report = new Bundle();
            report.putString("REAPPZUKU_REAL_RECONCILE_ID", request.getId().toString());
            InstrumentationRegistry.getInstrumentation().sendStatus(0, report);
        } catch (Throwable failure) {
            wm.cancelUniqueWork(UNIQUE).getResult().get(15, TimeUnit.SECONDS);
            throw failure;
        }
    }

    @Test
    public void verifyActualWorkerRecoveredAfterProcessRestart() throws Exception {
        requireDedicatedCi();
        String expected = InstrumentationRegistry.getArguments().getString(EXPECTED_ID);
        assertNotNull("CI must supply UUID observed before process death", expected);
        UUID id = UUID.fromString(expected);
        WorkManager wm = WorkManager.getInstance(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            List<WorkInfo> retained = wm.getWorkInfosForUniqueWork(UNIQUE)
                    .get(15, TimeUnit.SECONDS);
            assertEquals("Dedicated retry work identity must persist", 1, retained.size());
            assertEquals(id, retained.get(0).getId());
            WorkInfo success = awaitSuccess(wm, id);
            assertTrue("Injected post-pass exception must have caused a retry",
                    success.getRunAttemptCount() >= 1);
            assertEquals("after_retry", success.getOutputData().getString(OUTPUT_KEY));
            Bundle report = new Bundle();
            report.putString("REAPPZUKU_REAL_RECONCILE_RETRY_SUCCESS", "yes");
            InstrumentationRegistry.getInstrumentation().sendStatus(0, report);
        } finally {
            wm.cancelUniqueWork(UNIQUE).getResult().get(15, TimeUnit.SECONDS);
        }
    }

    private static void requireDedicatedCi() {
        Assume.assumeTrue("Never run outside the opt-in disposable-emulator lane",
                "verify".equals(InstrumentationRegistry.getArguments().getString(CI_MODE)));
    }

    private static void awaitFirstRetry(WorkManager wm, UUID id) throws Exception {
        long deadline = System.currentTimeMillis() + 45_000L;
        do {
            WorkInfo info = wm.getWorkInfoById(id).get(15, TimeUnit.SECONDS);
            assertNotNull(info);
            if (info.getState() == WorkInfo.State.ENQUEUED
                    && info.getRunAttemptCount() >= 1) return;
            assertTrue("Real Worker cannot succeed before the injected first retry",
                    info.getState() != WorkInfo.State.SUCCEEDED
                            && info.getState() != WorkInfo.State.FAILED
                            && info.getState() != WorkInfo.State.CANCELLED);
            Thread.sleep(100L);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Real reconciliation Worker did not enter retry state");
    }

    private static WorkInfo awaitSuccess(WorkManager wm, UUID id) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        do {
            WorkInfo info = wm.getWorkInfoById(id).get(15, TimeUnit.SECONDS);
            assertNotNull(info);
            if (info.getState() == WorkInfo.State.SUCCEEDED) return info;
            assertTrue("Real reconciliation Worker cannot fail or be cancelled",
                    info.getState() != WorkInfo.State.FAILED
                            && info.getState() != WorkInfo.State.CANCELLED);
            Thread.sleep(250L);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Real reconciliation Worker never completed after retry");
    }
}
