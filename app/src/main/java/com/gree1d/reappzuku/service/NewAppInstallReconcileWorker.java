package com.gree1d.reappzuku.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.gree1d.reappzuku.core.NewAppInstallMonitor;

public final class NewAppInstallReconcileWorker extends Worker {
    public NewAppInstallReconcileWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            NewAppInstallMonitor.reconcileInstalledPackages(getApplicationContext());
            // Debug builds may inject an isolated, one-time *post-reconciliation* fault.
            // The release variant implements this hook as a permanent no-op.
            NewAppReconcileWorkerProbe.afterReconcile(getInputData(), getRunAttemptCount());
            return Result.success(NewAppReconcileWorkerProbe.successOutput(getInputData()));
        } catch (RuntimeException e) {
            return Result.retry();
        }
    }
}
