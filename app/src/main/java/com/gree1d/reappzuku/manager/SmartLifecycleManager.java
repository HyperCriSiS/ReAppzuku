package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.gree1d.reappzuku.core.UsageAccess;
import com.gree1d.reappzuku.core.AppConstants;
import com.gree1d.reappzuku.core.AppPolicyLegacyMigrator;
import com.gree1d.reappzuku.core.AppPolicyResolver;
import com.gree1d.reappzuku.core.ProtectedApps;
import com.gree1d.reappzuku.core.PrivilegedShell;
import com.gree1d.reappzuku.core.SensorConnectionParser;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;

/**
 * Conservative lifecycle management inspired by Brevent's "standby first,
 * force-stop later" model, but using ReAppzuku/Shizuku as the privilege layer.
 *
 * During Phase 9 explicit Room-backed policies own per-app behavior. Packages
 * without an explicit policy keep the bounded legacy blacklist fallback only
 * while compatibility migration is still required.
 */
public final class SmartLifecycleManager {

    public static final int PROFILE_GENTLE = 0;
    public static final int PROFILE_BALANCED = 1;
    public static final int PROFILE_AGGRESSIVE = 2;

    private static final long MINUTE = 60_000L;
    private final Context context;
    private final ShellManager shellManager;
    private final PrivilegedShell privilegedShell;
    private final SharedPreferences prefs;
    private final PackageManager packageManager;

    public SmartLifecycleManager(Context context, ShellManager shellManager) {
        this.context = context.getApplicationContext();
        this.shellManager = shellManager;
        this.privilegedShell = new PrivilegedShell(shellManager);
        this.prefs = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        this.packageManager = this.context.getPackageManager();
    }

