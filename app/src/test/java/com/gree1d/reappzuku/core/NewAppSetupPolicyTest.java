package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class NewAppSetupPolicyTest {
    @Test public void defaultsToAskForUnknownModes() {
        assertEquals(NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL, NewAppSetupPolicy.sanitizeMode(-1));
        assertEquals(NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL, NewAppSetupPolicy.sanitizeMode(99));
    }
    @Test public void ineligibleOrConfiguredPackagesAreIgnored() {
        assertEquals(NewAppSetupPolicy.ACTION_IGNORE, NewAppSetupPolicy.decide(NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET, false, false, true));
        assertEquals(NewAppSetupPolicy.ACTION_IGNORE, NewAppSetupPolicy.decide(NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL, true, true, false));
    }
    @Test public void askQueuesWithoutManagedPreset() {
        assertEquals(NewAppSetupPolicy.ACTION_QUEUE_UNMANAGED, NewAppSetupPolicy.decide(NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL, true, false, false));
    }
    @Test public void automaticModeRequiresAvailablePreset() {
        assertEquals(NewAppSetupPolicy.ACTION_APPLY_PRESET, NewAppSetupPolicy.decide(NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET, true, false, true));
        assertEquals(NewAppSetupPolicy.ACTION_QUEUE_PRESET_MISSING, NewAppSetupPolicy.decide(NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET, true, false, false));
    }
    @Test public void leaveUnmanagedNeverAppliesPreset() {
        assertEquals(NewAppSetupPolicy.ACTION_LEAVE_UNMANAGED, NewAppSetupPolicy.decide(NewAppSetupPolicy.MODE_LEAVE_UNMANAGED, true, false, true));
    }
}
