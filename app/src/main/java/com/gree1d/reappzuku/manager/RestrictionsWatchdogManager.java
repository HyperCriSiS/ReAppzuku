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

    private static final Pattern SLEEP_PACKAGE_HEADER = Pattern.compile("(?m)^\\s*Package \\[");
    private static final Pattern SLEEP_USER_HEADER = Pattern.compile("(?m)^\\s*User \\d+:");
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
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS);
            executor.execute(RestrictionsWatchdogManager.this::runCheck);
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
            // Stop on the thread that starts it, then look again: a target may
            // have been added while this check was running.
            handler.post(() -> {
                stop();
                startIfNeeded();
            });

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

        for (String pkg : permanent) {
            if (scheduler != null
                    && scheduler.isProtected(pkg, RestrictionsScheduler.PROTECT_SLEEP_MODE)) {

                continue;
            }

            String userBlock = userZeroBlock(fullDump, pkg);
            if (userBlock == null) continue;

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


            // The user may have unfrozen the app since the set was read.
            if (!sleepModeManager.getPermanentFreezeApps().contains(pkg)) continue;
            boolean ok = sleepModeManager.reapplyPermanentFreeze(pkg);
            SleepModeLogManager.logFreeze(context, pkg, ok, "WatchDog Repair", method, SleepModeManager.FreezeType.PERMANENT);
        }
    }

    /**
     * The "User 0:" block of one package in "dumpsys package" output, or null.
     * Plain scanning on purpose: a regex that repeats a group over a dump of
     * this size can overflow the stack.
     */
    static String userZeroBlock(String dump, String pkg) {
        int section = dump.indexOf("\nPackages:\n");
        Matcher header = Pattern.compile("(?m)^\\s*Package \\[" + Pattern.quote(pkg) + "\\]").matcher(dump);
        if (!header.find(Math.max(section, 0))) return null;
        Matcher nextPackage = SLEEP_PACKAGE_HEADER.matcher(dump);
        int packageEnd = nextPackage.find(header.end()) ? nextPackage.start() : dump.length();

        Matcher user = SLEEP_USER_HEADER.matcher(dump).region(header.end(), packageEnd);
        while (user.find()) {
            if (!user.group().trim().equals("User 0:")) continue;
            int start = user.start();
            return dump.substring(start, user.find() ? user.start() : packageEnd);
        }
        return null;
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
            // The user may have removed the app since the set was read.
            if (!appManager.getBackgroundRestrictedApps().contains(pkg)) continue;

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
