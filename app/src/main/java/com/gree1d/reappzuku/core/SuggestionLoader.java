package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.WorkerThread;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.DailyDeviceUsage;
import com.gree1d.reappzuku.manager.BackgroundAppManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Collects the facts for {@link SuggestionEngine} and remembers what the user dismissed. */
public final class SuggestionLoader {
    /** What applying a recommendation sets: standby when idle, never stopped, no boot cleanup. */
    public static final long APPLIED_PRESET = PolicyPresetSeeder.PRESET_MESSENGER;

    /** Standby when idle. Notifications, alarms and background work keep running. */
    public static final int LEVEL_GENTLE = 0;
    /** Also restricts background activity. */
    public static final int LEVEL_BALANCED = 1;
    /** Also stops the app once it has been idle for a while. */
    public static final int LEVEL_STRICT = 2;

    /** The system dumps behind the background facts are not repeated for every screen visit. */
    private static final long BACKGROUND_TTL_MS = 10 * 60_000L;
    // Its own lock: the shell calls must not hold up dismiss(), which runs on the main thread.
    private static final Object BACKGROUND_LOCK = new Object();
    private static Background cachedBackground;
    private static long cachedBackgroundAt;

    private static final String PREFS = "recommendations";
    private static final String KEY_DISMISSED = "dismissed";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    /** Uids of one Android user span this range. */
    private static final int PER_USER_RANGE = 100_000;
    /** How long a dismissed recommendation stays hidden. */
    static final long DISMISS_MS = 30 * DAY_MS;

    private SuggestionLoader() {
    }

    @WorkerThread
    public static List<SuggestionEngine.Suggestion> load(Context context) {
        try {
            return loadUnguarded(context);
        } catch (RuntimeException e) {
            // Recommendations are an extra; a database or system service failure must not crash a screen.
            return Collections.emptyList();
        }
    }

    private static List<SuggestionEngine.Suggestion> loadUnguarded(Context context) {
        long now = System.currentTimeMillis();
        TimeZone zone = TimeZone.getDefault();
        int fromDay = DailyUsageMath.dayKey(now, zone, SuggestionEngine.WINDOW_DAYS - 1);
        int today = DailyUsageMath.dayKey(now, zone);
        AppDatabase database = AppDatabase.getInstance(context);

        double drainPct = 0;
        for (DailyDeviceUsage day : database.dailyUsageDao().getDeviceUsageBetween(fromDay, today)) {
            drainPct += day.drainPct;
        }
        SuggestionEngine.Usage usage = new SuggestionEngine.Usage(
                database.dailyUsageDao().getAppUsageBetween(fromDay, today), drainPct);
        AppPolicyListSnapshot policies = AppPolicyListSnapshot.capture(context);
        Map<String, Long> lastOpened = lastOpened(context, now);
        Background background = background(context, now);
        PackageManager packageManager = context.getPackageManager();

        Set<String> packages = new HashSet<>(usage.packages());
        packages.addAll(background.minutesPerDay.keySet());
        packages.addAll(background.sensorHolders);
        List<SuggestionEngine.AppFacts> facts = new ArrayList<>();
        for (String packageName : packages) {
            int status = policies.resolveStatus(packageName, false);
            // Stops from before the current policy say nothing about it.
            int countFrom = fromDay;
            if (status == AppPolicyListState.STATUS_IMMEDIATE) {
                AppPolicy policy = database.appPolicyDao().getByPackage(packageName);
                if (policy != null) {
                    countFrom = Math.max(fromDay, DailyUsageMath.dayKey(policy.updatedAt, zone));
                }
            }
            Long opened = lastOpened == null ? null : lastOpened.get(packageName);
            if (lastOpened != null && (opened == null || opened <= 0)
                    && background.contains(packageName)
                    && packageManager.getLaunchIntentForPackage(packageName) != null) {
                // Never opened as far as the usage data reaches: idle since it was installed.
                // Only for a user-installed app that can be opened at all; a system
                // component or a plugin without a launcher entry stays unknown.
                opened = installedAt(packageManager, packageName);
            }
            Double minutes = background.minutesPerDay.get(packageName);
            facts.add(new SuggestionEngine.AppFacts(
                    packageName, status, usage.batteryShare(packageName),
                    usage.kills(packageName, countFrom), usage.relaunches(packageName, countFrom),
                    daysSince(opened, now), minutes == null ? 0 : minutes,
                    background.sensorHolders.contains(packageName)));
        }

        // The system is only asked about the few apps that produced a recommendation.
        Set<String> dismissed = dismissed(context);
        String keyboard = ProtectedApps.getCurrentKeyboardPackage(context);
        String launcher = ProtectedApps.getCurrentLauncherPackage(context);
        List<SuggestionEngine.Suggestion> result = new ArrayList<>();
        for (SuggestionEngine.Suggestion suggestion : SuggestionEngine.suggest(facts, dismissed)) {
            if (result.size() >= SuggestionEngine.MAX_SUGGESTIONS) break;
            if (isManageable(context, packageManager, suggestion.packageName, keyboard, launcher)) {
                result.add(suggestion);
            }
        }
        return result;
    }

