package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.WorkerThread;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.DailyDeviceUsage;

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

    private static final String PREFS = "recommendations";
    private static final String KEY_DISMISSED = "dismissed";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
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

        List<SuggestionEngine.AppFacts> facts = new ArrayList<>();
        for (String packageName : usage.packages()) {
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
            facts.add(new SuggestionEngine.AppFacts(
                    packageName, status, usage.batteryShare(packageName),
                    usage.kills(packageName, countFrom), usage.relaunches(packageName, countFrom),
                    daysSince(opened, now)));
        }

        // The system is only asked about the few apps that produced a recommendation.
        Set<String> dismissed = dismissed(context);
        PackageManager packageManager = context.getPackageManager();
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
