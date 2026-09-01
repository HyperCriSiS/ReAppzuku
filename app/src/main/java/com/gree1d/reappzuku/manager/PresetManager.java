package com.gree1d.reappzuku.manager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.ExactAlarmCapability;
import com.gree1d.reappzuku.manager.AdditionalScenariosManager;
import com.gree1d.reappzuku.service.AutoKillWorker;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import com.gree1d.reappzuku.utils.PresetModel;
import com.gree1d.reappzuku.service.ShappkyService;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;

public class PresetManager {

    private static final String TAG = "PresetManager";

    private static final String PREFS_PRESET_1 = "preset_1_prefs";
    private static final String PREFS_PRESET_2 = "preset_2_prefs";

    static final String ACTION_PRESET_ACTIVATE = "com.gree1d.reappzuku.PRESET_ACTIVATE";
    static final String ACTION_PRESET_DEACTIVATE = "com.gree1d.reappzuku.PRESET_DEACTIVATE";
    static final String EXTRA_PRESET_NUMBER = "preset_number";

    public static final String KEY_BACKUP_PREFIX = "preset_backup_";

    private static final String P_NAME = "name";
    private static final String P_ENABLED = "enabled";
    private static final String P_AUTO_KILL_ENABLED = "autoKillEnabled";
    private static final String P_PERIODIC_KILL_ENABLED = "periodicKillEnabled";
    private static final String P_KILL_INTERVAL = "killInterval";
    private static final String P_KILL_ON_SCREEN_OFF = "killOnScreenOff";
    private static final String P_RAM_THRESHOLD_ENABLED = "ramThresholdEnabled";
    private static final String P_RAM_THRESHOLD = "ramThreshold";
    private static final String P_AUTO_KILL_TYPE = "autoKillType";
    private static final String P_KILL_MODE = "killMode";
    private static final String P_HW_HEADSET = "hwTriggerHeadset";
    private static final String P_HW_USB = "hwTriggerUsb";
    private static final String P_HW_CHARGER = "hwTriggerCharger";
    private static final String P_HW_WIFI = "hwTriggerWifi";
    private static final String P_HW_BLUETOOTH = "hwTriggerBluetooth";
    private static final String P_HW_GPS = "hwTriggerGps";
    private static final String P_HW_HOTSPOT = "hwTriggerHotspot";
    private static final String P_APP_LAUNCH_ENABLED = "appLaunchTriggerEnabled";
    private static final String P_APP_LAUNCH_CLEAR_CACHE = "appLaunchClearCache";
    private static final String P_APP_LAUNCH_PACKAGES = "appLaunchTriggerPackages";
    private static final String P_WHITELIST = "whitelistedApps";
    private static final String P_BLACKLIST = "blacklistedApps";
    private static final String P_START_HOUR = "startHour";
    private static final String P_START_MINUTE = "startMinute";
    private static final String P_END_HOUR = "endHour";
    private static final String P_END_MINUTE = "endMinute";

    private static final int REQUEST_CODE_ACTIVATE_1 = 1001;
    private static final int REQUEST_CODE_DEACTIVATE_1 = 1002;
    private static final int REQUEST_CODE_ACTIVATE_2 = 1003;
    private static final int REQUEST_CODE_DEACTIVATE_2 = 1004;

    private final Context context;
    private final SharedPreferences mainPrefs;

