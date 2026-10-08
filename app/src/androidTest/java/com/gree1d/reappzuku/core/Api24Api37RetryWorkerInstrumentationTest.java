package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.Assume;
import org.junit.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Only run with the dedicated CI option on disposable API-24/API-37 emulators.
 * CI kills the main debug process between the two instrumentation invocations.
 * The worker itself lives exclusively in app/src/debug (never in release).
 */
public final class Api24Api37RetryWorkerInstrumentationTest {
    private static final String UNIQUE_NAME = "ReAppzuku_CI_DebugRetryAfterDeath";
    private static final String MODE = "ci_worker_retry_mode";
    private static final String EXPECTED_UUID = "expected_uuid";
    private static final long TIMEOUT_MS = 120_000L;

    @Test
    public void startWorkerAndObserveActualFirstRetryBeforeProcessDeath() throws Exception {
        requireDedicatedCi();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WorkManager wm = WorkManager.getInstance(context);
        List<WorkInfo> prior = wm.getWorkInfosForUniqueWork(UNIQUE_NAME)
                .get(15, TimeUnit.SECONDS);
        assertTrue("Dedicated CI probe must not reuse existing work", prior.isEmpty());

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                DebugWorkRetryProbeWorker.class)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build();
        wm.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
                .getResult().get(15, TimeUnit.SECONDS);
        try {
            // A retry count of one proves doWork returned Result.retry() once.
            waitForFirstRetry(wm, request.getId());
            Bundle report = new Bundle();
            report.putString("REAPPZUKU_RECOVERY_ID", request.getId().toString());
            report.putString("REAPPZUKU_RECOVERY_CREATED", "yes");
            InstrumentationRegistry.getInstrumentation().sendStatus(0, report);
        } catch (Throwable failure) {
            wm.cancelUniqueWork(UNIQUE_NAME).getResult().get(15, TimeUnit.SECONDS);
            throw failure;
        }
    }

    @Test
    public void verifyWorkerReallyRetriedAndSucceededAfterRestart() throws Exception {
        requireDedicatedCi();
        Bundle args = InstrumentationRegistry.getArguments();
        String uuidText = args.getString(EXPECTED_UUID);
        assertNotNull("CI must pass the WorkManager ID captured before SIGKILL", uuidText);
        UUID id = UUID.fromString(uuidText);

        WorkManager wm = WorkManager.getInstance(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            // WorkManager persisted the same ID through an actual process death.
            List<WorkInfo> persisted = wm.getWorkInfosForUniqueWork(UNIQUE_NAME)
                    .get(15, TimeUnit.SECONDS);
            assertEquals(1, persisted.size());
            assertEquals(id, persisted.get(0).getId());
            WorkInfo outcome = waitForSuccess(wm, id);
            assertTrue("At least one retry must have occurred", outcome.getRunAttemptCount() >= 1);
            assertEquals(DebugWorkRetryProbeWorker.OUTPUT_VALUE,
                    outcome.getOutputData().getString(DebugWorkRetryProbeWorker.OUTPUT_PROOF));
            Bundle report = new Bundle();
            report.putString("REAPPZUKU_RETRY_SUCCESS", "yes");
            InstrumentationRegistry.getInstrumentation().sendStatus(0, report);
        } finally {
            // Only the isolated CI-owned work is cancelled; production tasks untouched.
            wm.cancelUniqueWork(UNIQUE_NAME).getResult().get(15, TimeUnit.SECONDS);
        }
    }

    private static void requireDedicatedCi() {
        Assume.assumeTrue("Only disposable dedicated CI may enqueue the retry probe",
                "verify".equals(InstrumentationRegistry.getArguments().getString(MODE)));
    }

    private static void waitForFirstRetry(WorkManager wm, UUID id) throws Exception {
        long deadline = System.currentTimeMillis() + 45_000L;
        do {
            WorkInfo info = wm.getWorkInfoById(id).get(15, TimeUnit.SECONDS);
            assertNotNull(info);
            if (info.getState() == WorkInfo.State.ENQUEUED && info.getRunAttemptCount() >= 1) {
                return;
            }
            assertTrue("Retry worker must not succeed on first attempt",
                    info.getState() != WorkInfo.State.SUCCEEDED
                            && info.getState() != WorkInfo.State.FAILED
                            && info.getState() != WorkInfo.State.CANCELLED);
            Thread.sleep(100L);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Debug Worker never executed Result.retry()");
    }

    private static WorkInfo waitForSuccess(WorkManager wm, UUID id) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        do {
            WorkInfo info = wm.getWorkInfoById(id).get(15, TimeUnit.SECONDS);
            assertNotNull(info);
            if (info.getState() == WorkInfo.State.SUCCEEDED) return info;
            assertTrue("Worker must not fail or be cancelled after recovery",
                    info.getState() != WorkInfo.State.FAILED
                            && info.getState() != WorkInfo.State.CANCELLED);
            Thread.sleep(250L);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Retry WorkManager job was not executed after process restart");
    }
}
