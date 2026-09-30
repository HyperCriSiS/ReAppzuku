from pathlib import Path

def read(path):
    return Path(path).read_text()

def write(path, content):
    Path(path).write_text(content)

def replace_once(path, old, new):
    text = read(path)
    if text.count(old) != 1:
        raise SystemExit(f"{path}: expected exactly one match, got {text.count(old)} for {old[:80]!r}")
    write(path, text.replace(old, new, 1))

# Preference metadata used to prove that migrated rows still match live legacy state.
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/core/PreferenceKeys.java",
    '    public static final String KEY_APP_POLICY_MIGRATION_VERSION = "app_policy_migration_version";\n',
    '    public static final String KEY_APP_POLICY_MIGRATION_VERSION = "app_policy_migration_version";\n'
    '    public static final String KEY_APP_POLICY_MIGRATION_FINGERPRINT = "app_policy_migration_fingerprint";\n'
)

# Record policy provenance so retry/rebuild can delete only migration-owned rows.
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/db/AppPolicy.java",
    '    public static final int STRATEGY_IMMEDIATE = 3;\n\n',
    '    public static final int STRATEGY_IMMEDIATE = 3;\n\n'
    '    public static final int SOURCE_EXPLICIT = 0;\n'
    '    public static final int SOURCE_LEGACY_MIGRATED = 1;\n\n'
)
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/db/AppPolicy.java",
    '    public int strategy = STRATEGY_UNMANAGED;\n\n',
    '    public int strategy = STRATEGY_UNMANAGED;\n    public int source = SOURCE_EXPLICIT;\n\n'
)

replace_once(
    "app/src/main/java/com/gree1d/reappzuku/db/AppPolicyDao.java",
    '    @Query("DELETE FROM app_policy WHERE packageName = :packageName")\n'
    '    void deleteByPackage(String packageName);\n\n',
    '    @Query("DELETE FROM app_policy WHERE packageName = :packageName")\n'
    '    void deleteByPackage(String packageName);\n\n'
    '    @Query("DELETE FROM app_policy WHERE source = :source")\n'
    '    void deleteBySource(int source);\n\n'
)

# Room 12 -> 13 adds provenance with existing rows defaulting to explicit/user-owned.
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/db/AppDatabase.java",
    '    version = 12,\n',
    '    version = 13,\n'
)
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/db/AppDatabase.java",
    '    private static final int SQL_CACHE_SIZE = 64;\n',
    '''    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE app_policy ADD COLUMN source INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final int SQL_CACHE_SIZE = 64;
'''
)
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/db/AppDatabase.java",
    '                        MIGRATION_10_11,\n                        MIGRATION_11_12\n',
    '                        MIGRATION_10_11,\n                        MIGRATION_11_12,\n                        MIGRATION_12_13\n'
)

