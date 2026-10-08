package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.gree1d.reappzuku.service.NewAppInstallReconcileWorker;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_NEW_APP_KNOWN_PACKAGES;
import static com.gree1d.reappzuku.core.PreferenceKeys.PREFERENCES_NAME;

/**
 * Android-compatible new-app detection for targetSdk 37.
 *
 * <p>ACTION_PACKAGE_ADDED is not one of the implicit broadcasts that modern Android delivers
 * to manifest-declared receivers. While the ReAppzuku process is alive, this class therefore
 * registers a context receiver. A persisted installed-package inventory plus periodic WorkManager
 * reconciliation catches installs that happened while the process was stopped.</p>
 */
public final class NewAppInstallMonitor {
    public static final String PERIODIC_WORK = "NewAppInstallReconcile";

    private static final Object LOCK = new Object();
    private static PackageAddedReceiver liveReceiver;
    private static boolean liveReceiverRegistered;

    private NewAppInstallMonitor() {}

    public static void start(Context context) {
        Context appContext = context.getApplicationContext();

        // Seed the persisted inventory first. Register the live receiver afterwards, then
        // reconcile once more so installs that raced with startup are still detected.
        try {
            reconcileInstalledPackages(appContext);
        } catch (RuntimeException ignored) {
        }
        try {
            registerLiveReceiver(appContext);
        } catch (RuntimeException ignored) {
        }
        try {
            reconcileInstalledPackages(appContext);
        } catch (RuntimeException ignored) {
        }
        schedulePeriodic(appContext);
    }

    static void registerLiveReceiver(Context context) {
        synchronized (LOCK) {
            if (liveReceiverRegistered) return;

            IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
            filter.addDataScheme("package");

            PackageAddedReceiver receiver = new PackageAddedReceiver();
            ContextCompat.registerReceiver(
                    context.getApplicationContext(),
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED);
            liveReceiver = receiver;
            liveReceiverRegistered = true;
        }
    }

    public static void reconcileInstalledPackages(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
        Set<String> current = readInstalledPackages(appContext);

        Set<String> known;
        synchronized (LOCK) {
            if (!prefs.contains(KEY_NEW_APP_KNOWN_PACKAGES)) {
                if (!prefs.edit()
                        .putStringSet(KEY_NEW_APP_KNOWN_PACKAGES, new HashSet<>(current))
                        .commit()) {
                    throw new IllegalStateException("Could not initialize new-app inventory");
                }
                return;
            }
            Set<String> stored = prefs.getStringSet(
                    KEY_NEW_APP_KNOWN_PACKAGES, Collections.emptySet());
            known = stored == null ? new HashSet<>() : new HashSet<>(stored);
        }

        Set<String> added = findNewPackages(known, current);
        Set<String> completed = new HashSet<>();
        for (String packageName : added) {
            try {
                NewAppSetupCoordinator.handlePackageAdded(appContext, packageName);
                completed.add(packageName);
            } catch (RuntimeException ignored) {
                // Leave the package absent from the inventory so the next reconciliation retries.
            }
        }

        synchronized (LOCK) {
            // Refresh the package snapshot while holding the same lock used by live-broadcast
            // inventory updates. This preserves packages recorded concurrently by the receiver
            // instead of overwriting them with the stale snapshot taken at reconciliation start.
            Set<String> latestCurrent = readInstalledPackages(appContext);
            Set<String> latest = prefs.getStringSet(
                    KEY_NEW_APP_KNOWN_PACKAGES, Collections.emptySet());
            Set<String> updated = mergeKnownAfterPass(
                    latest, known, completed, latestCurrent);
            if (!prefs.edit().putStringSet(KEY_NEW_APP_KNOWN_PACKAGES, updated).commit()) {
                throw new IllegalStateException("Could not update new-app inventory");
            }
        }
    }

    static Set<String> findNewPackages(Set<String> known, Set<String> current) {
        Set<String> result = new HashSet<>(current);
        result.removeAll(known);
        return result;
    }

    /**
     * The inventory update is a commutative union constrained to currently installed packages.
     * Keep a successfully processed install and any concurrent live-receiver observations;
     * leave failed packages absent so the next reconciliation retries them. Do not mutate inputs.
     */
    static Set<String> mergeKnownAfterPass(Set<String> latestRecorded,
            Set<String> knownAtStart, Set<String> completed, Set<String> installedNow) {
        Set<String> result = latestRecorded == null
                ? new HashSet<>() : new HashSet<>(latestRecorded);
        if (knownAtStart != null) result.addAll(knownAtStart);
        if (completed != null) result.addAll(completed);
        result.retainAll(installedNow);
        return result;
    }

    public static void recordInstalledPackage(Context context, String packageName) {
        if (!PackageNameValidator.isValid(packageName)) return;
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
        synchronized (LOCK) {
            Set<String> stored = prefs.getStringSet(
                    KEY_NEW_APP_KNOWN_PACKAGES, Collections.emptySet());
            Set<String> known = stored == null ? new HashSet<>() : new HashSet<>(stored);
            known.add(packageName);
            if (!prefs.edit().putStringSet(KEY_NEW_APP_KNOWN_PACKAGES, known).commit()) {
                throw new IllegalStateException("Could not record installed package");
            }
        }
    }

    public static void schedulePeriodic(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                NewAppInstallReconcileWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancelPeriodic(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(PERIODIC_WORK);
    }

    private static Set<String> readInstalledPackages(Context context) {
        Set<String> result = new HashSet<>();
        for (ApplicationInfo info : context.getPackageManager().getInstalledApplications(0)) {
            if (info != null && PackageNameValidator.isValid(info.packageName)) {
                result.add(info.packageName);
            }
        }
        return result;
    }
}