    public PresetManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainPrefs = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private SharedPreferences presetPrefs(int presetNumber) {
        String name = presetNumber == PresetModel.PRESET_1 ? PREFS_PRESET_1 : PREFS_PRESET_2;
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private SharedPreferences.Editor presetEditor(PresetModel model) {
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

    public PresetModel loadPreset(int presetNumber) {
        SharedPreferences p = presetPrefs(presetNumber);
        if (!p.contains(P_NAME)) {
            AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: loadPreset #" + presetNumber + " — not found");
            return null;
        }
        PresetModel model = new PresetModel(presetNumber);
        model.name = p.getString(P_NAME, "Preset " + presetNumber);
        model.enabled = p.getBoolean(P_ENABLED, true);
        model.autoKillEnabled = p.getBoolean(P_AUTO_KILL_ENABLED, false);
        model.periodicKillEnabled = p.getBoolean(P_PERIODIC_KILL_ENABLED, false);
        model.killInterval = p.getInt(P_KILL_INTERVAL, 15);
        model.killOnScreenOff = p.getBoolean(P_KILL_ON_SCREEN_OFF, false);
        model.ramThresholdEnabled = p.getBoolean(P_RAM_THRESHOLD_ENABLED, false);
        model.ramThreshold = p.getInt(P_RAM_THRESHOLD, 80);
        model.autoKillType = p.getInt(P_AUTO_KILL_TYPE, 0);
        model.killMode = p.getInt(P_KILL_MODE, 1);
        model.hwTriggerHeadset = p.getBoolean(P_HW_HEADSET, false);
        model.hwTriggerUsb = p.getBoolean(P_HW_USB, false);
        model.hwTriggerCharger = p.getBoolean(P_HW_CHARGER, false);
        model.hwTriggerWifi = p.getBoolean(P_HW_WIFI, false);
        model.hwTriggerBluetooth = p.getBoolean(P_HW_BLUETOOTH, false);
        model.hwTriggerGps = p.getBoolean(P_HW_GPS, false);
        model.hwTriggerHotspot = p.getBoolean(P_HW_HOTSPOT, false);
        model.appLaunchTriggerEnabled = p.getBoolean(P_APP_LAUNCH_ENABLED, false);
        model.appLaunchClearCache = p.getBoolean(P_APP_LAUNCH_CLEAR_CACHE, false);
        model.appLaunchTriggerPackages = new HashSet<>(p.getStringSet(P_APP_LAUNCH_PACKAGES, new HashSet<>()));
        model.whitelistedApps = new HashSet<>(p.getStringSet(P_WHITELIST, new HashSet<>()));
        model.blacklistedApps = new HashSet<>(p.getStringSet(P_BLACKLIST, new HashSet<>()));
        model.startHour = p.getInt(P_START_HOUR, 8);
        model.startMinute = p.getInt(P_START_MINUTE, 0);
        model.endHour = p.getInt(P_END_HOUR, 20);
        model.endMinute = p.getInt(P_END_MINUTE, 0);
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: loadPreset #" + presetNumber + " OK | name=" + model.name
                + " enabled=" + model.enabled
                + " start=" + model.startHour + ":" + String.format("%02d", model.startMinute)
                + " end=" + model.endHour + ":" + String.format("%02d", model.endMinute)
                + " whitelist=" + model.whitelistedApps.size()
                + " blacklist=" + model.blacklistedApps.size());
        return model;
    }

    public void deletePreset(int presetNumber) {
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: deletePreset #" + presetNumber + " wasActive=" + (getActivePresetNumber() == presetNumber));
        presetPrefs(presetNumber).edit().clear().apply();
        cancelAlarms(presetNumber);
        if (getActivePresetNumber() == presetNumber) {
            restoreBackup();
            clearActivePreset();
        }
    }

    public boolean presetExists(int presetNumber) {
        return presetPrefs(presetNumber).contains(P_NAME);
    }

    public String getPresetName(int presetNumber) {
        return presetPrefs(presetNumber).getString(P_NAME, "Preset " + presetNumber);
    }

    public int getActivePresetNumber() {
        return mainPrefs.getInt(KEY_ACTIVE_PRESET, 0);
    }

    private void setActivePresetNumber(int number) {
        mainPrefs.edit().putInt(KEY_ACTIVE_PRESET, number).apply();
    }

    private void clearActivePreset() {
        mainPrefs.edit().remove(KEY_ACTIVE_PRESET).apply();
    }

    public void activatePreset(int presetNumber) {
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: activatePreset #" + presetNumber + " | currentActive=" + getActivePresetNumber());
        PresetModel model = loadPreset(presetNumber);
        if (model == null) {
            AppDebugManager.w(Category.AUTO_KILL_PRESETS, "PresetManager: activatePreset #" + presetNumber + " ABORTED — not found");
            return;
        }
        if (!model.enabled) {
            AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: activatePreset #" + presetNumber + " SKIPPED — preset is disabled");
            return;
        }

        int currentActive = getActivePresetNumber();
        if (currentActive == 0) {
            AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: activatePreset: no preset active — saving backup of current settings");
            saveBackup();
        } else if (currentActive != presetNumber) {
            AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: activatePreset: switching from preset #" + currentActive + " — original backup preserved");
        }

        applyPreset(model);
        setActivePresetNumber(presetNumber);
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: activatePreset #" + presetNumber + " DONE");
    }

    public void deactivatePreset(int presetNumber) {
        int currentActive = getActivePresetNumber();
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: deactivatePreset #" + presetNumber + " | currentActive=" + currentActive);
        if (currentActive != presetNumber) {
            AppDebugManager.w(Category.AUTO_KILL_PRESETS, "PresetManager: deactivatePreset #" + presetNumber + " SKIPPED — not the active preset");
            return;
        }
        PresetModel model = loadPreset(presetNumber);
        if (model != null && isCurrentlyActive(model)) {
            AppDebugManager.w(Category.AUTO_KILL_PRESETS, "PresetManager: deactivatePreset #" + presetNumber + " SKIPPED — still inside time window (alarm drift)");
            return;
        }
        restoreBackup();
        clearActivePreset();
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: deactivatePreset #" + presetNumber + " DONE");
    }

    public void forceDeactivateIfActive(int presetNumber) {
        if (getActivePresetNumber() == presetNumber) {
            restoreBackup();
            clearActivePreset();
            AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: forceDeactivateIfActive #" + presetNumber + " DONE");
        }
    }

    private void applyPreset(PresetModel model) {
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: applyPreset #" + model.presetNumber
                + " | autoKill=" + model.autoKillEnabled
                + " periodic=" + model.periodicKillEnabled
                + " interval=" + model.killInterval
                + " screenOff=" + model.killOnScreenOff
                + " killMode=" + model.killMode + " killType=" + model.autoKillType
                + " ramEnabled=" + model.ramThresholdEnabled + " ramThreshold=" + model.ramThreshold
                + " headset=" + model.hwTriggerHeadset + " usb=" + model.hwTriggerUsb
                + " charger=" + model.hwTriggerCharger + " wifi=" + model.hwTriggerWifi
                + " bt=" + model.hwTriggerBluetooth + " gps=" + model.hwTriggerGps
                + " hotspot=" + model.hwTriggerHotspot
                + " appLaunch=" + model.appLaunchTriggerEnabled
                + " whitelist=" + model.whitelistedApps + " blacklist=" + model.blacklistedApps);

        SharedPreferences.Editor editor = mainPrefs.edit();
        editor.putBoolean(KEY_AUTO_KILL_ENABLED, model.autoKillEnabled);
        editor.putBoolean(KEY_PERIODIC_KILL_ENABLED, model.periodicKillEnabled);
        editor.putInt(KEY_KILL_INTERVAL, model.killInterval);
        editor.putBoolean(KEY_KILL_ON_SCREEN_OFF, model.killOnScreenOff);
        editor.putBoolean(KEY_RAM_THRESHOLD_ENABLED, model.ramThresholdEnabled);
        editor.putInt(KEY_RAM_THRESHOLD, model.ramThreshold);
        editor.putInt(KEY_AUTO_KILL_TYPE, model.autoKillType);
        editor.putInt(KEY_KILL_MODE, model.killMode);
        editor.putBoolean(KEY_HW_TRIGGER_HEADSET, model.hwTriggerHeadset);
        editor.putBoolean(KEY_HW_TRIGGER_USB, model.hwTriggerUsb);
        editor.putBoolean(KEY_HW_TRIGGER_CHARGER, model.hwTriggerCharger);
        editor.putBoolean(KEY_HW_TRIGGER_WIFI, model.hwTriggerWifi);
        editor.putBoolean(KEY_HW_TRIGGER_BLUETOOTH, model.hwTriggerBluetooth);
        editor.putBoolean(KEY_HW_TRIGGER_GPS, model.hwTriggerGps);
        editor.putBoolean(KEY_HW_TRIGGER_HOTSPOT, model.hwTriggerHotspot);
        editor.putBoolean(KEY_APP_LAUNCH_TRIGGER_ENABLED, model.appLaunchTriggerEnabled);
        editor.putBoolean(KEY_APP_LAUNCH_CLEAR_CACHE, model.appLaunchClearCache);
        editor.putStringSet(KEY_APP_LAUNCH_TRIGGER_PACKAGES, new HashSet<>(model.appLaunchTriggerPackages));
        editor.putStringSet(KEY_WHITELISTED_APPS, new HashSet<>(model.whitelistedApps));
        editor.putStringSet(KEY_BLACKLISTED_APPS, new HashSet<>(model.blacklistedApps));
        editor.apply();

        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: applyPreset #" + model.presetNumber + " prefs written — rescheduling worker");
        notifyServiceUpdateHwReceivers();
        rescheduleWorker();
    }

