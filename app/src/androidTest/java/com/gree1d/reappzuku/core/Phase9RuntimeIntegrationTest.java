package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

import androidx.core.content.ContextCompat;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.service.SmartLifecycleWorker;
import com.gree1d.reappzuku.ui.AppPolicyEditorActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Phase9RuntimeIntegrationTest {
    private static final String PROBE_PACKAGE = "com.reappzuku.securityprobe";
    private static final String PROBE_APK =
            "/data/local/tmp/reappzuku-security-probe.apk";
    private static final String SMART_PERIODIC_WORK = "SmartLifecyclePeriodic";
    private static final String NEW_APP_CHANNEL = "new_app_setup";

    private Context context;
    private SharedPreferences prefs;
    private AppDatabase db;
    private WorkManager workManager;
    private NotificationManager notificationManager;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        assertTrue(prefs.edit().clear().commit());

        db = AppDatabase.getInstance(context);
        db.runInTransaction(() -> {
            db.appPolicyDao().deleteAll();
            db.policyPresetDao().deleteAll();
        });
        PolicyPresetSeeder.seedBuiltIns(db);

        workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(SMART_PERIODIC_WORK)
                .getResult().get(10, TimeUnit.SECONDS);

        notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        clearNewAppNotifications();
    }

    @After
    public void tearDown() throws Exception {
        clearNewAppNotifications();
        workManager.cancelUniqueWork(SMART_PERIODIC_WORK)
                .getResult().get(10, TimeUnit.SECONDS);

        // The external abuse probe runs after instrumentation in the API-37 workflow.
        if (!isInstalled(PROBE_PACKAGE)) {
            String output = shell("pm install " + PROBE_APK);
            assertTrue("probe reinstall failed: " + output, output.contains("Success"));
        }
    }

    @Test
    public void conflictingLegacyAutoKillAndSmartResolveToSingleCanonicalSmartOwner()
            throws Exception {
        assertTrue("security probe must be installed by API-37 workflow",
                isInstalled(PROBE_PACKAGE));

        assertTrue(prefs.edit()
                .putBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, true)
                .putBoolean(PreferenceKeys.KEY_SMART_LIFECYCLE_ENABLED, true)
                .putInt(PreferenceKeys.KEY_KILL_MODE, 1)
                .putStringSet(PreferenceKeys.KEY_BLACKLISTED_APPS, Set.of(PROBE_PACKAGE))
                .commit());

        assertTrue(AppPolicyLegacyMigrator.migrateIfNeeded(context));
        assertTrue(AppPolicyLegacyMigrator.isMigrationSnapshotCurrent(prefs));

        AppPolicy migrated = db.appPolicyDao().getByPackage(PROBE_PACKAGE);
        assertNotNull(migrated);
        assertEquals(AppPolicy.SOURCE_LEGACY_MIGRATED, migrated.source);
        assertEquals(AppPolicy.STRATEGY_SMART, migrated.strategy);

        AppPolicy explicit = new AppPolicy(PROBE_PACKAGE);
        explicit.source = AppPolicy.SOURCE_EXPLICIT;
        explicit.strategy = AppPolicy.STRATEGY_PROTECTED;
        explicit.customized = true;
        explicit.createdAt = System.currentTimeMillis();
        explicit.updatedAt = explicit.createdAt;
        db.appPolicyDao().upsert(explicit);

        // Change the legacy inputs so a reconciliation is required. Explicit ownership
        // must survive and remain the sole row for this package.
        assertTrue(prefs.edit()
                .putBoolean(PreferenceKeys.KEY_SMART_LIFECYCLE_ENABLED, false)
                .commit());
        assertTrue(AppPolicyLegacyMigrator.migrateIfNeeded(context));

        AppPolicy resolved = db.appPolicyDao().getByPackage(PROBE_PACKAGE);
        assertNotNull(resolved);
        assertEquals(AppPolicy.SOURCE_EXPLICIT, resolved.source);
        assertEquals(AppPolicy.STRATEGY_PROTECTED, resolved.strategy);

        int matchingRows = 0;
        for (AppPolicy row : db.appPolicyDao().getAll()) {
            if (PROBE_PACKAGE.equals(row.packageName)) matchingRows++;
        }
        assertEquals(1, matchingRows);
    }

    @Test
    public void smartPeriodicWorkTracksCanonicalPolicyOwnership() throws Exception {
        AppPolicy smart = explicitPolicy(PROBE_PACKAGE, AppPolicy.STRATEGY_SMART);
        db.appPolicyDao().upsert(smart);

        SmartLifecycleWorker.reconcilePeriodic(context);
        assertTrue("SMART policy did not enqueue periodic work",
                waitForActiveSmartWork(true, 10_000L));

        smart.strategy = AppPolicy.STRATEGY_PROTECTED;
        smart.updatedAt = System.currentTimeMillis();
        db.appPolicyDao().upsert(smart);

        SmartLifecycleWorker.reconcilePeriodic(context);
        assertTrue("non-SMART policy left periodic work active",
                waitForActiveSmartWork(false, 10_000L));
    }

    @Test
    public void realPackageInstallQueuesSetupWithoutNotificationsThenDeepLinksWhenGranted()
            throws Exception {
        // The API-37 workflow stages the external probe APK under /data/local/tmp.
        // pm install is the authoritative existence/access check for this shell context.
        // Fresh targetSdk-37 installs start without POST_NOTIFICATIONS. This validates that
        // setup state is durable even when Android suppresses the notification.
        assertEquals(PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS));

        assertTrue(prefs.edit()
                .putInt(PreferenceKeys.KEY_NEW_APP_SETUP_MODE,
                        NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL)
                .commit());

        reinstallProbe();
        assertTrue("PACKAGE_ADDED did not create pending setup state",
                waitUntil(() -> NewAppSetupStore.isPending(context, PROBE_PACKAGE), 10_000L));

        AppPolicy pending = db.appPolicyDao().getByPackage(PROBE_PACKAGE);
        assertNotNull(pending);
        assertEquals(AppPolicy.SOURCE_EXPLICIT, pending.source);
        assertEquals(AppPolicy.STRATEGY_UNMANAGED, pending.strategy);
        assertFalse("notification must not post while permission is denied",
                hasActiveNewAppNotification());

        String grant = shell("pm grant " + context.getPackageName()
                + " " + Manifest.permission.POST_NOTIFICATIONS);
        assertTrue("notification permission grant failed: " + grant,
                ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED);

        // Reset the package-specific canonical state and perform another real install.
        db.appPolicyDao().deleteByPackage(PROBE_PACKAGE);
        NewAppSetupStore.removePending(context, PROBE_PACKAGE);
        clearNewAppNotifications();

        reinstallProbe();
        assertTrue("second PACKAGE_ADDED did not queue setup",
                waitUntil(() -> NewAppSetupStore.isPending(context, PROBE_PACKAGE), 10_000L));
        assertTrue("granted notification permission did not produce setup notification",
                waitUntil(this::hasActiveNewAppNotification, 10_000L));

        StatusBarNotification setupNotification = findNewAppNotification();
        assertNotNull(setupNotification);
        assertNotNull(setupNotification.getNotification().contentIntent);
        setupNotification.getNotification().contentIntent.send();

        AppPolicyEditorActivity editor = waitForResumedPolicyEditor(10_000L);
        assertNotNull("notification did not deep-link to AppPolicyEditorActivity", editor);
        assertEquals(PROBE_PACKAGE,
                editor.getIntent().getStringExtra(AppPolicyEditorActivity.EXTRA_PACKAGE_NAME));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(editor::finish);
    }

    @Test
    public void versionSevenRestoreReconcilesSmartPeriodicWork() throws Exception {
        AppPolicy smart = explicitPolicy("com.example.restore.smart", AppPolicy.STRATEGY_SMART);
        db.appPolicyDao().upsert(smart);

        String backup = new BackupManager(context).createBackupJson();
        assertNotNull(backup);

        db.appPolicyDao().deleteAll();
        SmartLifecycleWorker.reconcilePeriodic(context);
        assertTrue("pre-restore periodic work was not cancelled",
                waitForActiveSmartWork(false, 10_000L));

        assertTrue(new BackupManager(context).restoreBackupJson(backup));
        AppPolicy restored =
                db.appPolicyDao().getByPackage("com.example.restore.smart");
        assertNotNull(restored);
        assertEquals(AppPolicy.STRATEGY_SMART, restored.strategy);
        assertTrue("v7 restore did not reconcile Smart periodic work",
                waitForActiveSmartWork(true, 10_000L));
    }

    private static AppPolicy explicitPolicy(String packageName, int strategy) {
        AppPolicy policy = new AppPolicy(packageName);
        policy.source = AppPolicy.SOURCE_EXPLICIT;
        policy.strategy = strategy;
        policy.customized = true;
        policy.standbyDelayMs = 60_000L;
        policy.forceStopDelayMs = 120_000L;
        policy.triggerMask = AppPolicy.TRIGGER_PERIODIC;
        policy.createdAt = System.currentTimeMillis();
        policy.updatedAt = policy.createdAt;
        return policy;
    }

    private void reinstallProbe() throws Exception {
        if (isInstalled(PROBE_PACKAGE)) {
            String uninstall = shell("pm uninstall " + PROBE_PACKAGE);
            assertTrue("probe uninstall failed: " + uninstall, uninstall.contains("Success"));
            assertTrue("probe remained installed after uninstall",
                    waitUntil(() -> !isInstalled(PROBE_PACKAGE), 5_000L));
        }

        String install = shell("pm install " + PROBE_APK);
        assertTrue("probe install failed: " + install, install.contains("Success"));
        assertTrue("probe not visible after install",
                waitUntil(() -> isInstalled(PROBE_PACKAGE), 5_000L));
    }

    private boolean waitForActiveSmartWork(boolean expected, long timeoutMs) throws Exception {
        return waitUntil(() -> hasActiveSmartWork() == expected, timeoutMs);
    }

    private boolean hasActiveSmartWork() {
        try {
            List<WorkInfo> infos = workManager.getWorkInfosForUniqueWork(SMART_PERIODIC_WORK)
                    .get(5, TimeUnit.SECONDS);
            for (WorkInfo info : infos) {
                if (!info.getState().isFinished()) return true;
            }
            return false;
        } catch (Exception e) {
            throw new AssertionError("Unable to query WorkManager", e);
        }
    }

    private boolean hasActiveNewAppNotification() {
        return findNewAppNotification() != null;
    }

    private StatusBarNotification findNewAppNotification() {
        if (notificationManager == null) return null;
        for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
            if (notification.getNotification() != null
                    && NEW_APP_CHANNEL.equals(notification.getNotification().getChannelId())) {
                return notification;
            }
        }
        return null;
    }

    private void clearNewAppNotifications() {
        if (notificationManager == null) return;
        for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
            if (notification.getNotification() != null
                    && NEW_APP_CHANNEL.equals(notification.getNotification().getChannelId())) {
                notificationManager.cancel(notification.getId());
            }
        }
    }

    private AppPolicyEditorActivity waitForResumedPolicyEditor(long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<AppPolicyEditorActivity> result = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activity instanceof AppPolicyEditorActivity) {
                        result.set((AppPolicyEditorActivity) activity);
                        break;
                    }
                }
            });
            if (result.get() != null) return result.get();
            SystemClock.sleep(100L);
        }
        return null;
    }

    private boolean isInstalled(String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }


    private String shell(String command) throws Exception {
        ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        try (InputStream input = new FileInputStream(descriptor.getFileDescriptor());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
        } finally {
            descriptor.close();
        }
    }

    private static boolean waitUntil(CheckedCondition condition, long timeoutMs) throws Exception {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        do {
            if (condition.get()) return true;
            SystemClock.sleep(100L);
        } while (SystemClock.uptimeMillis() < deadline);
        return condition.get();
    }

    private interface CheckedCondition {
        boolean get() throws Exception;
    }
}