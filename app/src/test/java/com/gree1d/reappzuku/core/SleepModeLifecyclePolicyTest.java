package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SleepModeLifecyclePolicyTest {
    @Test
    public void idleFreezeRequiresEnabledSleepModeAndNonInteractiveScreen() {
        assertTrue(SleepModeLifecyclePolicy.shouldExecuteIdleFreeze(true, false));
        assertFalse(SleepModeLifecyclePolicy.shouldExecuteIdleFreeze(true, true));
        assertFalse(SleepModeLifecyclePolicy.shouldExecuteIdleFreeze(false, false));
        assertFalse(SleepModeLifecyclePolicy.shouldExecuteIdleFreeze(false, true));
    }

    @Test
    public void noOwnedFrozenAppsNeedsNoRecovery() {
        for (boolean enabled : new boolean[] {false, true}) {
            for (boolean interactive : new boolean[] {false, true}) {
                assertEquals(
                        SleepModeLifecyclePolicy.RecoveryAction.NONE,
                        SleepModeLifecyclePolicy.recoveryAction(enabled, interactive, false));
            }
        }
    }

    @Test
    public void ownedFrozenAppsAreThawedWhenScreenIsInteractive() {
        assertEquals(
                SleepModeLifecyclePolicy.RecoveryAction.UNFREEZE,
                SleepModeLifecyclePolicy.recoveryAction(true, true, true));
    }

    @Test
    public void disablingSleepModeAlwaysThawsOwnedFrozenApps() {
        assertEquals(
                SleepModeLifecyclePolicy.RecoveryAction.UNFREEZE,
                SleepModeLifecyclePolicy.recoveryAction(false, false, true));
        assertEquals(
                SleepModeLifecyclePolicy.RecoveryAction.UNFREEZE,
                SleepModeLifecyclePolicy.recoveryAction(false, true, true));
    }

    @Test
    public void ownedFrozenAppsRemainFrozenOnlyWhileEnabledAndScreenOff() {
        assertEquals(
                SleepModeLifecyclePolicy.RecoveryAction.KEEP_FROZEN_AND_HEARTBEAT,
                SleepModeLifecyclePolicy.recoveryAction(true, false, true));
    }
}
