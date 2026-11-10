package com.gree1d.reappzuku.core;

/** Pure decisions of the setup screen. */
public final class FirstRunPolicy {
    /** Ask per app after each install. This is what the app does without setup. */
    public static final int CHOICE_ASK = 0;
    /** New apps go on standby when idle and are never stopped. */
    public static final int CHOICE_GENTLE = 1;
    /** New apps go on standby when idle and are stopped after a longer idle time. */
    public static final int CHOICE_BALANCED = 2;

    private FirstRunPolicy() {
    }

    /**
     * Setup is offered on a fresh install only. Someone who updates from an
     * earlier version has a working configuration and is not interrupted.
     */
    public static boolean shouldOffer(boolean done, long firstInstallTime, long lastUpdateTime) {
        return !done && firstInstallTime == lastUpdateTime;
    }

    /** Only a choice the user actually changed is written. */
    public static boolean shouldWrite(int storedChoice, int chosen) {
        return storedChoice != chosen;
    }

    public static int newAppMode(int choice) {
        return choice == CHOICE_ASK
                ? NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL
                : NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET;
    }

    /** The preset new apps get; only used when the mode applies one. */
    public static long defaultPreset(int choice) {
        return choice == CHOICE_GENTLE
                ? PolicyPresetSeeder.PRESET_MESSENGER
                : PolicyPresetSeeder.PRESET_BALANCED;
    }

    /** The choice that matches what is stored, so the screen opens on the current state. */
    public static int choiceFor(int newAppMode, long defaultPresetId) {
        if (newAppMode != NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET) return CHOICE_ASK;
        return defaultPresetId == PolicyPresetSeeder.PRESET_MESSENGER ? CHOICE_GENTLE : CHOICE_BALANCED;
    }
}
