package com.gree1d.reappzuku.service;

import androidx.work.Data;

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

    private NewAppReconcileWorkerProbe() {}

    static void afterReconcile(Data input, int attempt) {
        if (isProbe(input) && attempt == 0) {
            throw new IllegalStateException("CI: simulated post-reconciliation transient error");
        }
    }

    static Data successOutput(Data input) {
        if (!isProbe(input)) return Data.EMPTY;
        return new Data.Builder().putString(RESULT_KEY, RESULT_VALUE).build();
    }

    private static boolean isProbe(Data input) {
        return MODE_VALUE.equals(input.getString(MODE_KEY));
    }
}