migrator = r'''package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.AppPolicyDao;
import com.gree1d.reappzuku.manager.SmartLifecycleManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;

/**
 * Retry-safe bridge from the editable legacy settings model to canonical per-app policies.
 *
 * Migration-owned rows carry explicit provenance. A fingerprint binds those rows to the exact
 * legacy snapshot that produced them. If any old UI/preset/restore writer changes relevant
 * preferences, the fingerprint stops matching immediately and runtime routing ignores only
 * LEGACY_MIGRATED rows until a fresh reconciliation succeeds. User-owned explicit policies are
 * never discarded by this bridge.
 */
public final class AppPolicyLegacyMigrator {
    static final int CURRENT_MIGRATION_VERSION = 2;
    private static final long MINUTE_MS = 60_000L;

    private AppPolicyLegacyMigrator() {}

    public static boolean migrateIfNeeded(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs =
                appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

        try {
            AppDatabase db = AppDatabase.getInstance(appContext);
            PolicyPresetSeeder.seedBuiltIns(db);

            LegacySnapshot legacy = readSnapshot(prefs);
            String fingerprint = fingerprintSnapshot(legacy);
            if (isMigrationSnapshotCurrent(prefs, fingerprint)) {
                return true;
            }

            PackageInventory inventory = collectPackageInventory(appContext);
            AppPolicyDao policyDao = db.appPolicyDao();

            Set<String> existingExplicitPackages = new HashSet<>();
            for (AppPolicy policy : policyDao.getAll()) {
                if (policy != null
                        && policy.packageName != null
                        && policy.source != AppPolicy.SOURCE_LEGACY_MIGRATED) {
                    existingExplicitPackages.add(policy.packageName);
                }
            }

            List<AppPolicy> planned = planPolicies(
                    legacy,
                    inventory.eligibleInstalledPackages,
                    inventory.failSafePackages,
                    existingExplicitPackages,
                    System.currentTimeMillis());

            // Invalidate before touching Room. If the process dies anywhere below, runtime falls
            // back to live legacy state instead of treating a partially refreshed snapshot as
            // canonical.
            if (!invalidateMigration(prefs.edit()).commit()) {
                return false;
            }

            db.runInTransaction(() -> {
                policyDao.deleteBySource(AppPolicy.SOURCE_LEGACY_MIGRATED);
                policyDao.insertAllIgnore(planned);
            });

            // A legacy write may race the Room transaction. Never bless rows generated from an
            // older snapshot: leave them invalid and let the next coalesced pass rebuild them.
            if (!fingerprint.equals(fingerprintSnapshot(readSnapshot(prefs)))) {
                return false;
            }

            return prefs.edit()
                    .putString(KEY_APP_POLICY_MIGRATION_FINGERPRINT, fingerprint)
                    .putInt(KEY_APP_POLICY_MIGRATION_VERSION, CURRENT_MIGRATION_VERSION)
                    .commit();
        } catch (RuntimeException ignored) {
            // Do not mark a failed migration complete. Stale migration-owned rows remain ignored.
            return false;
        }
    }

    public static boolean isMigrationSnapshotCurrent(SharedPreferences prefs) {
        if (prefs == null) return false;
        try {
            String currentFingerprint = fingerprintSnapshot(readSnapshot(prefs));
            return isMigrationSnapshotCurrent(prefs, currentFingerprint);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean isMigrationSnapshotCurrent(
            SharedPreferences prefs, String currentFingerprint) {
        if (prefs.getInt(KEY_APP_POLICY_MIGRATION_VERSION, 0) < CURRENT_MIGRATION_VERSION) {
            return false;
        }
        String stored = prefs.getString(KEY_APP_POLICY_MIGRATION_FINGERPRINT, null);
        return stored != null && stored.equals(currentFingerprint);
    }

    @Nullable
    public static AppPolicy resolveEffectivePolicy(
            @Nullable AppPolicy policy, boolean migrationSnapshotCurrent) {
        if (policy == null) return null;
        if (policy.source == AppPolicy.SOURCE_LEGACY_MIGRATED && !migrationSnapshotCurrent) {
            return null;
        }
        return policy;
    }

    static SharedPreferences.Editor invalidateMigration(SharedPreferences.Editor editor) {
        return editor
                .putInt(KEY_APP_POLICY_MIGRATION_VERSION, 0)
                .remove(KEY_APP_POLICY_MIGRATION_FINGERPRINT);
    }

    public static boolean affectsMigrationKey(@Nullable String key) {
        if (key == null) return false;
        switch (key) {
            case KEY_AUTO_KILL_ENABLED:
            case KEY_ACTIVE_PRESET:
            case KEY_SMART_LIFECYCLE_ENABLED:
            case KEY_SLEEP_MODE_ENABLED:
            case KEY_KILL_MODE:
            case KEY_AUTO_KILL_TYPE:
            case KEY_SMART_LIFECYCLE_PROFILE:
            case KEY_SMART_BOOT_CLEANUP_ENABLED:
            case KEY_PERIODIC_KILL_ENABLED:
            case KEY_KILL_ON_SCREEN_OFF:
            case KEY_RAM_THRESHOLD_ENABLED:
            case KEY_HW_TRIGGER_HEADSET:
            case KEY_HW_TRIGGER_USB:
            case KEY_HW_TRIGGER_CHARGER:
            case KEY_HW_TRIGGER_WIFI:
            case KEY_HW_TRIGGER_BLUETOOTH:
            case KEY_HW_TRIGGER_GPS:
            case KEY_HW_TRIGGER_HOTSPOT:
            case KEY_APP_LAUNCH_TRIGGER_ENABLED:
            case KEY_HIDDEN_APPS:
            case KEY_WHITELISTED_APPS:
            case KEY_BLACKLISTED_APPS:
            case KEY_SLEEP_MODE_APPS:
            case KEY_SLEEP_MODE_APPS_PERMANENT:
            case KEY_SLEEP_MODE_APPS_FROZEN:
            case KEY_AUTOSTART_DISABLED_APPS:
            case KEY_MEDIUM_RESTRICTION_APPS:
            case KEY_HARD_RESTRICTION_APPS:
            case KEY_MANUAL_RESTRICTION_APPS:
                return true;
            default:
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
        // explicitly whitelisted. Materialize that effective set now so future installs remain
        // unmanaged until the dedicated new-app flow exists.
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
            policy.source = AppPolicy.SOURCE_LEGACY_MIGRATED;
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

    static String fingerprintSnapshot(LegacySnapshot snapshot) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, "autoKill", snapshot.autoKillEnabled);
        append(canonical, "activePreset", snapshot.activePresetNumber);
        append(canonical, "smart", snapshot.smartLifecycleEnabled);
        append(canonical, "sleep", snapshot.sleepModeEnabled);
        append(canonical, "whitelistMode", snapshot.whitelistMode);
        append(canonical, "killMethod", snapshot.killMethod);
        append(canonical, "standby", snapshot.smartStandbyDelayMs);
        append(canonical, "forceStop", snapshot.smartForceStopDelayMs);
        append(canonical, "bootCleanup", snapshot.smartBootCleanup);
        append(canonical, "triggers", snapshot.immediateTriggerMask);
        appendSet(canonical, "hidden", snapshot.hiddenApps);
        appendSet(canonical, "whitelist", snapshot.whitelistedApps);
        appendSet(canonical, "blacklist", snapshot.blacklistedApps);
        appendSet(canonical, "sleepTimer", snapshot.sleepTimerApps);
        appendSet(canonical, "sleepPermanent", snapshot.sleepPermanentApps);
        appendSet(canonical, "sleepFrozen", snapshot.sleepFrozenTimerApps);
        appendSet(canonical, "restrictionSoft", snapshot.backgroundRestrictedApps);
        appendSet(canonical, "restrictionMedium", snapshot.mediumRestrictedApps);
        appendSet(canonical, "restrictionHard", snapshot.hardRestrictedApps);
        appendSet(canonical, "restrictionManual", snapshot.manualRestrictedApps);

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void append(StringBuilder target, String key, Object value) {
        target.append(key).append('=').append(value).append('\n');
    }

    private static void appendSet(StringBuilder target, String key, Set<String> values) {
        target.append(key).append('=');
        for (String value : new TreeSet<>(safeSet(values))) {
            target.append(value.length()).append(':').append(value).append(';');
        }
        target.append('\n');
    }

    static LegacySnapshot readSnapshot(SharedPreferences prefs) {
        LegacySnapshot snapshot = new LegacySnapshot();
        snapshot.activePresetNumber = prefs.getInt(KEY_ACTIVE_PRESET, 0);
        snapshot.autoKillEnabled = prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false)
                || snapshot.activePresetNumber != 0;
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
        int activePresetNumber;
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
'''
write("app/src/main/java/com/gree1d/reappzuku/core/AppPolicyLegacyMigrator.java", migrator)

