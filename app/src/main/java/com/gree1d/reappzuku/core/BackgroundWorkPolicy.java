package com.gree1d.reappzuku.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;

/**
 * Single source of truth for App Behavior options that can terminate or avoid
 * starting ReAppzuku's main process. Those options are incompatible with
 * active background automation because they would interrupt the work.
 */
public final class BackgroundWorkPolicy {
    private static final String KEY_RESTRICTIONS_SCHEDULES = "restrictions_schedules";

    private BackgroundWorkPolicy() {}

    public static boolean requiresBackgroundContinuity(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

        // The foreground/background management service and everything attached
        // to it (periodic kill, screen-off actions, RAM threshold, HW triggers,
        // sleep mode, etc.) is represented by the master service switch.
        if (prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false)) return true;

        // Smart Lifecycle is intentionally independent from the legacy service
        // and uses WorkManager, so it must be considered separately.
        if (prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false)) return true;

        // Defensive: do not allow process-killing behavior while a sleep mode or
        // preset remains active even if preferences temporarily become inconsistent.
        if (prefs.getBoolean(KEY_SLEEP_MODE_ENABLED, false)) return true;
        if (prefs.getInt(KEY_ACTIVE_PRESET, 0) != 0) return true;

        // Restriction schedules are AlarmManager-based and can be enabled without
        // keeping the Auto-Kill screen open. Treat any enabled entry as automation.
        if (hasEnabledRestrictionSchedule(prefs)) return true;

        return false;
    }

    private static boolean hasEnabledRestrictionSchedule(SharedPreferences prefs) {
        String json = prefs.getString(KEY_RESTRICTIONS_SCHEDULES, null);
        if (json == null || json.trim().isEmpty()) return false;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.optJSONObject(i);
                if (entry != null && entry.optBoolean("enabled", true)) return true;
            }
        } catch (Exception ignored) {
            // If stored scheduler state is unreadable, fail safe: do not permit
            // behavior that could terminate background work unexpectedly.
            return true;
        }
        return false;
    }

    public static boolean isOnDemandBehaviorAllowed(Context context) {
        return !requiresBackgroundContinuity(context);
    }

    public static boolean shouldPreventShizukuAutoStart(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return isOnDemandBehaviorAllowed(context)
                && prefs.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, true);
    }

    /**
     * Keep the cross-process Shizuku provider independent from SharedPreferences.
     * Android does not provide reliable live SharedPreferences coherence across
     * processes, so the normal process persists the policy in the enabled state
     * of a non-exported receiver. The :shizuku process only sends an explicit
     * broadcast; Android delivers it only when auto-start is allowed.
     */
    public static void syncShizukuWakeComponent(Context context) {
        boolean enableWakeReceiver = !shouldPreventShizukuAutoStart(context);
        ComponentName component = new ComponentName(context, ShizukuWakeReceiver.class);
        int desired = enableWakeReceiver
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        PackageManager pm = context.getPackageManager();
        if (pm.getComponentEnabledSetting(component) != desired) {
            pm.setComponentEnabledSetting(
                    component, desired, PackageManager.DONT_KILL_APP);
        }
    }

    /**
     * Force conflicting options off when background automation becomes active.
     * Returns true when the App Behavior options must be disabled in the UI.
     */
    public static boolean enforceCompatibleBehavior(Context context) {
        boolean blocked = requiresBackgroundContinuity(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (blocked) {
            boolean needsWrite = prefs.getBoolean(KEY_EXIT_ON_BACK, false)
                    || prefs.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, true);
            if (needsWrite) {
                prefs.edit()
                        .putBoolean(KEY_EXIT_ON_BACK, false)
                        .putBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, false)
                        .apply();
            }
        }
        syncShizukuWakeComponent(context);
        return blocked;
    }
}
