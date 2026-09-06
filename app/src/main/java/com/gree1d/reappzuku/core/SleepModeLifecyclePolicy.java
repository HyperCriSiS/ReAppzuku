package com.gree1d.reappzuku.core;

/**
 * Pure lifecycle decisions for Sleep Mode.
 *
 * <p>The durable frozen-app ownership marker is the source of truth. Process-local booleans are
 * deliberately excluded so service/process recreation cannot strand apps in a frozen state.</p>
 */
public final class SleepModeLifecyclePolicy {
    private SleepModeLifecyclePolicy() {}

    public enum RecoveryAction {
        NONE,
        UNFREEZE,
        KEEP_FROZEN_AND_HEARTBEAT
    }

    public static boolean shouldExecuteIdleFreeze(boolean sleepModeEnabled, boolean screenInteractive) {
        return sleepModeEnabled && !screenInteractive;
    }

    public static RecoveryAction recoveryAction(
            boolean sleepModeEnabled,
            boolean screenInteractive,
            boolean hasOwnedFrozenTimerApps) {
        if (!hasOwnedFrozenTimerApps) {
            return RecoveryAction.NONE;
        }
        if (!sleepModeEnabled || screenInteractive) {
            return RecoveryAction.UNFREEZE;
        }
        return RecoveryAction.KEEP_FROZEN_AND_HEARTBEAT;
    }
}
