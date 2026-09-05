from pathlib import Path

path = Path('app/src/androidTest/java/com/gree1d/reappzuku/core/BackupManagerRestoreTest.java')
text = path.read_text()
anchor = '''    @Test
    public void rollbackHelperRestoresMainAndPresetSnapshots() throws Exception {
'''
if anchor not in text:
    raise SystemExit('rollback helper anchor not found')
method = '''    @Test
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

'''
text = text.replace(anchor, method + anchor, 1)
path.write_text(text)
