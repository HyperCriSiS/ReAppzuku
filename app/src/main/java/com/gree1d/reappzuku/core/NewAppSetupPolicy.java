package com.gree1d.reappzuku.core;

/** Pure decision policy for newly installed application handling. */
public final class NewAppSetupPolicy {
    public static final int MODE_ASK_AFTER_INSTALL = 0;
    public static final int MODE_APPLY_DEFAULT_PRESET = 1;
    public static final int MODE_LEAVE_UNMANAGED = 2;

    public static final int ACTION_IGNORE = 0;
    public static final int ACTION_QUEUE_UNMANAGED = 1;
    public static final int ACTION_APPLY_PRESET = 2;
    public static final int ACTION_LEAVE_UNMANAGED = 3;
    public static final int ACTION_QUEUE_PRESET_MISSING = 4;

    private NewAppSetupPolicy() {}

    public static int sanitizeMode(int mode) {
        if (mode < MODE_ASK_AFTER_INSTALL || mode > MODE_LEAVE_UNMANAGED) {
            return MODE_ASK_AFTER_INSTALL;
        }
        return mode;
    }

    public static int decide(int mode, boolean eligible, boolean alreadyConfigured,
            boolean presetAvailable) {
        if (!eligible || alreadyConfigured) return ACTION_IGNORE;
        switch (sanitizeMode(mode)) {
            case MODE_APPLY_DEFAULT_PRESET:
                return presetAvailable ? ACTION_APPLY_PRESET : ACTION_QUEUE_PRESET_MISSING;
            case MODE_LEAVE_UNMANAGED:
                return ACTION_LEAVE_UNMANAGED;
            case MODE_ASK_AFTER_INSTALL:
            default:
                return ACTION_QUEUE_UNMANAGED;
        }
    }
}
