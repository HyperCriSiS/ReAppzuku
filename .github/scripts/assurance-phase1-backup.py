#!/usr/bin/env python3
from pathlib import Path

p = Path("app/src/main/java/com/gree1d/reappzuku/core/BackupManager.java")
s = p.read_text(encoding="utf-8")

def once(old, new):
    global s
    if new in s:
        return
    if old not in s:
        raise RuntimeError(f"BackupManager anchor not found: {old[:100]!r}")
    s = s.replace(old, new, 1)

s = s.replace("private static final int BACKUP_VERSION = 4;", "private static final int BACKUP_VERSION = 5;")

once('''            root.put(KEY_SLEEP_MODE_ENABLED, getSafeBool(KEY_SLEEP_MODE_ENABLED, false));
            root.put(KEY_SLEEP_MODE_DELAY, prefs.getLong(KEY_SLEEP_MODE_DELAY, AppConstants.DEFAULT_SLEEP_MODE_DELAY_MS));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: sleep mode settings written");

''', '''            root.put(KEY_SLEEP_MODE_ENABLED, getSafeBool(KEY_SLEEP_MODE_ENABLED, false));
            root.put(KEY_SLEEP_MODE_DELAY, prefs.getLong(KEY_SLEEP_MODE_DELAY, AppConstants.DEFAULT_SLEEP_MODE_DELAY_MS));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: sleep mode settings written");

            root.put(KEY_EXIT_ON_BACK, getSafeBool(KEY_EXIT_ON_BACK, false));
            root.put(KEY_PREVENT_SHIZUKU_AUTOSTART, getSafeBool(KEY_PREVENT_SHIZUKU_AUTOSTART, true));
            root.put(KEY_SMART_LIFECYCLE_ENABLED, getSafeBool(KEY_SMART_LIFECYCLE_ENABLED, false));
            root.put(KEY_SMART_BOOT_CLEANUP_ENABLED, getSafeBool(KEY_SMART_BOOT_CLEANUP_ENABLED, true));
            root.put(KEY_SMART_LIFECYCLE_PROFILE, prefs.getInt(KEY_SMART_LIFECYCLE_PROFILE, 1));
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: createBackupJson: fork behavior settings written");

''')

once('''            int version = root.optInt(KEY_BACKUP_VERSION, -1);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: backup version=" + version);

            SharedPreferences.Editor editor = prefs.edit();
''', '''            int version = root.optInt(KEY_BACKUP_VERSION, -1);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: backup version=" + version);
            if (version > BACKUP_VERSION) {
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: refusing unsupported future backup version=" + version + " supported=" + BACKUP_VERSION);
                return false;
            }
            if (version < 1) {
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: legacy/unversioned backup detected; validating available fields");
            }

            validatePresets(root);
            SharedPreferences.Editor editor = prefs.edit();
''')

once('''            restoreBoolean(editor, root, KEY_SLEEP_MODE_ENABLED);
            restoreLong(editor, root, KEY_SLEEP_MODE_DELAY);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: sleep mode settings restored");

''', '''            restoreBoolean(editor, root, KEY_SLEEP_MODE_ENABLED);
            restoreLong(editor, root, KEY_SLEEP_MODE_DELAY);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: sleep mode settings restored");

            restoreBoolean(editor, root, KEY_EXIT_ON_BACK);
            restoreBoolean(editor, root, KEY_PREVENT_SHIZUKU_AUTOSTART);
            restoreBoolean(editor, root, KEY_SMART_LIFECYCLE_ENABLED);
            restoreBoolean(editor, root, KEY_SMART_BOOT_CLEANUP_ENABLED);
            restoreInt(editor, root, KEY_SMART_LIFECYCLE_PROFILE);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: fork behavior settings restored");

''')

once('''            editor.apply();

            restorePresets(root);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: presets restored");

            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: success");
            return true;
''', '''            if (!editor.commit()) {
                AppDebugManager.e(Category.BACKUP_RESTORE,
                        "BackupManager: restoreBackupJson: main preferences commit failed");
                return false;
            }

            restorePresets(root);
            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: presets restored");
            BackgroundWorkPolicy.enforceCompatibleBehavior(context);

            AppDebugManager.d(Category.BACKUP_RESTORE, "BackupManager: restoreBackupJson: success");
            return true;
''')

once('''    private void restorePresets(JSONObject root) throws Exception {
''', '''    private void validatePresets(JSONObject root) throws Exception {
        if (!root.has(KEY_PRESETS)) return;
        JSONObject presets = root.getJSONObject(KEY_PRESETS);
        for (int presetNumber : new int[]{ PresetModel.PRESET_1, PresetModel.PRESET_2 }) {
            String key = KEY_PRESET_PREFIX + presetNumber;
            if (presets.has(key)) PresetModel.fromJson(presetNumber, presets.getJSONObject(key));
        }
    }

    private void restorePresets(JSONObject root) throws Exception {
''')

p.write_text(s, encoding="utf-8")
print("assurance-phase1-backup applied")
