from pathlib import Path

backup_path = Path('app/src/main/java/com/gree1d/reappzuku/core/BackupManager.java')
test_path = Path('app/src/androidTest/java/com/gree1d/reappzuku/core/BackupManagerRestoreTest.java')

backup = backup_path.read_text()
old_ctor = '''    private final BackupCodec backupCodec;\n\n    public BackupManager(Context context) {\n        this(context, new BackupCodec());\n    }\n\n    BackupManager(Context context, BackupCodec backupCodec) {\n        if (backupCodec == null) throw new IllegalArgumentException("backupCodec == null");\n        this.context = context.getApplicationContext();\n        this.prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);\n        this.backupCodec = backupCodec;\n    }'''
new_ctor = '''    private final BackupCodec backupCodec;\n    private final RestoreFaultInjector restoreFaultInjector;\n\n    enum RestoreCommitPoint {\n        AFTER_MAIN_COMMIT,\n        AFTER_PRESET_1_COMMIT,\n        AFTER_PRESET_2_COMMIT\n    }\n\n    interface RestoreFaultInjector {\n        RestoreFaultInjector NONE = point -> { };\n\n        void afterCommit(RestoreCommitPoint point);\n    }\n\n    public BackupManager(Context context) {\n        this(context, new BackupCodec(), RestoreFaultInjector.NONE);\n    }\n\n    BackupManager(Context context, BackupCodec backupCodec) {\n        this(context, backupCodec, RestoreFaultInjector.NONE);\n    }\n\n    BackupManager(Context context, BackupCodec backupCodec, RestoreFaultInjector restoreFaultInjector) {\n        if (backupCodec == null) throw new IllegalArgumentException("backupCodec == null");\n        if (restoreFaultInjector == null) throw new IllegalArgumentException("restoreFaultInjector == null");\n        this.context = context.getApplicationContext();\n        this.prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);\n        this.backupCodec = backupCodec;\n        this.restoreFaultInjector = restoreFaultInjector;\n    }'''
if old_ctor not in backup:
    raise SystemExit('BackupManager constructor anchor not found')
backup = backup.replace(old_ctor, new_ctor, 1)

old_commits = '''            if (!editor.commit()) throw new IllegalStateException("main preferences commit failed");\n\n            if (containsPresetSection) {\n                if (!writePresetStorage(presetManager, PresetModel.PRESET_1, restoredPresets[0]))\n                    throw new IllegalStateException("preset 1 commit failed");\n                if (!writePresetStorage(presetManager, PresetModel.PRESET_2, restoredPresets[1]))\n                    throw new IllegalStateException("preset 2 commit failed");\n            }'''
new_commits = '''            if (!editor.commit()) throw new IllegalStateException("main preferences commit failed");\n            restoreFaultInjector.afterCommit(RestoreCommitPoint.AFTER_MAIN_COMMIT);\n\n            if (containsPresetSection) {\n                if (!writePresetStorage(presetManager, PresetModel.PRESET_1, restoredPresets[0]))\n                    throw new IllegalStateException("preset 1 commit failed");\n                restoreFaultInjector.afterCommit(RestoreCommitPoint.AFTER_PRESET_1_COMMIT);\n                if (!writePresetStorage(presetManager, PresetModel.PRESET_2, restoredPresets[1]))\n                    throw new IllegalStateException("preset 2 commit failed");\n                restoreFaultInjector.afterCommit(RestoreCommitPoint.AFTER_PRESET_2_COMMIT);\n            }'''
if old_commits not in backup:
    raise SystemExit('BackupManager durable commit anchor not found')
backup = backup.replace(old_commits, new_commits, 1)
backup_path.write_text(backup)

test = test_path.read_text()
old_import = 'import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_EXIT_ON_BACK;\nimport static org.junit.Assert.assertFalse;'
new_import = 'import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_EXIT_ON_BACK;\nimport static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertFalse;'
if old_import not in test:
    raise SystemExit('test import anchor not found')
test = test.replace(old_import, new_import, 1)

old_setup = '        assertTrue(prefs.edit().clear().commit());\n'
new_setup = '''        assertTrue(prefs.edit().clear().commit());\n        PresetManager presetManager = new PresetManager(context);\n        assertTrue(presetManager.clearPresetStorageBlocking(PresetModel.PRESET_1));\n        assertTrue(presetManager.clearPresetStorageBlocking(PresetModel.PRESET_2));\n'''
if old_setup not in test:
    raise SystemExit('test setup anchor not found')
test = test.replace(old_setup, new_setup, 1)

anchor = '''    @Test\n    public void rollbackHelperRestoresMainAndPresetSnapshots() throws Exception {'''
new_tests = '''    @Test\n    public void failureAfterMainCommitRollsBackPersistedMainState() throws Exception {\n        assertTrue(prefs.edit().putBoolean(KEY_EXIT_ON_BACK, false).commit());\n        JSONObject backup = new JSONObject().put(KEY_EXIT_ON_BACK, true);\n\n        BackupManager manager = new BackupManager(\n                context,\n                new BackupCodec(),\n                point -> {\n                    if (point == BackupManager.RestoreCommitPoint.AFTER_MAIN_COMMIT) {\n                        throw new IllegalStateException("injected failure after main commit");\n                    }\n                });\n\n        assertFalse(manager.restoreBackupJson(backup.toString()));\n        assertFalse(prefs.getBoolean(KEY_EXIT_ON_BACK, true));\n    }\n\n    @Test\n    public void failureAfterFirstPresetCommitRollsBackMainAndPresetStorage() throws Exception {\n        PresetManager presetManager = new PresetManager(context);\n        PresetModel original = new PresetModel(PresetModel.PRESET_1);\n        original.name = "before";\n        original.enabled = false;\n        assertTrue(presetManager.savePresetBlocking(original));\n        assertTrue(prefs.edit().putBoolean(KEY_EXIT_ON_BACK, false).commit());\n\n        PresetModel incoming = new PresetModel(PresetModel.PRESET_1);\n        incoming.name = "after";\n        incoming.enabled = false;\n        JSONObject presets = new JSONObject().put("preset_1", incoming.toJson());\n        JSONObject backup = new JSONObject()\n                .put(KEY_EXIT_ON_BACK, true)\n                .put("presets", presets);\n\n        BackupManager manager = new BackupManager(\n                context,\n                new BackupCodec(),\n                point -> {\n                    if (point == BackupManager.RestoreCommitPoint.AFTER_PRESET_1_COMMIT) {\n                        throw new IllegalStateException("injected failure after preset 1 commit");\n                    }\n                });\n\n        assertFalse(manager.restoreBackupJson(backup.toString()));\n        assertFalse(prefs.getBoolean(KEY_EXIT_ON_BACK, true));\n        PresetModel restored = presetManager.loadPreset(PresetModel.PRESET_1);\n        assertEquals("before", restored.name);\n        assertFalse(restored.enabled);\n    }\n\n'''
if anchor not in test:
    raise SystemExit('rollback test anchor not found')
test = test.replace(anchor, new_tests + anchor, 1)
test_path.write_text(test)
