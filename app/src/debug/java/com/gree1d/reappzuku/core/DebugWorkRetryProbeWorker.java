package com.gree1d.reappzuku.core;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * DEBUG APK ONLY: demonstrates WorkManager's persisted backoff/retry state.
 * Never package this worker in the release variant; no real app or policy action.
 */
public final class DebugWorkRetryProbeWorker extends Worker {
    public static final String OUTPUT_PROOF = "reappzuku_retry_probe_verified";
    public static final String OUTPUT_VALUE = "executed_after_retry";

    public DebugWorkRetryProbeWorker(
            @NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (getRunAttemptCount() == 0) {
            return Result.retry();
        }
        return Result.success(new Data.Builder()
                .putString(OUTPUT_PROOF, OUTPUT_VALUE)
                .build());
    }
}
