#!/usr/bin/env python3
from pathlib import Path


def rw(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    if new in s:
        return
    if old not in s:
        raise RuntimeError(f"Anchor not found in {path}: {old[:100]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


def write(path, content):
    p = Path(path); p.parent.mkdir(parents=True, exist_ok=True); p.write_text(content, encoding="utf-8")

write("app/src/main/java/com/gree1d/reappzuku/core/ExactAlarmCapability.java", '''package com.gree1d.reappzuku.core;

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
''')

rw("app/src/main/java/com/gree1d/reappzuku/manager/PresetManager.java",
'import com.gree1d.reappzuku.core.AppDebugManager.Category;\n',
'import com.gree1d.reappzuku.core.AppDebugManager.Category;\nimport com.gree1d.reappzuku.core.ExactAlarmCapability;\n')

rw("app/src/main/java/com/gree1d/reappzuku/manager/PresetManager.java",
'''        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, activateTime,
                buildPendingIntent(model.presetNumber, ACTION_PRESET_ACTIVATE));
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deactivateTime,
                buildPendingIntent(model.presetNumber, ACTION_PRESET_DEACTIVATE));
''',
'''        boolean activateExact = ExactAlarmCapability.scheduleExactOrBestEffort(
                context, alarmManager, AlarmManager.RTC_WAKEUP, activateTime,
                buildPendingIntent(model.presetNumber, ACTION_PRESET_ACTIVATE), true);
        boolean deactivateExact = ExactAlarmCapability.scheduleExactOrBestEffort(
                context, alarmManager, AlarmManager.RTC_WAKEUP, deactivateTime,
                buildPendingIntent(model.presetNumber, ACTION_PRESET_DEACTIVATE), true);
        if (!activateExact || !deactivateExact) {
            AppDebugManager.w(Category.AUTO_KILL_PRESETS,
                    "PresetManager: exact alarm permission unavailable; using best-effort timing for preset #" + model.presetNumber);
        }
''')

rw("app/src/main/java/com/gree1d/reappzuku/manager/PresetManager.java",
'''        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(),
                buildPendingIntent(presetNumber, action));
''',
'''        boolean exact = ExactAlarmCapability.scheduleExactOrBestEffort(
                context, alarmManager, AlarmManager.RTC_WAKEUP, next.getTimeInMillis(),
                buildPendingIntent(presetNumber, action), true);
        if (!exact) {
            AppDebugManager.w(Category.AUTO_KILL_PRESETS,
                    "PresetManager: rescheduleNextAlarm using best-effort timing for preset #" + presetNumber + " action=" + action);
        }
''')

rw("app/src/main/java/com/gree1d/reappzuku/manager/PresetManager.java",
'    public void checkAndApplyCurrentPreset() {\n',
'''    public void restoreAfterBoot() {
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: restoreAfterBoot: rebuilding preset alarms");
        for (int number : new int[]{PresetModel.PRESET_1, PresetModel.PRESET_2}) {
            PresetModel model = loadPreset(number);
            if (model != null && model.enabled) scheduleAlarms(model);
            else cancelAlarms(number);
        }
        checkAndApplyCurrentPreset();
    }

    public void checkAndApplyCurrentPreset() {
''')

rw("app/src/main/java/com/gree1d/reappzuku/manager/RestrictionsScheduler.java",
'import com.gree1d.reappzuku.core.AppDebugManager.Category;\n',
'import com.gree1d.reappzuku.core.AppDebugManager.Category;\nimport com.gree1d.reappzuku.core.ExactAlarmCapability;\n')

rw("app/src/main/java/com/gree1d/reappzuku/manager/RestrictionsScheduler.java",
'''        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nearest, getAlarmIntent());
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, nearest, getAlarmIntent());
        }
''',
'''        boolean exact = ExactAlarmCapability.scheduleExactOrBestEffort(
                context, am, AlarmManager.RTC_WAKEUP, nearest, getAlarmIntent(), true);
        if (!exact) {
            AppDebugManager.w(Category.RESTRICTIONS_SCHEDULER,
                    "RestrictionsScheduler: exact alarm permission unavailable; using best-effort timing");
        }
''')

rw("app/src/main/java/com/gree1d/reappzuku/manager/RestrictionsScheduler.java",
'''        PendingIntent pi = getAlarmIntent(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nearest, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, nearest, pi);
        }
''',
'''        PendingIntent pi = getAlarmIntent(context);
        boolean exact = ExactAlarmCapability.scheduleExactOrBestEffort(
                context, am, AlarmManager.RTC_WAKEUP, nearest, pi, true);
        if (!exact) {
            AppDebugManager.w(Category.RESTRICTIONS_SCHEDULER,
                    "RestrictionsScheduler: scheduleNextStatic using best-effort timing");
        }
''')

rw("app/src/main/java/com/gree1d/reappzuku/core/BootReceiver.java",
'import com.gree1d.reappzuku.manager.RestrictionsScheduler;\n',
'import com.gree1d.reappzuku.manager.RestrictionsScheduler;\nimport com.gree1d.reappzuku.manager.PresetManager;\n')

rw("app/src/main/java/com/gree1d/reappzuku/core/BootReceiver.java",
'''        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {

            RestrictionsScheduler.scheduleNextStatic(context);
''',
'''        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {

            RestrictionsScheduler.scheduleNextStatic(context);
            new PresetManager(context).restoreAfterBoot();
''')

print("assurance-phase1-scheduler applied")
