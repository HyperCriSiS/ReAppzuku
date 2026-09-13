package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.core.PrivilegedShell;
import com.gree1d.reappzuku.utils.SleepModeLogManager;
import com.gree1d.reappzuku.utils.BackgroundRestrictionLog;

public class RestrictionsWatchdogManager {

    private static final String FILE_NAME = "RestrictionsWatchdogManager";
    private static final long WATCHDOG_INTERVAL_MS = 35 * 60 * 1000L; 

    private static final Pattern SLEEP_PACKAGES_SECTION =
            Pattern.compile("(?m)^\\s*Packages:\\n(?:(?!^\\S).*\\n?)*");
    private static final Pattern SLEEP_USER0_BLOCK =
            Pattern.compile("(?m)^\\s*User 0:.*(?:\\n(?!\\s*User \\d+:).*)*");
    private static final Pattern SLEEP_SUSPENDED = Pattern.compile("\\bsuspended=(true|false)\\b");
    private static final Pattern SLEEP_ENABLED = Pattern.compile("\\benabled=(\\d+)\\b");
    private static final Pattern BUCKET_LINE = Pattern.compile("(?m)^([\\w.]+):\\s*(\\d+)\\s*$");

    private final Context context;
    private final Handler handler;
    private final BackgroundAppManager appManager;
    private final ShellManager shellManager;
    private final PrivilegedShell privilegedShell;
    private final RestrictionsScheduler scheduler;

    private SleepModeManager sleepModeManager;

    private boolean running = false;

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            runCheck();
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    public RestrictionsWatchdogManager(Context context, Handler handler,
            BackgroundAppManager appManager, ShellManager shellManager,
            RestrictionsScheduler scheduler) {
        this.context      = context;
        this.handler      = handler;
        this.appManager       = appManager;
        this.shellManager     = shellManager;
        this.privilegedShell  = new PrivilegedShell(shellManager);
        this.scheduler        = scheduler;
    }

    public void setSleepModeManager(SleepModeManager sleepModeManager) {
        this.sleepModeManager = sleepModeManager;
    }

