package com.gree1d.reappzuku.core;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

/** Central capability boundary for exact-alarm scheduling. */
public final class ExactAlarmCapability {
    private ExactAlarmCapability() {}

    public static boolean canScheduleExact(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return am.canScheduleExactAlarms();
        return true;
    }

    /** Returns true for exact scheduling, false when an inexact safe fallback was used. */
    public static boolean scheduleExactOrBestEffort(Context context, AlarmManager am, int type,
            long triggerAtMillis, PendingIntent operation, boolean allowWhileIdle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            if (allowWhileIdle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(type, triggerAtMillis, operation);
            } else {
                am.set(type, triggerAtMillis, operation);
            }
            return false;
        }
        if (allowWhileIdle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(type, triggerAtMillis, operation);
        } else {
            am.setExact(type, triggerAtMillis, operation);
        }
        return true;
    }
}
