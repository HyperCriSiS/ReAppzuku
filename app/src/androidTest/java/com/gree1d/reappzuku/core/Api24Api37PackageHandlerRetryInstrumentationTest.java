package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Disposable emulator only: force one failure at a real package-handler
 * dispatch inside the actual reconciliation Worker, then verify a retry.
 *
 * Only the APP'S OWN package is temporarily removed from its known-package
 * cache, then restored. Its coordinator handler is a no-op, so no policy,
 * setup-queue entry, notification or privilege operation is created.
 */
public final class Api24Api37PackageHandlerRetryInstrumentationTest {
    private static final String UNIQUE = "ReAppzuku_CI_PackageHandlerRetry";
    private static final String MODE_KEY = "reappzuku_ci_real_reconcile_retry";
    private static final String OUTPUT_KEY = "reappzuku_ci_package_reconcile_completed";

    @Test
    public void packageHandlerFailureRetriesOnlyMissingPackage() throws Exception {
        Assume.assumeTrue("Opt-in disposable-emulator check only",
                "verify".equals(InstrumentationRegistry.getArguments()
                        .getString("ci_package_handler_retry")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String self = context.getPackageName();
        context.getPackageManager().getApplicationInfo(self, 0);
        SharedPreferences prefs = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);

        assertTrue("Startup must have initialized inventory before probe",
                prefs.contains(PreferenceKeys.KEY_NEW_APP_KNOWN_PACKAGES));
        Set<String> originallyKnown = new HashSet<>(prefs.getStringSet(
                PreferenceKeys.KEY_NEW_APP_KNOWN_PACKAGES, Collections.emptySet()));
        assertTrue("App itself must be known before a test-owned transient fault",
                originallyKnown.contains(self));

        WorkManager wm = WorkManager.getInstance(context);
        List<WorkInfo> prior = wm.getWorkInfosForUniqueWork(UNIQUE)
                .get(15, TimeUnit.SECONDS);
        assertTrue("Do not reuse previous CI work", prior.isEmpty());

        // The only intentional cache perturbation is the currently installed,
        // test-owned app package. No preferences are cleared or Room rows edited.
        Set<String> missingSelf = new HashSet<>(originallyKnown);
        missingSelf.remove(self);
        assertTrue(prefs.edit().putStringSet(
                PreferenceKeys.KEY_NEW_APP_KNOWN_PACKAGES, missingSelf).commit());

        try {
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                    NewAppInstallReconcileWorker.class)
                    .setInputData(new Data.Builder()
                            .putString(MODE_KEY, "per_package_once").build())
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .build();
            wm.enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.KEEP, request)
                    .getResult().get(15, TimeUnit.SECONDS);

            awaitFirstRetry(wm, request.getId());
            assertFalse("Failed handler must remain missing after durable inventory commit",
                    knownPackages(prefs).contains(self));

            WorkInfo success = awaitSuccess(wm, request.getId());
            assertTrue("Package handler must have retried", success.getRunAttemptCount() >= 1);
            assertEquals("recovered_after_package_retry",
                    success.getOutputData().getString(OUTPUT_KEY));
            assertTrue("Successful retry must durably record the package",
                    knownPackages(prefs).contains(self));

            Bundle report = new Bundle();
            report.putString("REAPPZUKU_PACKAGE_HANDLER_RETRY_SUCCESS", "yes");
            InstrumentationRegistry.getInstrumentation().sendStatus(0, report);
        } finally {
            wm.cancelUniqueWork(UNIQUE).getResult().get(15, TimeUnit.SECONDS);
            // Do not revert other packages concurrently registered by Android.
            Set<String> restored = knownPackages(prefs);
            restored.add(self);
            assertTrue("Restore the originally known self package",
                    prefs.edit().putStringSet(
                            PreferenceKeys.KEY_NEW_APP_KNOWN_PACKAGES, restored).commit());
        }
    }

    private static Set<String> knownPackages(SharedPreferences prefs) {
        return new HashSet<>(prefs.getStringSet(
                PreferenceKeys.KEY_NEW_APP_KNOWN_PACKAGES, Collections.emptySet()));
    }

    private static void awaitFirstRetry(WorkManager wm, UUID id) throws Exception {
        long deadline = System.currentTimeMillis() + 45000L;
        do {
            WorkInfo info = wm.getWorkInfoById(id).get(15, TimeUnit.SECONDS);
            assertNotNull(info);
            if (info.getState() == WorkInfo.State.ENQUEUED && info.getRunAttemptCount() >= 1)
                return;
            assertTrue("First invocation must not succeed",
                    info.getState() != WorkInfo.State.SUCCEEDED
                            && info.getState() != WorkInfo.State.FAILED
                            && info.getState() != WorkInfo.State.CANCELLED);
            Thread.sleep(80L);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Per-package handler exception did not yield WorkManager retry");
    }

    private static WorkInfo awaitSuccess(WorkManager wm, UUID id) throws Exception {
        long deadline = System.currentTimeMillis() + 90000L;
        do {
            WorkInfo info = wm.getWorkInfoById(id).get(15, TimeUnit.SECONDS);
            assertNotNull(info);
            if (info.getState() == WorkInfo.State.SUCCEEDED) return info;
            assertTrue("Retry may not fail or be cancelled",
                    info.getState() != WorkInfo.State.FAILED
                            && info.getState() != WorkInfo.State.CANCELLED);
            Thread.sleep(200L);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Per-package handler retry never completed");
    }
}
