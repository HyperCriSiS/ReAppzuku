package com.gree1d.reappzuku.core;

import android.content.Context;

import androidx.annotation.WorkerThread;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.service.SmartLifecycleWorker;

/**
 * Sets the strategy of one app without opening the policy editor. This is the
 * replacement for adding an app to the legacy whitelist or blacklist.
 */
public final class AppPolicyQuickChoice {

    private AppPolicyQuickChoice() {
    }

    @WorkerThread
    public static void apply(Context context, String packageName, int strategy) {
        AppDatabase database = AppDatabase.getInstance(context);
        PolicyPresetSeeder.seedBuiltIns(database);
        AppPolicy existing = database.appPolicyDao().getByPackage(packageName);
        AppPolicy policy = AppPolicyEditorModel.withStrategy(
                existing, packageName, strategy,
                database.policyPresetDao().getById(PolicyPresetSeeder.PRESET_BALANCED),
                System.currentTimeMillis());
        if (policy != existing) {
            // A new policy stops apps the way the global setting does.
            policy.killMethod = context.getSharedPreferences(
                    PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .getInt(PreferenceKeys.KEY_AUTO_KILL_TYPE, 0) == 1
                    ? AppPolicy.KILL_METHOD_AM_KILL : AppPolicy.KILL_METHOD_FORCE_STOP;
        }
        save(context, database, policy);
    }

    /** Gives the app exactly the values of a built-in preset, replacing its policy. */
    @WorkerThread
    public static void applyPreset(Context context, String packageName, long presetId) {
        AppDatabase database = AppDatabase.getInstance(context);
        PolicyPresetSeeder.seedBuiltIns(database);
        AppPolicy existing = database.appPolicyDao().getByPackage(packageName);
        long now = System.currentTimeMillis();
        AppPolicy policy = AppPolicyEditorModel.fromPreset(
                packageName, database.policyPresetDao().getById(presetId), now);
        if (existing != null && existing.createdAt > 0L) policy.createdAt = existing.createdAt;
        save(context, database, policy);
    }

    private static void save(Context context, AppDatabase database, AppPolicy policy) {
        String packageName = policy.packageName;
        // The restriction screens own this level; the choice must not change it.
        policy.backgroundRestriction =
                BackgroundAppManager.storedRestrictionLevel(context, packageName);
        database.appPolicyDao().upsert(policy);
        SmartLifecycleWorker.reconcilePeriodic(context);
        NewAppSetupStore.removePending(context, packageName);
        NewAppSetupNotifier.cancel(context, packageName);
    }
}
