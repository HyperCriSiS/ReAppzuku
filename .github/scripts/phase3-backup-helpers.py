#!/usr/bin/env python3
from pathlib import Path
P = Path("app/src/main/java/com/gree1d/reappzuku/core/BackupManager.java")
text = P.read_text()
start = text.find("    private void validatePresets(JSONObject root) throws Exception {")
end = text.find("    private void restoreBoolean(SharedPreferences.Editor editor", start)
if start < 0 or end < 0:
    if "private boolean rollbackRestore(" in text:
        print("Backup rollback helpers already applied")
        raise SystemExit(0)
    raise RuntimeError("backup preset helper block not found")
new = '''    private PresetModel[] parsePresets(JSONObject root) throws Exception {
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

'''
P.write_text(text[:start] + new + text[end:])
print("Backup rollback helpers applied")
