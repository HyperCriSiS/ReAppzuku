package com.gree1d.reappzuku.service;

import android.content.Context;

import androidx.work.Data;

import com.gree1d.reappzuku.core.NewAppInstallMonitor;
import com.gree1d.reappzuku.core.NewAppSetupCoordinator;

/**
 * Debug-build-only CI hook in the ACTUAL new-app reconciliation Worker.
 * A named, explicit one-time WorkRequest must opt in; the regular periodic
 * request carries no input and can never trigger this probe.
 */
final class NewAppReconcileWorkerProbe {
    private static final String MODE_KEY = "reappzuku_ci_real_reconcile_retry";
    private static final String MODE_VALUE = "post_reconcile_once";
    private static final String RESULT_KEY = "reappzuku_ci_real_reconcile_completed";
    private static final String RESULT_VALUE = "after_retry";
    private static final String PACKAGE_MODE = "per_package_once";
    private static final String PACKAGE_RESULT_KEY = "reappzuku_ci_package_reconcile_completed";

    private NewAppReconcileWorkerProbe() {}

    static void reconcile(Context context, Data input, int attempt) {
        if (!PACKAGE_MODE.equals(input.getString(MODE_KEY))) {
            NewAppInstallMonitor.reconcileInstalledPackages(context);
            return;
        }

        // Only the app's own package is eligible for injection. Handling self
        // normally has no policy/queue side effects, even on the successful retry.
        NewAppInstallMonitor.reconcileInstalledPackages(context, name -> {
            if (attempt == 0 && context.getPackageName().equals(name)) {
                throw new IllegalStateException("CI-only package-handler dispatch fault");
            }
            NewAppSetupCoordinator.handlePackageAdded(context, name);
        });
    }

    static void afterReconcile(Data input, int attempt) {
        if (isProbe(input) && attempt == 0) {
            throw new IllegalStateException("CI: simulated post-reconciliation transient error");
        }
    }

    static Data successOutput(Data input) {
        if (PACKAGE_MODE.equals(input.getString(MODE_KEY))) {
            return new Data.Builder()
                    .putString(PACKAGE_RESULT_KEY, "recovered_after_package_retry")
                    .build();
        }
        if (!isProbe(input)) return Data.EMPTY;
        return new Data.Builder().putString(RESULT_KEY, RESULT_VALUE).build();
    }

    private static boolean isProbe(Data input) {
        return MODE_VALUE.equals(input.getString(MODE_KEY));
    }
}