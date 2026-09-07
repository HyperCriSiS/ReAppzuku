package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.utils.PresetModel;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;

public class BackupManager {
    public enum RestoreCommitPoint {
        AFTER_MAIN_COMMIT,
        AFTER_PRESET_1_COMMIT,
        AFTER_PRESET_2_COMMIT
    }

    interface RestoreFaultInjector {
        void afterCommit(RestoreCommitPoint point) throws Exception;
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final BackupCodec backupCodec;
    private final RestoreFaultInjector restoreFaultInjector;

    public BackupManager(Context context) {
        this(context, new BackupCodec(), point -> {});
    }

    BackupManager(Context context, BackupCodec backupCodec, RestoreFaultInjector restoreFaultInjector) {
        if (context == null) throw new IllegalArgumentException("context == null");
        if (backupCodec == null) throw new IllegalArgumentException("backupCodec == null");
        if (restoreFaultInjector == null) throw new IllegalArgumentException("restoreFaultInjector == null");
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences("ShappkyPrefs", Context.MODE_PRIVATE);
        this.backupCodec = backupCodec;
        this.restoreFaultInjector = restoreFaultInjector;
    }

    public String createBackupJson() {
        try {
            JSONObject root = backupCodec.newRoot();

            putStringSet(root, KEY_HIDDEN_APPS);
            putStringSet(root, KEY_WHITELISTED_APPS);
            putStringSet(root, KEY_BLACKLISTED_APPS);
            putStringSet(root, KEY_AUTOSTART_DISABLED_APPS);
            putStringSet(root, KEY_HARD_RESTRICTION_APPS);
            putStringSet(root, KEY_MANUAL_RESTRICTION_APPS);
            putManualOpsMasks(root);
            putStringSet(root, KEY_SLEEP_MODE_APPS);
            putStringSet(root, KEY_SLEEP_MODE_APPS_PERMANENT);
            putStringSet(root, KEY_MEDIUM_RESTRICTION_APPS);
            putStringSet(root, KEY_BATTERY_WHITELIST_REMOVED);
            putStringSet(root, KEY_APP_LAUNCH_TRIGGER_PACKAGES);

            root.put(KEY_KILL_MODE, prefs.getInt(KEY_KILL_MODE, 0));
            root.put(KEY_AUTO_KILL_ENABLED, prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false));
            root.put(KEY_PERIODIC_KILL_ENABLED, prefs.getBoolean(KEY_PERIODIC_KILL_ENABLED, false));
            root.put(KEY_KILL_INTERVAL, prefs.getInt(KEY_KILL_INTERVAL, 15));
            root.put(KEY_KILL_ON_SCREEN_OFF, prefs.getBoolean(KEY_KILL_ON_SCREEN_OFF, false));
            root.put(KEY_RAM_THRESHOLD, prefs.getInt(KEY_RAM_THRESHOLD, 85));
            root.put(KEY_RAM_THRESHOLD_ENABLED, prefs.getBoolean(KEY_RAM_THRESHOLD_ENABLED, false));

            root.put(KEY_SHOW_SYSTEM_APPS, prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false));
            root.put(KEY_SHOW_PERSISTENT_APPS, prefs.getBoolean(KEY_SHOW_PERSISTENT_APPS, false));
            root.put(KEY_THEME, prefs.getInt(KEY_THEME, 0));
            root.put(KEY_ACCENT, prefs.getInt(KEY_ACCENT, 0));
            root.put(KEY_ACCENT_CUSTOM_COLOR, prefs.getInt(KEY_ACCENT_CUSTOM_COLOR, 0));
            root.put(KEY_ACCENT_ON_COLOR, prefs.getInt(KEY_ACCENT_ON_COLOR, 0));
            root.put(KEY_AMOLED, prefs.getBoolean(KEY_AMOLED, false));
            root.put(KEY_SORT_MODE, prefs.getInt(KEY_SORT_MODE, 0));
            root.put(KEY_NOTIFICATION_MODE, prefs.getInt(KEY_NOTIFICATION_MODE, 0));
            root.put(KEY_AUTO_KILL_TYPE, prefs.getInt(KEY_AUTO_KILL_TYPE, 0));

