package com.gree1d.reappzuku.core;

import android.content.Context;
import android.os.Build;

import androidx.annotation.WorkerThread;

/**
 * Single answer to "does this feature work in the current operating mode?".
 * Engines and screens ask here instead of combining shell, SDK and permission
 * checks on their own.
 */
public final class CapabilityMatrix {

    public enum Mode { ROOT, SHIZUKU, NONE }

    public enum Feature {
        KILL_APPS,
        SLEEP_MODE,
        STATISTICS,
        BACKGROUND_RESTRICTION,
        CLEAR_CACHE,
        MEMORY_PSS,
        EXACT_ALARMS
    }

    public enum Availability {
        AVAILABLE,
        /** Works, but with reduced precision. */
        DEGRADED_RSS_ONLY,
        DEGRADED_INEXACT_ALARMS,
        UNAVAILABLE_NEEDS_PRIVILEGE,
        UNAVAILABLE_NEEDS_ANDROID_11;

        public boolean isUsable() {
            return this != UNAVAILABLE_NEEDS_PRIVILEGE && this != UNAVAILABLE_NEEDS_ANDROID_11;
        }
    }

    private final Mode mode;
    private final boolean shizukuServiceUsable;
    private final int sdkInt;
    private final boolean exactAlarmsAllowed;

    /**
     * @param shizukuServiceUsable Shizuku can be usable while root is the active
     *                             backend; PSS readings only come from its service.
     */
    public CapabilityMatrix(Mode mode, boolean shizukuServiceUsable, int sdkInt,
                            boolean exactAlarmsAllowed) {
        this.mode = mode;
        this.shizukuServiceUsable = shizukuServiceUsable;
        this.sdkInt = sdkInt;
        this.exactAlarmsAllowed = exactAlarmsAllowed;
    }

    /** Root wins when both are present, matching the command dispatch order. */
    @WorkerThread
    public static CapabilityMatrix current(Context context, ShellManager shell) {
        boolean shizuku = shell.hasShizukuPermission();
        Mode mode = shell.hasRootAccess() ? Mode.ROOT : shizuku ? Mode.SHIZUKU : Mode.NONE;
        return new CapabilityMatrix(mode, shizuku, Build.VERSION.SDK_INT,
                ExactAlarmCapability.canScheduleExact(context));
    }

    public Mode mode() {
        return mode;
    }

    public Availability of(Feature feature) {
        if (feature == Feature.EXACT_ALARMS) {
            return exactAlarmsAllowed ? Availability.AVAILABLE : Availability.DEGRADED_INEXACT_ALARMS;
        }
        if (mode == Mode.NONE) return Availability.UNAVAILABLE_NEEDS_PRIVILEGE;
        switch (feature) {
            case BACKGROUND_RESTRICTION:
            case CLEAR_CACHE:
                return sdkInt >= Build.VERSION_CODES.R
                        ? Availability.AVAILABLE
                        : Availability.UNAVAILABLE_NEEDS_ANDROID_11;
            case MEMORY_PSS:
                return mode == Mode.SHIZUKU || shizukuServiceUsable
                        ? Availability.AVAILABLE
                        : Availability.DEGRADED_RSS_ONLY;
            default:
                return Availability.AVAILABLE;
        }
    }
}
