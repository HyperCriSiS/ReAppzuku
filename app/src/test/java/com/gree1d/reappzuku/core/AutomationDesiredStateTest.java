package com.gree1d.reappzuku.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class AutomationDesiredStateTest {
    @Test public void foregroundServiceFollowsMasterSwitch() {
        assertTrue(AutomationDesiredState.shouldRunForegroundService(true));
        assertFalse(AutomationDesiredState.shouldRunForegroundService(false));
    }

    @Test public void noAutomationAllowsOnDemandBehavior() {
        assertFalse(AutomationDesiredState.requiresBackgroundContinuity(false, false, false, false, false));
    }

    @Test public void everyAutomationSourceBlocksOnDemandBehavior() {
        assertTrue(AutomationDesiredState.requiresBackgroundContinuity(true, false, false, false, false));
        assertTrue(AutomationDesiredState.requiresBackgroundContinuity(false, true, false, false, false));
        assertTrue(AutomationDesiredState.requiresBackgroundContinuity(false, false, true, false, false));
        assertTrue(AutomationDesiredState.requiresBackgroundContinuity(false, false, false, true, false));
        assertTrue(AutomationDesiredState.requiresBackgroundContinuity(false, false, false, false, true));
    }
}