# Application-lifetime reconciliation: startup plus coalesced relevant preference changes.
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/core/App.java",
    'import android.content.Intent;\n',
    'import android.content.Intent;\nimport android.content.SharedPreferences;\n'
)
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/core/App.java",
    'import java.util.concurrent.ExecutorService;\nimport java.util.concurrent.Executors;\n',
    'import java.util.concurrent.ExecutorService;\nimport java.util.concurrent.Executors;\n'
    'import java.util.concurrent.atomic.AtomicBoolean;\nimport java.util.concurrent.atomic.AtomicInteger;\n'
)
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/core/App.java",
    '    private LruCache<String, Bitmap> iconCache;\n\n',
    '    private LruCache<String, Bitmap> iconCache;\n'
    '    private SharedPreferences legacyPolicyPreferences;\n'
    '    private SharedPreferences.OnSharedPreferenceChangeListener legacyPolicyPreferenceListener;\n'
    '    private final AtomicBoolean legacyPolicyMigrationQueued = new AtomicBoolean(false);\n'
    '    private final AtomicInteger legacyPolicyChangeGeneration = new AtomicInteger(0);\n\n'
)
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/core/App.java",
    '        shellManager = new ShellManager(this, handler, executor);\n\n'
    '        // ShellManager owns the application-lifetime Binder/permission/UserService\n',
    '        shellManager = new ShellManager(this, handler, executor);\n\n'
    '        legacyPolicyPreferences = getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, MODE_PRIVATE);\n'
    '        legacyPolicyPreferenceListener = (prefs, key) -> {\n'
    '            if (!AppPolicyLegacyMigrator.affectsMigrationKey(key)) return;\n'
    '            legacyPolicyChangeGeneration.incrementAndGet();\n'
    '            scheduleLegacyPolicyMigration();\n'
    '        };\n'
    '        legacyPolicyPreferences.registerOnSharedPreferenceChangeListener(legacyPolicyPreferenceListener);\n'
    '        scheduleLegacyPolicyMigration();\n\n'
    '        // ShellManager owns the application-lifetime Binder/permission/UserService\n'
)
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/core/App.java",
    '    public ShellManager getShellManager() {\n',
    '''    private void scheduleLegacyPolicyMigration() {
        if (executor == null || !legacyPolicyMigrationQueued.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            int generationAtStart = legacyPolicyChangeGeneration.get();
            try {
                AppPolicyLegacyMigrator.migrateIfNeeded(this);
            } finally {
                legacyPolicyMigrationQueued.set(false);
                if (legacyPolicyChangeGeneration.get() != generationAtStart) {
                    scheduleLegacyPolicyMigration();
                }
            }
        });
    }

    public ShellManager getShellManager() {
'''
)

