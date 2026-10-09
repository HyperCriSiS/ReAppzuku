package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.PolicyPreset;
import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.utils.PresetModel;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Guarded, non-destructive v7 import regression checks on disposable emulators. */
public final class Api24Api37BackupV7BoundaryInstrumentationTest {
    private static final String AUTHORITY = "com.gree1d.reappzuku.test.privatebackup";

    @Test public void corruptVersionSevenNeverChangesExistingState() throws Exception {
        requireCi();
        Context c = target();
        SharedPreferences prefs = preferences(c);
        Map<String, Object> before = snapshot(prefs);
        AppDatabase db = AppDatabase.getInstance(c);
        int policies = db.appPolicyDao().getAll().size();
        int presets = db.policyPresetDao().getAll().size();
        JSONObject missingPending = phase9();
        missingPending.getJSONObject("new_app_setup").remove("pending");
        JSONObject unmatchedPending = phase9();
        unmatchedPending.getJSONObject("new_app_setup")
                .put("pending", new JSONArray().put("com.example.unmatched"));
        String[] invalid = {
                "{not-json",
                "{\"backup_version\":7,\"phase9\":",
                new JSONObject().put("backup_version", 7)
                        .put(PreferenceKeys.KEY_EXIT_ON_BACK, true).toString(),
                new JSONObject().put("backup_version", 7)
                        .put(PreferenceKeys.KEY_EXIT_ON_BACK, true)
                        .put("phase9", missingPending).toString(),
                new JSONObject().put("backup_version", 7)
                        .put(PreferenceKeys.KEY_EXIT_ON_BACK, true)
                        .put("phase9", unmatchedPending).toString(),
                new JSONObject().put("backup_version", 7)
                        .put(PreferenceKeys.KEY_EXIT_ON_BACK, "not-a-boolean")
                        .put("phase9", phase9()).toString()
        };
        BackupManager manager = new BackupManager(c);
        for (String data : invalid) {
            assertFalse("Invalid v7 backup must be rejected", manager.restoreBackupJson(data));
            assertEquals("Invalid restore changed preferences", before, snapshot(prefs));
            assertEquals(policies, db.appPolicyDao().getAll().size());
            assertEquals(presets, db.policyPresetDao().getAll().size());
        }
    }

    @Test public void privateContentUriIsDeniedForReadAndWrite() throws Exception {
        requireCi();
        Context target = target();
        Context tests = InstrumentationRegistry.getInstrumentation().getContext();
        assertNotNull("Test-only private provider must exist",
                tests.getPackageManager().resolveContentProvider(AUTHORITY, 0));
        Map<String, Object> before = snapshot(preferences(target));
        BackupFileStore store = new BackupFileStore(target.getContentResolver());
        Uri uri = Uri.parse("content://" + AUTHORITY + "/private-backup.json");
        try {
            store.read(uri);
            fail("Unexported provider must deny target-app read");
        } catch (SecurityException expected) {
            // Different UID, no exported URI grant.
        }
        try {
            store.write(uri, "{\"backup_version\":7}");
            fail("Unexported provider must deny target-app write");
        } catch (SecurityException expected) {
            // No write through an ungranted content URI.
        }
        assertEquals(before, snapshot(preferences(target)));
    }

    @Test public void postCommitFaultRollsBackEveryOriginalPreference() throws Exception {
        requireCi();
        Context c = target();
        SharedPreferences prefs = preferences(c);
        Map<String, Object> before = snapshot(prefs);
        AppDatabase db = AppDatabase.getInstance(c);
        int policyCount = db.appPolicyDao().getAll().size();
        int presetCount = db.policyPresetDao().getAll().size();
        Object old = before.get(PreferenceKeys.KEY_EXIT_ON_BACK);
        boolean previous = old instanceof Boolean && (Boolean) old;
        JSONObject valid = new JSONObject()
                .put("backup_version", BackupCodec.CURRENT_VERSION)
                .put(PreferenceKeys.KEY_EXIT_ON_BACK, !previous)
                .put("phase9", phase9());
        BackupManager injected = new BackupManager(
                c, new BackupCodec(), point -> {
                    if (point == BackupManager.RestoreCommitPoint.AFTER_MAIN_COMMIT) {
                        throw new IllegalStateException("injected post-commit fault");
                    }
                });
        assertFalse(injected.restoreBackupJson(valid.toString()));
        assertEquals("Rollback must restore absent keys and existing values",
                before, snapshot(prefs));
        assertEquals(policyCount, db.appPolicyDao().getAll().size());
        assertEquals(presetCount, db.policyPresetDao().getAll().size());
    }

