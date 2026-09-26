package com.gree1d.reappzuku.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "app_policy",
        indices = {
                @Index(value = {"strategy"}),
                @Index(value = {"presetId"})
        }
)
public class AppPolicy {
    public static final int STRATEGY_UNMANAGED = 0;
    public static final int STRATEGY_PROTECTED = 1;
    public static final int STRATEGY_SMART = 2;
    public static final int STRATEGY_IMMEDIATE = 3;

    public static final int KILL_METHOD_FORCE_STOP = 0;
    public static final int KILL_METHOD_AM_KILL = 1;

    public static final int RESTRICTION_NONE = 0;
    public static final int RESTRICTION_SOFT = 1;
    public static final int RESTRICTION_MEDIUM = 2;
    public static final int RESTRICTION_HARD = 3;
    public static final int RESTRICTION_MANUAL = 4;

    public static final long TRIGGER_PERIODIC = 1L;
    public static final long TRIGGER_SCREEN_OFF = 1L << 1;
    public static final long TRIGGER_RAM_THRESHOLD = 1L << 2;
    public static final long TRIGGER_BOOT_CLEANUP = 1L << 3;
    public static final long TRIGGER_HARDWARE_EVENT = 1L << 4;
    public static final long TRIGGER_APP_LAUNCH = 1L << 5;

    public static final long DEFAULT_SMART_STANDBY_DELAY_MS = 60L * 60L * 1000L;
    public static final long DEFAULT_SMART_FORCE_STOP_DELAY_MS = 6L * 60L * 60L * 1000L;

    @PrimaryKey
    @NonNull
    public String packageName;

    public int strategy = STRATEGY_UNMANAGED;

    @Nullable
    public Long presetId;

    public boolean customized = false;
    public long standbyDelayMs = DEFAULT_SMART_STANDBY_DELAY_MS;
    public long forceStopDelayMs = DEFAULT_SMART_FORCE_STOP_DELAY_MS;
    public int killMethod = KILL_METHOD_FORCE_STOP;
    public boolean bootCleanup = true;
    public int backgroundRestriction = RESTRICTION_NONE;
    public boolean protectMedia = true;
    public boolean protectForegroundServices = true;
    public boolean protectWidgets = true;
    public long triggerMask = 0L;
    public long createdAt = 0L;
    public long updatedAt = 0L;

    public AppPolicy(@NonNull String packageName) {
        this.packageName = packageName;
    }
}
