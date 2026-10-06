package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.service.notification.StatusBarNotification;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.service.SmartLifecycleWorker;
import com.gree1d.reappzuku.ui.AppPolicyEditorActivity;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Phase9CompletedModelInstrumentationTest {
    private static final String PROBE_PACKAGE = "com.reappzuku.securityprobe";
    private static final String PERIODIC_WORK = "SmartLifecyclePeriodic";
    private static final String NEW_APP_CHANNEL = "new_app_setup";

    private Context context;
    private SharedPreferences prefs;
    private AppDatabase db;
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

        notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertNotNull(notificationManager);
        notificationManager.cancelAll();

        SmartLifecycleWorker.cancel(context);
        waitForPeriodicWork(false);

        context.getPackageManager().getApplicationInfo(PROBE_PACKAGE, 0);
        assertTrue(NewAppSetupCoordinator.isEligible(context, PROBE_PACKAGE));
    }

    @Test
    public void a_conflictingLegacyInputsMigrateToOneSmartOwner() {
        assertTrue(prefs.edit()
                .putBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, true)
                .putBoolean(PreferenceKeys.KEY_SMART_LIFECYCLE_ENABLED, true)
                .putInt(PreferenceKeys.KEY_KILL_MODE, 1)
                .putBoolean(PreferenceKeys.KEY_PERIODIC_KILL_ENABLED, true)
                .putStringSet(PreferenceKeys.KEY_BLACKLISTED_APPS, Set.of(PROBE_PACKAGE))
                .commit());

        assertTrue(AppPolicyLegacyMigrator.migrateIfNeeded(context));
        assertTrue(AppPolicyLegacyMigrator.isMigrationSnapshotCurrent(prefs));

        AppPolicy migrated = db.appPolicyDao().getByPackage(PROBE_PACKAGE);
        assertNotNull(migrated);
        assertEquals(AppPolicy.SOURCE_LEGACY_MIGRATED, migrated.source);
        assertEquals(AppPolicy.STRATEGY_SMART, migrated.strategy);

        assertTrue(AppPolicyResolver.shouldExecuteSmart(migrated, null, false));
        assertFalse(AppPolicyResolver.shouldExecuteImmediate(
                migrated, null, AppPolicy.TRIGGER_PERIODIC));
    }

    @Test
    public void b_canonicalSmartOwnershipControlsPeriodicWorkWithoutLegacyToggle()
            throws Exception {
        assertFalse(prefs.getBoolean(PreferenceKeys.KEY_SMART_LIFECYCLE_ENABLED, false));

        AppPolicy smart = explicitPolicy(PROBE_PACKAGE, AppPolicy.STRATEGY_SMART);
        db.appPolicyDao().upsert(smart);
        SmartLifecycleWorker.reconcilePeriodic(context);
        waitForPeriodicWork(true);

        smart.strategy = AppPolicy.STRATEGY_UNMANAGED;
        smart.updatedAt++;
        db.appPolicyDao().upsert(smart);
        SmartLifecycleWorker.reconcilePeriodic(context);
        waitForPeriodicWork(false);

        assertFalse(prefs.getBoolean(PreferenceKeys.KEY_SMART_LIFECYCLE_ENABLED, false));
    }

    @Test
    public void c_backupRestoreReconcilesCanonicalSmartPeriodicWork() throws Exception {
        AppPolicy smart = explicitPolicy(PROBE_PACKAGE, AppPolicy.STRATEGY_SMART);
        db.appPolicyDao().upsert(smart);

        BackupManager manager = new BackupManager(context);
        String backup = manager.createBackupJson();
        assertNotNull(backup);

        smart.strategy = AppPolicy.STRATEGY_UNMANAGED;
        smart.updatedAt++;
        db.appPolicyDao().upsert(smart);
        SmartLifecycleWorker.reconcilePeriodic(context);
        waitForPeriodicWork(false);

        assertTrue(manager.restoreBackupJson(backup));
        AppPolicy restored = db.appPolicyDao().getByPackage(PROBE_PACKAGE);
        assertNotNull(restored);
        assertEquals(AppPolicy.STRATEGY_SMART, restored.strategy);
        assertFalse(prefs.getBoolean(PreferenceKeys.KEY_SMART_LIFECYCLE_ENABLED, false));
        waitForPeriodicWork(true);
    }

    @Test
    public void z_newAppAskFallsBackSafelyThenPostsEditorDeepLinkWhenAllowed()
            throws Exception {
        assertEquals(PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS));

        NewAppSetupStore.setMode(context, NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL);
        db.appPolicyDao().deleteByPackage(PROBE_PACKAGE);
        NewAppSetupStore.removePending(context, PROBE_PACKAGE);
        notificationManager.cancelAll();

        NewAppSetupCoordinator.handlePackageAdded(context, PROBE_PACKAGE);

        AppPolicy queuedPolicy = db.appPolicyDao().getByPackage(PROBE_PACKAGE);
        assertNotNull(queuedPolicy);
        assertEquals(AppPolicy.SOURCE_EXPLICIT, queuedPolicy.source);
        assertEquals(AppPolicy.STRATEGY_UNMANAGED, queuedPolicy.strategy);
        assertTrue(NewAppSetupStore.isPending(context, PROBE_PACKAGE));
        assertFalse(hasSetupNotification());

        grantNotificationPermission();
        assertEquals(PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS));

        NewAppSetupCoordinator.handlePackageAdded(context, PROBE_PACKAGE);
        Notification notification = waitForSetupNotification();
        assertNotNull(notification.contentIntent);
        assertEquals(NEW_APP_CHANNEL, notification.getChannelId());

        Intent deepLink = NewAppSetupNotifier.createPolicyEditorIntent(context, PROBE_PACKAGE);
        assertNotNull(deepLink.getComponent());
        assertEquals(AppPolicyEditorActivity.class.getName(),
                deepLink.getComponent().getClassName());
        assertEquals(PROBE_PACKAGE,
                deepLink.getStringExtra(AppPolicyEditorActivity.EXTRA_PACKAGE_NAME));
        assertTrue((deepLink.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue((deepLink.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
    }

    private static AppPolicy explicitPolicy(String packageName, int strategy) {
        AppPolicy policy = new AppPolicy(packageName);
        policy.source = AppPolicy.SOURCE_EXPLICIT;
        policy.strategy = strategy;
        policy.customized = true;
        policy.standbyDelayMs = 60_000L;
        policy.forceStopDelayMs = 120_000L;
        policy.triggerMask = AppPolicy.TRIGGER_PERIODIC | AppPolicy.TRIGGER_BOOT_CLEANUP;
        policy.createdAt = 10L;
        policy.updatedAt = 10L;
        return policy;
    }

    private void waitForPeriodicWork(boolean expected) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(8);
        boolean actual = hasActivePeriodicWork();
        while (actual != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(100L);
            actual = hasActivePeriodicWork();
        }
        assertEquals(expected, actual);
    }

    private boolean hasActivePeriodicWork() throws Exception {
        List<WorkInfo> workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(PERIODIC_WORK)
                .get(5, TimeUnit.SECONDS);
        for (WorkInfo workInfo : workInfos) {
            if (!workInfo.getState().isFinished()) return true;
        }
        return false;
    }

    private boolean hasSetupNotification() {
        for (StatusBarNotification item : notificationManager.getActiveNotifications()) {
            Notification notification = item.getNotification();
            if (notification != null && NEW_APP_CHANNEL.equals(notification.getChannelId())) {
                return true;
            }
        }
        return false;
    }

    private Notification waitForSetupNotification() throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        do {
            for (StatusBarNotification item : notificationManager.getActiveNotifications()) {
                Notification notification = item.getNotification();
                if (notification != null && NEW_APP_CHANNEL.equals(notification.getChannelId())) {
                    return notification;
                }
            }
            Thread.sleep(100L);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Needs-setup notification was not posted");
    }

    private void grantNotificationPermission() throws Exception {
        String command = "pm grant " + context.getPackageName() + " "
                + Manifest.permission.POST_NOTIFICATIONS;
        ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand(command);
        try (FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[256];
            while (input.read(buffer) != -1) {
                // Drain command output so the shell command has completed.
            }
        } finally {
            try {
                descriptor.close();
            } catch (IOException ignored) {
            }
        }

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        while (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100L);
        }
    }
}
