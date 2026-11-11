package com.gree1d.reappzuku.core;

/**
 * How much of the Settings screen is shown. Lower levels only hide rows; every
 * setting keeps working with its stored value.
 */
public final class SettingsLevel {
    /** Access, appearance, the background service, setup, recommendations, backup. */
    public static final int SIMPLE = 0;
    /** Plus Auto-Kill triggers, schedules, sleep mode, background restrictions, app behavior. */
    public static final int ADVANCED = 1;
    /** Everything. The default, so nobody loses a row they already use. */
    public static final int EXPERT = 2;

    private SettingsLevel() {
    }

    public static int sanitize(int level) {
        return level < SIMPLE || level > EXPERT ? EXPERT : level;
    }

    /** Whether a row that belongs to {@code rowLevel} is shown at {@code chosen}. */
    public static boolean shows(int chosen, int rowLevel) {
        return sanitize(chosen) >= rowLevel;
    }
}
