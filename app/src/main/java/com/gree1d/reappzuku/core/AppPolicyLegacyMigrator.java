package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.AppPolicyDao;
import com.gree1d.reappzuku.manager.SmartLifecycleManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_APP_LAUNCH_TRIGGER_ENABLED;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_APP_POLICY_MIGRATION_VERSION;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_AUTO_KILL_ENABLED;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_AUTO_KILL_TYPE;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_AUTOSTART_DISABLED_APPS;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_BLACKLISTED_APPS;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_HARD_RESTRICTION_APPS;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_HIDDEN_APPS;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_HW_TRIGGER_BLUETOOTH;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_HW_TRIGGER_CHARGER;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_HW_TRIGGER_GPS;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_HW_TRIGGER_HEADSET;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_HW_TRIGGER_HOTSPOT;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_HW_TRIGGER_USB;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_HW_TRIGGER_WIFI;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_KILL_MODE;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_KILL_ON_SCREEN_OFF;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_MANUAL_RESTRICTION_APPS;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_MEDIUM_RESTRICTION_APPS;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_PERIODIC_KILL_ENABLED;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_RAM_THRESHOLD_ENABLED;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_SLEEP_MODE_APPS;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_SLEEP_MODE_APPS_FROZEN;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_SLEEP_MODE_APPS_PERMANENT;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_SLEEP_MODE_ENABLED;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_SMART_BOOT_CLEANUP_ENABLED;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_SMART_LIFECYCLE_ENABLED;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_WHITELISTED_APPS;
import static com.gree1d.reappzuku.core.PreferenceKeys.PREFERENCES_NAME;

/**
 * One-time bridge from the legacy list/global settings model to explicit per-app policies.
 *
 * The bridge deliberately does not remove or rewrite any legacy preference. Runtime ownership
 * remains on the old managers until the later execution-routing block is proven. Existing
 * explicit policies are never overwritten, which also makes a retry after process death safe.
 */
public final class AppPolicyLegacyMigrator {
    static final int CURRENT_MIGRATION_VERSION = 1;
    private static final long MINUTE_MS = 60_000L;

    private AppPolicyLegacyMigrator() {}

    public static boolean migrateIfNeeded(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs =
                appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

        try {
            AppDatabase db = AppDatabase.getInstance(appContext);
            PolicyPresetSeeder.seedBuiltIns(db);

            if (prefs.getInt(KEY_APP_POLICY_MIGRATION_VERSION, 0) >= CURRENT_MIGRATION_VERSION) {
                return true;
            }

            LegacySnapshot legacy = readSnapshot(prefs);
            PackageInventory inventory = collectPackageInventory(appContext);

            Set<String> existingPackages = new HashSet<>();
            for (AppPolicy policy : db.appPolicyDao().getAll()) {
                if (policy != null && policy.packageName != null) {
                    existingPackages.add(policy.packageName);
                }
            }

            List<AppPolicy> planned = planPolicies(
                    legacy,
                    inventory.eligibleInstalledPackages,
                    inventory.failSafePackages,
                    existingPackages,
                    System.currentTimeMillis());

            AppPolicyDao policyDao = db.appPolicyDao();
            db.runInTransaction(() -> policyDao.insertAllIgnore(planned));

            // Commit only after the Room transaction. If this write fails, the next process start
            // safely retries and INSERT IGNORE preserves any rows already committed.
            return prefs.edit()
                    .putInt(KEY_APP_POLICY_MIGRATION_VERSION, CURRENT_MIGRATION_VERSION)
                    .commit();
        } catch (RuntimeException ignored) {
            // Do not mark a failed migration complete. Existing legacy execution remains intact.
            return false;
        }
    }

