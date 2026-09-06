package com.gree1d.reappzuku.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.core.ShellBackendState;
import com.gree1d.reappzuku.core.App;
import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.manager.AutoKillManager;
import com.gree1d.reappzuku.manager.SleepModeManager;
import com.gree1d.reappzuku.manager.CollectStatsManager;
import com.gree1d.reappzuku.service.CollectStatsReceiver;
import com.gree1d.reappzuku.manager.RestrictionsScheduler;
import com.gree1d.reappzuku.manager.RestrictionsWatchdogManager;
import com.gree1d.reappzuku.manager.AdditionalScenariosManager;
import com.gree1d.reappzuku.manager.RamKillShortcutManager;
import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.manager.UpdateChecker;
import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.utils.AppzukuWidget;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.BackgroundWorkPolicy;
import com.gree1d.reappzuku.core.SleepModeLifecyclePolicy;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;
import static com.gree1d.reappzuku.core.AppConstants.*;

public class ShappkyService extends Service {

    private static final String FILE_NAME = "ShappkyService";
    static final String ACTION_IDLE_FREEZE = "com.gree1d.reappzuku.IDLE_FREEZE";
    static final String ACTION_HEARTBEAT_CHECK = "com.gree1d.reappzuku.HEARTBEAT_CHECK";
    public static final String ACTION_SLEEP_MODE_DISABLED = "com.gree1d.reappzuku.SLEEP_MODE_DISABLED";
    private static final int FREEZE_ALARM_REQUEST_CODE = 1001;
    private static final int RESTART_ALARM_REQUEST_CODE = 1002;
    private static final int HEARTBEAT_ALARM_REQUEST_CODE = 1003;
    private static final int SNAPSHOT_ALARM_REQUEST_CODE = 1004;
    private static final long HEARTBEAT_INTERVAL_MS = 2 * 60 * 1000L;
    private static final long RAM_NOTIFICATION_UPDATE_INTERVAL_MS = 15 * 1000L;
    private static final int MAX_PENDING_START_INTENTS = 32;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Intent> pendingStartIntents = new ArrayDeque<>();
    private static boolean isRunning = false;

    private ShellManager shellManager;
    private BackgroundAppManager appManager;
    private AutoKillManager autoKillManager;
    private SleepModeManager sleepModeManager;
    private CollectStatsManager collectStatsManager;
    private RestrictionsScheduler scheduler;
    private KillTriggerReceiver screenOffReceiver;
    private BroadcastReceiver packageChangeReceiver;
    private RestrictionsWatchdogManager watchdog;
    private AdditionalScenariosManager additionalScenariosManager;
    private RamKillShortcutManager ramKillShortcutManager;

    private boolean managersInitialized = false;
    private boolean shizukuLostNotificationShown = false;
    private Runnable ramNotificationRunnable;

    public static boolean isRunning() {
        return isRunning;
    }

