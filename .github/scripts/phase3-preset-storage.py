#!/usr/bin/env python3
from pathlib import Path
P = Path("app/src/main/java/com/gree1d/reappzuku/manager/PresetManager.java")
text = P.read_text()
start = text.find("    public void savePreset(PresetModel model) {")
end = text.find("    public PresetModel loadPreset(int presetNumber) {", start)
if start < 0 or end < 0:
    if "public boolean savePresetBlocking(PresetModel model)" in text:
        print("PresetManager phase 3 patch already applied")
        raise SystemExit(0)
    raise RuntimeError("PresetManager savePreset block not found")
new = '''    private SharedPreferences.Editor presetEditor(PresetModel model) {
        SharedPreferences.Editor e = presetPrefs(model.presetNumber).edit();
        e.putString(P_NAME, model.name);
        e.putBoolean(P_ENABLED, model.enabled);
        e.putBoolean(P_AUTO_KILL_ENABLED, model.autoKillEnabled);
        e.putBoolean(P_PERIODIC_KILL_ENABLED, model.periodicKillEnabled);
        e.putInt(P_KILL_INTERVAL, model.killInterval);
        e.putBoolean(P_KILL_ON_SCREEN_OFF, model.killOnScreenOff);
        e.putBoolean(P_RAM_THRESHOLD_ENABLED, model.ramThresholdEnabled);
        e.putInt(P_RAM_THRESHOLD, model.ramThreshold);
        e.putInt(P_AUTO_KILL_TYPE, model.autoKillType);
        e.putInt(P_KILL_MODE, model.killMode);
        e.putBoolean(P_HW_HEADSET, model.hwTriggerHeadset);
        e.putBoolean(P_HW_USB, model.hwTriggerUsb);
        e.putBoolean(P_HW_CHARGER, model.hwTriggerCharger);
        e.putBoolean(P_HW_WIFI, model.hwTriggerWifi);
        e.putBoolean(P_HW_BLUETOOTH, model.hwTriggerBluetooth);
        e.putBoolean(P_HW_GPS, model.hwTriggerGps);
        e.putBoolean(P_HW_HOTSPOT, model.hwTriggerHotspot);
        e.putBoolean(P_APP_LAUNCH_ENABLED, model.appLaunchTriggerEnabled);
        e.putBoolean(P_APP_LAUNCH_CLEAR_CACHE, model.appLaunchClearCache);
        e.putStringSet(P_APP_LAUNCH_PACKAGES, new HashSet<>(model.appLaunchTriggerPackages));
        e.putStringSet(P_WHITELIST, new HashSet<>(model.whitelistedApps));
        e.putStringSet(P_BLACKLIST, new HashSet<>(model.blacklistedApps));
        e.putInt(P_START_HOUR, model.startHour);
        e.putInt(P_START_MINUTE, model.startMinute);
        e.putInt(P_END_HOUR, model.endHour);
        e.putInt(P_END_MINUTE, model.endMinute);
        return e;
    }

    public void savePreset(PresetModel model) {
        presetEditor(model).apply();
        logPresetSaved(model, "async");
    }

    /** Storage-only synchronous write used by transactional backup restore. */
    public boolean savePresetBlocking(PresetModel model) {
        boolean committed = presetEditor(model).commit();
        if (committed) logPresetSaved(model, "commit");
        return committed;
    }

    /** Storage-only clear: deliberately does not touch alarms or active-preset state. */
    public boolean clearPresetStorageBlocking(int presetNumber) {
        return presetPrefs(presetNumber).edit().clear().commit();
    }

    public java.util.Map<String, ?> snapshotPresetStorage(int presetNumber) {
        return deepCopyPreferenceMap(presetPrefs(presetNumber).getAll());
    }

    public boolean restorePresetStorageBlocking(int presetNumber, java.util.Map<String, ?> snapshot) {
        SharedPreferences.Editor editor = presetPrefs(presetNumber).edit().clear();
        putPreferenceMap(editor, snapshot);
        return editor.commit();
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, ?> deepCopyPreferenceMap(java.util.Map<String, ?> source) {
        java.util.Map<String, Object> copy = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, ?> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Set) value = new HashSet<>((Set<String>) value);
            copy.put(entry.getKey(), value);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static void putPreferenceMap(SharedPreferences.Editor editor, java.util.Map<String, ?> values) {
        for (java.util.Map.Entry<String, ?> entry : values.entrySet()) {
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
    }

    private void logPresetSaved(PresetModel model, String mode) {
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: savePreset #" + model.presetNumber
                + " mode=" + mode + " name=" + model.name + " enabled=" + model.enabled
                + " start=" + model.startHour + ":" + String.format("%02d", model.startMinute)
                + " end=" + model.endHour + ":" + String.format("%02d", model.endMinute));
    }

'''
P.write_text(text[:start] + new + text[end:])
print("PresetManager phase 3 transaction helpers applied")
