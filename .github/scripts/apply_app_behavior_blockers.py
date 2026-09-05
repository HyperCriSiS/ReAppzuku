from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one match in {path}: {text.count(old)}")
    p.write_text(text.replace(old, new, 1))


policy = "app/src/main/java/com/gree1d/reappzuku/core/BackgroundWorkPolicy.java"
replace_once(policy, "import org.json.JSONObject;\n", "import org.json.JSONObject;\n\nimport java.util.EnumSet;\n")
replace_once(
    policy,
    '''public final class BackgroundWorkPolicy {\n    private static final String KEY_RESTRICTIONS_SCHEDULES = "restrictions_schedules";\n\n    private BackgroundWorkPolicy() {}\n''',
    '''public final class BackgroundWorkPolicy {\n    private static final String KEY_RESTRICTIONS_SCHEDULES = "restrictions_schedules";\n\n    public enum Blocker {\n        AUTO_KILL,\n        SMART_LIFECYCLE,\n        SLEEP_MODE,\n        ACTIVE_PRESET,\n        RESTRICTIONS_SCHEDULE\n    }\n\n    private BackgroundWorkPolicy() {}\n''')
replace_once(
    policy,
    '''    public static boolean requiresBackgroundContinuity(Context context) {\n        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);\n        return AutomationDesiredState.requiresBackgroundContinuity(\n                prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false),\n                prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false),\n                prefs.getBoolean(KEY_SLEEP_MODE_ENABLED, false),\n                prefs.getInt(KEY_ACTIVE_PRESET, 0) != 0,\n                hasEnabledRestrictionSchedule(prefs));\n    }\n''',
    '''    public static EnumSet<Blocker> getActiveBlockers(Context context) {\n        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);\n        return resolveActiveBlockers(\n                prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false),\n                prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false),\n                prefs.getBoolean(KEY_SLEEP_MODE_ENABLED, false),\n                prefs.getInt(KEY_ACTIVE_PRESET, 0) != 0,\n                hasEnabledRestrictionSchedule(prefs));\n    }\n\n    static EnumSet<Blocker> resolveActiveBlockers(\n            boolean autoKillEnabled,\n            boolean smartLifecycleEnabled,\n            boolean sleepModeEnabled,\n            boolean activePreset,\n            boolean restrictionsScheduleEnabled) {\n        EnumSet<Blocker> blockers = EnumSet.noneOf(Blocker.class);\n        if (autoKillEnabled) blockers.add(Blocker.AUTO_KILL);\n        if (smartLifecycleEnabled) blockers.add(Blocker.SMART_LIFECYCLE);\n        if (sleepModeEnabled) blockers.add(Blocker.SLEEP_MODE);\n        if (activePreset) blockers.add(Blocker.ACTIVE_PRESET);\n        if (restrictionsScheduleEnabled) blockers.add(Blocker.RESTRICTIONS_SCHEDULE);\n        return blockers;\n    }\n\n    public static boolean requiresBackgroundContinuity(Context context) {\n        return !getActiveBlockers(context).isEmpty();\n    }\n''')

