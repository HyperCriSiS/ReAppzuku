package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.PolicyPreset;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PolicyPresetSeederTest {
    @Test
    public void builtInsHaveStableUniqueIdsAndNames() {
        List<PolicyPreset> presets = PolicyPresetSeeder.builtIns(42L);

        assertEquals(6, presets.size());
        Set<Long> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (PolicyPreset preset : presets) {
            assertTrue(preset.builtIn);
            assertTrue(ids.add(preset.id));
            assertTrue(names.add(preset.name));
            assertEquals(42L, preset.createdAt);
            assertEquals(42L, preset.updatedAt);
        }

        assertEquals(PolicyPresetSeeder.PRESET_NEVER_TOUCH, presets.get(0).id);
        assertEquals(PolicyPresetSeeder.PRESET_AGGRESSIVE, presets.get(5).id);
    }

    @Test
    public void neverTouchIsProtectedAndMutationFree() {
        PolicyPreset preset = PolicyPresetSeeder.builtIns(1L).get(0);

        assertEquals(AppPolicy.STRATEGY_PROTECTED, preset.strategy);
        assertEquals(AppPolicy.RESTRICTION_NONE, preset.backgroundRestriction);
        assertEquals(0L, preset.triggerMask);
        assertFalse(preset.bootCleanup);
    }

    @Test
    public void messengerUsesStandbyWithoutAutomaticForceStop() {
        PolicyPreset preset = PolicyPresetSeeder.builtIns(1L).get(1);

        assertEquals(AppPolicy.STRATEGY_SMART, preset.strategy);
        assertEquals(Long.MAX_VALUE, preset.forceStopDelayMs);
        assertFalse(preset.bootCleanup);
    }

    @Test
    public void balancedMatchesSmartLifecycleDefaults() {
        PolicyPreset preset = PolicyPresetSeeder.builtIns(1L).get(3);

        assertEquals(AppPolicy.STRATEGY_SMART, preset.strategy);
        assertEquals(AppPolicy.DEFAULT_SMART_STANDBY_DELAY_MS, preset.standbyDelayMs);
        assertEquals(AppPolicy.DEFAULT_SMART_FORCE_STOP_DELAY_MS, preset.forceStopDelayMs);
        assertEquals(AppPolicy.TRIGGER_BOOT_CLEANUP, preset.triggerMask);
    }

    @Test
    public void aggressiveIsImmediateAndHardRestricted() {
        PolicyPreset preset = PolicyPresetSeeder.builtIns(1L).get(5);

        assertEquals(AppPolicy.STRATEGY_IMMEDIATE, preset.strategy);
        assertEquals(AppPolicy.RESTRICTION_HARD, preset.backgroundRestriction);
        assertTrue((preset.triggerMask & AppPolicy.TRIGGER_PERIODIC) != 0);
        assertTrue((preset.triggerMask & AppPolicy.TRIGGER_SCREEN_OFF) != 0);
        assertTrue((preset.triggerMask & AppPolicy.TRIGGER_RAM_THRESHOLD) != 0);
        assertTrue((preset.triggerMask & AppPolicy.TRIGGER_HARDWARE_EVENT) != 0);
        assertTrue((preset.triggerMask & AppPolicy.TRIGGER_APP_LAUNCH) != 0);
    }
}
