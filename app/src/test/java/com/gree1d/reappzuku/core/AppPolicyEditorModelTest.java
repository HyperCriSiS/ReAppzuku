package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.PolicyPreset;

import org.junit.Test;

public class AppPolicyEditorModelTest {
    @Test
    public void fromPresetCreatesExplicitNonCustomizedPolicy() {
        PolicyPreset preset = new PolicyPreset("test");
        preset.id = 77L;
        preset.strategy = AppPolicy.STRATEGY_SMART;
        preset.standbyDelayMs = 10L;
        preset.forceStopDelayMs = 20L;
        preset.triggerMask = AppPolicy.TRIGGER_SCREEN_OFF;

        AppPolicy policy = AppPolicyEditorModel.fromPreset("com.example.app", preset, 123L);

        assertEquals(AppPolicy.SOURCE_EXPLICIT, policy.source);
        assertEquals(Long.valueOf(77L), policy.presetId);
        assertFalse(policy.customized);
        assertEquals(AppPolicy.STRATEGY_SMART, policy.strategy);
        assertEquals(10L, policy.standbyDelayMs);
        assertEquals(20L, policy.forceStopDelayMs);
        assertEquals(AppPolicy.TRIGGER_SCREEN_OFF, policy.triggerMask);
    }

    @Test
    public void normalizeClampsSmartForceStopAfterStandbyAndUnknownBits() {
        AppPolicy policy = new AppPolicy("com.example.app");
        policy.strategy = AppPolicy.STRATEGY_SMART;
        policy.standbyDelayMs = 20L;
        policy.forceStopDelayMs = 10L;
        policy.triggerMask = AppPolicy.TRIGGER_PERIODIC | (1L << 30);
        policy.source = AppPolicy.SOURCE_LEGACY_MIGRATED;

        AppPolicyEditorModel.normalize(policy);

        assertEquals(20L, policy.forceStopDelayMs);
        assertEquals(AppPolicy.TRIGGER_PERIODIC, policy.triggerMask);
        assertEquals(AppPolicy.SOURCE_EXPLICIT, policy.source);
    }

    @Test
    public void neverForceStopSurvivesNormalization() {
        AppPolicy policy = new AppPolicy("com.example.app");
        policy.strategy = AppPolicy.STRATEGY_SMART;
        policy.standbyDelayMs = 100L;
        policy.forceStopDelayMs = Long.MAX_VALUE;

        AppPolicyEditorModel.normalize(policy);

        assertEquals(Long.MAX_VALUE, policy.forceStopDelayMs);
    }

    @Test
    public void presetRoundTripMatchesEditableFields() {
        AppPolicy policy = new AppPolicy("com.example.app");
        policy.strategy = AppPolicy.STRATEGY_IMMEDIATE;
        policy.killMethod = AppPolicy.KILL_METHOD_AM_KILL;
        policy.backgroundRestriction = AppPolicy.RESTRICTION_HARD;
        policy.bootCleanup = false;
        policy.protectMedia = false;
        policy.protectForegroundServices = false;
        policy.protectWidgets = false;
        policy.triggerMask = AppPolicyEditorModel.ALL_TRIGGER_BITS;

        PolicyPreset preset = AppPolicyEditorModel.presetFromPolicy("Mine", policy, 50L);

        assertTrue(AppPolicyEditorModel.matchesPreset(policy, preset));
        assertFalse(preset.builtIn);
        assertEquals("Mine", preset.name);
    }
}
