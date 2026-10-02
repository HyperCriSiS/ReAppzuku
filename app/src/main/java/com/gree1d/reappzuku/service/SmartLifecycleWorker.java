package com.gree1d.reappzuku.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.gree1d.reappzuku.core.App;
import com.gree1d.reappzuku.core.AppPolicyLegacyMigrator;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.manager.SmartLifecycleManager;
import com.gree1d.reappzuku.manager.SmartLifecycleRecoveryPolicy;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;

public class SmartLifecycleWorker extends Worker {
    private static final String PERIODIC_WORK = "SmartLifecyclePeriodic";
    private static final String BOOT_WORK = "SmartLifecycleBootCleanup";
    private static final String INPUT_BOOT_PASS = "smart_boot_pass";

    public SmartLifecycleWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedulePeriodic(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SmartLifecycleWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    /** Reconcile periodic Smart work from canonical policy ownership plus bounded legacy fallback. */
    public static void reconcilePeriodic(Context context) {
        try {
            if (hasEffectiveSmartWork(context)) {
                schedulePeriodic(context);
            } else {
                cancel(context);
            }
        } catch (RuntimeException e) {
            // Fail safe for existing Smart users if persistence is transiently unavailable.
            schedulePeriodic(context);
        }
    }

    static boolean hasEffectiveSmartWork(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
        boolean migrationSnapshotCurrent =
                AppPolicyLegacyMigrator.isMigrationSnapshotCurrent(prefs);

        for (AppPolicy policy : AppDatabase.getInstance(appContext).appPolicyDao().getAll()) {
            AppPolicy effective = AppPolicyLegacyMigrator.resolveEffectivePolicy(
                    policy, migrationSnapshotCurrent);
            if (effective != null && effective.strategy == AppPolicy.STRATEGY_SMART) {
                return true;
            }
        }

        if (migrationSnapshotCurrent
                || !prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false)) {
            return false;
        }
        Set<String> legacyBlacklist = prefs.getStringSet(
                KEY_BLACKLISTED_APPS, Collections.emptySet());
        return legacyBlacklist != null && !legacyBlacklist.isEmpty();
    }

    public static void scheduleAfterBoot(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_SMART_BOOT_EPOCH_MS,
                System.currentTimeMillis() - SystemClock.elapsedRealtime()).apply();

        // Periodic scheduling is reconciled after Application startup migration.
        // Always enqueue the boot pass. SmartLifecycleManager filters both canonical
        // policy ownership and the bounded legacy fallback, while explicit SMART
        // policies own their per-app boot cleanup decision.
        int grace = SmartLifecycleManager.getBootGraceMinutes(prefs);
        Data data = new Data.Builder().putBoolean(INPUT_BOOT_PASS, true).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SmartLifecycleWorker.class)
                .setInitialDelay(grace, TimeUnit.MINUTES)
                .setInputData(data)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                BOOT_WORK, ExistingWorkPolicy.REPLACE, request);
    }

    public static void cancel(Context context) {
        WorkManager wm = WorkManager.getInstance(context);
        wm.cancelUniqueWork(PERIODIC_WORK);
        wm.cancelUniqueWork(BOOT_WORK);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        try {
            App app = (App) context;
            ShellManager shellManager = app.getShellManager();
            boolean bootPass = getInputData().getBoolean(INPUT_BOOT_PASS, false);
            SmartLifecycleManager manager = new SmartLifecycleManager(context, shellManager);
            boolean passCompleted = manager.runPass(bootPass);
            if (SmartLifecycleRecoveryPolicy.shouldRetryWorker(bootPass, passCompleted)) {
                return Result.retry();
            }
            return Result.success();
        } catch (Throwable t) {
            return Result.retry();
        }
    }
}