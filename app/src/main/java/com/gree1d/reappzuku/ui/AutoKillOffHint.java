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
 * Apps with the Immediate strategy are only stopped while Auto-Kill is on,
 * and Auto-Kill is off on a fresh install. Without this hint the choice was
 * saved and then nothing ever happened.
 */
final class AutoKillOffHint {

    private AutoKillOffHint() {
    }

    /** @param activeSchedule the active Automation Schedule slot, 0 for none; a schedule runs Auto-Kill too */
    static boolean needed(int strategy, boolean autoKillEnabled, int activeSchedule) {
        return strategy == AppPolicy.STRATEGY_IMMEDIATE && !autoKillEnabled && activeSchedule == 0;
    }

    /**
     * @param afterwards run when the hint is closed, or right away when none is needed
     * @return whether the hint was shown
     */
    static boolean show(Activity activity, int strategy, @Nullable Runnable afterwards) {
        SharedPreferences prefs = activity.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (!needed(strategy,
                prefs.getBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false),
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
