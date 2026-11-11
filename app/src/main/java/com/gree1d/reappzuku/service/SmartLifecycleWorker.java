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

import com.gree1d.reappzuku.core.AppPolicyResolver;
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
        schedulePeriodic(context, MIN_PERIOD_MINUTES);
    }

    private static void schedulePeriodic(Context context, long minutes) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SmartLifecycleWorker.class, minutes, TimeUnit.MINUTES).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    static final long MIN_PERIOD_MINUTES = 15;
    static final long MAX_PERIOD_MINUTES = 60;

    /**
     * Checks run four times per shortest standby delay, so a deadline is met
     * at most a quarter late. WorkManager allows no less than 15 minutes; more
     * than an hour would let a stop come noticeably late.
     */
    static long periodMinutes(long shortestStandbyDelayMs) {
        long quarter = TimeUnit.MILLISECONDS.toMinutes(shortestStandbyDelayMs) / 4;
        return Math.max(MIN_PERIOD_MINUTES, Math.min(MAX_PERIOD_MINUTES, quarter));
    }

    /** Reconcile periodic Smart work from canonical policy ownership plus bounded legacy fallback. */
    public static void reconcilePeriodic(Context context) {
        try {
            long shortestDelay = shortestSmartStandbyDelayMs(context);
            if (shortestDelay > 0L) {
                schedulePeriodic(context, periodMinutes(shortestDelay));
            } else {
                cancel(context);
            }
        } catch (RuntimeException e) {
            // Fail safe for existing Smart users if persistence is transiently unavailable.
            schedulePeriodic(context);
        }
    }

    /** The shortest standby delay of any app Smart manages, or 0 when it manages none. */
    static long shortestSmartStandbyDelayMs(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
        boolean migrationSnapshotCurrent =
                AppPolicyLegacyMigrator.isMigrationSnapshotCurrent(prefs);

        long shortest = 0L;
        for (AppPolicy policy : AppDatabase.getInstance(appContext).appPolicyDao().getAll()) {
            AppPolicy effective = AppPolicyLegacyMigrator.resolveEffectivePolicy(
                    policy, migrationSnapshotCurrent);
            if (effective != null && effective.strategy == AppPolicy.STRATEGY_SMART) {
                long delay = AppPolicyResolver.resolveSmartStandbyDelayMs(effective, 0L);
                shortest = shortest == 0L ? delay : Math.min(shortest, delay);
            }
        }
        if (shortest > 0L) return shortest;

        if (migrationSnapshotCurrent
                || !prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false)) {
            return 0L;
        }
        Set<String> legacyBlacklist = prefs.getStringSet(
                KEY_BLACKLISTED_APPS, Collections.emptySet());
        // Legacy profiles can use a 30 minute delay; keep the shortest period for them.
        return legacyBlacklist != null && !legacyBlacklist.isEmpty()
                ? TimeUnit.MINUTES.toMillis(MIN_PERIOD_MINUTES) : 0L;
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