    static List<AppPolicy> planPolicies(
            LegacySnapshot legacy,
            Set<String> eligibleInstalledPackages,
            Set<String> failSafePackages,
            Set<String> existingPolicyPackages,
            long now) {
        if (legacy == null) return Collections.emptyList();

        Set<String> eligibleInstalled = safeSet(eligibleInstalledPackages);
        Set<String> failSafe = safeSet(failSafePackages);
        Set<String> existing = safeSet(existingPolicyPackages);

        TreeSet<String> candidates = new TreeSet<>();
        candidates.addAll(legacy.whitelistedApps);
        candidates.addAll(legacy.blacklistedApps);
        candidates.addAll(legacy.sleepTimerApps);
        candidates.addAll(legacy.sleepPermanentApps);
        candidates.addAll(legacy.sleepFrozenTimerApps);
        candidates.addAll(legacy.backgroundRestrictedApps);
        candidates.addAll(legacy.mediumRestrictedApps);
        candidates.addAll(legacy.hardRestrictedApps);
        candidates.addAll(legacy.manualRestrictedApps);

        // Whitelist mode historically targets every installed non-protected app that is not
        // explicitly whitelisted. Materialize that effective set now so the later policy engine
        // does not need to reinterpret a global inverse list.
        if (legacy.autoKillEnabled && legacy.whitelistMode) {
            candidates.addAll(eligibleInstalled);
        }

        List<AppPolicy> result = new ArrayList<>();
        for (String packageName : candidates) {
            if (!PackageNameValidator.isValid(packageName) || existing.contains(packageName)) {
                continue;
            }

            AppPolicy policy = new AppPolicy(packageName);
            policy.strategy = resolveMigratedStrategy(
                    packageName, legacy, eligibleInstalled, failSafe);
            policy.presetId = null;
            policy.customized = true;
            policy.standbyDelayMs = legacy.smartStandbyDelayMs;
            policy.forceStopDelayMs = legacy.smartForceStopDelayMs;
            policy.killMethod = legacy.killMethod;
            policy.bootCleanup = legacy.smartBootCleanup;
            policy.backgroundRestriction = resolveBackgroundRestriction(packageName, legacy);
            policy.protectMedia = true;
            policy.protectForegroundServices = true;
            policy.protectWidgets = true;
            policy.triggerMask = resolveTriggerMask(policy.strategy, legacy);
            policy.createdAt = now;
            policy.updatedAt = now;
            result.add(policy);
        }
        return result;
    }

    static int resolveMigratedStrategy(
            String packageName,
            LegacySnapshot legacy,
            Set<String> eligibleInstalledPackages,
            Set<String> failSafePackages) {
        if (failSafePackages.contains(packageName)) {
            return AppPolicy.STRATEGY_PROTECTED;
        }

        boolean sleepOwnsPackage =
                legacy.sleepPermanentApps.contains(packageName)
                        || legacy.sleepFrozenTimerApps.contains(packageName)
                        || (legacy.sleepModeEnabled && legacy.sleepTimerApps.contains(packageName));
        if (sleepOwnsPackage) {
            return AppPolicy.STRATEGY_PROTECTED;
        }

        // Smart Lifecycle historically consumes the blacklist independently of Auto-Kill's
        // whitelist/blacklist mode. Give it ownership first to remove dual-engine control.
        if (legacy.smartLifecycleEnabled && legacy.blacklistedApps.contains(packageName)) {
            return AppPolicy.STRATEGY_SMART;
        }

        if (!legacy.autoKillEnabled) {
            return AppPolicy.STRATEGY_UNMANAGED;
        }

        if (legacy.whitelistMode) {
            if (legacy.hiddenApps.contains(packageName)
                    || legacy.whitelistedApps.contains(packageName)) {
                return AppPolicy.STRATEGY_PROTECTED;
            }
            return eligibleInstalledPackages.contains(packageName)
                    ? AppPolicy.STRATEGY_IMMEDIATE
                    : AppPolicy.STRATEGY_UNMANAGED;
        }

        if (legacy.hiddenApps.contains(packageName)) {
            return AppPolicy.STRATEGY_PROTECTED;
        }

        return legacy.blacklistedApps.contains(packageName)
                ? AppPolicy.STRATEGY_IMMEDIATE
                : AppPolicy.STRATEGY_UNMANAGED;
    }