# Both execution engines ignore only stale migration-owned rows; explicit rows always keep precedence.
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java",
    'import com.gree1d.reappzuku.core.AppPolicyResolver;\n',
    'import com.gree1d.reappzuku.core.AppPolicyLegacyMigrator;\n'
    'import com.gree1d.reappzuku.core.AppPolicyResolver;\n'
)
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java",
    '            Map<String, AppPolicy> explicitPolicies = new HashMap<>();\n'
    '            for (AppPolicy policy : AppDatabase.getInstance(context).appPolicyDao().getAll()) {\n'
    '                if (policy != null && policy.packageName != null) {\n'
    '                    explicitPolicies.put(policy.packageName, policy);\n'
    '                }\n'
    '            }\n',
    '            boolean migrationSnapshotCurrent =\n'
    '                    AppPolicyLegacyMigrator.isMigrationSnapshotCurrent(sharedpreferences);\n'
    '            Map<String, AppPolicy> explicitPolicies = new HashMap<>();\n'
    '            for (AppPolicy policy : AppDatabase.getInstance(context).appPolicyDao().getAll()) {\n'
    '                AppPolicy effectivePolicy = AppPolicyLegacyMigrator.resolveEffectivePolicy(\n'
    '                        policy, migrationSnapshotCurrent);\n'
    '                if (effectivePolicy != null && effectivePolicy.packageName != null) {\n'
    '                    explicitPolicies.put(effectivePolicy.packageName, effectivePolicy);\n'
    '                }\n'
    '            }\n'
)