    /**
     * Non-destructive counterpart to Phase9BackupRestoreTest's isolated fixture:
     * the incoming v7 JSON adds one synthetic UNMANAGED row without ever
     * pre-seeding or clearing the real Room/preference stores.
     *
     * A failure AFTER the committed Phase 9 DB replacement must restore every
     * original Room row and the complete main + schedule preference snapshots.
     */
    @Test public void postPhase9DatabaseCommitFaultRestoresEveryOriginalRow()
            throws Exception {
        requireCi();
        Context c = target();
        AppDatabase db = AppDatabase.getInstance(c);
        SharedPreferences prefs = preferences(c);
        PresetManager schedules = new PresetManager(c);

        final String syntheticPackage = "com.reappzuku.ci.backuprollback";
        assertNull("Fixture package must not have an existing user policy",
                db.appPolicyDao().getByPackage(syntheticPackage));

        Map<String, Object> mainBefore = snapshot(prefs);
        Map<String, Object> schedule1Before = snapshotMap(
                schedules.snapshotPresetStorage(PresetModel.PRESET_1));
        Map<String, Object> schedule2Before = snapshotMap(
                schedules.snapshotPresetStorage(PresetModel.PRESET_2));
        Map<String, List<Object>> policiesBefore = snapshotPolicies(db);
        Map<Long, List<Object>> presetsBefore = snapshotPresets(db);

        String originalBackup = new BackupManager(c).createBackupJson();
        assertNotNull("An unmodified backup should be serializable", originalBackup);
        JSONObject incoming = new JSONObject(originalBackup);
        Phase9BackupCodec.Snapshot current =
                Phase9BackupCodec.parse(incoming, BackupCodec.CURRENT_VERSION);
        List<AppPolicy> importedPolicies = new ArrayList<>(current.appPolicies);
        AppPolicy fake = new AppPolicy(syntheticPackage);
        fake.source = AppPolicy.SOURCE_EXPLICIT;
        fake.strategy = AppPolicy.STRATEGY_UNMANAGED;
        fake.customized = true;
        importedPolicies.add(fake);
        Phase9BackupCodec.putPhase9(incoming, importedPolicies, current.userPresets,
                current.newAppSetupMode, current.defaultPresetId, current.pendingSetup);

        // Ensure the incoming main preferences and Phase9 DB actually differ.
        incoming.put(PreferenceKeys.KEY_EXIT_ON_BACK,
                !incoming.getBoolean(PreferenceKeys.KEY_EXIT_ON_BACK));
        AtomicBoolean faultReached = new AtomicBoolean(false);
        AtomicBoolean committedRowWasVisible = new AtomicBoolean(false);
        BackupManager injected = new BackupManager(c, new BackupCodec(), point -> {
            if (point == BackupManager.RestoreCommitPoint.AFTER_PHASE9_DB_COMMIT) {
                faultReached.set(true);
                committedRowWasVisible.set(
                        db.appPolicyDao().getByPackage(syntheticPackage) != null);
                throw new IllegalStateException("synthetic fault after Phase9 Room commit");
            }
        });

        assertFalse("Injected late commit failure must reject the restore",
                injected.restoreBackupJson(incoming.toString()));
        assertTrue("The injection must run after actual Phase9 DB commit",
                faultReached.get());
        assertTrue("Committed synthetic policy must be observable at the fault point",
                committedRowWasVisible.get());
        assertNull("Rollback must remove the injected synthetic policy",
                db.appPolicyDao().getByPackage(syntheticPackage));
        assertEquals("Rollback changed complete explicit/migrated Room policies",
                policiesBefore, snapshotPolicies(db));
        assertEquals("Rollback changed built-in/user Room policy presets",
                presetsBefore, snapshotPresets(db));
        assertEquals("Rollback must restore all portable main preference values/absence",
                mainBefore, snapshot(prefs));
        assertEquals("Rollback must restore first schedule store",
                schedule1Before, snapshotMap(schedules.snapshotPresetStorage(PresetModel.PRESET_1)));
        assertEquals("Rollback must restore second schedule store",
                schedule2Before, snapshotMap(schedules.snapshotPresetStorage(PresetModel.PRESET_2)));
    }

