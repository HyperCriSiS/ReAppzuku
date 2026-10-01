package com.gree1d.reappzuku.core;

import androidx.annotation.NonNull;

import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.PolicyPreset;

/**
 * Pure policy-editor transformations. Keeps UI parsing separate from canonical policy semantics.
 */
public final class AppPolicyEditorModel {
    public static final long ALL_TRIGGER_BITS =
            AppPolicy.TRIGGER_PERIODIC
                    | AppPolicy.TRIGGER_SCREEN_OFF
                    | AppPolicy.TRIGGER_RAM_THRESHOLD
                    | AppPolicy.TRIGGER_BOOT_CLEANUP
                    | AppPolicy.TRIGGER_HARDWARE_EVENT
                    | AppPolicy.TRIGGER_APP_LAUNCH;

    private AppPolicyEditorModel() {}

    @NonNull
    public static AppPolicy defaultPolicy(@NonNull String packageName, long now) {
        AppPolicy policy = new AppPolicy(packageName);
        policy.source = AppPolicy.SOURCE_EXPLICIT;
        policy.createdAt = now;
        policy.updatedAt = now;
        return policy;
    }

    @NonNull
    public static AppPolicy fromPreset(
            @NonNull String packageName,
            @NonNull PolicyPreset preset,
            long now) {
        AppPolicy policy = defaultPolicy(packageName, now);
        copyPresetValues(preset, policy);
        policy.presetId = preset.id;
        policy.customized = false;
        return normalize(policy);
    }

    public static void copyPresetValues(@NonNull PolicyPreset preset, @NonNull AppPolicy policy) {
        policy.strategy = preset.strategy;
        policy.standbyDelayMs = preset.standbyDelayMs;
        policy.forceStopDelayMs = preset.forceStopDelayMs;
        policy.killMethod = preset.killMethod;
        policy.bootCleanup = preset.bootCleanup;
        policy.backgroundRestriction = preset.backgroundRestriction;
        policy.protectMedia = preset.protectMedia;
        policy.protectForegroundServices = preset.protectForegroundServices;
        policy.protectWidgets = preset.protectWidgets;
        policy.triggerMask = preset.triggerMask;
    }

    @NonNull
    public static PolicyPreset presetFromPolicy(
            @NonNull String name,
            @NonNull AppPolicy policy,
            long now) {
        PolicyPreset preset = new PolicyPreset(name.trim());
        preset.strategy = policy.strategy;
        preset.standbyDelayMs = policy.standbyDelayMs;
        preset.forceStopDelayMs = policy.forceStopDelayMs;
        preset.killMethod = policy.killMethod;
        preset.bootCleanup = policy.bootCleanup;
        preset.backgroundRestriction = policy.backgroundRestriction;
        preset.protectMedia = policy.protectMedia;
        preset.protectForegroundServices = policy.protectForegroundServices;
        preset.protectWidgets = policy.protectWidgets;
        preset.triggerMask = policy.triggerMask & ALL_TRIGGER_BITS;
        preset.builtIn = false;
        preset.createdAt = now;
        preset.updatedAt = now;
        return preset;
    }

    @NonNull
    public static AppPolicy normalize(@NonNull AppPolicy policy) {
        if (policy.strategy < AppPolicy.STRATEGY_UNMANAGED
                || policy.strategy > AppPolicy.STRATEGY_IMMEDIATE) {
            policy.strategy = AppPolicy.STRATEGY_UNMANAGED;
        }

        policy.standbyDelayMs = Math.max(0L, policy.standbyDelayMs);
        if (policy.forceStopDelayMs != Long.MAX_VALUE) {
            policy.forceStopDelayMs = Math.max(0L, policy.forceStopDelayMs);
            if (policy.strategy == AppPolicy.STRATEGY_SMART
                    && policy.forceStopDelayMs < policy.standbyDelayMs) {
                policy.forceStopDelayMs = policy.standbyDelayMs;
            }
        }

        if (policy.killMethod != AppPolicy.KILL_METHOD_FORCE_STOP
                && policy.killMethod != AppPolicy.KILL_METHOD_AM_KILL) {
            policy.killMethod = AppPolicy.KILL_METHOD_FORCE_STOP;
        }

        if (policy.backgroundRestriction < AppPolicy.RESTRICTION_NONE
                || policy.backgroundRestriction > AppPolicy.RESTRICTION_MANUAL) {
            policy.backgroundRestriction = AppPolicy.RESTRICTION_NONE;
        }

        policy.triggerMask &= ALL_TRIGGER_BITS;
        policy.source = AppPolicy.SOURCE_EXPLICIT;
        return policy;
    }

    public static boolean matchesPreset(@NonNull AppPolicy policy, @NonNull PolicyPreset preset) {
        return policy.strategy == preset.strategy
                && policy.standbyDelayMs == preset.standbyDelayMs
                && policy.forceStopDelayMs == preset.forceStopDelayMs
                && policy.killMethod == preset.killMethod
                && policy.bootCleanup == preset.bootCleanup
                && policy.backgroundRestriction == preset.backgroundRestriction
                && policy.protectMedia == preset.protectMedia
                && policy.protectForegroundServices == preset.protectForegroundServices
                && policy.protectWidgets == preset.protectWidgets
                && (policy.triggerMask & ALL_TRIGGER_BITS) == (preset.triggerMask & ALL_TRIGGER_BITS);
    }
}