    private static Long installedAt(PackageManager packageManager, String packageName) {
        try {
            return packageManager.getPackageInfo(packageName, 0).firstInstallTime;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** What the system knows about background work, per package of an installed user app. */
    static final class Background {
        final Map<String, Double> minutesPerDay = new HashMap<>();
        final Set<String> sensorHolders = new HashSet<>();

        boolean contains(String packageName) {
            return minutesPerDay.containsKey(packageName) || sensorHolders.contains(packageName);
        }
    }

    /**
     * Wakelock and job time since the last charge and the apps holding a
     * sensor now. Both come from the system, so they are known on the first
     * day. Empty without a shell.
     */
    private static Background background(Context context, long now) {
        synchronized (BACKGROUND_LOCK) {
            if (cachedBackground != null && now >= cachedBackgroundAt
                    && now - cachedBackgroundAt < BACKGROUND_TTL_MS) {
                return cachedBackground;
            }
            Background result = new Background();
            if (!(context.getApplicationContext() instanceof App)) return result;
            ShellManager shell = ((App) context.getApplicationContext()).getShellManager();
            if (shell == null || !shell.isAnyShellReady()) return result;
            String checkin = shell.runShellCommandAndGetFullOutput(
                    "dumpsys batterystats --charged --checkin");
            // A failed or timed out dump is asked for again next time instead of being kept.
            if (checkin == null || checkin.isEmpty()) return result;
            PackageManager packageManager = context.getPackageManager();
            for (Map.Entry<Integer, Double> entry
                    : BackgroundActivityParser.minutesPerDay(checkin).entrySet()) {
                String packageName = userAppOf(packageManager, entry.getKey());
                if (packageName != null) result.minutesPerDay.put(packageName, entry.getValue());
            }
            for (Integer uid : SensorConnectionParser.parse(
                    shell.runShellCommandAndGetFullOutput("dumpsys sensorservice")).keySet()) {
                String packageName = userAppOf(packageManager, uid);
                if (packageName != null) result.sensorHolders.add(packageName);
            }
            cachedBackground = result;
            cachedBackgroundAt = now;
            return result;
        }
    }

    /** The package of a uid that belongs to exactly one user-installed app, else null. */
    private static String userAppOf(PackageManager packageManager, int uid) {
        // The copy of an app in a work profile or for another user is not this user's app.
        if (uid / PER_USER_RANGE != android.os.Process.myUid() / PER_USER_RANGE) return null;
        String[] packages = packageManager.getPackagesForUid(uid);
        if (packages == null || packages.length != 1) return null;
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packages[0], 0);
            return (info.flags & ApplicationInfo.FLAG_SYSTEM) == 0 ? packages[0] : null;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Whether the two firmer levels can be offered. Without a shell or below
     * Android 11 a restriction would only be stored, not applied.
     */
    public static boolean canRestrict(Context context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return false;
        if (!(context.getApplicationContext() instanceof App)) return false;
        ShellManager shell = ((App) context.getApplicationContext()).getShellManager();
        return shell != null && shell.isAnyShellReady();
    }

    static long presetFor(int level) {
        return level == LEVEL_STRICT ? PolicyPresetSeeder.PRESET_RARELY_USED : APPLIED_PRESET;
    }

    static int restrictionFor(int level) {
        return level == LEVEL_STRICT ? AppPolicy.RESTRICTION_MEDIUM
                : level == LEVEL_BALANCED ? AppPolicy.RESTRICTION_SOFT
                : AppPolicy.RESTRICTION_NONE;
    }

    /** Applies a recommendation as firmly as the user chose and remembers what to undo. */
    @WorkerThread
    public static void apply(Context context, SuggestionEngine.Suggestion suggestion, int level) {
        String packageName = suggestion.packageName;
        // A level the user set on the restriction screens is never lowered or replaced.
        boolean restrict = restrictionFor(level) != AppPolicy.RESTRICTION_NONE
                && BackgroundAppManager.storedRestrictionLevel(context, packageName)
                        == AppPolicy.RESTRICTION_NONE;
        RecommendationEffect.recordApplied(context, packageName, suggestion.facts.status,
                restrict ? restrictionFor(level) : -1);
        AppPolicyQuickChoice.applyPreset(context, packageName, presetFor(level));
        if (restrict) {
            // Quietly: stopping the app here would cut its notifications until it is opened again.
            BackgroundAppManager.applyPolicyRestriction(
                    context, packageName, restrictionFor(level), true);
        }
    }

    /** Whole days since the given moment, or -1 if it is unknown. */
    static int daysSince(Long timeMs, long now) {
        return timeMs == null || timeMs <= 0 ? -1 : (int) Math.max(0, (now - timeMs) / DAY_MS);
    }

    /**
     * Two of the three rules need to know when an app was last opened. Until
     * now only the Smart engine asked for that access, which left users
     * without a Smart app without it.
     */
    private static Map<String, Long> lastOpened(Context context, long now) {
        Map<String, Long> lastOpened = UsageAccess.lastOpenedByPackage(context, now);
        if (lastOpened == null && context.getApplicationContext() instanceof App) {
            ShellManager shell = ((App) context.getApplicationContext()).getShellManager();
            if (shell != null && shell.isAnyShellReady()) {
                UsageAccess.grant(context, new PrivilegedShell(shell));
                lastOpened = UsageAccess.lastOpenedByPackage(context, now);
            }
        }
        return lastOpened;
    }

    private static boolean isManageable(Context context, PackageManager packageManager,
            String packageName, String keyboard, String launcher) {
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            return !packageName.equals(context.getPackageName())
                    && (info.flags & ApplicationInfo.FLAG_PERSISTENT) == 0
                    && !ProtectedApps.isProtected(packageName, keyboard, launcher);
        } catch (PackageManager.NameNotFoundException e) {
            return false; // uninstalled since the data was recorded
        }
    }

    // Synchronized with the write-back in dismissed(): a reload must not overwrite a new dismissal.
    public static synchronized void dismiss(Context context, SuggestionEngine.Suggestion suggestion) {
        long now = System.currentTimeMillis();
        Map<String, Long> active = activeDismissals(
                prefs(context).getStringSet(KEY_DISMISSED, Collections.emptySet()), now);
        active.put(suggestion.key(), now);
        prefs(context).edit().putStringSet(KEY_DISMISSED, entries(active)).apply();
    }

    private static synchronized Set<String> dismissed(Context context) {
        Set<String> stored = prefs(context).getStringSet(KEY_DISMISSED, Collections.emptySet());
        Map<String, Long> active = activeDismissals(stored, System.currentTimeMillis());
        Set<String> entries = entries(active);
        if (!entries.equals(stored)) {
            // Drops expired entries and stamps the ones stored before the time was kept.
            prefs(context).edit().putStringSet(KEY_DISMISSED, entries).apply();
        }
        return active.keySet();
    }

    /**
     * @param stored entries "key|dismissedAt"; a key is "package|reason", so an
     *               entry with a single separator was stored without a time
     * @return the keys still hidden at {@code now}, with the time they were dismissed.
     *         An entry without a time counts as dismissed at {@code now}.
     */
    static Map<String, Long> activeDismissals(Set<String> stored, long now) {
        Map<String, Long> active = new HashMap<>();
        for (String entry : stored) {
            int last = entry.lastIndexOf('|');
            if (last < 0) continue;
            String key = entry;
            long at = now;
            if (entry.indexOf('|') != last) {
                key = entry.substring(0, last);
                try {
                    at = Long.parseLong(entry.substring(last + 1));
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            // A time ahead of now comes from a clock that was set back; start the days again.
            if (at > now) at = now;
            if (now - at < DISMISS_MS) active.put(key, at);
        }
        return active;
    }

    private static Set<String> entries(Map<String, Long> active) {
        Set<String> entries = new HashSet<>();
        for (Map.Entry<String, Long> entry : active.entrySet()) {
            entries.add(entry.getKey() + "|" + entry.getValue());
        }
        return entries;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