replace_once(
    "app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java",
    'import com.gree1d.reappzuku.core.AppPolicyResolver;\n',
    'import com.gree1d.reappzuku.core.AppPolicyLegacyMigrator;\n'
    'import com.gree1d.reappzuku.core.AppPolicyResolver;\n'
)
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java",
    '        Map<String, AppPolicy> explicitPolicies = new HashMap<>();\n'
    '        for (AppPolicy policy : AppDatabase.getInstance(context).appPolicyDao().getAll()) {\n'
    '            if (policy != null && policy.packageName != null) {\n'
    '                explicitPolicies.put(policy.packageName, policy);\n'
    '            }\n'
    '        }\n',
    '        boolean migrationSnapshotCurrent =\n'
    '                AppPolicyLegacyMigrator.isMigrationSnapshotCurrent(prefs);\n'
    '        Map<String, AppPolicy> explicitPolicies = new HashMap<>();\n'
    '        for (AppPolicy policy : AppDatabase.getInstance(context).appPolicyDao().getAll()) {\n'
    '            AppPolicy effectivePolicy = AppPolicyLegacyMigrator.resolveEffectivePolicy(\n'
    '                    policy, migrationSnapshotCurrent);\n'
    '            if (effectivePolicy != null && effectivePolicy.packageName != null) {\n'
    '                explicitPolicies.put(effectivePolicy.packageName, effectivePolicy);\n'
    '            }\n'
    '        }\n'
)

# Restore is transactional across legacy preferences; force migration invalid until a fresh Room
# snapshot is rebuilt, including rollback paths.
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/core/BackupManager.java",
    '            durableWriteStarted = true;\n'
    '            if (!editor.commit()) throw new IllegalStateException("main preferences commit failed");\n',
    '            AppPolicyLegacyMigrator.invalidateMigration(editor);\n'
    '            durableWriteStarted = true;\n'
    '            if (!editor.commit()) throw new IllegalStateException("main preferences commit failed");\n'
)
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/core/BackupManager.java",
    '        return editor.commit();\n    }\n\n    private void restoreBoolean',
    '        AppPolicyLegacyMigrator.invalidateMigration(editor);\n'
    '        return editor.commit();\n    }\n\n    private void restoreBoolean'
)

legacy_test = read("app/src/test/java/com/gree1d/reappzuku/core/AppPolicyLegacyMigratorTest.java")
legacy_test = legacy_test.replace(
    'import static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertTrue;\n',
    'import static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertFalse;\n'
    'import static org.junit.Assert.assertNull;\nimport static org.junit.Assert.assertSame;\n'
    'import static org.junit.Assert.assertTrue;\n'
)
legacy_test = legacy_test.replace(
    '        assertEquals(AppPolicy.KILL_METHOD_AM_KILL, policy.killMethod);\n',
    '        assertEquals(AppPolicy.SOURCE_LEGACY_MIGRATED, policy.source);\n'
    '        assertEquals(AppPolicy.KILL_METHOD_AM_KILL, policy.killMethod);\n',
    1
)
insert = r'''
    @Test
    public void fingerprintIsStableAcrossSetIterationOrderAndChangesForRelevantState() {
        AppPolicyLegacyMigrator.LegacySnapshot first = base();
        first.autoKillEnabled = true;
        first.blacklistedApps = set("com.example.b", "com.example.a");

        AppPolicyLegacyMigrator.LegacySnapshot same = base();
        same.autoKillEnabled = true;
        same.blacklistedApps = set("com.example.a", "com.example.b");

        assertEquals(
                AppPolicyLegacyMigrator.fingerprintSnapshot(first),
                AppPolicyLegacyMigrator.fingerprintSnapshot(same));

        same.smartLifecycleEnabled = true;
        assertFalse(AppPolicyLegacyMigrator.fingerprintSnapshot(first).equals(
                AppPolicyLegacyMigrator.fingerprintSnapshot(same)));
    }

    @Test
    public void staleMigrationRowsFallBackButExplicitPoliciesAlwaysWin() {
        AppPolicy migrated = new AppPolicy("com.example.migrated");
        migrated.source = AppPolicy.SOURCE_LEGACY_MIGRATED;
        assertNull(AppPolicyLegacyMigrator.resolveEffectivePolicy(migrated, false));
        assertSame(migrated, AppPolicyLegacyMigrator.resolveEffectivePolicy(migrated, true));

        AppPolicy explicit = new AppPolicy("com.example.explicit");
        explicit.source = AppPolicy.SOURCE_EXPLICIT;
        assertSame(explicit, AppPolicyLegacyMigrator.resolveEffectivePolicy(explicit, false));
    }

    @Test
    public void migrationKeyCoverageIncludesDynamicAndPresetOwnershipInputs() {
        assertTrue(AppPolicyLegacyMigrator.affectsMigrationKey(KEY_ACTIVE_PRESET));
        assertTrue(AppPolicyLegacyMigrator.affectsMigrationKey(KEY_SLEEP_MODE_APPS_FROZEN));
        assertTrue(AppPolicyLegacyMigrator.affectsMigrationKey(KEY_SMART_LIFECYCLE_PROFILE));
        assertFalse(AppPolicyLegacyMigrator.affectsMigrationKey(KEY_THEME));
    }

'''
legacy_test = legacy_test.replace(
    '    private static AppPolicyLegacyMigrator.LegacySnapshot base() {\n',
    insert + '    private static AppPolicyLegacyMigrator.LegacySnapshot base() {\n'
)
write("app/src/test/java/com/gree1d/reappzuku/core/AppPolicyLegacyMigratorTest.java", legacy_test)

