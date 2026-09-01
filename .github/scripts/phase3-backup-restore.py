#!/usr/bin/env python3
from pathlib import Path
P = Path("app/src/main/java/com/gree1d/reappzuku/core/BackupManager.java")
text = P.read_text()
if "durableWriteStarted = false" in text:
    print("Backup restore phase 3 already applied")
    raise SystemExit(0)
if "MAX_BACKUP_CHARS" not in text:
    text = text.replace('    private static final String KEY_PRESET_PREFIX = "preset_";\n',
                        '    private static final String KEY_PRESET_PREFIX = "preset_";\n    private static final int MAX_BACKUP_CHARS = 2 * 1024 * 1024;\n', 1)
if "import java.util.Map;" not in text:
    text = text.replace('import java.util.HashSet;\nimport java.util.Set;\n',
                        'import java.util.HashSet;\nimport java.util.Map;\nimport java.util.HashMap;\nimport java.util.Set;\n', 1)
start = text.find("    public boolean restoreBackupJson(String json) {")
end = text.find("    private void restoreSet(SharedPreferences.Editor editor", start)
if start < 0 or end < 0:
    if "rollbackRestore(" in text:
        print("Backup restore phase 3 already applied")
        P.write_text(text)
        raise SystemExit(0)
    raise RuntimeError("restoreBackupJson block not found")
new = '''    public boolean restoreBackupJson(String json) {
        AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: start, json length="
                + (json != null ? json.length() : -1));
        if (json == null || json.length() == 0 || json.length() > MAX_BACKUP_CHARS) {
            AppDebugManager.w(Category.BACKUP_RESTORE,
                    "BackupManager: refusing empty/oversized backup payload");
            return false;
        }

        Map<String, ?> mainSnapshot = null;
        Map<String, ?> preset1Snapshot = null;
        Map<String, ?> preset2Snapshot = null;
        PresetManager presetManager = new PresetManager(context);
        boolean durableWriteStarted = false;
        try {
            JSONObject root = new JSONObject(json);
            int version = root.optInt(KEY_BACKUP_VERSION, -1);
            if (version > BACKUP_VERSION) {
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: refusing unsupported future backup version=" + version
                                + " supported=" + BACKUP_VERSION);
                return false;
            }
            if (version < 1) {
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

            if (containsPresetSection) {
                if (!writePresetStorage(presetManager, PresetModel.PRESET_1, restoredPresets[0]))
                    throw new IllegalStateException("preset 1 commit failed");
                if (!writePresetStorage(presetManager, PresetModel.PRESET_2, restoredPresets[1]))
                    throw new IllegalStateException("preset 2 commit failed");
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

'''
P.write_text(text[:start] + new + text[end:])
print("Backup restore transaction body applied")