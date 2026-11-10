package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.core.PrivilegedShell;
import com.gree1d.reappzuku.utils.SleepModeLogManager;
import com.gree1d.reappzuku.utils.BackgroundRestrictionLog;

public class RestrictionsWatchdogManager {

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
    private final ExecutorService executor;
    private final BackgroundAppManager appManager;
    private final ShellManager shellManager;
    private final PrivilegedShell privilegedShell;
    private final RestrictionsScheduler scheduler;

    private SleepModeManager sleepModeManager;

    private volatile boolean running = false;
    // Fetched at most once per check, and only when a bucket has drifted.
    private String foregroundPackage;
    private String servicesDump;

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            // The check runs shell commands and must stay off the main thread.
            executor.execute(RestrictionsWatchdogManager.this::runCheck);
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    public RestrictionsWatchdogManager(Context context, Handler handler, ExecutorService executor,
            BackgroundAppManager appManager, ShellManager shellManager,
            RestrictionsScheduler scheduler) {
        this.context      = context;
        this.handler      = handler;
        this.executor     = executor;
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

    // ActivityManager.getRunningAppProcesses() only reports this app's own
    // processes, so the foreground state comes from the privileged shell.
    private boolean isAppForeground(String packageName) {
        if (foregroundPackage == null) {
            String parsed = SmartLifecycleTextParser.parseForegroundPackage(
                    shellManager.runShellCommandAndGetFullOutput("dumpsys activity activities"));
            foregroundPackage = parsed == null ? "" : parsed;
        }
        return foregroundPackage.equals(packageName);
    }

    private boolean isAppForegroundService(String packageName) {
        if (servicesDump == null) {
            String dump = shellManager.runShellCommandAndGetFullOutput("dumpsys activity services");
            servicesDump = dump == null ? "" : dump;
        }
        return PackageTextMatcher.containsMarkerNearPackage(
                servicesDump, packageName, "isForeground=true", 1800);
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

        foregroundPackage = null;
        servicesDump = null;
        java.util.Map<String, Integer> currentBuckets = fetchAllStandbyBuckets();
        if (currentBuckets == null) {

            return;
        }

        for (String pkg : desired) {
            if (scheduler != null
                    && scheduler.isProtected(pkg, RestrictionsScheduler.PROTECT_BG_RESTRICTIONS)) {

                continue;
            }

            boolean hard = hardSet.contains(pkg);
            boolean medium = !hard && mediumSet.contains(pkg);
            boolean manual = !hard && !medium && manualSet.contains(pkg);
            int required = hard ? 45 : medium ? 40 : manual ? appManager.getManualBucket(pkg) : 0;
            if (required == 0) continue;

            Integer current = currentBuckets.get(pkg);
            if (current == null || current == required) continue;

            // The system raises the bucket of an app in use; that is not drift.
            if (isAppForeground(pkg)) continue;
            if (medium && isAppForegroundService(pkg)) continue;
            if (manual && required == 40 && isMediumLikeManual(pkg) && isAppForegroundService(pkg)) continue;

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
