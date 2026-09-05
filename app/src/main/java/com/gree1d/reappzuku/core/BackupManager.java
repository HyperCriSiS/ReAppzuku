package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.utils.PresetModel;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import static com.gree1d.reappzuku.core.PreferenceKeys.*;

public class BackupManager {
    private static final String TAG = "BackupManager";
    private static final String KEY_MANUAL_OPS_MASKS = "manual_ops_masks";
    private static final String KEY_PRESETS = "presets";
    private static final String KEY_PRESET_PREFIX = "preset_";

    private final Context context;
    private final SharedPreferences prefs;
    private final BackupCodec backupCodec;
    private final RestoreFaultInjector restoreFaultInjector;

    enum RestoreCommitPoint {
        AFTER_MAIN_COMMIT,
        AFTER_PRESET_1_COMMIT,
        AFTER_PRESET_2_COMMIT
    }

    interface RestoreFaultInjector {
        RestoreFaultInjector NONE = point -> { };

        void afterCommit(RestoreCommitPoint point);
    }

    public BackupManager(Context context) {
        this(context, new BackupCodec(), RestoreFaultInjector.NONE);
    }

    BackupManager(Context context, BackupCodec backupCodec) {
        this(context, backupCodec, RestoreFaultInjector.NONE);
    }

    BackupManager(Context context, BackupCodec backupCodec, RestoreFaultInjector restoreFaultInjector) {
        if (backupCodec == null) throw new IllegalArgumentException("backupCodec == null");
        if (restoreFaultInjector == null) throw new IllegalArgumentException("restoreFaultInjector == null");
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        this.backupCodec = backupCodec;
        this.restoreFaultInjector = restoreFaultInjector;
    }

    private boolean getSafeBool(String key, boolean defVal) {
        try {
            return prefs.getBoolean(key, defVal);
        } catch (ClassCastException e) {
            AppDebugManager.w(Category.BACKUP_RESTORE, "BackupManager: getSafeBool: key=" + key + " stored as wrong type, falling back to String parse");
            String raw = prefs.getString(key, null);
            if (raw == null) return defVal;
            return Boolean.parseBoolean(raw);
        }
    }

