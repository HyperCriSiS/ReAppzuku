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
        return autoKillEnabled || smartLifecycleEnabled || sleepModeEnabled
                || presetActive || restrictionScheduleEnabled;
    }
}
