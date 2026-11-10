package com.gree1d.reappzuku.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Battery drain of the whole device on one local calendar day. */
@Entity(tableName = "daily_device_usage")
public class DailyDeviceUsage {
    /** Local calendar day as yyyyMMdd. */
    @PrimaryKey
    public int day;

    /** Sum of the battery level drops seen between snapshots, in percent points. */
    public double drainPct;
}