    public String createBackupJson() {
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: start");
        try {
            JSONObject root = backupCodec.newRoot();
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: version written");

            putStringSet(root, KEY_HIDDEN_APPS);
            putStringSet(root, KEY_WHITELISTED_APPS);
            putStringSet(root, KEY_BLACKLISTED_APPS);
            putStringSet(root, KEY_AUTOSTART_DISABLED_APPS);
            putStringSet(root, KEY_HARD_RESTRICTION_APPS);
            putStringSet(root, KEY_MANUAL_RESTRICTION_APPS);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: app lists written");

            putManualOpsMasks(root);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: manual ops masks written");

            putStringSet(root, KEY_SLEEP_MODE_APPS);
            putStringSet(root, KEY_SLEEP_MODE_APPS_PERMANENT);
            putStringSet(root, KEY_MEDIUM_RESTRICTION_APPS);
            putStringSet(root, KEY_BATTERY_WHITELIST_REMOVED);
            putStringSet(root, KEY_APP_LAUNCH_TRIGGER_PACKAGES);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: extra sets written");

            root.put(KEY_KILL_MODE, prefs.getInt(KEY_KILL_MODE, 0));
            root.put(KEY_AUTO_KILL_ENABLED, getSafeBool(KEY_AUTO_KILL_ENABLED, false));
            root.put(KEY_PERIODIC_KILL_ENABLED, getSafeBool(KEY_PERIODIC_KILL_ENABLED, false));
            root.put(KEY_KILL_INTERVAL, prefs.getInt(KEY_KILL_INTERVAL, AppConstants.DEFAULT_KILL_INTERVAL_MS));
            root.put(KEY_KILL_ON_SCREEN_OFF, getSafeBool(KEY_KILL_ON_SCREEN_OFF, false));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: kill settings written");

            root.put(KEY_RAM_THRESHOLD, prefs.getInt(KEY_RAM_THRESHOLD, AppConstants.DEFAULT_RAM_THRESHOLD_PERCENT));
            root.put(KEY_RAM_THRESHOLD_ENABLED, getSafeBool(KEY_RAM_THRESHOLD_ENABLED, false));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: RAM settings written");

            root.put(KEY_SHOW_SYSTEM_APPS, getSafeBool(KEY_SHOW_SYSTEM_APPS, false));
            root.put(KEY_SHOW_PERSISTENT_APPS, getSafeBool(KEY_SHOW_PERSISTENT_APPS, false));
            root.put(KEY_THEME, prefs.getInt(KEY_THEME, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
            root.put(KEY_ACCENT, prefs.getInt(KEY_ACCENT, AppConstants.ACCENT_SYSTEM));
            root.put(KEY_ACCENT_CUSTOM_COLOR, prefs.getInt(KEY_ACCENT_CUSTOM_COLOR, AppConstants.ACCENT_CUSTOM_DEFAULT_COLOR));
            root.put(KEY_ACCENT_ON_COLOR, prefs.getInt(KEY_ACCENT_ON_COLOR, AppConstants.ACCENT_ON_WHITE));
            root.put(KEY_AMOLED, getSafeBool(KEY_AMOLED, false));
            root.put(KEY_SORT_MODE, prefs.getInt(KEY_SORT_MODE, AppConstants.SORT_MODE_DEFAULT));
            root.put(KEY_NOTIFICATION_MODE, prefs.getInt(KEY_NOTIFICATION_MODE, NOTIFICATION_MODE_ALL));
            root.put(KEY_AUTO_KILL_TYPE, prefs.getInt(KEY_AUTO_KILL_TYPE, 0));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: display/UI settings written");

            root.put(KEY_SLEEP_MODE_ENABLED, getSafeBool(KEY_SLEEP_MODE_ENABLED, false));
            root.put(KEY_SLEEP_MODE_DELAY, prefs.getLong(KEY_SLEEP_MODE_DELAY, AppConstants.DEFAULT_SLEEP_MODE_DELAY_MS));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: sleep mode settings written");

            root.put(KEY_EXIT_ON_BACK, getSafeBool(KEY_EXIT_ON_BACK, false));
            root.put(KEY_PREVENT_SHIZUKU_AUTOSTART, getSafeBool(KEY_PREVENT_SHIZUKU_AUTOSTART, true));
            root.put(KEY_SMART_LIFECYCLE_ENABLED, getSafeBool(KEY_SMART_LIFECYCLE_ENABLED, false));
            root.put(KEY_SMART_BOOT_CLEANUP_ENABLED, getSafeBool(KEY_SMART_BOOT_CLEANUP_ENABLED, true));
            root.put(KEY_SMART_LIFECYCLE_PROFILE, prefs.getInt(KEY_SMART_LIFECYCLE_PROFILE, 1));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: fork behavior settings written");

            root.put(KEY_HW_TRIGGER_HEADSET, getSafeBool(KEY_HW_TRIGGER_HEADSET, false));
            root.put(KEY_HW_TRIGGER_USB, getSafeBool(KEY_HW_TRIGGER_USB, false));
            root.put(KEY_HW_TRIGGER_CHARGER, getSafeBool(KEY_HW_TRIGGER_CHARGER, false));
            root.put(KEY_HW_TRIGGER_WIFI, getSafeBool(KEY_HW_TRIGGER_WIFI, false));
            root.put(KEY_HW_TRIGGER_BLUETOOTH, getSafeBool(KEY_HW_TRIGGER_BLUETOOTH, false));
            root.put(KEY_HW_TRIGGER_GPS, getSafeBool(KEY_HW_TRIGGER_GPS, false));
            root.put(KEY_HW_TRIGGER_HOTSPOT, getSafeBool(KEY_HW_TRIGGER_HOTSPOT, false));
            root.put(KEY_APP_LAUNCH_TRIGGER_ENABLED, getSafeBool(KEY_APP_LAUNCH_TRIGGER_ENABLED, false));
            root.put(KEY_APP_LAUNCH_CLEAR_CACHE, getSafeBool(KEY_APP_LAUNCH_CLEAR_CACHE, false));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: hardware triggers written");

            putPresets(root);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: presets written");

            String result = backupCodec.encode(root);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: success, json length=" + result.length());
            return result;
        } catch (Exception e) {
            AppDebugManager.e(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: FAILED", e);
            return null;
        }
    }

    public boolean restoreBackupJson(String json) {
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: start, json length="
                + (json != null ? json.length() : -1));
        BackupCodec.DecodedBackup decoded;
        try {
            decoded = backupCodec.decode(json);
        } catch (BackupCodec.DecodeException e) {
            if (e.reason == BackupCodec.DecodeFailure.PAYLOAD_SIZE) {
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: refusing empty/oversized backup payload");
            } else if (e.reason == BackupCodec.DecodeFailure.FUTURE_VERSION) {
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: refusing unsupported future backup version=" + e.detectedVersion
                                + " supported=" + BackupCodec.CURRENT_VERSION);
            } else {
                AppDebugManager.e(Category.BACKUP_RESTORE,
                        "BackupManager: restoreBackupJson: malformed payload", e);
            }
            return false;
        }

        Map<String, ?> mainSnapshot = null;
        Map<String, ?> preset1Snapshot = null;
        Map<String, ?> preset2Snapshot = null;
        PresetManager presetManager = new PresetManager(context);
        boolean durableWriteStarted = false;
        try {
            JSONObject root = decoded.root;
            if (decoded.legacy) {
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: legacy/unversioned backup detected; validating available fields");
            }

            // Validate all preset JSON before the first durable write.
            PresetModel[] restoredPresets = parsePresets(root);
            boolean containsPresetSection = root.has(KEY_PRESETS);

            // Snapshot every preference file participating in the transaction.
            mainSnapshot = deepCopyPreferenceMap(prefs.getAll());
            preset1Snapshot = presetManager.snapshotPresetStorage(PresetModel.PRESET_1);
            preset2Snapshot = presetManager.snapshotPresetStorage(PresetModel.PRESET_2);

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

            if (containsPresetSection) {
                // Active preset state is runtime state, not portable configuration.
                // The imported main settings become the new base state.
                editor.remove(KEY_ACTIVE_PRESET);
                for (String key : prefs.getAll().keySet()) {
                    if (key.startsWith(PresetManager.KEY_BACKUP_PREFIX)) editor.remove(key);
                }
            }

            durableWriteStarted = true;
            if (!editor.commit()) throw new IllegalStateException("main preferences commit failed");
            restoreFaultInjector.afterCommit(RestoreCommitPoint.AFTER_MAIN_COMMIT);

            if (containsPresetSection) {
                if (!writePresetStorage(presetManager, PresetModel.PRESET_1, restoredPresets[0]))
                    throw new IllegalStateException("preset 1 commit failed");
                restoreFaultInjector.afterCommit(RestoreCommitPoint.AFTER_PRESET_1_COMMIT);
                if (!writePresetStorage(presetManager, PresetModel.PRESET_2, restoredPresets[1]))
                    throw new IllegalStateException("preset 2 commit failed");
                restoreFaultInjector.afterCommit(RestoreCommitPoint.AFTER_PRESET_2_COMMIT);
            }

            // Side effects only after all durable state was committed successfully.
            if (containsPresetSection) presetManager.restoreAfterBoot();
            BackgroundWorkPolicy.enforceCompatibleBehavior(context);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: success");
            return true;
        } catch (Exception e) {
            AppDebugManager.e(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: FAILED", e);
            if (durableWriteStarted && mainSnapshot != null) {
                boolean rollbackOk = rollbackRestore(
                        presetManager, mainSnapshot, preset1Snapshot, preset2Snapshot);
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: restore rollback result=" + rollbackOk);
            }
            return false;
        }
    }

    private void restoreSet(SharedPreferences.Editor editor, JSONObject root, String key) throws Exception {
        if (root.has(key)) {
            JSONArray array = root.getJSONArray(key);
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
        root.put(key, new JSONArray(set));
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: putStringSet: " + key + " -> " + set.size() + " items");
    }

    private void putManualOpsMasks(JSONObject root) throws Exception {
        Set<String> manualPackages = prefs.getStringSet(KEY_MANUAL_RESTRICTION_APPS, new java.util.HashSet<>());
        if (manualPackages == null) manualPackages = new java.util.HashSet<>();
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
        for (int presetNumber : new int[]{PresetModel.PRESET_1, PresetModel.PRESET_2}) {
            String key = KEY_PRESET_PREFIX + presetNumber;
            if (presets.has(key)) {
                result[presetNumber - 1] =
                        PresetModel.fromJson(presetNumber, presets.getJSONObject(key));
            }
        }
        return result;
    }

    private boolean writePresetStorage(PresetManager manager, int number, PresetModel model) {
        return model == null
                ? manager.clearPresetStorageBlocking(number)
                : manager.savePresetBlocking(model);
    }

    private boolean rollbackRestore(PresetManager manager,
                                    Map<String, ?> mainSnapshot,
                                    Map<String, ?> preset1Snapshot,
                                    Map<String, ?> preset2Snapshot) {
        boolean mainOk = restorePreferenceMap(prefs, mainSnapshot);
        boolean p1Ok = preset1Snapshot != null
                && manager.restorePresetStorageBlocking(PresetModel.PRESET_1, preset1Snapshot);
        boolean p2Ok = preset2Snapshot != null
                && manager.restorePresetStorageBlocking(PresetModel.PRESET_2, preset2Snapshot);
        try {
            manager.restoreAfterBoot();
            BackgroundWorkPolicy.enforceCompatibleBehavior(context);
        } catch (Exception e) {
            AppDebugManager.e(Category.BACKUP_RESTORE,
                    "BackupManager: runtime reconciliation after rollback failed", e);
            return false;
        }
        return mainOk && p1Ok && p2Ok;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> deepCopyPreferenceMap(Map<String, ?> source) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Set) value = new HashSet<>((Set<String>) value);
            copy.put(entry.getKey(), value);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static boolean restorePreferenceMap(SharedPreferences target, Map<String, ?> values) {
        SharedPreferences.Editor editor = target.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Set) editor.putStringSet(key, new HashSet<>((Set<String>) value));
            else throw new IllegalArgumentException("Unsupported preference type for " + key);
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
