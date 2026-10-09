package com.gree1d.reappzuku.service;

import androidx.work.Data;

/**
 * Release variant: no fault injection and no CI output, regardless of work input.
 */
final class NewAppReconcileWorkerProbe {
    private NewAppReconcileWorkerProbe() {}

    static void afterReconcile(Data input, int attempt) {
        // Intentionally empty. Release builds cannot inject artificial failures.
    }

    static Data successOutput(Data input) {
        return Data.EMPTY;
    }
}
