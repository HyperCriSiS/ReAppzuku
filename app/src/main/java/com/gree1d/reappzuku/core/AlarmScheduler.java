package com.gree1d.reappzuku.core;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;

/** Small boundary around AlarmManager and exact-alarm fallback policy. */
public final class AlarmScheduler {
    public enum ScheduleResult {
        EXACT,
        BEST_EFFORT,
        UNAVAILABLE
    }

    private final Context context;
    private final AlarmManager alarmManager;

    public AlarmScheduler(Context context) {
        if (context == null) throw new IllegalArgumentException("context == null");
        this.context = context.getApplicationContext();
        this.alarmManager = (AlarmManager) this.context.getSystemService(Context.ALARM_SERVICE);
    }

    public boolean isAvailable() {
        return alarmManager != null;
    }

    public ScheduleResult scheduleRtcWakeup(long triggerAtMillis, PendingIntent operation, boolean allowWhileIdle) {
        if (operation == null) throw new IllegalArgumentException("operation == null");
        if (alarmManager == null) return ScheduleResult.UNAVAILABLE;
        boolean exact = ExactAlarmCapability.scheduleExactOrBestEffort(
                context,
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operation,
                allowWhileIdle);
        return exact ? ScheduleResult.EXACT : ScheduleResult.BEST_EFFORT;
    }

    public boolean cancel(PendingIntent operation) {
        if (operation == null) throw new IllegalArgumentException("operation == null");
        if (alarmManager == null) return false;
        alarmManager.cancel(operation);
        return true;
    }
}
