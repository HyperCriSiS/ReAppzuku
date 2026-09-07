package com.gree1d.reappzuku.core;

import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_EXIT_ON_BACK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.utils.PresetModel;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
                .put(BACKUP_VERSION, BackupCodec.CURRENT_VERSION + 1)
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
    public void successfulRestoreReconcilesOldActivePresetAgainstImportedBase() throws Exception {
        PresetManager presetManager = new PresetManager(context);

        assertTrue(prefs.edit()
                .putBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false)
                .commit());

        PresetModel oldPreset = new PresetModel(PresetModel.PRESET_1);
        oldPreset.name = "old-active";
        oldPreset.enabled = true;
        oldPreset.autoKillEnabled = true;
        oldPreset.startHour = 0;
        oldPreset.startMinute = 0;
        oldPreset.endHour = 0;
        oldPreset.endMinute = 0;
        assertTrue(presetManager.savePresetBlocking(oldPreset));
        presetManager.activatePreset(PresetModel.PRESET_1);

        assertEquals(PresetModel.PRESET_1, presetManager.getActivePresetNumber());
        assertTrue(prefs.getBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false));

        PresetModel importedPreset = new PresetModel(PresetModel.PRESET_1);
        importedPreset.name = "imported-active";
        importedPreset.enabled = true;
        importedPreset.autoKillEnabled = true;
        importedPreset.startHour = 0;
        importedPreset.startMinute = 0;
        importedPreset.endHour = 0;
        importedPreset.endMinute = 0;

        JSONObject backup = new JSONObject()
                .put(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false)
                .put("presets", new JSONObject().put("preset_1", importedPreset.toJson()));

        BackupManager manager = new BackupManager(context);
        assertTrue(manager.restoreBackupJson(backup.toString()));

        assertEquals(PresetModel.PRESET_1, presetManager.getActivePresetNumber());
        assertTrue(prefs.getBoolean(PreferenceKeys.KEY_AUTO_KILL_ENABLED, false));
        assertFalse(prefs.getBoolean(
                PresetManager.KEY_BACKUP_PREFIX + PreferenceKeys.KEY_AUTO_KILL_ENABLED,
                true));
        assertEquals("imported-active", presetManager.loadPreset(PresetModel.PRESET_1).name);
    }

    @Test
    public void manualRestrictionDetailsRoundTripAndReplaceStaleValues() throws Exception {
        String packageName = "com.example.manual";
        String stalePackage = "com.example.stale";
        assertTrue(prefs.edit()
                .putStringSet(PreferenceKeys.KEY_MANUAL_RESTRICTION_APPS, Set.of(packageName))
                .putInt(PreferenceKeys.KEY_MANUAL_OPS_PREFIX + packageName, 0x401)
                .putInt(PreferenceKeys.KEY_MANUAL_BUCKET_PREFIX + packageName, 45)
                .putBoolean(PreferenceKeys.KEY_MANUAL_WHITELIST_REMOVAL_PREFIX + packageName, true)
                .commit());

        BackupManager manager = new BackupManager(context);
        String encoded = manager.createBackupJson();
        assertTrue(encoded != null);
        JSONObject root = new JSONObject(encoded);
        assertEquals(BackupCodec.CURRENT_VERSION, root.getInt(BACKUP_VERSION));
        assertEquals(0x401, root.getJSONObject("manual_ops_masks").getInt(packageName));
        assertEquals(45, root.getJSONObject("manual_buckets").getInt(packageName));
        assertTrue(root.getJSONObject("manual_whitelist_removals").getBoolean(packageName));

        assertTrue(prefs.edit()
                .putStringSet(PreferenceKeys.KEY_MANUAL_RESTRICTION_APPS, Set.of(stalePackage))
                .putInt(PreferenceKeys.KEY_MANUAL_OPS_PREFIX + packageName, 0x01)
                .putInt(PreferenceKeys.KEY_MANUAL_BUCKET_PREFIX + packageName, 40)
                .putBoolean(PreferenceKeys.KEY_MANUAL_WHITELIST_REMOVAL_PREFIX + packageName, false)
                .putInt(PreferenceKeys.KEY_MANUAL_OPS_PREFIX + stalePackage, 0x02)
                .putInt(PreferenceKeys.KEY_MANUAL_BUCKET_PREFIX + stalePackage, 45)
                .putBoolean(PreferenceKeys.KEY_MANUAL_WHITELIST_REMOVAL_PREFIX + stalePackage, true)
                .commit());

        assertTrue(manager.restoreBackupJson(encoded));
        assertEquals(Set.of(packageName),
                prefs.getStringSet(PreferenceKeys.KEY_MANUAL_RESTRICTION_APPS, Set.of()));
        assertEquals(0x401, prefs.getInt(PreferenceKeys.KEY_MANUAL_OPS_PREFIX + packageName, -1));
        assertEquals(45, prefs.getInt(PreferenceKeys.KEY_MANUAL_BUCKET_PREFIX + packageName, -1));
        assertTrue(prefs.getBoolean(PreferenceKeys.KEY_MANUAL_WHITELIST_REMOVAL_PREFIX + packageName, false));
        assertFalse(prefs.contains(PreferenceKeys.KEY_MANUAL_OPS_PREFIX + stalePackage));
        assertFalse(prefs.contains(PreferenceKeys.KEY_MANUAL_BUCKET_PREFIX + stalePackage));
        assertFalse(prefs.contains(PreferenceKeys.KEY_MANUAL_WHITELIST_REMOVAL_PREFIX + stalePackage));
    }

    @Test
    public void invalidManualBucketIsRejectedBeforeDurableRestoreWrite() throws Exception {
        String packageName = "com.example.manual";
        String bucketKey = PreferenceKeys.KEY_MANUAL_BUCKET_PREFIX + packageName;
        assertTrue(prefs.edit()
                .putBoolean(KEY_EXIT_ON_BACK, false)
                .putInt(bucketKey, 40)
                .commit());

        JSONObject backup = new JSONObject()
                .put(BACKUP_VERSION, BackupCodec.CURRENT_VERSION)
                .put(KEY_EXIT_ON_BACK, true)
                .put(PreferenceKeys.KEY_MANUAL_RESTRICTION_APPS, new org.json.JSONArray().put(packageName))
                .put("manual_ops_masks", new JSONObject().put(packageName, 0x01))
                .put("manual_buckets", new JSONObject().put(packageName, 10))
                .put("manual_whitelist_removals", new JSONObject().put(packageName, false));

        BackupManager manager = new BackupManager(context);
        assertFalse(manager.restoreBackupJson(backup.toString()));
        assertFalse(prefs.getBoolean(KEY_EXIT_ON_BACK, true));
        assertEquals(40, prefs.getInt(bucketKey, -1));
    }

    @Test
    public void versionFiveManualRestoreClearsDetailsThatWereNotPortableYet() throws Exception {
        String packageName = "com.example.manual";
        assertTrue(prefs.edit()
                .putInt(PreferenceKeys.KEY_MANUAL_BUCKET_PREFIX + packageName, 45)
                .putBoolean(PreferenceKeys.KEY_MANUAL_WHITELIST_REMOVAL_PREFIX + packageName, true)
                .commit());

        JSONObject backup = new JSONObject()
                .put(BACKUP_VERSION, 5)
                .put(PreferenceKeys.KEY_MANUAL_RESTRICTION_APPS, new org.json.JSONArray().put(packageName))
                .put("manual_ops_masks", new JSONObject().put(packageName, 0x01));

        BackupManager manager = new BackupManager(context);
        assertTrue(manager.restoreBackupJson(backup.toString()));
        assertEquals(0, prefs.getInt(PreferenceKeys.KEY_MANUAL_BUCKET_PREFIX + packageName, 0));
        assertFalse(prefs.getBoolean(PreferenceKeys.KEY_MANUAL_WHITELIST_REMOVAL_PREFIX + packageName, false));
        assertFalse(prefs.contains(PreferenceKeys.KEY_MANUAL_BUCKET_PREFIX + packageName));
        assertFalse(prefs.contains(PreferenceKeys.KEY_MANUAL_WHITELIST_REMOVAL_PREFIX + packageName));
    }

    @Test
    public void unknownManualOpsBitsAreRejectedBeforeDurableRestoreWrite() throws Exception {
        String packageName = "com.example.manual";
        String maskKey = PreferenceKeys.KEY_MANUAL_OPS_PREFIX + packageName;
        assertTrue(prefs.edit()
                .putBoolean(KEY_EXIT_ON_BACK, false)
                .putInt(maskKey, 0x01)
                .commit());

        int unknownBit = 1 << BackgroundAppManager.ALL_OPS.length;
        JSONObject backup = new JSONObject()
                .put(KEY_EXIT_ON_BACK, true)
                .put("manual_ops_masks", new JSONObject().put(packageName, unknownBit));

        BackupManager manager = new BackupManager(context);

        assertFalse(manager.restoreBackupJson(backup.toString()));
        assertFalse(prefs.getBoolean(KEY_EXIT_ON_BACK, true));
        assertEquals(0x01, prefs.getInt(maskKey, -1));
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
