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
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.manager.SmartLifecycleManager;
import com.gree1d.reappzuku.manager.SmartLifecycleRecoveryPolicy;

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
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false)) {
            cancel(context);
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SmartLifecycleWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void scheduleAfterBoot(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false)) return;

        prefs.edit().putLong(KEY_SMART_BOOT_EPOCH_MS,
                System.currentTimeMillis() - SystemClock.elapsedRealtime()).apply();

        schedulePeriodic(context);
        if (!prefs.getBoolean(KEY_SMART_BOOT_CLEANUP_ENABLED, true)) return;

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
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false)) return Result.success();

        try {
            App app = (App) context;
            ShellManager shellManager = app.getShellManager();
            boolean bootPass = getInputData().getBoolean(INPUT_BOOT_PASS, false);
            if (!shellManager.resolveAnyShellPermission()) {
                AppDebugManager.w(Category.AUTO_KILL_BASE,
                        "SmartLifecycleWorker: shell unavailable" + (bootPass ? ", retrying boot cleanup" : ", waiting for next pass"));
                return bootPass ? Result.retry() : Result.success();
            }
            SmartLifecycleManager manager = new SmartLifecycleManager(context, shellManager);
            boolean passCompleted = manager.runPass(bootPass);
            if (SmartLifecycleRecoveryPolicy.shouldRetryWorker(bootPass, passCompleted)) {
                AppDebugManager.w(Category.AUTO_KILL_BASE,
                        "SmartLifecycleWorker: boot cleanup had force-stop failures, retrying");
                return Result.retry();
            }
            return Result.success();
        } catch (Throwable t) {
            AppDebugManager.e(Category.AUTO_KILL_BASE, "SmartLifecycleWorker: pass failed", t);
            return Result.retry();
        }
    }
}
