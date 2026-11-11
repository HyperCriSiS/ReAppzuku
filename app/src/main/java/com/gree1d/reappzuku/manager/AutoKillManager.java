package com.gree1d.reappzuku.manager;

import com.gree1d.reappzuku.core.ScheduleOverlay;
import com.gree1d.reappzuku.core.AppPolicyLegacyMigrator;
import com.gree1d.reappzuku.core.AppPolicyResolver;
import com.gree1d.reappzuku.core.PackageNameValidator;
import com.gree1d.reappzuku.core.PrivilegedShell;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.utils.AppModel;
import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.service.ShappkyService;
import com.gree1d.reappzuku.core.ProtectedApps;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;
import static com.gree1d.reappzuku.core.AppConstants.*;

public class AutoKillManager {

    private static final int STATS_LIMIT = 15_000;

    private final Context context;
    private final Handler handler;
    private final Handler relaunchHandler;
    private final ExecutorService executor;
    private final ShellManager shellManager;
    private final PrivilegedShell privilegedShell;
    private final SharedPreferences sharedpreferences;
    private final List<AppModel> currentAppsList;
    private RestrictionsScheduler scheduler;
    private final SparedPackages sparedPackages = new SparedPackages();

    public AutoKillManager(Context context, Handler handler, ExecutorService executor,
            ShellManager shellManager, List<AppModel> currentAppsList) {
        this.context = context;
        this.handler = handler;
        this.relaunchHandler = new Handler(Looper.getMainLooper());
        this.executor = executor;
        this.shellManager = shellManager;
        this.privilegedShell = new PrivilegedShell(shellManager);
        this.currentAppsList = currentAppsList;
        this.sharedpreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public void setScheduler(RestrictionsScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void performAutoKill(Runnable onComplete, String source) {
        performAutoKill(onComplete, null, source, 0L);
    }

    public void performAutoKill(Runnable onComplete, String source, long trigger) {
        performAutoKill(onComplete, null, source, trigger);
    }

    public void performAutoKill(Runnable onComplete, Set<String> extraWhitelist, String source) {
        performAutoKill(onComplete, extraWhitelist, source, 0L);
    }

    public void performAutoKill(Runnable onComplete, Set<String> extraWhitelist,
            String source, long trigger) {
        performAutoKillWithResult(onComplete, extraWhitelist, null, source, trigger);
    }

    public void performAutoKillWithResult(Runnable onComplete, Set<String> extraWhitelist,
            java.util.function.BiConsumer<Integer, Long> onResult, String source) {
        performAutoKillWithResult(onComplete, extraWhitelist, onResult, source, 0L);
    }

    public void performAutoKillWithResult(Runnable onComplete, Set<String> extraWhitelist,
            java.util.function.BiConsumer<Integer, Long> onResult, String source, long trigger) {
        executor.execute(() -> {
            if (!shellManager.resolveAnyShellPermission()) {
                if (onComplete != null)
                    handler.post(onComplete);
                return;
            }

            Set<String> hiddenApps = getHiddenApps();
            Set<String> whitelistedApps = getWhitelistedApps();
            Set<String> blacklistedApps = getBlacklistedApps();
            int killMode = getKillMode();
            PresetManager presetManager = new PresetManager(context);
            boolean presetActive = presetManager.getActivePresetNumber() != 0;
            ScheduleOverlay scheduleOverlay = presetActive ? presetManager.activeOverlay() : null;
            boolean legacyAutoKillEnabled =
                    sharedpreferences.getBoolean(KEY_AUTO_KILL_ENABLED, false) || presetActive;
            boolean legacySmartEnabled =
                    sharedpreferences.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false);

            boolean migrationSnapshotCurrent =
                    AppPolicyLegacyMigrator.isMigrationSnapshotCurrent(sharedpreferences);
            Map<String, AppPolicy> explicitPolicies = new HashMap<>();
            for (AppPolicy policy : AppDatabase.getInstance(context).appPolicyDao().getAll()) {
                AppPolicy effectivePolicy = AppPolicyLegacyMigrator.resolveEffectivePolicy(
                        policy, migrationSnapshotCurrent);
                // While a schedule is active its lists replace the legacy lists, so the
                // rows migrated from those lists do not take part.
                if (scheduleOverlay != null && effectivePolicy != null
                        && effectivePolicy.source == AppPolicy.SOURCE_LEGACY_MIGRATED) {
                    continue;
                }
                if (effectivePolicy != null && effectivePolicy.packageName != null) {
                    explicitPolicies.put(effectivePolicy.packageName, effectivePolicy);
                }
            }

            String dumpOutput = shellManager.runShellCommandAndGetFullOutput("dumpsys activity activities");

            if (dumpOutput == null) {

                if (onComplete != null)
                    handler.post(onComplete);
                return;
            }


            Set<String> runningPackages = new HashSet<>();
            Map<String, Long> psRssMap = new HashMap<>();
            PackageManager pm = context.getPackageManager();

            Map<String, List<Integer>> pidsByPackage = new HashMap<>();
            Map<Integer, Long> psRssByPid = new HashMap<>();
            String pidListOutput = shellManager.runShellCommandAndGetFullOutput("ps -A -o pid,rss,name | grep '\\.'");
            if (pidListOutput != null) {
                try (BufferedReader reader = new BufferedReader(new StringReader(pidListOutput))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.trim().split("\\s+", 3);
                        if (parts.length < 3) continue;
                        String packageName = parts[2].trim();
                        if (packageName.contains(":")) {
                            packageName = packageName.substring(0, packageName.indexOf(":"));
                        }
                        if (packageName.isEmpty() || !packageName.contains(".")) continue;
                        try {
                            int pid = Integer.parseInt(parts[0].trim());
                            pidsByPackage.computeIfAbsent(packageName, k -> new ArrayList<>()).add(pid);
                            try {
                                psRssByPid.put(pid, Long.parseLong(parts[1].trim()));
                            } catch (NumberFormatException ignored) {
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } catch (IOException ignored) {
                }
            }

            for (Map.Entry<String, List<Integer>> entry : pidsByPackage.entrySet()) {
                String packageName = entry.getKey();
                try {
                    pm.getApplicationInfo(packageName, 0);
                } catch (PackageManager.NameNotFoundException ignored) {
                    continue;
                }
                long total = 0;
                for (int pid : entry.getValue()) {
                    total += psRssByPid.getOrDefault(pid, 0L);
                }
                runningPackages.add(packageName);
                psRssMap.put(packageName, total);
            }

            if (runningPackages.isEmpty()) {

                String psOutput = shellManager.runShellCommandAndGetFullOutput(
                        "ps -A -o rss,name | grep '\\.'");
                if (psOutput == null || psOutput.trim().isEmpty()) {

                    if (onComplete != null)
                        handler.post(onComplete);
                    return;
                }
                try (BufferedReader reader = new BufferedReader(new StringReader(psOutput))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.trim().split("\\s+", 2);
                        if (parts.length < 2) continue;
                        String rssStr = parts[0].trim();
                        String fullName = parts[1].trim();
                        String packageName = fullName.contains(":")
                                ? fullName.substring(0, fullName.indexOf(":"))
                                : fullName;
                        if (packageName.isEmpty() || !packageName.contains(".")) continue;
                        try {
                            pm.getApplicationInfo(packageName, 0);
                            runningPackages.add(packageName);
                            try {
                                long rssKb = Long.parseLong(rssStr);
                                psRssMap.put(packageName, rssKb);
                            } catch (NumberFormatException ignored) {
                            }
                        } catch (PackageManager.NameNotFoundException ignored) {
                        }
                    }
                } catch (IOException ignored) {
                }
            }



            String currentKeyboard = ProtectedApps.getCurrentKeyboardPackage(context);
            String currentLauncher = ProtectedApps.getCurrentLauncherPackage(context);

            List<String> toKill = runningPackages.stream()
                    .filter(pkg -> {
                        try {
                            if (hiddenApps.contains(pkg)) {

                                return false;
                            }
                            if (ProtectedApps.isProtected(pkg, currentKeyboard, currentLauncher)) {

                                return false;
                            }
                            if (extraWhitelist != null && extraWhitelist.contains(pkg)) {

                                return false;
                            }
                            if (!presetActive && scheduler != null && scheduler.isProtected(pkg, RestrictionsScheduler.PROTECT_AUTO_KILL)) {

                                return false;
                            }
                            if (containsPackage(dumpOutput, pkg)) {

                                return false;
                            }
                            ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                            boolean persistent =
                                    (appInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
                            if (persistent) {
                                return false;
                            }

                            AppPolicy explicitPolicy = explicitPolicies.get(pkg);
                            if (scheduleOverlay != null && scheduleOverlay.targets(pkg, explicitPolicy)) {
                                return true;
                            }
                            // The schedule's lists replace the legacy fallback while it is active.
                            AppPolicyResolver.LegacyState legacyState = scheduleOverlay != null ? null
                                    : new AppPolicyResolver.LegacyState(
                                            legacyAutoKillEnabled,
                                            legacySmartEnabled,
                                            killMode == 0,
                                            whitelistedApps.contains(pkg),
                                            blacklistedApps.contains(pkg));
                            return AppPolicyResolver.shouldExecuteImmediate(
                                    explicitPolicy,
                                    migrationSnapshotCurrent ? null : legacyState,
                                    trigger);
                        } catch (PackageManager.NameNotFoundException e) {

                            return false;
                        }
                    })
                    .collect(Collectors.toCollection(ArrayList::new));

            long protectionCheckAt = android.os.SystemClock.elapsedRealtime();
            toKill.removeIf(pkg -> sparedPackages.isSpared(pkg, protectionCheckAt));
            if (!toKill.isEmpty()) {
                ProtectionDumps dumps = ProtectionDumps.collect(shellManager);
                toKill.removeIf(pkg -> {
                    if (dumps.reasonFor(context, pkg, explicitPolicies.get(pkg)) == null) {
                        return false;
                    }
                    sparedPackages.spare(pkg, protectionCheckAt);
                    return true;
                });
            }



            Map<String, Long> pendingRss = loadPendingRss();
            Map<String, Long> confirmedFreedKb = new HashMap<>();
            for (Map.Entry<String, Long> entry : pendingRss.entrySet()) {
                String pkg = entry.getKey();
                if (!psRssMap.containsKey(pkg)) {
                    confirmedFreedKb.put(pkg, entry.getValue());

                } else {

                }
            }
            if (!confirmedFreedKb.isEmpty()) {
                recordConfirmedRam(confirmedFreedKb);
            }

            if (!toKill.isEmpty()) {
                recordSuccessfulKills(toKill, null, source);

                // PSS costs one privileged query per process, so it is fetched only
                // for the kill candidates. RSS from ps covers everything else.
                Map<Integer, Long> pssByPid = new HashMap<>();
                int[] killPids = toKill.stream()
                        .flatMap(pkg -> pidsByPackage.getOrDefault(pkg, Collections.emptyList()).stream())
                        .mapToInt(Integer::intValue)
                        .toArray();
                for (com.gree1d.reappzuku.core.shell.ProcessMemoryInfo info
                        : shellManager.getProcessMemoryInfo(killPids)) {
                    pssByPid.put(info.pid, info.totalPssKb);
                }
                if (!pssByPid.isEmpty()) {
                    for (String pkg : toKill) {
                        long total = 0;
                        for (int pid : pidsByPackage.getOrDefault(pkg, Collections.emptyList())) {
                            total += pssByPid.getOrDefault(pid, psRssByPid.getOrDefault(pid, 0L));
                        }
                        psRssMap.put(pkg, total);
                    }
                }

                Map<String, Long> newPendingRss = new HashMap<>();
                for (String pkg : toKill) {
                    long rssKb = psRssMap.getOrDefault(pkg, 0L);
                    if (rssKb > 0) {
                        newPendingRss.put(pkg, rssKb);

                    }
                }
                savePendingRss(newPendingRss);

                List<String> forceStopPackages = new ArrayList<>();
                List<String> amKillPackages = new ArrayList<>();
                int legacyKillMethod = getAutoKillType();
                for (String pkg : toKill) {
                    AppPolicy explicitPolicy = explicitPolicies.get(pkg);
                    int killMethod = AppPolicyResolver.resolveImmediateKillMethod(
                            explicitPolicy, legacyKillMethod);
                    if (killMethod == AppPolicy.KILL_METHOD_AM_KILL) {
                        amKillPackages.add(pkg);
                    } else {
                        forceStopPackages.add(pkg);
                    }
                }
                if (!forceStopPackages.isEmpty()) {
                    privilegedShell.killPackagesAndGetFullOutput(
                            forceStopPackages, PrivilegedShell.KillMode.FORCE_STOP);
                }
                if (!amKillPackages.isEmpty()) {
                    privilegedShell.killPackagesAndGetFullOutput(
                            amKillPackages, PrivilegedShell.KillMode.KILL);
                }
                killOrphanShellProcesses(new HashSet<>(toKill));

                sendKillNotification(toKill.size());

                long totalRssKb = 0;
                for (String pkg : toKill) {
                    totalRssKb += psRssMap.getOrDefault(pkg, 0L);
                }
                final long finalTotalRssKb = totalRssKb;
                final int finalKillCount = toKill.size();
                if (onResult != null) {
                    handler.post(() -> onResult.accept(finalKillCount, finalTotalRssKb));
                }

                scheduleRelaunchCheck(toKill);
            } else {
                savePendingRss(new HashMap<>());
                if (onResult != null) {
                    handler.post(() -> onResult.accept(0, 0L));
                }
            }

            if (onComplete != null)
                handler.post(onComplete);
        });
    }

    private void scheduleRelaunchCheck(List<String> recentlyKilled) {
        if (recentlyKilled == null || recentlyKilled.isEmpty()) return;

        List<String> snapshot = new ArrayList<>(new HashSet<>(recentlyKilled));
        long killedAt = System.currentTimeMillis();
        relaunchHandler.postDelayed(() -> executeIfAlive(() -> {
            com.gree1d.reappzuku.db.AppDatabase db =
                    com.gree1d.reappzuku.db.AppDatabase.getInstance(context);
            List<String> stillStopped = new ArrayList<>(snapshot);
            stillStopped.removeAll(checkRelaunches(snapshot, db));
            if (stillStopped.isEmpty()) return;
            // Many apps come back through a job or an alarm later than a few seconds.
            relaunchHandler.postDelayed(() -> executeIfAlive(() -> {
                Map<String, Long> lastUsed = com.gree1d.reappzuku.core.UsageAccess
                        .lastOpenedByPackage(context, System.currentTimeMillis());
                // Without usage data an app the user opened in the meantime
                // cannot be told from one that restarted itself.
                if (lastUsed == null) return;
                stillStopped.removeIf(pkg -> lastUsed.getOrDefault(pkg, 0L) >= killedAt);
                checkRelaunches(stillStopped, db);
            }), RELAUNCH_LATE_CHECK_DELAY_MS - RELAUNCH_CHECK_DELAY_MS);
        }), RELAUNCH_CHECK_DELAY_MS);
    }

    /** The service shuts its executor down on destroy; a check that is still pending then is dropped. */
    private void executeIfAlive(Runnable task) {
        try {
            executor.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Nothing left to record the result for.
        }
    }

    private Set<String> checkRelaunches(List<String> recentlyKilled,
            com.gree1d.reappzuku.db.AppDatabase db) {
        if (recentlyKilled.isEmpty()) return Collections.emptySet();
        String psOutput = shellManager.runShellCommandAndGetFullOutput("ps -A -o name | grep '\\.'");
        Set<String> relaunched = RelaunchDetector.findRelaunchedPackages(recentlyKilled, psOutput);
        if (relaunched.isEmpty()) return relaunched;

        long now = System.currentTimeMillis();
        int day = com.gree1d.reappzuku.core.DailyUsageMath.dayKey(now, java.util.TimeZone.getDefault());
        for (String packageName : relaunched) {
            db.appStatsDao().incrementRelaunch(packageName, now);
            db.dailyUsageDao().accumulate(packageName, day, 0, 0, 0, 1);
        }
        reportRelaunches(relaunched);
        return relaunched;
    }

    private void reportRelaunches(Set<String> relaunched) {
        if (relaunched == null || relaunched.isEmpty()) return;

        final String message;
        if (relaunched.size() == 1) {
            String packageName = relaunched.iterator().next();
            String appName = resolveInstalledAppName(context.getPackageManager(), packageName);
            message = context.getString(R.string.stats_relaunch_detected_single,
                    appName != null ? appName : packageName);
        } else {
            message = context.getString(
                    R.string.stats_relaunch_detected_multiple,
                    relaunched.size());
        }

        if (ShappkyService.isRunning()) {
            ShappkyService.updateNotification(
                    context,
                    context.getString(R.string.stats_relaunch_detected_title),
                    message);
        } else {
            handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
        }
    }

    public void killPackages(List<String> packageNames, Runnable onComplete) {
        if (!shellManager.hasAnyShellPermission()) {
            shellManager.checkShellPermissions();
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }

        if (packageNames == null || packageNames.isEmpty()) {
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }
        long totalKb = 0;
        Map<String, Long> recoveredKbByPackage = new HashMap<>();
        for (String pkg : packageNames) {
            long appRamKb = 0;
            for (AppModel app : currentAppsList) {
                if (app.getPackageName().equals(pkg)) {
                    appRamKb = app.getAppRamBytes();
                    break;
                }
            }
            if (appRamKb > 0) {
                recoveredKbByPackage.put(pkg, appRamKb);
                totalKb += appRamKb;
            }
        }

        final PrivilegedShell.KillMode killMode =
                PrivilegedShell.KillMode.fromAutoKillType(getAutoKillType());

        final long finalTotalKb = totalKb;
        final List<String> packagesToLog = new ArrayList<>(packageNames);
        final Map<String, Long> recoveredToLog = new HashMap<>(recoveredKbByPackage);
        privilegedShell.killPackages(packageNames, killMode, () -> {
            executor.execute(() -> {
                recordSuccessfulKills(packagesToLog, recoveredToLog, "Manual Kill");
                killOrphanShellProcesses(new HashSet<>(packagesToLog));
                scheduleRelaunchCheck(packagesToLog);
            });
            Toast.makeText(context, context.getString(R.string.toast_free_up, formatMemorySize(finalTotalKb)), Toast.LENGTH_LONG).show();
            if (onComplete != null) {
                onComplete.run();
            }
        }, () -> {
            Toast.makeText(context, context.getString(R.string.bg_manager_kill_failed), Toast.LENGTH_SHORT).show();
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void killApp(String packageName, Runnable onComplete) {
        killApp(packageName, "Manual Kill", onComplete);
    }

    public void killApp(String packageName, String source, Runnable onComplete) {
        if (!shellManager.hasAnyShellPermission()) {
            shellManager.checkShellPermissions();
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }
        if (!PackageNameValidator.isValid(packageName)) {
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }
        long appRamBytes = 0;
        for (AppModel app : currentAppsList) {
            if (app.getPackageName().equals(packageName)) {
                appRamBytes = app.getAppRamBytes();
                break;
            }
        }
        final String packageToKill = packageName;
        final Map<String, Long> recoveredKbByPackage = new HashMap<>();
        if (appRamBytes > 0) {
            recoveredKbByPackage.put(packageToKill, appRamBytes);
        }
        final long finalAppRamBytes = appRamBytes;
        privilegedShell.killPackage(packageToKill,
                PrivilegedShell.KillMode.fromAutoKillType(getAutoKillType()), () -> {
            executor.execute(() -> {
                List<String> killedPackages = Collections.singletonList(packageToKill);
                recordSuccessfulKills(killedPackages, recoveredKbByPackage, source);
                killOrphanShellProcesses(Collections.singleton(packageToKill));
                scheduleRelaunchCheck(killedPackages);
            });
            if ("Shortcut Kill".equals(source)) {
                String appLabel = resolveInstalledAppName(context.getPackageManager(), packageToKill);
                String displayName = appLabel != null ? appLabel : packageToKill;
                Toast.makeText(context, context.getString(R.string.toast_shortcut_killed, displayName), Toast.LENGTH_LONG).show();
            } else if (finalAppRamBytes > 0) {
                Toast.makeText(context, context.getString(R.string.toast_free_up, formatMemorySize(finalAppRamBytes)), Toast.LENGTH_LONG).show();
            }
            if (onComplete != null) {
                onComplete.run();
            }
        }, () -> {
            Toast.makeText(context, context.getString(R.string.toast_failed_stop_app, packageToKill), Toast.LENGTH_SHORT).show();
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void uninstallPackage(String packageName, Runnable onComplete) {
        if (!shellManager.hasAnyShellPermission()) {
            shellManager.checkShellPermissions();
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }
        if (!PackageNameValidator.isValid(packageName)) {
            if (onComplete != null) {
                handler.post(onComplete);
            }
            return;
        }

        privilegedShell.uninstallPackage(packageName, () -> {
            Toast.makeText(context, context.getString(R.string.toast_uninstall_sent, packageName), Toast.LENGTH_SHORT).show();
            if (onComplete != null) {
                onComplete.run();
            }
        }, () -> {
            Toast.makeText(context, context.getString(R.string.toast_failed_uninstall, packageName), Toast.LENGTH_SHORT).show();
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private void sendKillNotification(int count) {
        ShappkyService.updateNotification(context,
                context.getString(R.string.bg_manager_auto_kill_active),
                context.getString(R.string.bg_manager_stopped_apps, count));
    }

    public void recordQuickTileKill(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        List<String> killedPackages = Collections.singletonList(packageName);
        recordSuccessfulKills(killedPackages, null, "Quick Tile");
        scheduleRelaunchCheck(killedPackages);
    }

    private void recordSuccessfulKills(List<String> packageNames,
            Map<String, Long> recoveredKbByPackage, String source) {
        if (packageNames == null || packageNames.isEmpty()) {
            return;
        }

        com.gree1d.reappzuku.db.AppStatsDao appStatsDao =
                com.gree1d.reappzuku.db.AppDatabase.getInstance(context).appStatsDao();
        PackageManager packageManager = context.getPackageManager();
        long now = System.currentTimeMillis();

        Set<String> uniquePackages = new HashSet<>(packageNames);
        int newEntries = uniquePackages.size();

        int currentCount = appStatsDao.getCount();
        int excess = (currentCount + newEntries) - STATS_LIMIT;
        if (excess > 0) {
            appStatsDao.deleteOldestStats(excess);

        }

        for (String packageName : uniquePackages) {
            if (packageName == null || packageName.isEmpty()) {
                continue;
            }

            String appName = resolveInstalledAppName(packageManager, packageName);

            if (appName != null && !appName.trim().isEmpty()) {
                appStatsDao.updateAppName(packageName, appName);
            }

            com.gree1d.reappzuku.db.AppStats stats =
                    new com.gree1d.reappzuku.db.AppStats(packageName);
            stats.appName = appName;
            stats.lastKillTime = now;
            stats.lastKillSource = source;
            stats.relaunchCount = 0;
            stats.lastRelaunchTime = 0;

            long recoveredKb = recoveredKbByPackage != null
                    ? recoveredKbByPackage.getOrDefault(packageName, 0L)
                    : 0L;
            stats.totalRecoveredKb = recoveredKb;

            appStatsDao.insert(stats);
            // The kill log can be cleared by the user; the daily history keeps its own count.
            com.gree1d.reappzuku.db.AppDatabase.getInstance(context).dailyUsageDao().accumulate(
                    packageName, com.gree1d.reappzuku.core.DailyUsageMath.dayKey(
                            now, java.util.TimeZone.getDefault()), 0, 0, 1, 0);


        }
    }

    private void recordConfirmedRam(Map<String, Long> confirmedFreedKb) {
        if (confirmedFreedKb == null || confirmedFreedKb.isEmpty()) return;
        com.gree1d.reappzuku.db.AppStatsDao appStatsDao =
                com.gree1d.reappzuku.db.AppDatabase.getInstance(context).appStatsDao();
        for (Map.Entry<String, Long> entry : confirmedFreedKb.entrySet()) {
            appStatsDao.addRecoveredKb(entry.getKey(), entry.getValue());

        }
    }

    private String resolveInstalledAppName(PackageManager packageManager, String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(appInfo);
            if (label != null) {
                return label.toString();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return null;
    }

    private void killOrphanShellProcesses(Set<String> targetPackages) {
        String psOutput = shellManager.runShellCommandAndGetFullOutput(
                "ps -A -o user,pid,ppid,name | grep '\\.'");
        if (psOutput == null || psOutput.trim().isEmpty()) return;

        Map<String, String> appProcessPids = new HashMap<>();
        Map<String, List<String>> orphanCandidatePids = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(psOutput))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 4) continue;
                String user = parts[0].trim();
                String pid = parts[1].trim();
                String ppid = parts[2].trim();
                String fullName = parts[3].trim();
                String packageName = fullName.contains(":")
                        ? fullName.substring(0, fullName.indexOf(":"))
                        : fullName;
                if (packageName.isEmpty() || !packageName.contains(".")) continue;
                if (targetPackages != null && !targetPackages.contains(packageName)) continue;
                if (user.equals("shell") && ppid.equals("1") && fullName.contains(":")) {
                    orphanCandidatePids.computeIfAbsent(packageName, k -> new ArrayList<>()).add(pid);
                } else if (!user.equals("shell")) {
                    appProcessPids.put(packageName, pid);
                }
            }
        } catch (IOException ignored) {
        }

        List<String> toKill = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : orphanCandidatePids.entrySet()) {
            if (!appProcessPids.containsKey(entry.getKey())) {
                toKill.addAll(entry.getValue());

            }
        }
        if (!toKill.isEmpty()) {
            privilegedShell.killPidsAndGetFullOutput(toKill);

        }
    }

    private static boolean containsPackage(String output, String packageName) {
        if (output == null || packageName == null) return false;

        int idx = output.indexOf(packageName);
        if (idx == -1) {

            return false;
        }

        while (idx != -1) {
            int end = idx + packageName.length();
            boolean endOk = end >= output.length()
                    || !Character.isLetterOrDigit(output.charAt(end)) && output.charAt(end) != '.';
            boolean startOk = idx == 0
                    || !Character.isLetterOrDigit(output.charAt(idx - 1)) && output.charAt(idx - 1) != '.';
            if (startOk && endOk) {
                int from = Math.max(0, idx - 40);
                int to = Math.min(output.length(), end + 40);

                return true;
            }
            idx = output.indexOf(packageName, idx + 1);
        }


        return false;
    }

    private String formatMemorySize(long kb) {
        if (kb < 1024)
            return kb + " KB";
        else if (kb < 1024 * 1024)
            return String.format(java.util.Locale.US, "%.2f MB", kb / 1024f);
        else
            return String.format(java.util.Locale.US, "%.2f GB", kb / (1024f * 1024f));
    }

    private Map<String, Long> loadPendingRss() {
        Map<String, Long> result = new HashMap<>();
        String json = sharedpreferences.getString(KEY_AUTO_KILL_PENDING_RSS, null);
        if (json == null) return result;
        try {
            JSONObject obj = new JSONObject(json);
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String pkg = keys.next();
                result.put(pkg, obj.getLong(pkg));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    private void savePendingRss(Map<String, Long> pendingRss) {
        JSONObject obj = new JSONObject();
        try {
            for (Map.Entry<String, Long> entry : pendingRss.entrySet()) {
                obj.put(entry.getKey(), entry.getValue());
            }
        } catch (JSONException ignored) {
        }
        sharedpreferences.edit().putString(KEY_AUTO_KILL_PENDING_RSS, obj.toString()).apply();
    }

    public void clearPendingRss() {
        sharedpreferences.edit().remove(KEY_AUTO_KILL_PENDING_RSS).apply();
    }

    public Set<String> getHiddenApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_HIDDEN_APPS, new HashSet<>()));
    }

    public void saveHiddenApps(Set<String> hiddenApps) {
        sharedpreferences.edit().putStringSet(KEY_HIDDEN_APPS, new HashSet<>(hiddenApps)).apply();
    }

    public Set<String> getWhitelistedApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_WHITELISTED_APPS, new HashSet<>()));
    }

    public Set<String> getBlacklistedApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_BLACKLISTED_APPS, new HashSet<>()));
    }

    public int getKillMode() {
        return sharedpreferences.getInt(KEY_KILL_MODE, DEFAULT_KILL_MODE);
    }

    public int getAutoKillType() {
        return sharedpreferences.getInt(KEY_AUTO_KILL_TYPE, 0);
    }

    public void setAutoKillType(int type) {
        sharedpreferences.edit().putInt(KEY_AUTO_KILL_TYPE, type).apply();
    }
}