    private static Map<String, Object> snapshotMap(Map<String, ?> source) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            Object value = entry.getValue();
            copy.put(entry.getKey(),
                    value instanceof Set ? new HashSet<>((Set<?>) value) : value);
        }
        return copy;
    }

    private static Map<String, List<Object>> snapshotPolicies(AppDatabase db) {
        Map<String, List<Object>> rows = new TreeMap<>();
        for (AppPolicy p : db.appPolicyDao().getAll()) {
            rows.put(p.packageName, Arrays.asList(
                    p.strategy, p.source, p.presetId, p.customized,
                    p.standbyDelayMs, p.forceStopDelayMs, p.killMethod, p.bootCleanup,
                    p.backgroundRestriction, p.protectMedia,
                    p.protectForegroundServices, p.protectWidgets,
                    p.triggerMask, p.createdAt, p.updatedAt));
        }
        return rows;
    }

    private static Map<Long, List<Object>> snapshotPresets(AppDatabase db) {
        Map<Long, List<Object>> rows = new TreeMap<>();
        for (PolicyPreset p : db.policyPresetDao().getAll()) {
            rows.put(p.id, Arrays.asList(
                    p.name, p.strategy, p.standbyDelayMs, p.forceStopDelayMs,
                    p.killMethod, p.bootCleanup, p.backgroundRestriction,
                    p.protectMedia, p.protectForegroundServices, p.protectWidgets,
                    p.triggerMask, p.builtIn, p.createdAt, p.updatedAt));
        }
        return rows;
    }

    private static JSONObject phase9() throws Exception {
        return new JSONObject()
                .put("app_policies", new JSONArray())
                .put("policy_presets", new JSONArray())
                .put("new_app_setup", new JSONObject()
                        .put("mode", NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL)
                        .put("default_preset_id", PolicyPresetSeeder.PRESET_BALANCED)
                        .put("pending", new JSONArray()));
    }
    private static Context target() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // App.onCreate processes migration, installed-package reconciliation and
        // replayPending on a single executor. Let its startup tasks finish before
        // taking a backup-integrity snapshot; otherwise the legitimate replay
        // can remove a stale pending entry in the middle of this unrelated test.
        App app = (App) context.getApplicationContext();
        assertNotNull("Startup executor is required for a stable fixture",
                app.getSharedExecutor());
        app.getSharedExecutor().submit(() -> {}).get(60, java.util.concurrent.TimeUnit.SECONDS);
        return context;
    }
    private static SharedPreferences preferences(Context c) {
        return c.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
    private static Map<String, Object> snapshot(SharedPreferences prefs) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            // Periodic inventory reconciliation may populate this non-backup key asynchronously.
            // All portable configuration fields must still match exactly.
            if (PreferenceKeys.KEY_NEW_APP_KNOWN_PACKAGES.equals(e.getKey())) continue;
            Object x = e.getValue();
            copy.put(e.getKey(), x instanceof Set ? new HashSet<>((Set<?>) x) : x);
        }
        return copy;
    }
    private static void requireCi() {
        Assume.assumeTrue("Only disposable emulator CI may run restore failure probes",
                "verify".equals(InstrumentationRegistry.getArguments()
                        .getString("ci_backup_v7_boundary")));
    }
}
