package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.net.Uri;

import com.gree1d.reappzuku.utils.PresetModel;

/**
 * Schedule-named facade for the two time-window Auto-Kill Automation Schedules.
 *
 * <p>The inherited implementation deliberately retains historical preset-named persistence,
 * broadcasts and backup keys. This keeps existing installs compatible while preventing new UI
 * code from conflating time schedules with Room-backed per-app PolicyPreset templates.</p>
 */
public final class AutomationScheduleManager extends PresetManager {
    public static final String LEGACY_BACKUP_PREFIX = PresetManager.KEY_BACKUP_PREFIX;

    public AutomationScheduleManager(Context context) {
        super(context);
    }

    public void saveSchedule(PresetModel model) {
        savePreset(model);
    }

    public PresetModel loadSchedule(int scheduleNumber) {
        return loadPreset(scheduleNumber);
    }

    public boolean scheduleExists(int scheduleNumber) {
        return presetExists(scheduleNumber);
    }

    public String getScheduleName(int scheduleNumber) {
        return getPresetName(scheduleNumber);
    }

    public int getActiveScheduleNumber() {
        return getActivePresetNumber();
    }

    public void forceDeactivateScheduleIfActive(int scheduleNumber) {
        forceDeactivateIfActive(scheduleNumber);
    }

    public void exportScheduleToJson(PresetModel model, Uri uri) {
        exportPresetToJson(model, uri);
    }

    public PresetModel importScheduleFromJson(int scheduleNumber, Uri uri) {
        return importPresetFromJson(scheduleNumber, uri);
    }

    public void scheduleActivationAlarms(PresetModel model) {
        scheduleAlarms(model);
    }

    public void cancelActivationAlarms(int scheduleNumber) {
        cancelAlarms(scheduleNumber);
    }

    public void reconcileCurrentSchedule() {
        checkAndApplyCurrentPreset();
    }
}
