package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmartLifecycleRecoveryPolicyTest {
    @Test
    public void successfulForceStopAlwaysClearsState() {
        assertEquals(SmartLifecycleRecoveryPolicy.ForceStopOutcome.CLEAR_STATE,
                SmartLifecycleRecoveryPolicy.onForceStopResult(false, true));
        assertEquals(SmartLifecycleRecoveryPolicy.ForceStopOutcome.CLEAR_STATE,
                SmartLifecycleRecoveryPolicy.onForceStopResult(true, true));
    }

    @Test
    public void periodicFailureKeepsAgeSoNextPassRetriesImmediately() {
        assertEquals(SmartLifecycleRecoveryPolicy.ForceStopOutcome.KEEP_STATE,
                SmartLifecycleRecoveryPolicy.onForceStopResult(false, false));
        assertFalse(SmartLifecycleRecoveryPolicy.shouldRetryWorker(false, false));
    }

    @Test
    public void bootFailureKeepsStateAndRequestsWorkManagerRetry() {
        assertEquals(SmartLifecycleRecoveryPolicy.ForceStopOutcome.KEEP_STATE_AND_RETRY,
                SmartLifecycleRecoveryPolicy.onForceStopResult(true, false));
        assertTrue(SmartLifecycleRecoveryPolicy.shouldRetryWorker(true, false));
    }

    @Test
    public void completedBootPassDoesNotRetry() {
        assertFalse(SmartLifecycleRecoveryPolicy.shouldRetryWorker(true, true));
    }
}