    private void saveBackup() {
        SharedPreferences.Editor e = mainPrefs.edit();
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_ENABLED, mainPrefs.getBoolean(KEY_AUTO_KILL_ENABLED, false));
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_PERIODIC_KILL_ENABLED, mainPrefs.getBoolean(KEY_PERIODIC_KILL_ENABLED, false));
        e.putInt(KEY_BACKUP_PREFIX + KEY_KILL_INTERVAL, mainPrefs.getInt(KEY_KILL_INTERVAL, 15));
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_KILL_ON_SCREEN_OFF, mainPrefs.getBoolean(KEY_KILL_ON_SCREEN_OFF, false));
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_RAM_THRESHOLD_ENABLED, mainPrefs.getBoolean(KEY_RAM_THRESHOLD_ENABLED, false));
        e.putInt(KEY_BACKUP_PREFIX + KEY_RAM_THRESHOLD, mainPrefs.getInt(KEY_RAM_THRESHOLD, 80));
        e.putInt(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_TYPE, mainPrefs.getInt(KEY_AUTO_KILL_TYPE, 0));
        e.putInt(KEY_BACKUP_PREFIX + KEY_KILL_MODE, mainPrefs.getInt(KEY_KILL_MODE, 1));
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_HEADSET, mainPrefs.getBoolean(KEY_HW_TRIGGER_HEADSET, false));
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_USB, mainPrefs.getBoolean(KEY_HW_TRIGGER_USB, false));
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_CHARGER, mainPrefs.getBoolean(KEY_HW_TRIGGER_CHARGER, false));
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_WIFI, mainPrefs.getBoolean(KEY_HW_TRIGGER_WIFI, false));
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_BLUETOOTH, mainPrefs.getBoolean(KEY_HW_TRIGGER_BLUETOOTH, false));
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_GPS, mainPrefs.getBoolean(KEY_HW_TRIGGER_GPS, false));
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_HOTSPOT, mainPrefs.getBoolean(KEY_HW_TRIGGER_HOTSPOT, false));
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_APP_LAUNCH_TRIGGER_ENABLED, mainPrefs.getBoolean(KEY_APP_LAUNCH_TRIGGER_ENABLED, false));
        e.putBoolean(KEY_BACKUP_PREFIX + KEY_APP_LAUNCH_CLEAR_CACHE, mainPrefs.getBoolean(KEY_APP_LAUNCH_CLEAR_CACHE, false));
        Set<String> launchPkgs = mainPrefs.getStringSet(KEY_APP_LAUNCH_TRIGGER_PACKAGES, new HashSet<>());
        e.putStringSet(KEY_BACKUP_PREFIX + KEY_APP_LAUNCH_TRIGGER_PACKAGES, new HashSet<>(launchPkgs));
        Set<String> whitelist = mainPrefs.getStringSet(KEY_WHITELISTED_APPS, new HashSet<>());
        e.putStringSet(KEY_BACKUP_PREFIX + KEY_WHITELISTED_APPS, new HashSet<>(whitelist));
        Set<String> blacklist = mainPrefs.getStringSet(KEY_BLACKLISTED_APPS, new HashSet<>());
        e.putStringSet(KEY_BACKUP_PREFIX + KEY_BLACKLISTED_APPS, new HashSet<>(blacklist));
        e.apply();
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: saveBackup DONE");
    }

    private void restoreBackup() {
        boolean hasBackup = mainPrefs.contains(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_ENABLED);
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: restoreBackup | hasBackup=" + hasBackup);
        if (!hasBackup) return;

        SharedPreferences.Editor e = mainPrefs.edit();
        e.putBoolean(KEY_AUTO_KILL_ENABLED, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_ENABLED, false));
        e.putBoolean(KEY_PERIODIC_KILL_ENABLED, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_PERIODIC_KILL_ENABLED, false));
        e.putInt(KEY_KILL_INTERVAL, mainPrefs.getInt(KEY_BACKUP_PREFIX + KEY_KILL_INTERVAL, 15));
        e.putBoolean(KEY_KILL_ON_SCREEN_OFF, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_KILL_ON_SCREEN_OFF, false));
        e.putBoolean(KEY_RAM_THRESHOLD_ENABLED, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_RAM_THRESHOLD_ENABLED, false));
        e.putInt(KEY_RAM_THRESHOLD, mainPrefs.getInt(KEY_BACKUP_PREFIX + KEY_RAM_THRESHOLD, 80));
        e.putInt(KEY_AUTO_KILL_TYPE, mainPrefs.getInt(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_TYPE, 0));
        e.putInt(KEY_KILL_MODE, mainPrefs.getInt(KEY_BACKUP_PREFIX + KEY_KILL_MODE, 1));
        e.putBoolean(KEY_HW_TRIGGER_HEADSET, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_HEADSET, false));
        e.putBoolean(KEY_HW_TRIGGER_USB, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_USB, false));
        e.putBoolean(KEY_HW_TRIGGER_CHARGER, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_CHARGER, false));
        e.putBoolean(KEY_HW_TRIGGER_WIFI, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_WIFI, false));
        e.putBoolean(KEY_HW_TRIGGER_BLUETOOTH, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_BLUETOOTH, false));
        e.putBoolean(KEY_HW_TRIGGER_GPS, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_GPS, false));
        e.putBoolean(KEY_HW_TRIGGER_HOTSPOT, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_HOTSPOT, false));
        e.putBoolean(KEY_APP_LAUNCH_TRIGGER_ENABLED, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_APP_LAUNCH_TRIGGER_ENABLED, false));
        e.putBoolean(KEY_APP_LAUNCH_CLEAR_CACHE, mainPrefs.getBoolean(KEY_BACKUP_PREFIX + KEY_APP_LAUNCH_CLEAR_CACHE, false));
        Set<String> launchPkgs = mainPrefs.getStringSet(KEY_BACKUP_PREFIX + KEY_APP_LAUNCH_TRIGGER_PACKAGES, new HashSet<>());
        e.putStringSet(KEY_APP_LAUNCH_TRIGGER_PACKAGES, new HashSet<>(launchPkgs));
        Set<String> whitelist = mainPrefs.getStringSet(KEY_BACKUP_PREFIX + KEY_WHITELISTED_APPS, new HashSet<>());
        e.putStringSet(KEY_WHITELISTED_APPS, new HashSet<>(whitelist));
        Set<String> blacklist = mainPrefs.getStringSet(KEY_BACKUP_PREFIX + KEY_BLACKLISTED_APPS, new HashSet<>());
        e.putStringSet(KEY_BLACKLISTED_APPS, new HashSet<>(blacklist));

        e.remove(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_ENABLED);
        e.remove(KEY_BACKUP_PREFIX + KEY_PERIODIC_KILL_ENABLED);
        e.remove(KEY_BACKUP_PREFIX + KEY_KILL_INTERVAL);
        e.remove(KEY_BACKUP_PREFIX + KEY_KILL_ON_SCREEN_OFF);
        e.remove(KEY_BACKUP_PREFIX + KEY_RAM_THRESHOLD_ENABLED);
        e.remove(KEY_BACKUP_PREFIX + KEY_RAM_THRESHOLD);
        e.remove(KEY_BACKUP_PREFIX + KEY_AUTO_KILL_TYPE);
        e.remove(KEY_BACKUP_PREFIX + KEY_KILL_MODE);
        e.remove(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_HEADSET);
        e.remove(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_USB);
        e.remove(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_CHARGER);
        e.remove(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_WIFI);
        e.remove(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_BLUETOOTH);
        e.remove(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_GPS);
        e.remove(KEY_BACKUP_PREFIX + KEY_HW_TRIGGER_HOTSPOT);
        e.remove(KEY_BACKUP_PREFIX + KEY_APP_LAUNCH_TRIGGER_ENABLED);
        e.remove(KEY_BACKUP_PREFIX + KEY_APP_LAUNCH_CLEAR_CACHE);
        e.remove(KEY_BACKUP_PREFIX + KEY_APP_LAUNCH_TRIGGER_PACKAGES);
        e.remove(KEY_BACKUP_PREFIX + KEY_WHITELISTED_APPS);
        e.remove(KEY_BACKUP_PREFIX + KEY_BLACKLISTED_APPS);
        e.apply();

        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: restoreBackup DONE — rescheduling worker");
        notifyServiceUpdateHwReceivers();
        rescheduleWorker();
    }

    private void notifyServiceUpdateHwReceivers() {
        if (ShappkyService.isRunning()) {
            Intent intent = new Intent(context, ShappkyService.class);
            intent.setAction("UPDATE_HW_RECEIVERS");
            context.startService(intent);
        }
    }

    private void rescheduleWorker() {
        boolean autoKillEnabled = mainPrefs.getBoolean(KEY_AUTO_KILL_ENABLED, false);
        boolean periodicEnabled = mainPrefs.getBoolean(KEY_PERIODIC_KILL_ENABLED, false);
        int interval = mainPrefs.getInt(KEY_KILL_INTERVAL, 15);
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: rescheduleWorker | autoKill=" + autoKillEnabled
                + " periodic=" + periodicEnabled + " interval=" + interval);
        boolean presetActive = mainPrefs.getInt(KEY_ACTIVE_PRESET, 0) != 0;
        AutoKillWorker.cancel(context);
        if ((autoKillEnabled || presetActive) && periodicEnabled) {
            int activePresetNumber = mainPrefs.getInt(KEY_ACTIVE_PRESET, 0);
            String source;
            if (activePresetNumber != 0) {
                String presetName = getPresetName(activePresetNumber);
                source = "Periodic Kill · " + presetName;
            } else {
                source = "Periodic Kill";
            }
            AutoKillWorker.schedule(context, source);
            AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: rescheduleWorker — scheduled with interval=" + interval + " source=" + source);
        } else {
            AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: rescheduleWorker — worker cancelled");
        }
    }

    public void exportPresetToJson(PresetModel model, Uri uri) {
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: exportPresetToJson #" + model.presetNumber + " uri=" + uri);
        try {
            JSONObject json = model.toJson();
            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IOException("OutputStream is null for uri: " + uri);
                os.write(json.toString(2).getBytes("UTF-8"));
                AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: exportPresetToJson #" + model.presetNumber + " OK");
            }
        } catch (IOException | JSONException e) {
            AppDebugManager.e(Category.AUTO_KILL_PRESETS, "PresetManager: exportPresetToJson #" + model.presetNumber + " FAILED", e);
        }
    }

    public PresetModel importPresetFromJson(int presetNumber, Uri uri) {
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: importPresetFromJson #" + presetNumber + " uri=" + uri);
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException("InputStream is null for uri: " + uri);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            JSONObject json = new JSONObject(buffer.toString("UTF-8"));
            PresetModel model = PresetModel.fromJson(presetNumber, json);
            AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: importPresetFromJson #" + presetNumber + " OK | name=" + model.name
                    + " enabled=" + model.enabled
                    + " start=" + model.startHour + ":" + String.format("%02d", model.startMinute)
                    + " end=" + model.endHour + ":" + String.format("%02d", model.endMinute)
                    + " whitelist=" + model.whitelistedApps.size()
                    + " blacklist=" + model.blacklistedApps.size());
            return model;
        } catch (IOException | JSONException e) {
            AppDebugManager.e(Category.AUTO_KILL_PRESETS, "PresetManager: importPresetFromJson #" + presetNumber + " FAILED", e);
            return null;
        }
    }


    public void scheduleAlarms(PresetModel model) {
        cancelAlarms(model.presetNumber);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            AppDebugManager.e(Category.AUTO_KILL_PRESETS, "PresetManager: scheduleAlarms #" + model.presetNumber + " — AlarmManager is null");
            return;
        }
        long activateTime = nextAlarmTime(model.startHour, model.startMinute);
        long deactivateTime = nextAlarmTime(model.endHour, model.endMinute);
        boolean activateExact = ExactAlarmCapability.scheduleExactOrBestEffort(
                context, alarmManager, AlarmManager.RTC_WAKEUP, activateTime,
                buildPendingIntent(model.presetNumber, ACTION_PRESET_ACTIVATE), true);
        boolean deactivateExact = ExactAlarmCapability.scheduleExactOrBestEffort(
                context, alarmManager, AlarmManager.RTC_WAKEUP, deactivateTime,
                buildPendingIntent(model.presetNumber, ACTION_PRESET_DEACTIVATE), true);
        if (!activateExact || !deactivateExact) {
            AppDebugManager.w(Category.AUTO_KILL_PRESETS,
                    "PresetManager: exact alarm permission unavailable; using best-effort timing for preset #" + model.presetNumber);
        }
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: scheduleAlarms #" + model.presetNumber
                + " | activateAt=" + model.startHour + ":" + String.format("%02d", model.startMinute)
                + " (ms=" + activateTime + ")"
                + " deactivateAt=" + model.endHour + ":" + String.format("%02d", model.endMinute)
                + " (ms=" + deactivateTime + ")");
    }

    public void rescheduleNextAlarm(int presetNumber, String action) {
        PresetModel model = loadPreset(presetNumber);
        if (model == null) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        boolean isActivate = ACTION_PRESET_ACTIVATE.equals(action);
        int hour = isActivate ? model.startHour : model.endHour;
        int minute = isActivate ? model.startMinute : model.endMinute;
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        next.add(Calendar.DAY_OF_YEAR, 1);
        boolean exact = ExactAlarmCapability.scheduleExactOrBestEffort(
                context, alarmManager, AlarmManager.RTC_WAKEUP, next.getTimeInMillis(),
                buildPendingIntent(presetNumber, action), true);
        if (!exact) {
            AppDebugManager.w(Category.AUTO_KILL_PRESETS,
                    "PresetManager: rescheduleNextAlarm using best-effort timing for preset #" + presetNumber + " action=" + action);
        }
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: rescheduleNextAlarm #" + presetNumber + " action=" + action
                + " nextAt=" + hour + ":" + String.format("%02d", minute)
                + " tomorrow ms=" + next.getTimeInMillis());
    }

    public void cancelAlarms(int presetNumber) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            AppDebugManager.e(Category.AUTO_KILL_PRESETS, "PresetManager: cancelAlarms #" + presetNumber + " — AlarmManager is null");
            return;
        }
        alarmManager.cancel(buildPendingIntent(presetNumber, ACTION_PRESET_ACTIVATE));
        alarmManager.cancel(buildPendingIntent(presetNumber, ACTION_PRESET_DEACTIVATE));
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: cancelAlarms #" + presetNumber + " DONE");
    }

    private PendingIntent buildPendingIntent(int presetNumber, String action) {
        Intent intent = new Intent(context, PresetReceiver.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_PRESET_NUMBER, presetNumber);
        int requestCode;
        if (presetNumber == PresetModel.PRESET_1) {
            requestCode = action.equals(ACTION_PRESET_ACTIVATE) ? REQUEST_CODE_ACTIVATE_1 : REQUEST_CODE_DEACTIVATE_1;
        } else {
            requestCode = action.equals(ACTION_PRESET_ACTIVATE) ? REQUEST_CODE_ACTIVATE_2 : REQUEST_CODE_DEACTIVATE_2;
        }
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private long nextAlarmTime(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return calendar.getTimeInMillis();
    }

    public boolean isCurrentlyActive(PresetModel model) {
        Calendar now = Calendar.getInstance();
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int startMinutes = model.getStartTotalMinutes();
        int endMinutes = model.getEndTotalMinutes();
        boolean active;
        if (endMinutes <= startMinutes) {
            active = nowMinutes >= startMinutes || nowMinutes < endMinutes;
        } else {
            active = nowMinutes >= startMinutes && nowMinutes < endMinutes;
        }
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: isCurrentlyActive #" + model.presetNumber
                + " | now=" + now.get(Calendar.HOUR_OF_DAY) + ":" + String.format("%02d", now.get(Calendar.MINUTE))
                + " range=" + model.startHour + ":" + String.format("%02d", model.startMinute)
                + "–" + model.endHour + ":" + String.format("%02d", model.endMinute)
                + " crossesMidnight=" + (endMinutes <= startMinutes)
                + " → active=" + active);
        return active;
    }

    public void restoreAfterBoot() {
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: restoreAfterBoot: rebuilding preset alarms");
        for (int number : new int[]{PresetModel.PRESET_1, PresetModel.PRESET_2}) {
            PresetModel model = loadPreset(number);
            if (model != null && model.enabled) scheduleAlarms(model);
            else cancelAlarms(number);
        }
        checkAndApplyCurrentPreset();
    }

    public void checkAndApplyCurrentPreset() {
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: checkAndApplyCurrentPreset | currentActive=" + getActivePresetNumber());
        for (int number : new int[]{PresetModel.PRESET_1, PresetModel.PRESET_2}) {
            PresetModel model = loadPreset(number);
            if (model == null) {
                AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: checkAndApplyCurrentPreset | preset #" + number + " not found, skipping");
                continue;
            }
            if (!model.enabled) {
                AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: checkAndApplyCurrentPreset | preset #" + number + " disabled, skipping");
                continue;
            }
            if (isCurrentlyActive(model)) {
                AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: checkAndApplyCurrentPreset | preset #" + number + " in window — activating");
                activatePreset(number);
                return;
            }
        }
        int currentActive = getActivePresetNumber();
        if (currentActive != 0) {
            PresetModel active = loadPreset(currentActive);
            if (active == null || !active.enabled || !isCurrentlyActive(active)) {
                AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: checkAndApplyCurrentPreset | preset #" + currentActive + " outside window — deactivating");
                deactivatePreset(currentActive);
            }
        } else {
            AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: checkAndApplyCurrentPreset | no preset active and none in window");
        }
    }

    public static class PresetReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                AppDebugManager.w(Category.AUTO_KILL_PRESETS, "PresetManager: PresetReceiver: null intent or action");
                return;
            }
            int presetNumber = intent.getIntExtra(EXTRA_PRESET_NUMBER, 0);
            AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: PresetReceiver | action=" + intent.getAction() + " preset=" + presetNumber);
            if (presetNumber == 0) {
                AppDebugManager.w(Category.AUTO_KILL_PRESETS, "PresetManager: PresetReceiver: missing preset_number extra");
                return;
            }
            PresetManager manager = new PresetManager(context);
            String action = intent.getAction();
            if (ACTION_PRESET_ACTIVATE.equals(action)) {
                manager.activatePreset(presetNumber);
            } else if (ACTION_PRESET_DEACTIVATE.equals(action)) {
                manager.deactivatePreset(presetNumber);
            }
            manager.rescheduleNextAlarm(presetNumber, action);
        }
    }
}
