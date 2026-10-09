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
    @Test
    public void changedThenRevertedPresetIsNotCustomized() {
        PolicyPreset preset = new PolicyPreset("Reversible");
        preset.id = 81L;
        preset.strategy = AppPolicy.STRATEGY_SMART;
        preset.standbyDelayMs = 10L * 60_000L;
        preset.forceStopDelayMs = 30L * 60_000L;
        AppPolicy policy = AppPolicyEditorModel.fromPreset("com.example.revert", preset, 101L);
        policy.standbyDelayMs += 60_000L;
        assertFalse(AppPolicyEditorModel.matchesPreset(policy, preset));
        policy.standbyDelayMs -= 60_000L;
        assertTrue(AppPolicyEditorModel.matchesPreset(policy, preset));
        policy.customized = true;
        assertTrue(AppPolicyEditorModel.matchesPreset(policy, preset));
    }

    @Test
    public void presetMatchIncludesProtectionAndTriggerFields() {
        PolicyPreset preset = new PolicyPreset("Protection");
        preset.id = 82L;
        preset.protectMedia = true;
        preset.triggerMask = AppPolicy.TRIGGER_SCREEN_OFF;
        AppPolicy policy = AppPolicyEditorModel.fromPreset("com.example.protection", preset, 102L);
        assertTrue(AppPolicyEditorModel.matchesPreset(policy, preset));
        policy.protectMedia = false;
        assertFalse(AppPolicyEditorModel.matchesPreset(policy, preset));
        policy.protectMedia = true;
        policy.triggerMask ^= AppPolicy.TRIGGER_PERIODIC;
        assertFalse(AppPolicyEditorModel.matchesPreset(policy, preset));
        policy.triggerMask ^= AppPolicy.TRIGGER_PERIODIC;
        assertTrue(AppPolicyEditorModel.matchesPreset(policy, preset));
    }

    @Test
    public void editorDerivesPresetStatusFromValuesAndSuppressesDeferredSelection() throws Exception {
        java.nio.file.Path root = java.nio.file.Paths.get("");
        for (int i = 0; i < 3 && !java.nio.file.Files.isRegularFile(root.resolve(
                "app/src/main/java/com/gree1d/reappzuku/ui/AppPolicyEditorActivity.java")); i++) {
            root = root.resolve("..");
        }
        String editor = java.nio.file.Files.readString(root.resolve(
                "app/src/main/java/com/gree1d/reappzuku/ui/AppPolicyEditorActivity.java"));
        String layout = java.nio.file.Files.readString(root.resolve(
                "app/src/main/res/layout/activity_app_policy_editor.xml"));
        assertTrue(editor.contains("policy.customized = !AppPolicyEditorModel.matchesPreset(policy, selected);"));
        assertFalse(editor.contains("customized ||"));
        assertFalse(editor.contains("setOnFocusChangeListener("));
        assertTrue(editor.contains("position == lastPresetPosition"));
        assertTrue(editor.contains("standbyMinutes.addTextChangedListener("));
        assertTrue(editor.contains("forceStopMinutes.addTextChangedListener("));
        assertTrue(layout.contains("@+id/policy_preset_status"));
    }

}