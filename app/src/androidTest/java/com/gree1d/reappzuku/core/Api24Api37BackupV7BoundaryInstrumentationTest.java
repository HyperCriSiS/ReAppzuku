package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import com.gree1d.reappzuku.db.AppDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