    public void startIfNeeded() {
        if (running) return;
        if (!shellManager.hasAnyShellPermission()) return;

        boolean hasBackgroundTargets = appManager.supportsBackgroundRestriction()
                && !appManager.getBackgroundRestrictedApps().isEmpty();
        boolean hasSleepTargets = sleepModeManager != null
                && !sleepModeManager.getPermanentFreezeApps().isEmpty();

        if (!hasBackgroundTargets && !hasSleepTargets) {

            return;
        }
        running = true;
        handler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS);

    }

    public void stop() {
        running = false;
        handler.removeCallbacks(watchdogRunnable);

    }


    private void runCheck() {
        if (!shellManager.hasAnyShellPermission()) {

            return;
        }

        Set<String> desired = new java.util.HashSet<>();
        boolean backgroundRestrictionActive = false;
        if (appManager.supportsBackgroundRestriction()) {
            desired = appManager.sanitizeBackgroundRestrictionTargets(
                    appManager.getBackgroundRestrictedApps());
            backgroundRestrictionActive = !desired.isEmpty();
        }

        boolean sleepModeActive = sleepModeManager != null
                && !sleepModeManager.getPermanentFreezeApps().isEmpty();

        if (!backgroundRestrictionActive && !sleepModeActive) {
            stop();

            return;
        }

        if (backgroundRestrictionActive) {
            appManager.checkAndRepairRestrictions(desired, scheduler);
            checkAndRepairBuckets(desired);
        }

        if (sleepModeActive) {
            checkAndRepairSleepMode();
        }
    }

    private boolean isMediumLikeManual(String packageName) {
        int mask = appManager.getManualOpsMask(packageName);
        int mediumMask = 0;
        for (int i = 0; i < BackgroundAppManager.ALL_OPS.length; i++) {
            for (String medOp : BackgroundAppManager.MEDIUM_OPS) {
                if (BackgroundAppManager.ALL_OPS[i].equals(medOp)) {
                    mediumMask |= (1 << i);
                    break;
                }
            }
        }
        int overlap = Integer.bitCount(mask & mediumMask);
        return overlap >= 3;
    }

    private boolean isAppForeground(String packageName) {
        android.app.ActivityManager am =
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (android.app.ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (info.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && java.util.Arrays.asList(info.pkgList).contains(packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAppForegroundService(String packageName) {
        android.app.ActivityManager am =
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (android.app.ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (info.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
                    && java.util.Arrays.asList(info.pkgList).contains(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void checkAndRepairSleepMode() {
        Set<String> permanent = sleepModeManager.getPermanentFreezeApps();

        String fullDump = shellManager.runShellCommandAndGetFullOutput("dumpsys package");
        if (fullDump == null || fullDump.trim().isEmpty()) {

            return;
        }

        Matcher packagesSectionMatcher = SLEEP_PACKAGES_SECTION.matcher(fullDump);
        String packagesSection = packagesSectionMatcher.find()
                ? packagesSectionMatcher.group() : fullDump;

        for (String pkg : permanent) {
            if (scheduler != null
                    && scheduler.isProtected(pkg, RestrictionsScheduler.PROTECT_SLEEP_MODE)) {

                continue;
            }

            Pattern pkgBlockPattern = Pattern.compile(
                    "(?m)^\\s*Package \\[" + Pattern.quote(pkg) + "\\].*\\n(?:(?!^\\s*Package \\[).*\\n?)*");
            Matcher pkgBlockMatcher = pkgBlockPattern.matcher(packagesSection);
            if (!pkgBlockMatcher.find()) continue;
            String pkgBlock = pkgBlockMatcher.group();

            Matcher userBlockMatcher = SLEEP_USER0_BLOCK.matcher(pkgBlock);
            if (!userBlockMatcher.find()) continue;
            String userBlock = userBlockMatcher.group();

            boolean isSystem = sleepModeManager.isSystemPackage(pkg);
            SleepModeManager.FreezeMethod method = sleepModeManager.getFreezeMethod(pkg);
            boolean drifted;
            if (method == SleepModeManager.FreezeMethod.SUSPEND) {
                Matcher suspendedMatcher = SLEEP_SUSPENDED.matcher(userBlock);
                drifted = !suspendedMatcher.find() || !"true".equals(suspendedMatcher.group(1));
            } else {
                Matcher enabledMatcher = SLEEP_ENABLED.matcher(userBlock);
                int enabledState = enabledMatcher.find()
                        ? Integer.parseInt(enabledMatcher.group(1)) : -1;
                drifted = enabledState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
            }

            if (!drifted) continue;


            boolean ok = sleepModeManager.reapplyPermanentFreeze(pkg);
            SleepModeLogManager.logFreeze(context, pkg, ok, "WatchDog Repair", method, SleepModeManager.FreezeType.PERMANENT);
        }
    }

    private java.util.Map<String, Integer> fetchAllStandbyBuckets() {
        String out = shellManager.runShellCommandAndGetFullOutput("am get-standby-bucket");
        if (out == null || out.trim().isEmpty()) {
            return null;
        }

        java.util.Map<String, Integer> buckets = new java.util.HashMap<>();
        Matcher m = BUCKET_LINE.matcher(out);
        while (m.find()) {
            try {
                buckets.put(m.group(1), Integer.parseInt(m.group(2)));
            } catch (NumberFormatException ignored) {
                // skip malformed line
            }
        }

        if (buckets.isEmpty()) {

            return null;
        }


        return buckets;
    }

    private void checkAndRepairBuckets(Set<String> desired) {
        Set<String> hardSet   = appManager.getHardRestrictedApps();
        Set<String> mediumSet = appManager.getMediumRestrictedApps();
        Set<String> manualSet = appManager.getManualRestrictedApps();

        java.util.Map<String, Integer> currentBuckets = fetchAllStandbyBuckets();
        if (currentBuckets == null) {

            return;
        }

        for (String pkg : desired) {
            if (scheduler != null
                    && scheduler.isProtected(pkg, RestrictionsScheduler.PROTECT_BG_RESTRICTIONS)) {

                continue;
            }

            int required;
            if (hardSet.contains(pkg)) {
                if (isAppForeground(pkg)) {

                    continue;
                }
                required = 45;
            } else if (mediumSet.contains(pkg)) {
                if (isAppForeground(pkg) || isAppForegroundService(pkg)) {

                    continue;
                }
                required = 40;
            } else if (manualSet.contains(pkg)) {
                if (isAppForeground(pkg)) {

                    continue;
                }
                required = appManager.getManualBucket(pkg);
                if (required == 40 && isMediumLikeManual(pkg) && isAppForegroundService(pkg)) {

                    continue;
                }
            } else {
                continue;
            }
            if (required == 0) continue;

            Integer current = currentBuckets.get(pkg);
            if (current == null) {

                continue;
            }

            if (current == required) continue;


            boolean ok;
            try {
                ok = privilegedShell.setStandbyBucket(
                        pkg, PrivilegedShell.StandbyBucket.fromLegacyValue(required)).succeeded();
            } catch (IllegalArgumentException e) {

                ok = false;
            }
            BackgroundRestrictionLog.log(context, pkg, "watchdog-bucket",
                    ok ? "ok" : "failed",
                    "was=" + current + " set=" + required);
            if (hardSet.contains(pkg)) {
                appManager.ensureBatteryWhitelistRestriction(pkg);
            } else if (manualSet.contains(pkg) && appManager.getManualWhitelistRemoval(pkg)) {
                appManager.ensureBatteryWhitelistRestriction(pkg);
            }
        }
    }
}
