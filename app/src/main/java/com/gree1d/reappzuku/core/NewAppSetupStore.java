package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;

public final class NewAppSetupStore {
    private static final Object LOCK = new Object();

    private NewAppSetupStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public static int getMode(Context context) {
        return NewAppSetupPolicy.sanitizeMode(prefs(context).getInt(
                KEY_NEW_APP_SETUP_MODE, NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL));
    }

    public static void setMode(Context context, int mode) {
        prefs(context).edit()
                .putInt(KEY_NEW_APP_SETUP_MODE, NewAppSetupPolicy.sanitizeMode(mode))
                .apply();
    }

    public static long getDefaultPresetId(Context context) {
        return prefs(context).getLong(
                KEY_NEW_APP_DEFAULT_PRESET_ID, PolicyPresetSeeder.PRESET_BALANCED);
    }

    public static void setDefaultPresetId(Context context, long presetId) {
        prefs(context).edit().putLong(KEY_NEW_APP_DEFAULT_PRESET_ID, presetId).apply();
    }

    public static void addPending(Context context, String packageName) {
        addPending(prefs(context), packageName);
    }

    // Package-private SharedPreferences overloads permit reliable injected
    // commit(false) tests without touching real user preferences.
    static void addPending(SharedPreferences preferences, String packageName) {
        if (!PackageNameValidator.isValid(packageName)) return;
        synchronized (LOCK) {
            Set<String> pending = new HashSet<>(
                    preferences.getStringSet(KEY_NEW_APP_SETUP_QUEUE, Collections.emptySet()));
            if (pending.add(packageName)) {
                requireCommit(preferences.edit()
                        .putStringSet(KEY_NEW_APP_SETUP_QUEUE, pending).commit());
            }
        }
    }

    public static void removePending(Context context, String packageName) {
        removePending(prefs(context), packageName);
    }

    static void removePending(SharedPreferences preferences, String packageName) {
        if (packageName == null) return;
        synchronized (LOCK) {
            Set<String> pending = new HashSet<>(
                    preferences.getStringSet(KEY_NEW_APP_SETUP_QUEUE, Collections.emptySet()));
            if (pending.remove(packageName)) {
                requireCommit(preferences.edit()
                        .putStringSet(KEY_NEW_APP_SETUP_QUEUE, pending).commit());
            }
        }
    }

    private static void requireCommit(boolean committed) {
        if (!committed) {
            throw new IllegalStateException("Could not persist new-app pending queue");
        }
    }

    public static boolean isPending(Context context, String packageName) {
        synchronized (LOCK) {
            return prefs(context)
                    .getStringSet(KEY_NEW_APP_SETUP_QUEUE, Collections.emptySet())
                    .contains(packageName);
        }
    }

    public static List<String> getPending(Context context) {
        synchronized (LOCK) {
            List<String> result = new ArrayList<>(
                    prefs(context).getStringSet(
                            KEY_NEW_APP_SETUP_QUEUE, Collections.emptySet()));
            Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
            return result;
        }
    }
}