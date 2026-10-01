package com.gree1d.reappzuku.core;

/** Compatibility helpers for the legacy preset-named Automation Schedule storage. */
public final class AutomationScheduleCompat {
    private static final int FIRST_SLOT = 1;
    private static final int LAST_SLOT = 2;

    private AutomationScheduleCompat() {}

    public static boolean isLegacyDefaultName(String value, int scheduleNumber) {
        validateSlot(scheduleNumber);
        return ("Preset " + scheduleNumber).equals(value);
    }

    public static String exportFileName(int scheduleNumber) {
        validateSlot(scheduleNumber);
        return "automation-schedule-" + scheduleNumber + ".json";
    }

    private static void validateSlot(int scheduleNumber) {
        if (scheduleNumber < FIRST_SLOT || scheduleNumber > LAST_SLOT) {
            throw new IllegalArgumentException("Automation Schedule slot must be 1 or 2");
        }
    }
}