    private boolean isRamMonitorNotificationEnabled() {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_NOTIFICATION_MODE, NOTIFICATION_MODE_ALL);
        return mode == NOTIFICATION_MODE_ALL || (mode & NOTIFICATION_MODE_RAM_MONITOR) != 0;
    }

    private void startRamMonitorNotification() {
        if (!isRamMonitorNotificationEnabled()) {
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": startRamMonitorNotification: skipped, notification mode disabled");
            return;
        }
        if (ramNotificationRunnable != null) {
            return;
        }
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": startRamMonitorNotification: starting");
        ramNotificationRunnable = new Runnable() {
            @Override
            public void run() {
                if (ramNotificationRunnable != this) {
                    return;
                }
                executor.execute(() -> {
                    long[] ram = readRamUsageMb();
                    if (ram != null) {
                        updateRamMonitorNotification(ram[0], ram[1]);
                    }
                    handler.postDelayed(this, RAM_NOTIFICATION_UPDATE_INTERVAL_MS);
                });
            }
        };
        handler.post(ramNotificationRunnable);
    }

    private void stopRamMonitorNotification() {
        if (ramNotificationRunnable != null) {
            handler.removeCallbacks(ramNotificationRunnable);
            ramNotificationRunnable = null;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID_RAM_MONITOR);
        }
    }

    private PendingIntent getOpenAppPendingIntent() {
        return getOpenAppPendingIntent(this);
    }

    private static PendingIntent getOpenAppPendingIntent(Context context) {
        Intent intent = new Intent(context, com.gree1d.reappzuku.ui.MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void updateRamMonitorNotification(long usedMb, long totalMb) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
                .setContentTitle(getString(R.string.ram_usage, usedMb, totalMb))
                .setSmallIcon(R.drawable.ic_shappky)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(getOpenAppPendingIntent());
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID_RAM_MONITOR, builder.build());
        }
    }

    private long[] readRamUsageMb() {
        try (java.io.RandomAccessFile reader = new java.io.RandomAccessFile("/proc/meminfo", "r")) {
            String line;
            long memTotal = 0;
            long memAvailable = 0;
            for (int i = 0; i < 3 && (line = reader.readLine()) != null; i++) {
                if (line.startsWith("MemTotal")) {
                    memTotal = parseMemValue(line);
                } else if (line.startsWith("MemAvailable")) {
                    memAvailable = parseMemValue(line);
                }
            }
            if (memTotal > 0) {
                long memUsed = memTotal - memAvailable;
                return new long[] { memUsed / 1024, memTotal / 1024 };
            }
            AppDebugManager.w(Category.FOREGROUND_SERVICE, FILE_NAME + ": readRamUsageMb: MemTotal not found or zero");
        } catch (IOException | NumberFormatException e) {
            AppDebugManager.w(Category.FOREGROUND_SERVICE, FILE_NAME + ": readRamUsageMb: failed to read RAM usage", e);
        }
        return null;
    }

    private long parseMemValue(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 2) {
            return Long.parseLong(parts[1]);
        }
        return 0;
    }

    public static void updateNotification(Context context, String title, String text) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_NOTIFICATION_MODE, NOTIFICATION_MODE_ALL);
        if (mode != NOTIFICATION_MODE_ALL && (mode & NOTIFICATION_MODE_AUTO_KILL) == 0) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_shappky)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(getOpenAppPendingIntent(context));
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID_SERVICE, builder.build());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": onCreate started");

        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text))
                .setSmallIcon(R.drawable.ic_shappky)
                .setOngoing(true)
                .setContentIntent(getOpenAppPendingIntent())
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_SERVICE, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": startForeground called (FOREGROUND_SERVICE_TYPE_SPECIAL_USE, API " + Build.VERSION.SDK_INT + ")");
        } else {
            startForeground(NOTIFICATION_ID_SERVICE, notification);
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": startForeground called (legacy, API " + Build.VERSION.SDK_INT + ")");
        }
        isRunning = true;
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": Service is now running (isRunning=true)");

        shellManager = ((App) getApplication()).getShellManager();

        executor.execute(() -> {
            // A granted Shizuku permission is not enough for queued service actions:
            // privileged work can begin as soon as managers are initialized. Wait for
            // the UserService itself so a fresh process cannot race an async bind.
            ShellBackendState backendState = shellManager.awaitAnyShellReadyBlocking();
            if (!backendState.isReady() && shellManager.hasShizukuPermission()) {
                // A just-destroyed predecessor can leave Shizuku's non-daemon
                // UserService record briefly in flight. One bounded retry lets that
                // teardown settle without processing privileged work prematurely.
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                backendState = shellManager.awaitAnyShellReadyBlocking();
            }
            ShellBackendState readyState = backendState;
            handler.post(() -> {
                if (!readyState.isReady()) {
                    AppDebugManager.w(Category.CORE, FILE_NAME
                            + ": Shell backend not ready (" + readyState + "), stopping service");
                    stopSelf();
                    return;
                }
                AppDebugManager.d(Category.CORE, FILE_NAME
                        + ": Shell backend ready (" + readyState + "), proceeding with service init");
                initializeManagersAndReceivers();
            });
        });
    }

    private void initializeManagersAndReceivers() {
        appManager = new BackgroundAppManager(this, handler, executor, shellManager);
        AppDebugManager.d(Category.BACKGROUND_RESTRICTIONS, FILE_NAME + ": BackgroundAppManager initialized");
        autoKillManager = new AutoKillManager(this, handler, executor, shellManager, appManager.getCurrentAppsList());
        sleepModeManager = new SleepModeManager(this, handler, executor, shellManager);
        collectStatsManager = new CollectStatsManager(this, shellManager);
        scheduler = new RestrictionsScheduler(this, handler, executor, shellManager, appManager, sleepModeManager);
        autoKillManager.setScheduler(scheduler);
        sleepModeManager.setScheduler(scheduler);
        appManager.setScheduler(scheduler);
        AppDebugManager.d(Category.BACKGROUND_RESTRICTIONS, FILE_NAME + ": BackgroundAppManager scheduler attached");
        watchdog = new RestrictionsWatchdogManager(this, handler, appManager, shellManager, scheduler);

        screenOffReceiver = new KillTriggerReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenOffReceiver, filter);

        packageChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                android.net.Uri data = intent.getData();
                if (data == null) return;
                String packageName = data.getSchemeSpecificPart();
                if (packageName == null || appManager == null) return;
                AppDebugManager.d(Category.BACKGROUND_RESTRICTIONS, FILE_NAME
                        + ": packageChangeReceiver invalidating icon cache for " + packageName
                        + " action=" + intent.getAction());
                appManager.invalidateIconCache(packageName);
            }
        };
        IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addDataScheme("package");
        registerReceiver(packageChangeReceiver, packageFilter);

        additionalScenariosManager = new AdditionalScenariosManager(this);
        AppDebugManager.d(Category.ADVANCED_CONDITIONS, FILE_NAME + ": AdditionalScenariosManager initialized");
        additionalScenariosManager.updateHardwareReceiverState();
        ramKillShortcutManager = new RamKillShortcutManager(this, shellManager);

        scheduleNextKill();
        scheduler.scheduleNext();

        cancelShizukuLostNotification();
        AppDebugManager.d(Category.CORE, FILE_NAME + ": Shizuku-lost notification cancelled on service create");
        registerShizukuBinderListeners();
        scheduleRootOnlyCheck();
        scheduleSnapshotAlarm();
        scheduleWidgetUpdate();

        AppDebugManager.d(Category.BACKGROUND_RESTRICTIONS, FILE_NAME + ": reapplySavedBackgroundRestrictions starting on service create");
        appManager.reapplySavedBackgroundRestrictions(() ->
                AppDebugManager.d(Category.BACKGROUND_RESTRICTIONS, FILE_NAME + ": reapplySavedBackgroundRestrictions finished"));
        watchdog.startIfNeeded();

        UpdateChecker.schedulePeriodicCheck(getApplicationContext());
        startRamMonitorNotification();

        reconcileSleepModeLifecycle("service-init", true);
        managersInitialized = true;
        drainPendingStartIntents();
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": onCreate completed; managersInitialized=true");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": onStartCommand: action=" + (intent != null ? intent.getAction() : "null"));
        if (intent == null) {
            AppDebugManager.w(Category.FOREGROUND_SERVICE, FILE_NAME + ": onStartCommand: intent is null, returning START_STICKY");
            return START_STICKY;
        }

        String action = intent.getAction();
        if (action == null) {
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": onStartCommand: action is null; service startup only");
            return START_STICKY;
        }

        if (!managersInitialized) {
            enqueuePendingStartIntent(intent);
            return START_STICKY;
        }

        handleServiceAction(intent);
        return START_STICKY;
    }

    private void enqueuePendingStartIntent(Intent intent) {
        if (pendingStartIntents.size() >= MAX_PENDING_START_INTENTS) {
            Intent dropped = pendingStartIntents.removeFirst();
            AppDebugManager.w(Category.FOREGROUND_SERVICE, FILE_NAME
                    + ": pending start-action queue full; dropping oldest action=" + dropped.getAction());
        }
        pendingStartIntents.addLast(new Intent(intent));
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME
                + ": queued start action until managers are ready: " + intent.getAction()
                + ", queued=" + pendingStartIntents.size());
    }

    private void drainPendingStartIntents() {
        while (managersInitialized && !pendingStartIntents.isEmpty()) {
            Intent pending = pendingStartIntents.removeFirst();
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME
                    + ": replaying queued start action=" + pending.getAction());
            handleServiceAction(pending);
        }
    }

    private void handleServiceAction(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case "TRIGGER_KILL":
                executor.execute(() -> {
                    SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
                    boolean ramThresholdEnabled = prefs.getBoolean(KEY_RAM_THRESHOLD_ENABLED, false);
                    if (ramThresholdEnabled) {
                        int threshold = prefs.getInt(KEY_RAM_THRESHOLD, DEFAULT_RAM_THRESHOLD_PERCENT);
                        int ramPercent = getCurrentRamUsagePercent();
                        AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": TRIGGER_KILL: RAM threshold check: " + ramPercent + "% / " + threshold + "%");
                        if (ramPercent >= threshold) {
                            AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": TRIGGER_KILL: threshold reached, starting Screen-Off Kill");
                            autoKillManager.performAutoKill(() -> KillTriggerReceiver.releaseAutoKillWakeLock(), resolveKillSource("Screen-Off Kill"));
                        } else {
                            AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": TRIGGER_KILL: RAM below threshold, kill skipped");
                            KillTriggerReceiver.releaseAutoKillWakeLock();
                        }
                    } else {
                        AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": TRIGGER_KILL: no RAM threshold, starting Screen-Off Kill");
                        autoKillManager.performAutoKill(() -> KillTriggerReceiver.releaseAutoKillWakeLock(), resolveKillSource("Screen-Off Kill"));
                    }
                });
                break;

            case "SCREEN_OFF": {
                boolean enabled = sleepModeManager.isSleepModeEnabled();
                boolean interactive = isScreenInteractive();
                if (SleepModeLifecyclePolicy.shouldExecuteIdleFreeze(enabled, interactive)) {
                    scheduleIdleFreezeAlarm();
                    long delayMs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                            .getLong(KEY_SLEEP_MODE_DELAY, DEFAULT_SLEEP_MODE_DELAY_MS);
                    AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Idle freeze alarm scheduled (" + (delayMs / 60000) + " min)");
                } else {
                    AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                            + ": SCREEN_OFF ignored as stale/ineligible; enabled=" + enabled + ", interactive=" + interactive);
                }
                break;
            }

            case "SCREEN_ON":
                handler.postDelayed(() -> {
                    if (!managersInitialized || sleepModeManager == null) return;
                    if (!isScreenInteractive()) {
                        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                                + ": delayed SCREEN_ON became stale because screen is off again");
                        return;
                    }
                    cancelIdleFreezeAlarm();
                    cancelHeartbeatAlarm();
                    if (sleepModeManager.hasFrozenTimerApps()) {
                        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                                + ": Screen on with owned frozen apps; unfreezing");
                        sleepModeManager.unfreezeBackgroundRestrictedApps(null);
                    } else {
                        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                                + ": Screen on before/without owned freeze; alarms cancelled");
                    }
                }, 1500);
                break;

            case "IDLE_FREEZE": {
                boolean enabled = sleepModeManager.isSleepModeEnabled();
                boolean interactive = isScreenInteractive();
                if (!SleepModeLifecyclePolicy.shouldExecuteIdleFreeze(enabled, interactive)) {
                    AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                            + ": stale/ineligible idle freeze ignored; enabled=" + enabled + ", interactive=" + interactive);
                    cancelHeartbeatAlarm();
                    if (SleepModeLifecyclePolicy.recoveryAction(
                            enabled, interactive, sleepModeManager.hasFrozenTimerApps())
                            == SleepModeLifecyclePolicy.RecoveryAction.UNFREEZE) {
                        sleepModeManager.unfreezeBackgroundRestrictedApps(null);
                    }
                    break;
                }
                AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Idle threshold reached, freezing background restricted apps");
                sleepModeManager.freezeBackgroundRestrictedApps(() -> {
                    if (sleepModeManager.hasFrozenTimerApps()) {
                        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                                + ": owned timer freeze active; scheduling recovery heartbeat");
                        scheduleHeartbeatAlarm();
                    } else {
                        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                                + ": idle freeze produced no owned frozen apps; heartbeat not needed");
                        cancelHeartbeatAlarm();
                    }
                });
                break;
            }

            case "HEARTBEAT_CHECK":
                handleHeartbeatCheck();
                break;

            case ACTION_SLEEP_MODE_DISABLED:
                AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Sleep Mode disabled; cancelling alarms and thawing owned apps");
                cancelIdleFreezeAlarm();
                cancelHeartbeatAlarm();
                if (sleepModeManager.hasFrozenTimerApps()) {
                    sleepModeManager.unfreezeBackgroundRestrictedApps(null);
                }
                break;

            case "SCHEDULER_TICK":
                scheduler.tick();
                break;

            case "WIDGET_KILL":
                ramKillShortcutManager.performKillAndUpdate(autoKillManager);
                break;

            case "SHORTCUT_KILL_FOREGROUND":
                String targetPkg = intent.getStringExtra("target_package");
                if (targetPkg != null && !targetPkg.isEmpty()) {
                    AppDebugManager.d(Category.SHORTCUTS_WIDGETS, FILE_NAME + ": SHORTCUT_KILL_FOREGROUND received for " + targetPkg);
                    autoKillManager.killApp(targetPkg, null);
                } else {
                    AppDebugManager.w(Category.SHORTCUTS_WIDGETS, FILE_NAME + ": SHORTCUT_KILL_FOREGROUND received but target_package is null");
                }
                break;

            case "UPDATE_HW_RECEIVERS":
                AppDebugManager.d(Category.ADVANCED_CONDITIONS, FILE_NAME + ": UPDATE_HW_RECEIVERS received, updating hardware receiver state");
                additionalScenariosManager.updateHardwareReceiverState();
                break;

            case "UPDATE_NOTIFICATION_MODE":
                AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": UPDATE_NOTIFICATION_MODE received, re-evaluating RAM monitor notification");
                if (isRamMonitorNotificationEnabled()) {
                    startRamMonitorNotification();
                } else {
                    stopRamMonitorNotification();
                }
                break;

            case "TAKE_SNAPSHOT":
                AppDebugManager.d(Category.UTILS, FILE_NAME + ": TAKE_SNAPSHOT received");
                collectStatsManager.takeSnapshotAsync(() -> {
                    releaseSnapshotWakeLock();
                    scheduleSnapshotAlarm();
                });
                break;

            default:
                AppDebugManager.w(Category.FOREGROUND_SERVICE, FILE_NAME + ": Unknown service action ignored: " + action);
                break;
        }
    }

    private boolean isScreenInteractive() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isInteractive();
    }

    private void reconcileSleepModeLifecycle(String reason, boolean stopWhenNotRequiredAfterThaw) {
        if (sleepModeManager == null) return;

        boolean enabled = sleepModeManager.isSleepModeEnabled();
        boolean interactive = isScreenInteractive();
        boolean hasFrozen = sleepModeManager.hasFrozenTimerApps();
        SleepModeLifecyclePolicy.RecoveryAction recovery = SleepModeLifecyclePolicy.recoveryAction(
                enabled, interactive, hasFrozen);

        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                + ": reconcileSleepModeLifecycle(" + reason + "): enabled=" + enabled
                + ", interactive=" + interactive + ", ownedFrozen=" + hasFrozen
                + ", recovery=" + recovery);

        switch (recovery) {
            case UNFREEZE:
                cancelIdleFreezeAlarm();
                cancelHeartbeatAlarm();
                sleepModeManager.unfreezeBackgroundRestrictedApps(() -> {
                    if (stopWhenNotRequiredAfterThaw && !BackgroundWorkPolicy.shouldRunForegroundService(this)) {
                        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                                + ": recovery thaw complete and no automation requires service; stopping self");
                        stopSelf();
                    }
                });
                break;
            case KEEP_FROZEN_AND_HEARTBEAT:
                cancelIdleFreezeAlarm();
                scheduleHeartbeatAlarm();
                break;
            case NONE:
            default:
                cancelHeartbeatAlarm();
                if (enabled && !interactive) {
                    // The process may have restarted after SCREEN_OFF but before the idle timer fired.
                    // Re-arm conservatively from now rather than freezing immediately.
                    scheduleIdleFreezeAlarm();
                } else {
                    cancelIdleFreezeAlarm();
                }
                break;
        }
    }

    private void registerShizukuBinderListeners() {
        AppDebugManager.d(Category.CORE, FILE_NAME + ": registerShizukuBinderListeners: subscribing to Shizuku binder events");
        shellManager.setShizukuBinderListeners(
                this::handleShizukuBinderReceived,
                this::handleShizukuBinderDead
        );
    }

    private void unregisterShizukuBinderListeners() {
        if (shellManager != null) {
            shellManager.removeShizukuBinderListeners();
        }
    }

    private void handleShizukuBinderReceived() {
        if (!isRunning) return;
        if (shellManager.hasRootAccess()) {
            AppDebugManager.d(Category.CORE, FILE_NAME + ": handleShizukuBinderReceived: root access available, ignoring");
            return;
        }
        shellManager.bindUserService();
        boolean shizukuOk = shellManager.hasShizukuPermission();
        AppDebugManager.d(Category.CORE, FILE_NAME + ": handleShizukuBinderReceived: permission=" + shizukuOk);
        if (shizukuOk) {
            if (shizukuLostNotificationShown) {
                AppDebugManager.d(Category.CORE, FILE_NAME + ": Shizuku permission restored, cancelling notification");
                shizukuLostNotificationShown = false;
            }
            cancelShizukuLostNotification();
        } else {

            if (!shizukuLostNotificationShown) {
                AppDebugManager.w(Category.CORE, FILE_NAME + ": handleShizukuBinderReceived: binder alive but permission missing, sending notification");
                shizukuLostNotificationShown = true;
            }
            sendShizukuLostNotification();
        }
    }

    private void handleShizukuBinderDead() {
        if (!isRunning) return;
        if (shellManager.hasRootAccess()) {
            AppDebugManager.d(Category.CORE, FILE_NAME + ": handleShizukuBinderDead: root access available, ignoring");
            return;
        }
        AppDebugManager.w(Category.CORE, FILE_NAME + ": handleShizukuBinderDead: Shizuku binder died, sending notification");
        if (!shizukuLostNotificationShown) {
            shizukuLostNotificationShown = true;
        }
        sendShizukuLostNotification();
    }

    private void scheduleRootOnlyCheck() {
        AppDebugManager.d(Category.CORE, FILE_NAME + ": scheduleRootOnlyCheck: starting root-only poll loop");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                if (shellManager.hasRootAccess()) {
                    if (shizukuLostNotificationShown) {
                        shizukuLostNotificationShown = false;
                        cancelShizukuLostNotification();
                    }
                }

                handler.postDelayed(this, SHIZUKU_POLL_INTERVAL_MS);
            }
        }, SHIZUKU_POLL_INTERVAL_MS);
    }

    private void sendShizukuLostNotification() {
        AppDebugManager.w(Category.CORE, FILE_NAME + ": sendShizukuLostNotification: showing Shizuku-lost notification");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_ACTIONS)
                .setContentTitle(getString(R.string.service_shizuku_lost_title))
                .setContentText(getString(R.string.service_shizuku_lost_text))
                .setSmallIcon(R.drawable.ic_shappky)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false)
                .setContentIntent(getOpenAppPendingIntent());
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID_SHIZUKU_LOST, builder.build());
        } else {
            AppDebugManager.e(Category.CORE, FILE_NAME + ": sendShizukuLostNotification: NotificationManager is null, cannot show notification");
        }
    }

    private void cancelShizukuLostNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID_SHIZUKU_LOST);
        }
    }

    private boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    private void scheduleIdleFreezeAlarm() {
        cancelIdleFreezeAlarm();
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            AppDebugManager.e(Category.SLEEP_MODE, FILE_NAME + ": scheduleIdleFreezeAlarm: AlarmManager is null, cannot schedule");
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        long delayMs = prefs.getLong(KEY_SLEEP_MODE_DELAY, DEFAULT_SLEEP_MODE_DELAY_MS);
        PendingIntent pendingIntent = getFreezeAlarmIntent();
        long triggerAt = System.currentTimeMillis() + delayMs;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                AppDebugManager.w(Category.SLEEP_MODE, FILE_NAME + ": scheduleIdleFreezeAlarm: exact alarm not permitted, using inexact");
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": scheduleIdleFreezeAlarm: armed, triggerAt=" + triggerAt);
    }

    private void cancelIdleFreezeAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        alarmManager.cancel(getFreezeAlarmIntent());
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Idle freeze alarm cancelled");
    }

    private PendingIntent getFreezeAlarmIntent() {
        Intent intent = new Intent(this, KillTriggerReceiver.class);
        intent.setAction(ACTION_IDLE_FREEZE);
        return PendingIntent.getBroadcast(
                this,
                FREEZE_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void scheduleHeartbeatAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            AppDebugManager.e(Category.SLEEP_MODE, FILE_NAME + ": scheduleHeartbeatAlarm: AlarmManager is null, cannot schedule");
            return;
        }
        PendingIntent pendingIntent = getHeartbeatAlarmIntent();
        long triggerAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                AppDebugManager.w(Category.SLEEP_MODE, FILE_NAME + ": scheduleHeartbeatAlarm: exact alarm not permitted, using inexact");
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": scheduleHeartbeatAlarm: armed, triggerAt=" + triggerAt);
    }

    private void cancelHeartbeatAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        alarmManager.cancel(getHeartbeatAlarmIntent());
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": Heartbeat alarm cancelled");
    }

    private PendingIntent getHeartbeatAlarmIntent() {
        Intent intent = new Intent(this, KillTriggerReceiver.class);
        intent.setAction(ACTION_HEARTBEAT_CHECK);
        return PendingIntent.getBroadcast(
                this,
                HEARTBEAT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void handleHeartbeatCheck() {
        boolean enabled = sleepModeManager.isSleepModeEnabled();
        boolean interactive = isScreenInteractive();
        boolean hasFrozen = sleepModeManager.hasFrozenTimerApps();
        SleepModeLifecyclePolicy.RecoveryAction recovery = SleepModeLifecyclePolicy.recoveryAction(
                enabled, interactive, hasFrozen);

        switch (recovery) {
            case UNFREEZE:
                AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                        + ": Heartbeat recovery requires thaw; enabled=" + enabled + ", interactive=" + interactive);
                cancelHeartbeatAlarm();
                sleepModeManager.unfreezeBackgroundRestrictedApps(() -> {
                    if (!BackgroundWorkPolicy.shouldRunForegroundService(this)) {
                        stopSelf();
                    }
                });
                break;
            case KEEP_FROZEN_AND_HEARTBEAT:
                AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                        + ": Heartbeat check: owned freeze remains valid while screen is off; rescheduling");
                scheduleHeartbeatAlarm();
                break;
            case NONE:
            default:
                AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                        + ": Heartbeat check: no owned frozen apps; stopping heartbeat");
                cancelHeartbeatAlarm();
                if (!BackgroundWorkPolicy.shouldRunForegroundService(this)) {
                    stopSelf();
                }
                break;
        }
    }

    private void scheduleServiceRestart() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(this, RestartReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                RESTART_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3000, pi);
    }

    private void cancelServiceRestart() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(this, RestartReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                RESTART_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    private static final long SNAPSHOT_INTERVAL_MS = 15 * 60 * 1000L;
    private static final long WIDGET_UPDATE_INTERVAL_MS = 60 * 1000L;

    private void scheduleSnapshotAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            AppDebugManager.e(Category.UTILS, FILE_NAME
                    + ": scheduleSnapshotAlarm: AlarmManager is null, cannot schedule");
            return;
        }

        long now = System.currentTimeMillis();
        long triggerAt = now + SNAPSHOT_INTERVAL_MS;
        PendingIntent pi = getSnapshotAlarmIntent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                AppDebugManager.w(Category.UTILS, FILE_NAME + ": scheduleSnapshotAlarm: exact alarm not permitted, using inexact");
            }
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
        AppDebugManager.d(Category.UTILS, FILE_NAME
                + ": scheduleSnapshotAlarm: armed, triggerAt=" + triggerAt
                + " (in " + ((triggerAt - now) / 60_000) + " min)");
    }

    private void releaseSnapshotWakeLock() {
        CollectStatsReceiver.releaseSnapshotWakeLock();
    }

    private void cancelSnapshotAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(getSnapshotAlarmIntent());
        AppDebugManager.d(Category.UTILS, FILE_NAME + ": cancelSnapshotAlarm: cancelled");
    }

    private PendingIntent getSnapshotAlarmIntent() {
        Intent intent = new Intent(this, CollectStatsReceiver.class);
        intent.setAction(CollectStatsReceiver.ACTION_COLLECT_SNAPSHOT);
        return PendingIntent.getBroadcast(
                this,
                SNAPSHOT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void scheduleWidgetUpdate() {
        Runnable widgetRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                AppzukuWidget.updateAllWidgetsFromJava(ShappkyService.this);
                ramKillShortcutManager.updateShortcut();
                handler.postDelayed(this, WIDGET_UPDATE_INTERVAL_MS);
            }
        };
        handler.post(widgetRunnable);
    }

    private void scheduleNextKill() {
        if (!isRunning)
            return;

        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        int killInterval = prefs.getInt(KEY_KILL_INTERVAL, DEFAULT_KILL_INTERVAL_MS);

        handler.postDelayed(() -> {
            if (!isRunning)
                return;

            executor.execute(() -> {
                boolean autoKillEnabled = prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false);
                boolean periodicKillEnabled = prefs.getBoolean(KEY_PERIODIC_KILL_ENABLED, false);
                boolean ramThresholdEnabled = prefs.getBoolean(KEY_RAM_THRESHOLD_ENABLED, false);

                if (autoKillEnabled && periodicKillEnabled) {
                    if (ramThresholdEnabled) {
                        int threshold = prefs.getInt(KEY_RAM_THRESHOLD, DEFAULT_RAM_THRESHOLD_PERCENT);
                        int ramPercent = getCurrentRamUsagePercent();
                        AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": scheduleNextKill: RAM threshold check: " + ramPercent + "% / " + threshold + "%");
                        if (ramPercent >= threshold) {
                            AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": scheduleNextKill: threshold reached, starting Service Periodic Kill");
                            autoKillManager.performAutoKill(() -> handler.post(this::scheduleNextKill), resolveKillSource("Service Periodic Kill"));
                        } else {
                            AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": scheduleNextKill: RAM below threshold, kill skipped");
                            handler.post(this::scheduleNextKill);
                        }
                    } else {
                        AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": scheduleNextKill: no RAM threshold, starting Service Periodic Kill");
                        autoKillManager.performAutoKill(() -> handler.post(this::scheduleNextKill), resolveKillSource("Service Periodic Kill"));
                    }
                } else {
                    AppDebugManager.d(Category.AUTO_KILL_BASE, FILE_NAME + ": scheduleNextKill: skipped (autoKill=" + autoKillEnabled + " periodic=" + periodicKillEnabled + ")");
                    handler.post(this::scheduleNextKill);
                }
            });
        }, killInterval);
    }

    private int getCurrentRamUsagePercent() {
        try (java.io.RandomAccessFile reader = new java.io.RandomAccessFile("/proc/meminfo", "r")) {
            String load = reader.readLine();
            long totalRam = Long.parseLong(load.replaceAll("\\D+", ""));
            load = reader.readLine();
            load = reader.readLine();
            long availableRam = Long.parseLong(load.replaceAll("\\D+", ""));
            return (int) ((totalRam - availableRam) * 100 / totalRam);
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    private String resolveKillSource(String defaultSource) {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        int activePreset = prefs.getInt(KEY_ACTIVE_PRESET, 0);
        if (activePreset != 0) {
            PresetManager pm = new PresetManager(this);
            return defaultSource + " · " + pm.getPresetName(activePreset);
        }
        return defaultSource;
    }

    @Override
    public void onDestroy() {
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": onDestroy called, stopping service");
        isRunning = false;
        if (BackgroundWorkPolicy.shouldRunForegroundService(this)) {
            scheduleServiceRestart();
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": Service restart scheduled because Auto-Kill is still enabled");
        } else {
            cancelServiceRestart();
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": Service restart suppressed because Auto-Kill is disabled");
        }
        cancelIdleFreezeAlarm();
        if (sleepModeManager != null && sleepModeManager.hasFrozenTimerApps()) {
            // If a controlled service stop races a thaw failure, preserve a recovery path.
            // The heartbeat receiver can restart the service, whose init reconciliation then
            // thaws the durable owned set when appropriate.
            scheduleHeartbeatAlarm();
            AppDebugManager.w(Category.SLEEP_MODE, FILE_NAME
                    + ": onDestroy: owned frozen timer apps remain; recovery heartbeat preserved");
        } else {
            cancelHeartbeatAlarm();
        }
        cancelSnapshotAlarm();
        cancelShizukuLostNotification();
        AppDebugManager.d(Category.CORE, FILE_NAME + ": Shizuku-lost notification cancelled on service destroy");
        unregisterShizukuBinderListeners();
        AppDebugManager.d(Category.CORE, FILE_NAME + ": Shizuku binder listeners unregistered on service destroy");
        stopRamMonitorNotification();
        if (screenOffReceiver != null) {
            unregisterReceiver(screenOffReceiver);
        }
        if (packageChangeReceiver != null) {
            unregisterReceiver(packageChangeReceiver);
        }
        if (additionalScenariosManager != null) {
            AppDebugManager.d(Category.ADVANCED_CONDITIONS, FILE_NAME + ": Stopping AdditionalScenariosManager (onDestroy)");
            additionalScenariosManager.stop();
        }
        if (watchdog != null) {
            watchdog.stop();
        }
        managersInitialized = false;
        pendingStartIntents.clear();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": onDestroy completed, executor shut down");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": createNotificationChannel: registering channels");
            NotificationManager nm = getSystemService(NotificationManager.class);

            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID_SERVICE,
                    getString(R.string.service_channel_foreground_name),
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(serviceChannel);

            NotificationChannel actionsChannel = new NotificationChannel(
                    CHANNEL_ID_ACTIONS,
                    getString(R.string.service_channel_actions_name),
                    NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(actionsChannel);
        }
    }

    public static class RestartReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BackgroundWorkPolicy.shouldRunForegroundService(context)) {
                AppDebugManager.d(Category.FOREGROUND_SERVICE,
                        "ShappkyService.RestartReceiver: ignoring stale restart alarm because Auto-Kill is disabled");
                return;
            }
            if (!ShappkyService.isRunning()) {
                AppDebugManager.d(Category.FOREGROUND_SERVICE, "ShappkyService.RestartReceiver: Service not running, restarting via " +
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? "startForegroundService" : "startService"));
                Intent service = new Intent(context, ShappkyService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(service);
                } else {
                    context.startService(service);
                }
            } else {
                AppDebugManager.d(Category.FOREGROUND_SERVICE, "ShappkyService.RestartReceiver: Service already running, restart skipped");
            }
        }
    }
}
