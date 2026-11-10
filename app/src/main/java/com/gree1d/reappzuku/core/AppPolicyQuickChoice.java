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
        AppPolicy policy = AppPolicyEditorModel.withStrategy(
                database.appPolicyDao().getByPackage(packageName), packageName, strategy,
                database.policyPresetDao().getById(PolicyPresetSeeder.PRESET_BALANCED),
                System.currentTimeMillis());
        // The restriction screens own this level; the choice must not change it.
        policy.backgroundRestriction =
                BackgroundAppManager.storedRestrictionLevel(context, packageName);
        database.appPolicyDao().upsert(policy);
        SmartLifecycleWorker.reconcilePeriodic(context);
        NewAppSetupStore.removePending(context, packageName);
        NewAppSetupNotifier.cancel(context, packageName);
    }
}
