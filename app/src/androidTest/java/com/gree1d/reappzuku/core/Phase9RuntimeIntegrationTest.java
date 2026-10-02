package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.service.SmartLifecycleWorker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Phase9RuntimeIntegrationTest {
    private static final String PROBE_PACKAGE = "com.reappzuku.securityprobe";
    private static final String SMART_PERIODIC_WORK = "SmartLifecyclePeriodic";

    private Context context;
    private SharedPreferences prefs;
    private AppDatabase db;
    private WorkManager workManager;

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
    }

    @After
    public void tearDown() throws Exception {
        workManager.cancelUniqueWork(SMART_PERIODIC_WORK)
                .getResult().get(10, TimeUnit.SECONDS);
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

        // A later legacy change must never reclaim a package explicitly owned by the user.
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

    private boolean isInstalled(String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
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