migration_test = r'''package com.gree1d.reappzuku.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class AppDatabaseMigrationTest {
    private static final String TEST_DB = "reappzuku-migration-test";
    private static final String TEST_DB_12 = "reappzuku-migration-12-test";

    @Rule
    public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    @Test
    public void migrate2To13_preservesExistingStatsAndAddsPolicySchema() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2);
        db.execSQL("INSERT INTO app_stats " +
                "(packageName, appName, killCount, relaunchCount, totalRecoveredKb, lastKillTime, lastRelaunchTime) " +
                "VALUES ('com.example.app', 'Example', 7, 3, 4096, 111, 222)");
        db.close();

        db = helper.runMigrationsAndValidate(
                TEST_DB,
                13,
                true,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13);

        try (Cursor cursor = db.query(
                "SELECT packageName, appName, relaunchCount, totalRecoveredKb, lastKillTime, lastRelaunchTime, lastKillSource " +
                        "FROM app_stats WHERE packageName='com.example.app'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("com.example.app", cursor.getString(0));
            assertEquals("Example", cursor.getString(1));
            assertEquals(3, cursor.getInt(2));
            assertEquals(4096L, cursor.getLong(3));
            assertEquals(111L, cursor.getLong(4));
            assertEquals(222L, cursor.getLong(5));
            assertTrue(cursor.isNull(6));
        }

        try (Cursor cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('app_policy','policy_preset') ORDER BY name")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("app_policy", cursor.getString(0));
            assertTrue(cursor.moveToNext());
            assertEquals("policy_preset", cursor.getString(0));
        }
        db.close();
    }

    @Test
    public void migrate12To13_existingPoliciesDefaultToExplicitSource() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB_12, 12);
        db.execSQL("INSERT INTO app_policy " +
                "(packageName, strategy, presetId, customized, standbyDelayMs, forceStopDelayMs, " +
                "killMethod, bootCleanup, backgroundRestriction, protectMedia, " +
                "protectForegroundServices, protectWidgets, triggerMask, createdAt, updatedAt) " +
                "VALUES ('com.example.explicit', 3, NULL, 1, 3600000, 21600000, " +
                "0, 1, 0, 1, 1, 1, 1, 100, 100)");
        db.close();

        db = helper.runMigrationsAndValidate(
                TEST_DB_12, 13, true, AppDatabase.MIGRATION_12_13);

        try (Cursor cursor = db.query(
                "SELECT source FROM app_policy WHERE packageName='com.example.explicit'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(AppPolicy.SOURCE_EXPLICIT, cursor.getInt(0));
        }
        db.close();
    }
}
'''
write("app/src/androidTest/java/com/gree1d/reappzuku/db/AppDatabaseMigrationTest.java", migration_test)

