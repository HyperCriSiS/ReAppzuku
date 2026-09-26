package com.gree1d.reappzuku.core;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.PolicyPreset;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable built-in templates for the per-app policy editor.
 *
 * IDs 1..6 are reserved for built-ins. User-created presets use Room's normal
 * autoincrement sequence after those IDs.
 */
public final class PolicyPresetSeeder {
    public static final long PRESET_NEVER_TOUCH = 1L;
    public static final long PRESET_MESSENGER = 2L;
    public static final long PRESET_MEDIA = 3L;
    public static final long PRESET_BALANCED = 4L;
    public static final long PRESET_RARELY_USED = 5L;
    public static final long PRESET_AGGRESSIVE = 6L;

    private static final long HOUR_MS = 60L * 60L * 1000L;

    private PolicyPresetSeeder() {}

    public static void seedBuiltIns(AppDatabase db) {
        List<PolicyPreset> presets = builtIns(System.currentTimeMillis());
        db.runInTransaction(() -> db.policyPresetDao().insertAllIgnore(presets));
    }

    static List<PolicyPreset> builtIns(long now) {
        List<PolicyPreset> result = new ArrayList<>(6);

        PolicyPreset neverTouch = preset(PRESET_NEVER_TOUCH, "never_touch", now);
        neverTouch.strategy = AppPolicy.STRATEGY_PROTECTED;
        neverTouch.bootCleanup = false;
        result.add(neverTouch);

        PolicyPreset messenger = preset(PRESET_MESSENGER, "messenger", now);
        messenger.strategy = AppPolicy.STRATEGY_SMART;
        messenger.standbyDelayMs = 4L * HOUR_MS;
        messenger.forceStopDelayMs = Long.MAX_VALUE;
        messenger.bootCleanup = false;
        result.add(messenger);

        PolicyPreset media = preset(PRESET_MEDIA, "media", now);
        media.strategy = AppPolicy.STRATEGY_SMART;
        media.standbyDelayMs = 2L * HOUR_MS;
        media.forceStopDelayMs = 12L * HOUR_MS;
        media.bootCleanup = false;
        result.add(media);

        PolicyPreset balanced = preset(PRESET_BALANCED, "balanced", now);
        balanced.strategy = AppPolicy.STRATEGY_SMART;
        balanced.standbyDelayMs = AppPolicy.DEFAULT_SMART_STANDBY_DELAY_MS;
        balanced.forceStopDelayMs = AppPolicy.DEFAULT_SMART_FORCE_STOP_DELAY_MS;
        balanced.triggerMask = AppPolicy.TRIGGER_BOOT_CLEANUP;
        result.add(balanced);

        PolicyPreset rarelyUsed = preset(PRESET_RARELY_USED, "rarely_used", now);
        rarelyUsed.strategy = AppPolicy.STRATEGY_SMART;
        rarelyUsed.standbyDelayMs = 30L * 60L * 1000L;
        rarelyUsed.forceStopDelayMs = 2L * HOUR_MS;
        rarelyUsed.backgroundRestriction = AppPolicy.RESTRICTION_MEDIUM;
        rarelyUsed.triggerMask = AppPolicy.TRIGGER_BOOT_CLEANUP;
        result.add(rarelyUsed);

        PolicyPreset aggressive = preset(PRESET_AGGRESSIVE, "aggressive", now);
        aggressive.strategy = AppPolicy.STRATEGY_IMMEDIATE;
        aggressive.backgroundRestriction = AppPolicy.RESTRICTION_HARD;
        aggressive.triggerMask = AppPolicy.TRIGGER_PERIODIC
                | AppPolicy.TRIGGER_SCREEN_OFF
                | AppPolicy.TRIGGER_RAM_THRESHOLD
                | AppPolicy.TRIGGER_BOOT_CLEANUP
                | AppPolicy.TRIGGER_HARDWARE_EVENT
                | AppPolicy.TRIGGER_APP_LAUNCH;
        result.add(aggressive);

        return result;
    }

    private static PolicyPreset preset(long id, String name, long now) {
        PolicyPreset preset = new PolicyPreset(name);
        preset.id = id;
        preset.builtIn = true;
        preset.createdAt = now;
        preset.updatedAt = now;
        return preset;
    }
}