    public static void recordForeground(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) return;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        // Only the use is recorded. The pass moves the idle clock forward from it;
        // dropping the clock here would let a later pass restart it from older data.
        prefs.edit()
                .putLong(KEY_SMART_LAST_FOREGROUND_PREFIX + packageName, System.currentTimeMillis())
                .apply();
    }

    public static int getBootGraceMinutes(SharedPreferences prefs) {
        switch (prefs.getInt(KEY_SMART_LIFECYCLE_PROFILE, PROFILE_BALANCED)) {
            case PROFILE_GENTLE: return 10;
            case PROFILE_AGGRESSIVE: return 2;
            default: return 5;
        }
    }

    public static int getStandbyDelayMinutes(SharedPreferences prefs) {
        switch (prefs.getInt(KEY_SMART_LIFECYCLE_PROFILE, PROFILE_BALANCED)) {
            case PROFILE_GENTLE: return 120;
            case PROFILE_AGGRESSIVE: return 30;
            default: return 60;
        }
    }

    public static int getForceStopDelayMinutes(SharedPreferences prefs) {
        switch (prefs.getInt(KEY_SMART_LIFECYCLE_PROFILE, PROFILE_BALANCED)) {
            case PROFILE_GENTLE: return 720;
            case PROFILE_AGGRESSIVE: return 120;
            default: return 360;
        }
    }

    public boolean runPass(boolean bootPass) {
        boolean legacySmartEnabled = prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false);

        Set<String> blacklisted = new HashSet<>(
                prefs.getStringSet(KEY_BLACKLISTED_APPS, Collections.emptySet()));
        Set<String> whitelisted = new HashSet<>(
                prefs.getStringSet(KEY_WHITELISTED_APPS, Collections.emptySet()));
        boolean legacyAutoKillEnabled = prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false)
                || prefs.getInt(KEY_ACTIVE_PRESET, 0) != 0;
        boolean whitelistMode = prefs.getInt(KEY_KILL_MODE, AppConstants.DEFAULT_KILL_MODE) == 0;

        boolean migrationSnapshotCurrent =
                AppPolicyLegacyMigrator.isMigrationSnapshotCurrent(prefs);
        Map<String, AppPolicy> explicitPolicies = new HashMap<>();
        for (AppPolicy policy : AppDatabase.getInstance(context).appPolicyDao().getAll()) {
            AppPolicy effectivePolicy = AppPolicyLegacyMigrator.resolveEffectivePolicy(
                    policy, migrationSnapshotCurrent);
            if (effectivePolicy != null && effectivePolicy.packageName != null) {
                explicitPolicies.put(effectivePolicy.packageName, effectivePolicy);
            }
        }

        Set<String> managed = new HashSet<>(blacklisted);
        // Include every explicit row so switching a formerly-SMART package to
        // PROTECTED/UNMANAGED/IMMEDIATE also clears stale Smart timing state.
        managed.addAll(explicitPolicies.keySet());
        if (managed.isEmpty()) {
            return true;
        }

        Set<String> smartManaged = new HashSet<>();
        for (String pkg : managed) {
            AppPolicy explicitPolicy = explicitPolicies.get(pkg);
            AppPolicyResolver.LegacyState legacyState = new AppPolicyResolver.LegacyState(
                    legacyAutoKillEnabled,
                    legacySmartEnabled,
                    whitelistMode,
                    whitelisted.contains(pkg),
                    blacklisted.contains(pkg));
            if (AppPolicyResolver.shouldExecuteSmart(
                    explicitPolicy,
                    migrationSnapshotCurrent ? null : legacyState,
                    bootPass)) {
                smartManaged.add(pkg);
            } else {
                clearBackgroundState(pkg);
            }
        }
        if (smartManaged.isEmpty()) {
            return true;
        }

        long now = System.currentTimeMillis();
        long legacyStandbyDelay = getStandbyDelayMinutes(prefs) * MINUTE;
        long legacyForceStopDelay = getForceStopDelayMinutes(prefs) * MINUTE;
        Map<String, long[]> delays = new HashMap<>();
        for (java.util.Iterator<String> it = smartManaged.iterator(); it.hasNext();) {
            String pkg = it.next();
            // Needs no shell, so a package that can never be handled does not cause full passes.
            if (!isEligiblePackage(pkg)) {
                clearBackgroundState(pkg);
                it.remove();
                continue;
            }
            long standbyDelay = AppPolicyResolver.resolveSmartStandbyDelayMs(
                    explicitPolicies.get(pkg), legacyStandbyDelay);
            delays.put(pkg, new long[] {standbyDelay, AppPolicyResolver.resolveSmartForceStopDelayMs(
                    explicitPolicies.get(pkg), legacyForceStopDelay, standbyDelay)});
        }

        // Null without usage data. Then every pass runs in full, as it always did.
        Map<String, Long> lastUsed = UsageAccess.lastUsedByPackage(context, now);
        boolean usageKnown = lastUsed != null;
        if (prefs.getBoolean(KEY_SMART_USAGE_CLOCK, false) != usageKnown) {
            // Clocks kept alive by usage data were not re-observed on every pass.
            // Without that data they cannot be trusted and start again.
            if (!usageKnown) {
                for (String pkg : smartManaged) clearBackgroundState(pkg);
            }
            prefs.edit().putBoolean(KEY_SMART_USAGE_CLOCK, usageKnown).apply();
        }
        if (usageKnown && !bootPass) {
            boolean anyDue = false;
            for (String pkg : smartManaged) {
                long[] delay = delays.get(pkg);
                // An app the usage data does not know is observed on every pass, as before.
                if (!lastUsed.containsKey(pkg) || SmartIdleClock.isDue(
                        prefs.getLong(KEY_SMART_BACKGROUND_SINCE_PREFIX + pkg, 0L),
                        lastUse(pkg, lastUsed), now, delay[0], delay[1],
                        prefs.getBoolean(KEY_SMART_STANDBY_APPLIED_PREFIX + pkg, false))) {
                    anyDue = true;
                    break;
                }
            }
            if (!anyDue) return true; // nothing due
        }

        if (!shellManager.resolveAnyShellPermission()) {
            return false;
        }
        if (!usageKnown) UsageAccess.grant(context, privilegedShell);

        String currentForeground = getCurrentForegroundPackage();
        if (currentForeground != null) recordForeground(context, currentForeground);

        Set<String> running = getRunningPackages();
        ProtectionDumps protectionDumps = ProtectionDumps.collect(shellManager);

        long bootEpoch = prefs.getLong(KEY_SMART_BOOT_EPOCH_MS, 0L);
        boolean legacyBootCleanupEnabled =
                prefs.getBoolean(KEY_SMART_BOOT_CLEANUP_ENABLED, true);
        boolean retryRequired = false;
        // Read on the first app that goes on standby in this pass.
        Map<Integer, List<String>> sensorHolders = null;

        for (String pkg : smartManaged) {
            AppPolicy explicitPolicy = explicitPolicies.get(pkg);

            if (pkg.equals(currentForeground)
                    || !running.contains(pkg)
                    || protectionDumps.reasonFor(context, pkg, explicitPolicy) != null) {
                restartIdleClock(pkg, now, usageKnown && lastUsed.containsKey(pkg));
                continue;
            }

            if (bootPass) {
                // Explicit SMART policies own their boot-cleanup decision. Legacy-owned
                // packages keep the old global switch until that compatibility path is retired.
                if (explicitPolicy == null && !legacyBootCleanupEnabled) continue;

                long lastForeground = lastUse(pkg, lastUsed);
                if (bootEpoch > 0 && lastForeground >= bootEpoch) {
                    continue;
                }
                SmartLifecycleRecoveryPolicy.ForceStopOutcome outcome =
                        SmartLifecycleRecoveryPolicy.onForceStopResult(
                                true, forceStop(pkg, "boot cleanup"));
                if (outcome == SmartLifecycleRecoveryPolicy.ForceStopOutcome.CLEAR_STATE) {
                    restartIdleClock(pkg, now, usageKnown);
                } else if (outcome == SmartLifecycleRecoveryPolicy.ForceStopOutcome.KEEP_STATE_AND_RETRY) {
                    retryRequired = true;
                }
                continue;
            }

            long standbyDelay = delays.get(pkg)[0];
            long forceStopDelay = delays.get(pkg)[1];

            String sinceKey = KEY_SMART_BACKGROUND_SINCE_PREFIX + pkg;
            String standbyKey = KEY_SMART_STANDBY_APPLIED_PREFIX + pkg;
            long stored = prefs.getLong(sinceKey, 0L);
            long used = lastUse(pkg, lastUsed);
            long backgroundSince = SmartIdleClock.resolveSince(stored, used, now);
            if (backgroundSince != stored) {
                SharedPreferences.Editor editor = prefs.edit().putLong(sinceKey, backgroundSince);
                if (SmartIdleClock.usedSinceClockStart(stored, used)) editor.remove(standbyKey);
                editor.apply();
            }
            if (backgroundSince >= now) continue;

            long idle = now - backgroundSince;
            if (idle >= standbyDelay && !prefs.getBoolean(standbyKey, false)) {
                if (setStandby(pkg)) {
                    prefs.edit().putBoolean(standbyKey, true).apply();
                    // A standby bucket leaves a registered sensor running. Ending the idle
                    // process releases it; alarms, jobs and notifications start the app again.
                    if (sensorHolders == null) {
                        sensorHolders = SensorConnectionParser.parse(safeShell("dumpsys sensorservice"));
                    }
                    if (holdsSensor(pkg, sensorHolders)) {
                        privilegedShell.stopPackage(pkg, PrivilegedShell.KillMode.KILL);
                    }
                }
            }

            if (idle >= forceStopDelay) {
                SmartLifecycleRecoveryPolicy.ForceStopOutcome outcome =
                        SmartLifecycleRecoveryPolicy.onForceStopResult(
                                false, forceStop(pkg, "inactive " + (idle / MINUTE) + " min"));
                if (outcome == SmartLifecycleRecoveryPolicy.ForceStopOutcome.CLEAR_STATE) {
                    restartIdleClock(pkg, now, usageKnown);
                }
            }
        }
        return !retryRequired;
    }

    private boolean isEligiblePackage(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(context.getPackageName())) return false;
        if (ProtectedApps.isProtected(context, pkg)) return false;
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(pkg, 0);
            return (info.flags & ApplicationInfo.FLAG_PERSISTENT) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String getCurrentForegroundPackage() {
        String activities = safeShell("dumpsys activity activities");
        String foreground = SmartLifecycleTextParser.parseForegroundPackage(activities);
        if (foreground != null) return foreground;

        String window = safeShell("dumpsys window windows");
        return SmartLifecycleTextParser.parseForegroundPackage(window);
    }

    private Set<String> getRunningPackages() {
        return SmartLifecycleTextParser.parseRunningPackages(safeShell("ps -A -o NAME"));
    }

    private boolean holdsSensor(String pkg, Map<Integer, List<String>> sensorHolders) {
        try {
            int uid = packageManager.getApplicationInfo(pkg, 0).uid;
            // With a shared uid the sensor may belong to another app.
            String[] packages = packageManager.getPackagesForUid(uid);
            return sensorHolders.containsKey(uid) && packages != null && packages.length == 1;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean setStandby(String pkg) {
        return privilegedShell.setStandbyBucketBlocking(
                pkg, PrivilegedShell.StandbyBucket.RARE);
    }

    private boolean forceStop(String pkg, String reason) {
        return privilegedShell.forceStopPackageBlocking(pkg);
    }

    /** The latest use known from usage data and from recorded foreground events; 0 if none. */
    private long lastUse(String pkg, Map<String, Long> lastUsed) {
        return Math.max(prefs.getLong(KEY_SMART_LAST_FOREGROUND_PREFIX + pkg, 0L),
                lastUsed == null ? 0L : lastUsed.getOrDefault(pkg, 0L));
    }

    /**
     * The app is not idle in the background right now. With usage data for it
     * the clock restarts at this pass, so the cheap due check does not report
     * the app again before a full delay has passed. Without usage data the
     * state is dropped and the next pass that sees the app starts the clock.
     */
    private void restartIdleClock(String pkg, long now, boolean usageKnown) {
        if (!usageKnown) {
            clearBackgroundState(pkg);
            return;
        }
        prefs.edit()
                .putLong(KEY_SMART_BACKGROUND_SINCE_PREFIX + pkg, now)
                .remove(KEY_SMART_STANDBY_APPLIED_PREFIX + pkg)
                .apply();
    }

    private void clearBackgroundState(String pkg) {
        prefs.edit()
                .remove(KEY_SMART_BACKGROUND_SINCE_PREFIX + pkg)
                .remove(KEY_SMART_STANDBY_APPLIED_PREFIX + pkg)
                .apply();
    }

    private String safeShell(String command) {
        String output = shellManager.runShellCommandAndGetFullOutput(command);
        return output == null ? "" : output;
    }
}