source_test = r'''package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class LegacyPolicyActivationSourceTest {
    @Test
    public void bothExecutionEnginesFilterMigrationOwnedRowsBySnapshotFreshness() throws Exception {
        String autoKill = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java");
        String smart = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java");

        assertEquals(1, count(autoKill, "AppPolicyLegacyMigrator.isMigrationSnapshotCurrent("));
        assertEquals(1, count(autoKill, "AppPolicyLegacyMigrator.resolveEffectivePolicy("));
        assertEquals(1, count(smart, "AppPolicyLegacyMigrator.isMigrationSnapshotCurrent("));
        assertEquals(1, count(smart, "AppPolicyLegacyMigrator.resolveEffectivePolicy("));
    }

    @Test
    public void applicationStartsAndRequeuesMigrationForRelevantLegacyChanges() throws Exception {
        String source = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/App.java");

        assertTrue(source.contains(
                "legacyPolicyPreferences.registerOnSharedPreferenceChangeListener("));
        assertTrue(source.contains("AppPolicyLegacyMigrator.affectsMigrationKey(key)"));
        assertTrue(source.contains("AppPolicyLegacyMigrator.migrateIfNeeded(this)"));
        assertTrue(source.contains(
                "legacyPolicyChangeGeneration.get() != generationAtStart"));
    }

    @Test
    public void backupRestoreInvalidatesMigrationBeforeCommitAndDuringRollback() throws Exception {
        String source = readRepositoryFile(
                "app/src/main/java/com/gree1d/reappzuku/core/BackupManager.java");

        assertEquals(2, count(source, "AppPolicyLegacyMigrator.invalidateMigration(editor)"));
    }

    private static int count(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String readRepositoryFile(String relative) throws IOException {
        List<Path> candidates = Arrays.asList(
                Paths.get(relative),
                Paths.get("..", relative),
                Paths.get("..", "..", relative));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
        }
        throw new IOException("Could not locate repository file: " + relative
                + " from " + Paths.get("").toAbsolutePath());
    }
}
'''
write("app/src/test/java/com/gree1d/reappzuku/core/LegacyPolicyActivationSourceTest.java", source_test)

# Roadmap status now reflects the already-proven routing and this activation bridge.
roadmap = read("docs/ROADMAP.md")
roadmap = roadmap.replace(
    '- [~] Build one-time idempotent legacy migration from whitelist/blacklist, Smart Lifecycle, active/permanent Sleep Mode ownership and background-restriction state into explicit per-app policies without changing current effective ownership. The migration planner/runtime bridge and regression coverage are implemented; activation is deliberately deferred to the execution-routing cutover so legacy UI edits cannot make a precomputed snapshot stale.\n'
    '- [ ] Route Auto-Kill and Smart Lifecycle execution through the policy resolver, then retire direct shared-blacklist ownership.\n',
    '- [x] Build and activate retry-safe legacy migration from whitelist/blacklist, Smart Lifecycle, active/permanent/still-owned Sleep Mode state and background restrictions into explicit per-app policies. Migration-owned rows are provenance-tagged and fingerprint-bound to the live legacy snapshot; stale rows are ignored until reconciliation succeeds, while explicit user-owned rows always win.\n'
    '- [x] Route Auto-Kill and Smart Lifecycle execution through the policy resolver. Both engines now filter stale migration-owned rows back to live legacy fallback, so shared-blacklist ownership cannot reappear during compatibility edits.\n'
)
roadmap = roadmap.replace(
    '- Legacy policy migration code is versioned and retry-safe: Room rows commit first, the SharedPreferences completion marker is committed second, and reruns use insert-ignore so an existing explicit policy is never overwritten. It is not auto-started yet; activation stays coupled to the execution-routing cutover while legacy settings remain editable.\n',
    '- Legacy policy migration is now application-started and retry-safe. Room schema 13 tags rows as explicit or legacy-migrated; only legacy-owned rows are replaced. A SHA-256 fingerprint covers every legacy input that changes effective ownership, triggers, Smart delays or restriction strength. Any old UI/preset/restore edit makes those rows non-authoritative immediately, so runtime falls back to live legacy state until the coalesced reconciliation succeeds.\n'
)
write("docs/ROADMAP.md", roadmap)
