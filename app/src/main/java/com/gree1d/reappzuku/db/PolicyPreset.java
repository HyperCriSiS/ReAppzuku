package com.gree1d.reappzuku.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "policy_preset",
        indices = {
                @Index(value = {"name"})
        }
)
public class PolicyPreset {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name;

    public int strategy = AppPolicy.STRATEGY_UNMANAGED;
    public long standbyDelayMs = AppPolicy.DEFAULT_SMART_STANDBY_DELAY_MS;
    public long forceStopDelayMs = AppPolicy.DEFAULT_SMART_FORCE_STOP_DELAY_MS;
    public int killMethod = AppPolicy.KILL_METHOD_FORCE_STOP;
    public boolean bootCleanup = true;
    public int backgroundRestriction = AppPolicy.RESTRICTION_NONE;
    public boolean protectMedia = true;
    public boolean protectForegroundServices = true;
    public boolean protectWidgets = true;
    public long triggerMask = 0L;
    public boolean builtIn = false;
    public long createdAt = 0L;
    public long updatedAt = 0L;

    public PolicyPreset(@NonNull String name) {
        this.name = name;
    }
}
