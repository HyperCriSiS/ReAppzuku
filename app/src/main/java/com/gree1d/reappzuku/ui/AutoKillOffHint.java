package com.gree1d.reappzuku.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.PreferenceKeys;
import com.gree1d.reappzuku.db.AppPolicy;

/**
 * Apps with the Immediate strategy are only stopped while the background
 * service runs and at least one Auto-Kill trigger is enabled. Both are off on
 * a fresh install. Without this hint the choice was saved and then nothing
 * ever happened.
 */
final class AutoKillOffHint {

    private AutoKillOffHint() {
    }

    /** Every setting that makes an Auto-Kill pass run; one of them has to be on. */
    static final String[] TRIGGER_KEYS = {
        PreferenceKeys.KEY_PERIODIC_KILL_ENABLED,
        PreferenceKeys.KEY_KILL_ON_SCREEN_OFF,
        PreferenceKeys.KEY_RAM_THRESHOLD_ENABLED,
        PreferenceKeys.KEY_APP_LAUNCH_TRIGGER_ENABLED,
        PreferenceKeys.KEY_HW_TRIGGER_HEADSET,
        PreferenceKeys.KEY_HW_TRIGGER_USB,
        PreferenceKeys.KEY_HW_TRIGGER_CHARGER,
        PreferenceKeys.KEY_HW_TRIGGER_WIFI,
        PreferenceKeys.KEY_HW_TRIGGER_BLUETOOTH,
        PreferenceKeys.KEY_HW_TRIGGER_GPS,
        PreferenceKeys.KEY_HW_TRIGGER_HOTSPOT
    };

    /**
     * @param serviceEnabled the background service switch
     * @param anyTrigger     whether one of {@link #TRIGGER_KEYS} is on
     * @param activeSchedule the active Automation Schedule slot, 0 for none; a schedule brings its own settings
     */
    static boolean needed(int strategy, boolean serviceEnabled, boolean anyTrigger, int activeSchedule) {
        return strategy == AppPolicy.STRATEGY_IMMEDIATE && activeSchedule == 0
                && !(serviceEnabled && anyTrigger);
    }

    /**
     * @param afterwards run when the hint is closed, or right away when none is needed
     * @return whether the hint was shown
     */
    static boolean show(Activity activity, int strategy, @Nullable Runnable afterwards) {
        SharedPreferences prefs = activity.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        boolean anyTrigger = false;
        for (String key : TRIGGER_KEYS) anyTrigger |= prefs.getBoolean(key, false);
        if (!needed(strategy,
                prefs.getBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false),
                anyTrigger,
                prefs.getInt(PreferenceKeys.KEY_ACTIVE_PRESET, 0))) {
            if (afterwards != null) afterwards.run();
            return false;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.auto_kill_off_hint_title)
                .setMessage(R.string.auto_kill_off_hint_message)
                .setPositiveButton(R.string.auto_kill_off_hint_open_settings, (dialog, which) ->
                        activity.startActivity(new Intent(activity, SettingsActivity.class)))
                .setNegativeButton(R.string.auto_kill_off_hint_later, null)
                .setOnDismissListener(dialog -> {
                    if (afterwards != null) afterwards.run();
                })
                .show();
        return true;
    }
}