settings = "app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivity.java"
replace_once(
    settings,
    '''    private void updateAppBehaviorAvailability() {\n        boolean blocked = BackgroundWorkPolicy.enforceCompatibleBehavior(this);\n        boolean enabled = !blocked;\n        float alpha = enabled ? 1.0f : 0.5f;\n\n        boolean preventAutoStart = sharedPreferences.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, true);\n        boolean exitOnBack = sharedPreferences.getBoolean(KEY_EXIT_ON_BACK, false);\n\n        binding.switchPreventShizukuAutostart.setChecked(preventAutoStart);\n        binding.switchExitOnBack.setChecked(exitOnBack);\n        binding.switchPreventShizukuAutostart.setEnabled(enabled);\n        binding.switchExitOnBack.setEnabled(enabled);\n        binding.layoutPreventShizukuAutostart.setEnabled(enabled);\n        binding.layoutExitOnBack.setEnabled(enabled);\n        binding.layoutPreventShizukuAutostart.setAlpha(alpha);\n        binding.layoutExitOnBack.setAlpha(alpha);\n    }\n''',
    '''    private void updateAppBehaviorAvailability() {\n        boolean blocked = BackgroundWorkPolicy.enforceCompatibleBehavior(this);\n        Set<BackgroundWorkPolicy.Blocker> blockers = BackgroundWorkPolicy.getActiveBlockers(this);\n        boolean enabled = !blocked;\n        float alpha = enabled ? 1.0f : 0.5f;\n\n        boolean preventAutoStart = sharedPreferences.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, true);\n        boolean exitOnBack = sharedPreferences.getBoolean(KEY_EXIT_ON_BACK, false);\n\n        binding.switchPreventShizukuAutostart.setChecked(preventAutoStart);\n        binding.switchExitOnBack.setChecked(exitOnBack);\n        binding.switchPreventShizukuAutostart.setEnabled(enabled);\n        binding.switchExitOnBack.setEnabled(enabled);\n        binding.layoutPreventShizukuAutostart.setEnabled(enabled);\n        binding.layoutExitOnBack.setEnabled(enabled);\n        binding.layoutPreventShizukuAutostart.setAlpha(alpha);\n        binding.layoutExitOnBack.setAlpha(alpha);\n\n        if (blocked && !blockers.isEmpty()) {\n            binding.textAppBehaviorBlocked.setText(getString(\n                    R.string.settings_app_behavior_blocked_by,\n                    formatAppBehaviorBlockers(blockers)));\n            binding.textAppBehaviorBlocked.setVisibility(View.VISIBLE);\n        } else {\n            binding.textAppBehaviorBlocked.setVisibility(View.GONE);\n        }\n    }\n\n    private String formatAppBehaviorBlockers(Set<BackgroundWorkPolicy.Blocker> blockers) {\n        List<String> labels = new ArrayList<>();\n        for (BackgroundWorkPolicy.Blocker blocker : blockers) {\n            switch (blocker) {\n                case AUTO_KILL:\n                    labels.add(getString(R.string.settings_section_kill_rules));\n                    break;\n                case SMART_LIFECYCLE:\n                    labels.add(getString(R.string.settings_smart_lifecycle_title));\n                    break;\n                case SLEEP_MODE:\n                    labels.add(getString(R.string.settings_sleep_mode_title));\n                    break;\n                case ACTIVE_PRESET:\n                    labels.add(getString(R.string.settings_presets_title));\n                    break;\n                case RESTRICTIONS_SCHEDULE:\n                    labels.add(getString(R.string.settings_restrictions_scheduler_title));\n                    break;\n            }\n        }\n        StringBuilder joined = new StringBuilder();\n        for (String label : labels) {\n            if (joined.length() > 0) joined.append(", ");\n            joined.append(label);\n        }\n        return joined.toString();\n    }\n''')

layout = "app/src/main/res/layout/activity_settings.xml"
replace_once(
    layout,
    '''                    <LinearLayout\n                        android:id="@+id/layout_prevent_shizuku_autostart"''',
    '''                    <TextView\n                        android:id="@+id/text_app_behavior_blocked"\n                        android:layout_width="match_parent"\n                        android:layout_height="wrap_content"\n                        android:paddingHorizontal="8dp"\n                        android:paddingTop="4dp"\n                        android:paddingBottom="4dp"\n                        android:text="@string/settings_app_behavior_blocked"\n                        android:textColor="@color/text_secondary"\n                        android:textSize="12sp"\n                        android:visibility="gone" />\n\n                    <LinearLayout\n                        android:id="@+id/layout_prevent_shizuku_autostart"''')

translations = {
    "app/src/main/res/values/strings_assurance.xml": "Unavailable while active: %1$s",
    "app/src/main/res/values-es/strings_assurance.xml": "No disponible mientras esté activo: %1$s",
    "app/src/main/res/values-ru/strings_assurance.xml": "Недоступно, пока активно: %1$s",
    "app/src/main/res/values-uk/strings_assurance.xml": "Недоступно, доки активне: %1$s",
    "app/src/main/res/values-zh-rCN/strings_assurance.xml": "以下功能启用时不可用：%1$s",
}
for path, value in translations.items():
    p = Path(path)
    text = p.read_text()
    if "settings_app_behavior_blocked_by" in text:
        raise SystemExit(f"string already exists in {path}")
    p.write_text(text.replace(
        "</resources>",
        f'    <string name="settings_app_behavior_blocked_by">{value}</string>\\n</resources>',
        1))

Path("app/src/test/java/com/gree1d/reappzuku/core/BackgroundWorkPolicyTest.java").write_text('''package com.gree1d.reappzuku.core;\n\nimport org.junit.Test;\n\nimport java.util.EnumSet;\n\nimport static org.junit.Assert.assertEquals;\n\npublic class BackgroundWorkPolicyTest {\n\n    @Test\n    public void noAutomationProducesNoBlockers() {\n        assertEquals(\n                EnumSet.noneOf(BackgroundWorkPolicy.Blocker.class),\n                BackgroundWorkPolicy.resolveActiveBlockers(false, false, false, false, false));\n    }\n\n    @Test\n    public void reportsEveryActiveContinuityRequirement() {\n        assertEquals(\n                EnumSet.allOf(BackgroundWorkPolicy.Blocker.class),\n                BackgroundWorkPolicy.resolveActiveBlockers(true, true, true, true, true));\n    }\n\n    @Test\n    public void reportsOnlyEnabledBlockers() {\n        assertEquals(\n                EnumSet.of(\n                        BackgroundWorkPolicy.Blocker.SMART_LIFECYCLE,\n                        BackgroundWorkPolicy.Blocker.RESTRICTIONS_SCHEDULE),\n                BackgroundWorkPolicy.resolveActiveBlockers(false, true, false, false, true));\n    }\n}\n''')
