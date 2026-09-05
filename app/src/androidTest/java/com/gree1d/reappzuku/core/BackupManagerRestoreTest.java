package com.gree1d.reappzuku.core;

import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_EXIT_ON_BACK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.utils.PresetModel;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

public class BackupManagerRestoreTest {

    private static final String BACKUP_VERSION = "backup_version";
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        assertTrue(prefs.edit().clear().commit());
        PresetManager presetManager = new PresetManager(context);
        assertTrue(presetManager.clearPresetStorageBlocking(PresetModel.PRESET_1));
        assertTrue(presetManager.clearPresetStorageBlocking(PresetModel.PRESET_2));
    }

    @Test
    public void corruptBackupIsRejectedWithoutChangingState() {
        assertTrue(prefs.edit().putBoolean(KEY_EXIT_ON_BACK, false).commit());

        BackupManager manager = new BackupManager(context);

        assertFalse(manager.restoreBackupJson("{not-json"));
        assertFalse(prefs.getBoolean(KEY_EXIT_ON_BACK, true));
    }

    @Test
    public void legacyUnversionedBackupRestoresAvailableFields() throws Exception {
        JSONObject backup = new JSONObject()
                .put(KEY_EXIT_ON_BACK, true);

        BackupManager manager = new BackupManager(context);

        assertTrue(manager.restoreBackupJson(backup.toString()));
        assertTrue(prefs.getBoolean(KEY_EXIT_ON_BACK, false));
    }

    @Test
    public void futureBackupVersionIsRejectedWithoutChangingState() throws Exception {
        assertTrue(prefs.edit().putBoolean(KEY_EXIT_ON_BACK, false).commit());
        JSONObject backup = new JSONObject()
                .put(BACKUP_VERSION, 6)
                .put(KEY_EXIT_ON_BACK, true);

        BackupManager manager = new BackupManager(context);

        assertFalse(manager.restoreBackupJson(backup.toString()));
        assertFalse(prefs.getBoolean(KEY_EXIT_ON_BACK, true));
    }

    @Test
    public void oversizedBackupIsRejectedWithoutParsing() {
        String oversized = "x".repeat((2 * 1024 * 1024) + 1);

        BackupManager manager = new BackupManager(context);

        assertFalse(manager.restoreBackupJson(oversized));
    }

    @Test
    public void failureAfterMainCommitRollsBackPersistedMainState() throws Exception {
        assertTrue(prefs.edit().putBoolean(KEY_EXIT_ON_BACK, false).commit());
        JSONObject backup = new JSONObject().put(KEY_EXIT_ON_BACK, true);

        BackupManager manager = new BackupManager(
                context,
                new BackupCodec(),
                point -> {
                    if (point == BackupManager.RestoreCommitPoint.AFTER_MAIN_COMMIT) {
                        throw new IllegalStateException("injected failure after main commit");
                    }
                });

        assertFalse(manager.restoreBackupJson(backup.toString()));
        assertFalse(prefs.getBoolean(KEY_EXIT_ON_BACK, true));
    }

    @Test
    public void failureAfterFirstPresetCommitRollsBackMainAndPresetStorage() throws Exception {
        PresetManager presetManager = new PresetManager(context);
        PresetModel original = new PresetModel(PresetModel.PRESET_1);
        original.name = "before";
        original.enabled = false;
        assertTrue(presetManager.savePresetBlocking(original));
        assertTrue(prefs.edit().putBoolean(KEY_EXIT_ON_BACK, false).commit());

        PresetModel incoming = new PresetModel(PresetModel.PRESET_1);
        incoming.name = "after";
        incoming.enabled = false;
        JSONObject presets = new JSONObject().put("preset_1", incoming.toJson());
        JSONObject backup = new JSONObject()
                .put(KEY_EXIT_ON_BACK, true)
                .put("presets", presets);

        BackupManager manager = new BackupManager(
                context,
                new BackupCodec(),
                point -> {
                    if (point == BackupManager.RestoreCommitPoint.AFTER_PRESET_1_COMMIT) {
                        throw new IllegalStateException("injected failure after preset 1 commit");
                    }
                });

        assertFalse(manager.restoreBackupJson(backup.toString()));
        assertFalse(prefs.getBoolean(KEY_EXIT_ON_BACK, true));
        PresetModel restored = presetManager.loadPreset(PresetModel.PRESET_1);
        assertEquals("before", restored.name);
        assertFalse(restored.enabled);
    }

    @Test
    public void rollbackHelperRestoresMainAndPresetSnapshots() throws Exception {
        PresetManager presetManager = new PresetManager(context);
        assertTrue(prefs.edit().putBoolean(KEY_EXIT_ON_BACK, false).commit());
        Map<String, ?> mainSnapshot = new HashMap<>(prefs.getAll());
        Map<String, ?> preset1Snapshot = presetManager.snapshotPresetStorage(PresetModel.PRESET_1);
        Map<String, ?> preset2Snapshot = presetManager.snapshotPresetStorage(PresetModel.PRESET_2);

        assertTrue(prefs.edit().putBoolean(KEY_EXIT_ON_BACK, true).commit());
        BackupManager manager = new BackupManager(context);
        Method rollback = BackupManager.class.getDeclaredMethod(
                "rollbackRestore", PresetManager.class, Map.class, Map.class, Map.class);
        rollback.setAccessible(true);

        boolean restored = (Boolean) rollback.invoke(
                manager, presetManager, mainSnapshot, preset1Snapshot, preset2Snapshot);

        assertTrue(restored);
        assertFalse(prefs.getBoolean(KEY_EXIT_ON_BACK, true));
    }

}
