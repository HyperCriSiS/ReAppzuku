package com.gree1d.reappzuku.core;

/** Pure validation rules for untrusted preset data before it reaches scheduling or preferences. */
public final class PresetInputPolicy {
    /** Deliberately generous; prevents pathological import amplification without constraining real devices. */
    public static final int MAX_PACKAGE_ENTRIES = 10_000;

    private PresetInputPolicy() {}

    public static void requirePackageCount(String field, int count) {
        if (count < 0 || count > MAX_PACKAGE_ENTRIES) {
            throw new IllegalArgumentException(
                    "Preset package collection out of bounds: " + field + " count=" + count);
        }
    }

    public static void requireClockTime(String field, int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException(
                    "Preset time out of bounds: " + field + "=" + hour + ":" + minute);
        }
    }
}
