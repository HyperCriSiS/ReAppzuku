package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.PolicyPreset;
import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.utils.PresetModel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

public class Phase9BackupRestoreTest {
    private Context context;
    private SharedPreferences prefs;
    private AppDatabase db;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        assertTrue(prefs.edit().clear().commit());

        PresetManager schedules = new PresetManager(context);
        assertTrue(schedules.clearPresetStorageBlocking(PresetModel.PRESET_1));
        assertTrue(schedules.clearPresetStorageBlocking(PresetModel.PRESET_2));

        db = AppDatabase.getInstance(context);
        db.runInTransaction(() -> {
            db.appPolicyDao().deleteAll();
            db.policyPresetDao().deleteAll();
        });
        PolicyPresetSeeder.seedBuiltIns(db);
    }

    @Test
    public void versionSevenRoundTripsExplicitPoliciesUserPresetsAndNewAppState() throws Exception {
        PolicyPreset user = userPreset(77L, "My smart");
        db.policyPresetDao().upsert(user);

        AppPolicy explicit = explicitPolicy("com.example.explicit", 77L);
        db.appPolicyDao().upsert(explicit);

        AppPolicy pending = new AppPolicy("com.example.pending");
        pending.source = AppPolicy.SOURCE_EXPLICIT;
        pending.strategy = AppPolicy.STRATEGY_UNMANAGED;
        pending.customized = true;
        pending.createdAt = 10L;
        pending.updatedAt = 10L;
        db.appPolicyDao().upsert(pending);

        AppPolicy migrated = new AppPolicy("com.example.migrated");
        migrated.source = AppPolicy.SOURCE_LEGACY_MIGRATED;
        migrated.strategy = AppPolicy.STRATEGY_IMMEDIATE;
        db.appPolicyDao().upsert(migrated);

        assertTrue(prefs.edit()
                .putInt(PreferenceKeys.KEY_NEW_APP_SETUP_MODE,
                        NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL)
                .putLong(PreferenceKeys.KEY_NEW_APP_DEFAULT_PRESET_ID, 77L)
                .putStringSet(PreferenceKeys.KEY_NEW_APP_SETUP_QUEUE,
                        Set.of("com.example.pending"))
                .commit());

        BackupManager manager = new BackupManager(context);
        String json = manager.createBackupJson();
        assertNotNull(json);

        JSONObject root = new JSONObject(json);
        assertEquals(7, root.getInt("backup_version"));
        JSONArray exportedPolicies = root.getJSONObject("phase9")
                .getJSONArray("app_policies");
        assertEquals(2, exportedPolicies.length());

        db.runInTransaction(() -> {
            db.appPolicyDao().deleteAll();
            db.policyPresetDao().deleteUserPresets();
        });
        assertTrue(prefs.edit()
                .putInt(PreferenceKeys.KEY_NEW_APP_SETUP_MODE,
                        NewAppSetupPolicy.MODE_LEAVE_UNMANAGED)
                .putLong(PreferenceKeys.KEY_NEW_APP_DEFAULT_PRESET_ID,
                        PolicyPresetSeeder.PRESET_BALANCED)
                .putStringSet(PreferenceKeys.KEY_NEW_APP_SETUP_QUEUE, Set.of())
                .commit());

        assertTrue(manager.restoreBackupJson(json));

        AppPolicy restored = db.appPolicyDao().getByPackage("com.example.explicit");
        assertNotNull(restored);
        assertEquals(AppPolicy.SOURCE_EXPLICIT, restored.source);
        assertEquals(Long.valueOf(77L), restored.presetId);
        assertNotNull(db.policyPresetDao().getById(77L));
        assertNull(db.appPolicyDao().getByPackage("com.example.migrated"));
        assertEquals(NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL,
                NewAppSetupStore.getMode(context));
        assertEquals(77L, NewAppSetupStore.getDefaultPresetId(context));
        assertEquals(Set.of("com.example.pending"),
                Set.copyOf(NewAppSetupStore.getPending(context)));
    }

    @Test
    public void versionSixRestorePreservesExistingPhase9State() throws Exception {
        PolicyPreset user = userPreset(88L, "Keep me");
        db.policyPresetDao().upsert(user);
        AppPolicy policy = explicitPolicy("com.example.keep", 88L);
        db.appPolicyDao().upsert(policy);
        assertTrue(prefs.edit()
                .putInt(PreferenceKeys.KEY_NEW_APP_SETUP_MODE,
                        NewAppSetupPolicy.MODE_LEAVE_UNMANAGED)
                .putLong(PreferenceKeys.KEY_NEW_APP_DEFAULT_PRESET_ID, 88L)
                .commit());

        JSONObject legacy = new JSONObject()
                .put("backup_version", 6)
                .put(PreferenceKeys.KEY_EXIT_ON_BACK, true);

        assertTrue(new BackupManager(context).restoreBackupJson(legacy.toString()));
        assertNotNull(db.appPolicyDao().getByPackage("com.example.keep"));
        assertNotNull(db.policyPresetDao().getById(88L));
        assertEquals(NewAppSetupPolicy.MODE_LEAVE_UNMANAGED,
                NewAppSetupStore.getMode(context));
        assertEquals(88L, NewAppSetupStore.getDefaultPresetId(context));
    }

    @Test
    public void failureAfterPhase9CommitRollsBackPreferencesAndRoom() throws Exception {
        PolicyPreset originalPreset = userPreset(90L, "Before");
        db.policyPresetDao().upsert(originalPreset);
        AppPolicy originalPolicy = explicitPolicy("com.example.before", 90L);
        db.appPolicyDao().upsert(originalPolicy);
        assertTrue(prefs.edit()
                .putInt(PreferenceKeys.KEY_NEW_APP_SETUP_MODE,
                        NewAppSetupPolicy.MODE_LEAVE_UNMANAGED)
                .putLong(PreferenceKeys.KEY_NEW_APP_DEFAULT_PRESET_ID, 90L)
                .commit());

        // Build an incoming v7 snapshot from a distinct state.
        db.runInTransaction(() -> {
            db.appPolicyDao().deleteAll();
            db.policyPresetDao().deleteUserPresets();
        });
        PolicyPreset incomingPreset = userPreset(91L, "After");
        db.policyPresetDao().upsert(incomingPreset);
        db.appPolicyDao().upsert(explicitPolicy("com.example.after", 91L));
        assertTrue(prefs.edit()
                .putInt(PreferenceKeys.KEY_NEW_APP_SETUP_MODE,
                        NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET)
                .putLong(PreferenceKeys.KEY_NEW_APP_DEFAULT_PRESET_ID, 91L)
                .commit());
        String incoming = new BackupManager(context).createBackupJson();
        assertNotNull(incoming);

        // Restore the original local state that must survive the injected failure.
        db.runInTransaction(() -> {
            db.appPolicyDao().deleteAll();
            db.policyPresetDao().deleteUserPresets();
        });
        db.policyPresetDao().upsert(originalPreset);
        db.appPolicyDao().upsert(originalPolicy);
        assertTrue(prefs.edit()
                .putInt(PreferenceKeys.KEY_NEW_APP_SETUP_MODE,
                        NewAppSetupPolicy.MODE_LEAVE_UNMANAGED)
                .putLong(PreferenceKeys.KEY_NEW_APP_DEFAULT_PRESET_ID, 90L)
                .commit());

        BackupManager failing = new BackupManager(
                context,
                new BackupCodec(),
                point -> {
                    if (point == BackupManager.RestoreCommitPoint.AFTER_PHASE9_DB_COMMIT) {
                        throw new IllegalStateException("injected phase9 failure");
                    }
                });

        assertFalse(failing.restoreBackupJson(incoming));
        assertNotNull(db.appPolicyDao().getByPackage("com.example.before"));
        assertNull(db.appPolicyDao().getByPackage("com.example.after"));
        assertNotNull(db.policyPresetDao().getById(90L));
        assertNull(db.policyPresetDao().getById(91L));
        assertEquals(NewAppSetupPolicy.MODE_LEAVE_UNMANAGED,
                NewAppSetupStore.getMode(context));
        assertEquals(90L, NewAppSetupStore.getDefaultPresetId(context));
    }

    private static PolicyPreset userPreset(long id, String name) {
        PolicyPreset preset = new PolicyPreset(name);
        preset.id = id;
        preset.strategy = AppPolicy.STRATEGY_SMART;
        preset.standbyDelayMs = 60_000L;
        preset.forceStopDelayMs = 120_000L;
        preset.triggerMask = AppPolicy.TRIGGER_PERIODIC;
        preset.builtIn = false;
        preset.createdAt = 10L;
        preset.updatedAt = 10L;
        return preset;
    }

    private static AppPolicy explicitPolicy(String packageName, long presetId) {
        AppPolicy policy = new AppPolicy(packageName);
        policy.source = AppPolicy.SOURCE_EXPLICIT;
        policy.strategy = AppPolicy.STRATEGY_SMART;
        policy.presetId = presetId;
        policy.customized = false;
        policy.standbyDelayMs = 60_000L;
        policy.forceStopDelayMs = 120_000L;
        policy.triggerMask = AppPolicy.TRIGGER_PERIODIC;
        policy.createdAt = 10L;
        policy.updatedAt = 10L;
        return policy;
    }
}
