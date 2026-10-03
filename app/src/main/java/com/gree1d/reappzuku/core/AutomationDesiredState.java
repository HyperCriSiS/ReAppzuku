package com.gree1d.reappzuku.core;

/** Pure desired-state rules for ReAppzuku background automation. */
public final class AutomationDesiredState {
    private AutomationDesiredState() {}

    public static boolean shouldRunForegroundService(boolean autoKillEnabled) {
        return autoKillEnabled;
    }

    public static boolean requiresBackgroundContinuity(
            boolean autoKillEnabled,
            boolean smartLifecycleEnabled,
            boolean sleepModeEnabled,
            boolean presetActive,
            boolean restrictionScheduleEnabled) {
        // Smart lifecycle is WorkManager-backed. Its legacy global flag remains migration
        // input, but no longer requires the main process to stay continuously alive.
        return autoKillEnabled || sleepModeEnabled || presetActive || restrictionScheduleEnabled;
    }
}