            root.put(KEY_SLEEP_MODE_ENABLED, prefs.getBoolean(KEY_SLEEP_MODE_ENABLED, false));
            root.put(KEY_SLEEP_MODE_DELAY, prefs.getLong(KEY_SLEEP_MODE_DELAY, 0L));
            root.put(KEY_EXIT_ON_BACK, prefs.getBoolean(KEY_EXIT_ON_BACK, false));
            root.put(KEY_PREVENT_SHIZUKU_AUTOSTART, prefs.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, false));
            root.put(KEY_SMART_LIFECYCLE_ENABLED, prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false));
            root.put(KEY_SMART_BOOT_CLEANUP_ENABLED, prefs.getBoolean(KEY_SMART_BOOT_CLEANUP_ENABLED, false));
            root.put(KEY_SMART_LIFECYCLE_PROFILE, prefs.getInt(KEY_SMART_LIFECYCLE_PROFILE, 0));

            root.put(KEY_HW_TRIGGER_HEADSET, prefs.getBoolean(KEY_HW_TRIGGER_HEADSET, false));
            root.put(KEY_HW_TRIGGER_USB, prefs.getBoolean(KEY_HW_TRIGGER_USB, false));
            root.put(KEY_HW_TRIGGER_CHARGER, prefs.getBoolean(KEY_HW_TRIGGER_CHARGER, false));
            root.put(KEY_HW_TRIGGER_WIFI, prefs.getBoolean(KEY_HW_TRIGGER_WIFI, false));
            root.put(KEY_HW_TRIGGER_BLUETOOTH, prefs.getBoolean(KEY_HW_TRIGGER_BLUETOOTH, false));
            root.put(KEY_HW_TRIGGER_GPS, prefs.getBoolean(KEY_HW_TRIGGER_GPS, false));
            root.put(KEY_HW_TRIGGER_HOTSPOT, prefs.getBoolean(KEY_HW_TRIGGER_HOTSPOT, false));
            root.put(KEY_APP_LAUNCH_TRIGGER_ENABLED, prefs.getBoolean(KEY_APP_LAUNCH_TRIGGER_ENABLED, false));
            root.put(KEY_APP_LAUNCH_CLEAR_CACHE, prefs.getBoolean(KEY_APP_LAUNCH_CLEAR_CACHE, false));

            putPresets(root);
            return backupCodec.encode(root);
        } catch (Exception e) {
            AppDebugManager.e(Category.BACKUP_RESTORE, "BackupManager: createBackupJson failed", e);
            return null;
        }
    }

    public boolean restoreBackupJson(String json) {
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: start, json length="
                + (json == null ? -1 : json.length()));
        BackupCodec.DecodedBackup decoded;
        try {
            decoded = backupCodec.decode(json);
        } catch (BackupCodec.DecodeException e) {
            switch (e.reason) {
                case PAYLOAD_SIZE:
                    AppDebugManager.w(Category.BACKUP_RESTORE,
                            "BackupManager: restoreBackupJson: rejected empty/oversized payload");
                    break;
                case FUTURE_VERSION:
                    AppDebugManager.w(Category.BACKUP_RESTORE,
                            "BackupManager: restoreBackupJson: future version " + e.detectedVersion);
                    break;
                case MALFORMED:
                default:
                    AppDebugManager.w(Category.BACKUP_RESTORE,
                            "BackupManager: restoreBackupJson: malformed payload", e);
                    break;
            }
            return false;
        }

        JSONObject root = decoded.root;
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: backup version="
                + decoded.version + (decoded.legacy ? " (legacy)" : ""));

        PresetManager presetManager = null;
        Map<String, ?> mainSnapshot = null;
        Map<String, ?> preset1Snapshot = null;
        Map<String, ?> preset2Snapshot = null;
        boolean mainCommitted = false;
        boolean preset1Committed = false;
        boolean preset2Committed = false;

        try {
            boolean containsPresetSection = root.has(KEY_PRESETS);
            PresetModel[] restoredPresets = parsePresets(root);
            if (containsPresetSection) presetManager = new PresetManager(context);

            mainSnapshot = snapshotPreferences(prefs);
            if (presetManager != null) {
                preset1Snapshot = presetManager.snapshotPresetStorage(PresetModel.PRESET_1);
                preset2Snapshot = presetManager.snapshotPresetStorage(PresetModel.PRESET_2);
            }

            SharedPreferences.Editor editor = prefs.edit();
            restoreSet(editor, root, KEY_HIDDEN_APPS);
            restoreSet(editor, root, KEY_WHITELISTED_APPS);
            restoreSet(editor, root, KEY_BLACKLISTED_APPS);
            restoreSet(editor, root, KEY_AUTOSTART_DISABLED_APPS);
            restoreSet(editor, root, KEY_HARD_RESTRICTION_APPS);
            restoreSet(editor, root, KEY_MANUAL_RESTRICTION_APPS);
            restoreManualOpsMasks(editor, root);
            restoreSet(editor, root, KEY_SLEEP_MODE_APPS);
            restoreSet(editor, root, KEY_SLEEP_MODE_APPS_PERMANENT);
            restoreSet(editor, root, KEY_MEDIUM_RESTRICTION_APPS);
            restoreSet(editor, root, KEY_BATTERY_WHITELIST_REMOVED);
            restoreSet(editor, root, KEY_APP_LAUNCH_TRIGGER_PACKAGES);

            restoreInt(editor, root, KEY_KILL_MODE);
            restoreBoolean(editor, root, KEY_AUTO_KILL_ENABLED);
            restoreBoolean(editor, root, KEY_PERIODIC_KILL_ENABLED);
            restoreInt(editor, root, KEY_KILL_INTERVAL);
            restoreBoolean(editor, root, KEY_KILL_ON_SCREEN_OFF);
            restoreInt(editor, root, KEY_RAM_THRESHOLD);
            restoreBoolean(editor, root, KEY_RAM_THRESHOLD_ENABLED);

            restoreBoolean(editor, root, KEY_SHOW_SYSTEM_APPS);
            restoreBoolean(editor, root, KEY_SHOW_PERSISTENT_APPS);
            restoreInt(editor, root, KEY_THEME);
            restoreInt(editor, root, KEY_ACCENT);
            restoreInt(editor, root, KEY_ACCENT_CUSTOM_COLOR);
            restoreInt(editor, root, KEY_ACCENT_ON_COLOR);
            restoreBoolean(editor, root, KEY_AMOLED);
            restoreInt(editor, root, KEY_SORT_MODE);
            restoreInt(editor, root, KEY_NOTIFICATION_MODE);
            restoreInt(editor, root, KEY_AUTO_KILL_TYPE);

            restoreBoolean(editor, root, KEY_SLEEP_MODE_ENABLED);
            restoreLong(editor, root, KEY_SLEEP_MODE_DELAY);
            restoreBoolean(editor, root, KEY_EXIT_ON_BACK);
            restoreBoolean(editor, root, KEY_PREVENT_SHIZUKU_AUTOSTART);
            restoreBoolean(editor, root, KEY_SMART_LIFECYCLE_ENABLED);
            restoreBoolean(editor, root, KEY_SMART_BOOT_CLEANUP_ENABLED);
            restoreInt(editor, root, KEY_SMART_LIFECYCLE_PROFILE);

            restoreBoolean(editor, root, KEY_HW_TRIGGER_HEADSET);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_USB);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_CHARGER);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_WIFI);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_BLUETOOTH);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_GPS);
            restoreBoolean(editor, root, KEY_HW_TRIGGER_HOTSPOT);
            restoreBoolean(editor, root, KEY_APP_LAUNCH_TRIGGER_ENABLED);
            restoreBoolean(editor, root, KEY_APP_LAUNCH_CLEAR_CACHE);

            if (!editor.commit()) throw new IllegalStateException("Main preferences commit failed");
            mainCommitted = true;
            restoreFaultInjector.afterCommit(RestoreCommitPoint.AFTER_MAIN_COMMIT);

            if (containsPresetSection) {
                if (!writePresetStorage(presetManager, PresetModel.PRESET_1, restoredPresets[0]))
                    throw new IllegalStateException("Preset 1 commit failed");
                preset1Committed = true;
                restoreFaultInjector.afterCommit(RestoreCommitPoint.AFTER_PRESET_1_COMMIT);
                if (!writePresetStorage(presetManager, PresetModel.PRESET_2, restoredPresets[1]))
                    throw new IllegalStateException("Preset 2 commit failed");
                preset2Committed = true;
                restoreFaultInjector.afterCommit(RestoreCommitPoint.AFTER_PRESET_2_COMMIT);
            }

            if (containsPresetSection) presetManager.restoreAfterBoot();
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: success");
            return true;
        } catch (Exception e) {
            AppDebugManager.e(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: FAILED", e);
            if (mainCommitted || preset1Committed || preset2Committed) {
                boolean rollbackOk = rollbackRestore(
                        presetManager,
                        mainSnapshot,
                        preset1Snapshot,
                        preset2Snapshot);
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: restore rollback result=" + rollbackOk);
            }
            return false;
        }
    }

    private void restoreSet(SharedPreferences.Editor editor, JSONObject root, String key) throws Exception {
        if (root.has(key)) {
            JSONArray array = root.getJSONArray(key);
            BackupCollectionPolicy.requirePackageEntryCount(key, array.length());
            Set<String> set = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                String packageName = array.getString(i);
                if (!PackageNameValidator.isValid(packageName)) {
                    throw new IllegalArgumentException("Invalid package name in backup: " + key);
                }
                set.add(packageName);
            }
            editor.putStringSet(key, set);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreSet: " + key + " -> " + set.size() + " items");
        }
    }

    private void putStringSet(JSONObject root, String key) throws Exception {
        Set<String> stored = prefs.getStringSet(key, new HashSet<>());
        Set<String> set = stored == null ? new HashSet<>() : new HashSet<>(stored);
        BackupCollectionPolicy.requirePackageEntryCount(key, set.size());
        root.put(key, new JSONArray(set));
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: putStringSet: " + key + " -> " + set.size() + " items");
    }

    private void putManualOpsMasks(JSONObject root) throws Exception {
        Set<String> manualPackages = prefs.getStringSet(KEY_MANUAL_RESTRICTION_APPS, new java.util.HashSet<>());
        if (manualPackages == null) manualPackages = new java.util.HashSet<>();
        BackupCollectionPolicy.requirePackageEntryCount(KEY_MANUAL_OPS_MASKS, manualPackages.size());
        JSONObject masks = new JSONObject();
        for (String pkg : manualPackages) {
            int mask = prefs.getInt(KEY_MANUAL_OPS_PREFIX + pkg, 0x01);
            masks.put(pkg, mask);
        }
        root.put(KEY_MANUAL_OPS_MASKS, masks);
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: putManualOpsMasks: " + manualPackages.size() + " packages");
    }

    private void restoreManualOpsMasks(SharedPreferences.Editor editor, JSONObject root) throws Exception {
        if (!root.has(KEY_MANUAL_OPS_MASKS)) return;
        JSONObject masks = root.getJSONObject(KEY_MANUAL_OPS_MASKS);
        BackupCollectionPolicy.requirePackageEntryCount(KEY_MANUAL_OPS_MASKS, masks.length());
        java.util.Iterator<String> keys = masks.keys();
        int count = 0;
        while (keys.hasNext()) {
            String pkg = keys.next();
            if (!PackageNameValidator.isValid(pkg)) {
                throw new IllegalArgumentException("Invalid package name in manual ops backup");
            }
            editor.putInt(KEY_MANUAL_OPS_PREFIX + pkg, masks.getInt(pkg));
            count++;
        }
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreManualOpsMasks: " + count + " packages");
    }

    private void putPresets(JSONObject root) throws Exception {
        PresetManager presetManager = new PresetManager(context);
        JSONObject presets = new JSONObject();
        for (int presetNumber : new int[]{ PresetModel.PRESET_1, PresetModel.PRESET_2 }) {
            PresetModel model = presetManager.loadPreset(presetNumber);
            if (model == null) {
                AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: putPresets: preset #" + presetNumber + " not set, skipping");
                continue;
            }
            presets.put(KEY_PRESET_PREFIX + presetNumber, model.toJson());
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: putPresets: preset #" + presetNumber + " written, name=" + model.name);
        }
        root.put(KEY_PRESETS, presets);
    }

    private PresetModel[] parsePresets(JSONObject root) throws Exception {
        PresetModel[] result = new PresetModel[2];
        if (!root.has(KEY_PRESETS)) return result;
        JSONObject presets = root.getJSONObject(KEY_PRESETS);
        if (presets.has(KEY_PRESET_PREFIX + PresetModel.PRESET_1)) {
            result[0] = PresetModel.fromJson(presets.getJSONObject(KEY_PRESET_PREFIX + PresetModel.PRESET_1));
        }
        if (presets.has(KEY_PRESET_PREFIX + PresetModel.PRESET_2)) {
            result[1] = PresetModel.fromJson(presets.getJSONObject(KEY_PRESET_PREFIX + PresetModel.PRESET_2));
        }
        return result;
    }

    private boolean writePresetStorage(PresetManager manager, int presetNumber, PresetModel model) {
        if (model == null) return manager.clearPresetStorageBlocking(presetNumber);
        return manager.savePresetBlocking(presetNumber, model);
    }

    private boolean rollbackRestore(
            PresetManager manager,
            Map<String, ?> mainSnapshot,
            Map<String, ?> preset1Snapshot,
            Map<String, ?> preset2Snapshot) {
        boolean mainOk = restorePreferenceMap(prefs, mainSnapshot);
        boolean preset1Ok = manager == null || preset1Snapshot == null
                || manager.restorePresetStorageBlocking(PresetModel.PRESET_1, preset1Snapshot);
        boolean preset2Ok = manager == null || preset2Snapshot == null
                || manager.restorePresetStorageBlocking(PresetModel.PRESET_2, preset2Snapshot);
        if (manager != null && mainOk && preset1Ok && preset2Ok) {
            manager.restoreAfterBoot();
        }
        return mainOk && preset1Ok && preset2Ok;
    }

    private static Map<String, ?> snapshotPreferences(SharedPreferences target) {
        return new java.util.HashMap<>(target.getAll());
    }

    private static boolean restorePreferenceMap(SharedPreferences target, Map<String, ?> values) {
        if (values == null) return false;
        SharedPreferences.Editor editor = target.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Set) {
                @SuppressWarnings("unchecked") Set<String> set = (Set<String>) value;
                editor.putStringSet(entry.getKey(), new HashSet<>(set));
            }
        }
        return editor.commit();
    }

    private void restoreBoolean(SharedPreferences.Editor editor, JSONObject root, String key) throws Exception {
        if (root.has(key)) {
            boolean value = root.getBoolean(key);
            editor.putBoolean(key, value);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBoolean: " + key + "=" + value);
        }
    }

    private void restoreInt(SharedPreferences.Editor editor, JSONObject root, String key) throws Exception {
        if (root.has(key)) {
            int value = root.getInt(key);
            editor.putInt(key, value);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreInt: " + key + "=" + value);
        }
    }

    private void restoreLong(SharedPreferences.Editor editor, JSONObject root, String key) throws Exception {
        if (root.has(key)) {
            long value = root.getLong(key);
            editor.putLong(key, value);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreLong: " + key + "=" + value);
        }
    }
}