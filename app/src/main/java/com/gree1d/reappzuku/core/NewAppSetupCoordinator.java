package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.PolicyPreset;
import com.gree1d.reappzuku.service.SmartLifecycleWorker;

import java.util.List;

public final class NewAppSetupCoordinator {
    private NewAppSetupCoordinator() {}

    public static void handlePackageAdded(Context context, String packageName) {
        if (!PackageNameValidator.isValid(packageName)
                || context.getPackageName().equals(packageName)) {
            return;
        }

        AppDatabase db = AppDatabase.getInstance(context);
        boolean queued = NewAppSetupStore.isPending(context, packageName);
        AppPolicy existing = db.appPolicyDao().getByPackage(packageName);
        boolean explicitlyConfigured = isCompletedExplicitSetup(existing, queued);
        if (explicitlyConfigured) {
            clearPendingBeforeCancel(
                    () -> NewAppSetupStore.removePending(context, packageName),
                    () -> NewAppSetupNotifier.cancel(context, packageName));
            return;
        }

        boolean eligible = isEligible(context, packageName);
        int mode = NewAppSetupStore.getMode(context);
        PolicyPreset preset = null;
        if (mode == NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET && eligible) {
            PolicyPresetSeeder.seedBuiltIns(db);
            preset = db.policyPresetDao().getById(NewAppSetupStore.getDefaultPresetId(context));
        }

        int action = NewAppSetupPolicy.decide(
                mode, eligible, explicitlyConfigured, preset != null);
        applyDecision(context, db, packageName, preset, action);
    }

    public static void replayPending(Context context) {
        List<String> pending = NewAppSetupStore.getPending(context);
        for (String packageName : pending) {
            if (!PackageNameValidator.isValid(packageName) || !isInstalled(context, packageName)) {
                NewAppSetupStore.removePending(context, packageName);
                NewAppSetupNotifier.cancel(context, packageName);
                continue;
            }
            handlePackageAdded(context, packageName);
        }
    }

    private static void applyDecision(Context context, AppDatabase db, String packageName,
            PolicyPreset preset, int action) {
        switch (action) {
            case NewAppSetupPolicy.ACTION_APPLY_PRESET:
                if (preset != null) {
                    db.appPolicyDao().upsert(AppPolicyEditorModel.fromPreset(
                            packageName, preset, System.currentTimeMillis()));
                    NewAppSetupStore.removePending(context, packageName);
                    NewAppSetupNotifier.cancel(context, packageName);
                }
                break;
            case NewAppSetupPolicy.ACTION_QUEUE_PRESET_MISSING:
            case NewAppSetupPolicy.ACTION_QUEUE_UNMANAGED:
                queueBeforePolicy(
                        () -> NewAppSetupStore.addPending(context, packageName),
                        () -> ensureExplicitUnmanaged(db, packageName),
                        () -> NewAppSetupNotifier.notifyNeedsSetup(context, packageName));
                break;
            case NewAppSetupPolicy.ACTION_LEAVE_UNMANAGED:
                ensureExplicitUnmanaged(db, packageName);
                NewAppSetupStore.removePending(context, packageName);
                NewAppSetupNotifier.cancel(context, packageName);
                break;
            case NewAppSetupPolicy.ACTION_IGNORE:
            default:
                if (!isEligible(context, packageName)) {
                    NewAppSetupStore.removePending(context, packageName);
                    NewAppSetupNotifier.cancel(context, packageName);
                }
                break;
        }
        SmartLifecycleWorker.reconcilePeriodic(context);
    }

    /**
     * The durable pending marker is the recovery anchor for Ask/preset-missing.
     * An explicit unmanaged Room row must never be created before its pending
     * marker can be committed: otherwise a retry sees SOURCE_EXPLICIT + !queued
     * and incorrectly concludes the user already configured the app.
     *
     * Do not emit a notification until both durable operations succeeded.
     */
    static void queueBeforePolicy(Runnable persistPending, Runnable ensureUnmanaged,
            Runnable notifyUser) {
        persistPending.run();
        ensureUnmanaged.run();
        notifyUser.run();
    }

    private static void ensureExplicitUnmanaged(AppDatabase db, String packageName) {
        AppPolicy current = db.appPolicyDao().getByPackage(packageName);
        if (current != null && current.source == AppPolicy.SOURCE_EXPLICIT) return;
        long now = System.currentTimeMillis();
        AppPolicy policy = AppPolicyEditorModel.defaultPolicy(packageName, now);
        policy.strategy = AppPolicy.STRATEGY_UNMANAGED;
        policy.presetId = null;
        policy.customized = true;
        db.appPolicyDao().upsert(policy);
    }

    static boolean isEligible(Context context, String packageName) {
        if (!PackageNameValidator.isValid(packageName)) return false;
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return false;
            if ((info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) return false;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return !ProtectedApps.isProtected(context, packageName);
    }

    private static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}