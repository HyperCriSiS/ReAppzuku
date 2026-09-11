package com.gree1d.reappzuku.manager;

/**
 * Pure recovery policy for Smart Lifecycle force-stop attempts. Keeping this
 * decision separate makes failure handling deterministic and unit-testable.
 */
public final class SmartLifecycleRecoveryPolicy {
    public enum ForceStopOutcome {
        CLEAR_STATE,
        KEEP_STATE,
        KEEP_STATE_AND_RETRY
    }

    private SmartLifecycleRecoveryPolicy() {
    }

    public static ForceStopOutcome onForceStopResult(boolean bootPass, boolean succeeded) {
        if (succeeded) return ForceStopOutcome.CLEAR_STATE;
        return bootPass ? ForceStopOutcome.KEEP_STATE_AND_RETRY : ForceStopOutcome.KEEP_STATE;
    }

    public static boolean shouldRetryWorker(boolean bootPass, boolean passCompleted) {
        return bootPass && !passCompleted;
    }
}
