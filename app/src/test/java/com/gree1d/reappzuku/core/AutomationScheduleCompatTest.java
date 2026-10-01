package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class AutomationScheduleCompatTest {
    @Test
    public void recognizesOnlyHistoricalDefaultNames() {
        assertTrue(AutomationScheduleCompat.isLegacyDefaultName("Preset 1", 1));
        assertTrue(AutomationScheduleCompat.isLegacyDefaultName("Preset 2", 2));
        assertFalse(AutomationScheduleCompat.isLegacyDefaultName("Night", 1));
        assertFalse(AutomationScheduleCompat.isLegacyDefaultName("Automation Schedule 1", 1));
    }

    @Test
    public void exportNamesUseAutomationScheduleTerminology() {
        assertEquals("automation-schedule-1.json", AutomationScheduleCompat.exportFileName(1));
        assertEquals("automation-schedule-2.json", AutomationScheduleCompat.exportFileName(2));
    }

    @Test
    public void invalidSlotsFailClosed() {
        try {
            AutomationScheduleCompat.exportFileName(0);
            fail("slot 0 must fail");
        } catch (IllegalArgumentException expected) {
        }
        try {
            AutomationScheduleCompat.isLegacyDefaultName("Preset 3", 3);
            fail("slot 3 must fail");
        } catch (IllegalArgumentException expected) {
        }
    }
}
