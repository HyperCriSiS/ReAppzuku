package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_NEW_APP_DISCOVERY_SNAPSHOT;
import static com.gree1d.reappzuku.core.PreferenceKeys.PREFERENCES_NAME;

/**
 * Durable new-install discovery for target SDKs where PACKAGE_ADDED cannot wake a
 * manifest receiver. firstInstallTime distinguishes uninstall/reinstall of the
 * same package name from an ordinary app update.
 */
public final class NewAppDiscoveryWorker extends Worker {
    static final String UNIQUE_WORK_NAME = "NewAppDiscoveryPeriodic";
    private static final Object SNAPSHOT_LOCK = new Object();
    private static final char SEPARATOR = '|';

    public NewAppDiscoveryWorker(
            @NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void schedulePeriodic(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                NewAppDiscoveryWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            reconcileNow(getApplicationContext());
            return Result.success();
        } catch (RuntimeException e) {
            return Result.retry();
        }
    }

    public static void reconcileNow(Context context) {
        Context appContext = context.getApplicationContext();
        Set<String> currentSnapshot = collectInstalledSnapshot(appContext);
        SharedPreferences prefs = appContext.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);

        synchronized (SNAPSHOT_LOCK) {
            if (!prefs.contains(KEY_NEW_APP_DISCOVERY_SNAPSHOT)) {
                requireSnapshotCommit(prefs, currentSnapshot);
                return;
            }

            Map<String, Long> previous = decodeSnapshot(
                    prefs.getStringSet(
                            KEY_NEW_APP_DISCOVERY_SNAPSHOT, Collections.emptySet()));
            Set<String> nextSnapshot = new HashSet<>();

            for (String encoded : currentSnapshot) {
                InstallIdentity current = decodeIdentity(encoded);
                if (current == null) continue;

                Long previousInstallTime = previous.get(current.packageName);
                if (previousInstallTime != null
                        && previousInstallTime.longValue() == current.firstInstallTime) {
                    nextSnapshot.add(encoded);
                    continue;
                }

                try {
                    NewAppSetupCoordinator.handlePackageAdded(
                            appContext, current.packageName);
                    nextSnapshot.add(encoded);
                } catch (RuntimeException ignored) {
                    // Preserve the previous identity (or absence) so a later pass retries.
                    if (previousInstallTime != null) {
                        nextSnapshot.add(encode(
                                current.packageName, previousInstallTime));
                    }
                }
            }

            requireSnapshotCommit(prefs, nextSnapshot);
        }
    }

    public static void handleObservedPackageAdded(Context context, String packageName) {
        if (!PackageNameValidator.isValid(packageName)) return;
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);

        synchronized (SNAPSHOT_LOCK) {
            NewAppSetupCoordinator.handlePackageAdded(appContext, packageName);

            if (!prefs.contains(KEY_NEW_APP_DISCOVERY_SNAPSHOT)) {
                requireSnapshotCommit(prefs, collectInstalledSnapshot(appContext));
                return;
            }

            Long installTime = queryFirstInstallTime(appContext, packageName);
            if (installTime == null) return;

            Set<String> snapshot = new HashSet<>(
                    prefs.getStringSet(
                            KEY_NEW_APP_DISCOVERY_SNAPSHOT, Collections.emptySet()));
            removePackage(snapshot, packageName);
            snapshot.add(encode(packageName, installTime));
            requireSnapshotCommit(prefs, snapshot);
        }
    }

    static boolean hasSnapshot(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .contains(KEY_NEW_APP_DISCOVERY_SNAPSHOT);
    }

    static boolean isCurrentInstallKnown(Context context, String packageName) {
        Context appContext = context.getApplicationContext();
        Long current = queryFirstInstallTime(appContext, packageName);
        if (current == null) return false;
        SharedPreferences prefs = appContext.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
        Map<String, Long> snapshot = decodeSnapshot(
                prefs.getStringSet(
                        KEY_NEW_APP_DISCOVERY_SNAPSHOT, Collections.emptySet()));
        Long known = snapshot.get(packageName);
        return known != null && known.longValue() == current.longValue();
    }

    private static Set<String> collectInstalledSnapshot(Context context) {
        List<PackageInfo> installed = context.getPackageManager().getInstalledPackages(0);
        Set<String> snapshot = new HashSet<>();
        for (PackageInfo info : installed) {
            if (info == null || !PackageNameValidator.isValid(info.packageName)) continue;
            snapshot.add(encode(info.packageName, info.firstInstallTime));
        }
        return snapshot;
    }

    private static Long queryFirstInstallTime(Context context, String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.firstInstallTime;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static Map<String, Long> decodeSnapshot(Set<String> encodedSnapshot) {
        Map<String, Long> result = new HashMap<>();
        if (encodedSnapshot == null) return result;
        for (String encoded : encodedSnapshot) {
            InstallIdentity identity = decodeIdentity(encoded);
            if (identity != null) {
                result.put(identity.packageName, identity.firstInstallTime);
            }
        }
        return result;
    }

    private static InstallIdentity decodeIdentity(String encoded) {
        if (encoded == null) return null;
        int separator = encoded.lastIndexOf(SEPARATOR);
        if (separator <= 0 || separator == encoded.length() - 1) return null;
        String packageName = encoded.substring(0, separator);
        if (!PackageNameValidator.isValid(packageName)) return null;
        try {
            long firstInstallTime = Long.parseLong(encoded.substring(separator + 1));
            if (firstInstallTime < 0L) return null;
            return new InstallIdentity(packageName, firstInstallTime);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String encode(String packageName, long firstInstallTime) {
        return packageName + SEPARATOR + firstInstallTime;
    }

    private static void removePackage(Set<String> snapshot, String packageName) {
        snapshot.removeIf(encoded -> {
            InstallIdentity identity = decodeIdentity(encoded);
            return identity != null && packageName.equals(identity.packageName);
        });
    }

    private static void requireSnapshotCommit(
            SharedPreferences prefs, Set<String> snapshot) {
        if (!prefs.edit()
                .putStringSet(
                        KEY_NEW_APP_DISCOVERY_SNAPSHOT, new HashSet<>(snapshot))
                .commit()) {
            throw new IllegalStateException("new-app discovery snapshot commit failed");
        }
    }

    private static final class InstallIdentity {
        final String packageName;
        final long firstInstallTime;

        InstallIdentity(String packageName, long firstInstallTime) {
            this.packageName = packageName;
            this.firstInstallTime = firstInstallTime;
        }
    }
}
