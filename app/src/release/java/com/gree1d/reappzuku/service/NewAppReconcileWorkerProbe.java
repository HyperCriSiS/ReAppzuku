package com.gree1d.reappzuku.service;

import android.content.Context;

import androidx.work.Data;

import com.gree1d.reappzuku.core.NewAppInstallMonitor;

/**
 * Release variant: no fault injection and no CI output, regardless of work input.
 */
final class NewAppReconcileWorkerProbe {
    private NewAppReconcileWorkerProbe() {}

    static void reconcile(Context context, Data input, int attempt) {
        NewAppInstallMonitor.reconcileInstalledPackages(context);
    }

    static void afterReconcile(Data input, int attempt) {
        // Intentionally empty. Release builds cannot inject artificial failures.
    }

    static Data successOutput(Data input) {
        return Data.EMPTY;
    }
}