package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.WorkerThread;

import com.gree1d.reappzuku.db.AppDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Collects the facts for {@link SuggestionEngine} and remembers what the user dismissed. */
public final class SuggestionLoader {
    private static final String PREFS = "recommendations";
    private static final String KEY_DISMISSED = "dismissed";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private SuggestionLoader() {
    }

    @WorkerThread
    public static List<SuggestionEngine.Suggestion> load(Context context) {
        long now = System.currentTimeMillis();
        TimeZone zone = TimeZone.getDefault();
        SuggestionEngine.Usage usage = new SuggestionEngine.Usage(
                AppDatabase.getInstance(context).dailyUsageDao().getAppUsageBetween(
                        DailyUsageMath.dayKey(now, zone, SuggestionEngine.WINDOW_DAYS - 1),
                        DailyUsageMath.dayKey(now, zone)));
        AppPolicyListSnapshot policies = AppPolicyListSnapshot.capture(context);
        Map<String, Long> lastOpened = UsageAccess.lastOpenedByPackage(context, now);
        PackageManager packageManager = context.getPackageManager();

        List<SuggestionEngine.AppFacts> facts = new ArrayList<>();
        for (String packageName : usage.packages()) {
            ApplicationInfo info;
            try {
                info = packageManager.getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                continue; // uninstalled since the data was recorded
            }
            // An app that can never be managed gets no recommendation.
            if (packageName.equals(context.getPackageName())
                    || (info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0
                    || ProtectedApps.isProtected(context, packageName)) {
                continue;
            }
            Long opened = lastOpened == null ? null : lastOpened.get(packageName);
            facts.add(new SuggestionEngine.AppFacts(
                    packageName,
                    policies.resolveStatus(packageName, false),
                    usage.batteryShare(packageName),
                    usage.kills(packageName),
                    usage.relaunches(packageName),
                    opened == null || opened <= 0 ? -1 : (int) Math.max(0, (now - opened) / DAY_MS)));
        }
        return SuggestionEngine.suggest(facts, dismissed(context));
    }

    public static void dismiss(Context context, SuggestionEngine.Suggestion suggestion) {
        Set<String> dismissed = new HashSet<>(dismissed(context));
        dismissed.add(suggestion.key());
        prefs(context).edit().putStringSet(KEY_DISMISSED, dismissed).apply();
    }

    private static Set<String> dismissed(Context context) {
        return prefs(context).getStringSet(KEY_DISMISSED, Collections.emptySet());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