    static int resolveBackgroundRestriction(String packageName, LegacySnapshot legacy) {
        if (legacy.manualRestrictedApps.contains(packageName)) {
            return AppPolicy.RESTRICTION_MANUAL;
        }
        if (legacy.hardRestrictedApps.contains(packageName)) {
            return AppPolicy.RESTRICTION_HARD;
        }
        if (legacy.mediumRestrictedApps.contains(packageName)) {
            return AppPolicy.RESTRICTION_MEDIUM;
        }
        if (legacy.backgroundRestrictedApps.contains(packageName)) {
            return AppPolicy.RESTRICTION_SOFT;
        }
        return AppPolicy.RESTRICTION_NONE;
    }

    static long resolveTriggerMask(int strategy, LegacySnapshot legacy) {
        if (strategy == AppPolicy.STRATEGY_SMART) {
            return legacy.smartBootCleanup ? AppPolicy.TRIGGER_BOOT_CLEANUP : 0L;
        }
        if (strategy == AppPolicy.STRATEGY_IMMEDIATE) {
            return legacy.immediateTriggerMask;
        }
        return 0L;
    }

    private static LegacySnapshot readSnapshot(SharedPreferences prefs) {
        LegacySnapshot snapshot = new LegacySnapshot();
        snapshot.autoKillEnabled = prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false);
        snapshot.smartLifecycleEnabled = prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false);
        snapshot.sleepModeEnabled = prefs.getBoolean(KEY_SLEEP_MODE_ENABLED, false);
        snapshot.whitelistMode = prefs.getInt(KEY_KILL_MODE, 1) == 0;
        snapshot.killMethod = prefs.getInt(KEY_AUTO_KILL_TYPE, 0) == 1
                ? AppPolicy.KILL_METHOD_AM_KILL
                : AppPolicy.KILL_METHOD_FORCE_STOP;
        snapshot.smartStandbyDelayMs =
                SmartLifecycleManager.getStandbyDelayMinutes(prefs) * MINUTE_MS;
        snapshot.smartForceStopDelayMs =
                SmartLifecycleManager.getForceStopDelayMinutes(prefs) * MINUTE_MS;
        snapshot.smartBootCleanup = prefs.getBoolean(KEY_SMART_BOOT_CLEANUP_ENABLED, true);

        long immediateTriggers = 0L;
        if (prefs.getBoolean(KEY_PERIODIC_KILL_ENABLED, false)) {
            immediateTriggers |= AppPolicy.TRIGGER_PERIODIC;
        }
        if (prefs.getBoolean(KEY_KILL_ON_SCREEN_OFF, false)) {
            immediateTriggers |= AppPolicy.TRIGGER_SCREEN_OFF;
        }
        if (prefs.getBoolean(KEY_RAM_THRESHOLD_ENABLED, false)) {
            immediateTriggers |= AppPolicy.TRIGGER_RAM_THRESHOLD;
        }
        if (hasHardwareTrigger(prefs)) {
            immediateTriggers |= AppPolicy.TRIGGER_HARDWARE_EVENT;
        }
        if (prefs.getBoolean(KEY_APP_LAUNCH_TRIGGER_ENABLED, false)) {
            immediateTriggers |= AppPolicy.TRIGGER_APP_LAUNCH;
        }
        snapshot.immediateTriggerMask = immediateTriggers;

        snapshot.hiddenApps = readSet(prefs, KEY_HIDDEN_APPS);
        snapshot.whitelistedApps = readSet(prefs, KEY_WHITELISTED_APPS);
        snapshot.blacklistedApps = readSet(prefs, KEY_BLACKLISTED_APPS);
        snapshot.sleepTimerApps = readSet(prefs, KEY_SLEEP_MODE_APPS);
        snapshot.sleepPermanentApps = readSet(prefs, KEY_SLEEP_MODE_APPS_PERMANENT);
        snapshot.sleepFrozenTimerApps = readSet(prefs, KEY_SLEEP_MODE_APPS_FROZEN);
        snapshot.backgroundRestrictedApps = readSet(prefs, KEY_AUTOSTART_DISABLED_APPS);
        snapshot.mediumRestrictedApps = readSet(prefs, KEY_MEDIUM_RESTRICTION_APPS);
        snapshot.hardRestrictedApps = readSet(prefs, KEY_HARD_RESTRICTION_APPS);
        snapshot.manualRestrictedApps = readSet(prefs, KEY_MANUAL_RESTRICTION_APPS);
        return snapshot;
    }

    private static boolean hasHardwareTrigger(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_HW_TRIGGER_HEADSET, false)
                || prefs.getBoolean(KEY_HW_TRIGGER_USB, false)
                || prefs.getBoolean(KEY_HW_TRIGGER_CHARGER, false)
                || prefs.getBoolean(KEY_HW_TRIGGER_WIFI, false)
                || prefs.getBoolean(KEY_HW_TRIGGER_BLUETOOTH, false)
                || prefs.getBoolean(KEY_HW_TRIGGER_GPS, false)
                || prefs.getBoolean(KEY_HW_TRIGGER_HOTSPOT, false);
    }

    private static Set<String> readSet(SharedPreferences prefs, String key) {
        return new HashSet<>(prefs.getStringSet(key, Collections.emptySet()));
    }

    private static Set<String> safeSet(Set<String> source) {
        return source == null ? Collections.emptySet() : source;
    }

    private static PackageInventory collectPackageInventory(Context context) {
        Set<String> eligible = new HashSet<>();
        Set<String> failSafe = new HashSet<>();
        PackageManager packageManager = context.getPackageManager();

        List<ApplicationInfo> apps;
        try {
            apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        } catch (RuntimeException ignored) {
            apps = Collections.emptyList();
        }

        for (ApplicationInfo info : apps) {
            if (info == null || !PackageNameValidator.isValid(info.packageName)) continue;
            if (context.getPackageName().equals(info.packageName)) continue;

            boolean persistent = (info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
            boolean protectedPackage = ProtectedApps.isProtected(context, info.packageName);
            if (persistent || protectedPackage) {
                failSafe.add(info.packageName);
            } else {
                eligible.add(info.packageName);
            }
        }
        return new PackageInventory(eligible, failSafe);
    }

    static final class LegacySnapshot {
        boolean autoKillEnabled;
        boolean smartLifecycleEnabled;
        boolean sleepModeEnabled;
        boolean whitelistMode;
        int killMethod = AppPolicy.KILL_METHOD_FORCE_STOP;
        long smartStandbyDelayMs = AppPolicy.DEFAULT_SMART_STANDBY_DELAY_MS;
        long smartForceStopDelayMs = AppPolicy.DEFAULT_SMART_FORCE_STOP_DELAY_MS;
        boolean smartBootCleanup = true;
        long immediateTriggerMask;

        Set<String> hiddenApps = Collections.emptySet();
        Set<String> whitelistedApps = Collections.emptySet();
        Set<String> blacklistedApps = Collections.emptySet();
        Set<String> sleepTimerApps = Collections.emptySet();
        Set<String> sleepPermanentApps = Collections.emptySet();
        Set<String> sleepFrozenTimerApps = Collections.emptySet();
        Set<String> backgroundRestrictedApps = Collections.emptySet();
        Set<String> mediumRestrictedApps = Collections.emptySet();
        Set<String> hardRestrictedApps = Collections.emptySet();
        Set<String> manualRestrictedApps = Collections.emptySet();
    }

    private static final class PackageInventory {
        final Set<String> eligibleInstalledPackages;
        final Set<String> failSafePackages;

        PackageInventory(Set<String> eligibleInstalledPackages, Set<String> failSafePackages) {
            this.eligibleInstalledPackages = eligibleInstalledPackages;
            this.failSafePackages = failSafePackages;
        }
    }
}
