#!/usr/bin/env python3
from pathlib import Path


def rw(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    if new in s:
        return
    if old not in s:
        raise RuntimeError(f"Anchor not found in {path}: {old[:100]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


def write(path, content):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8")

# Pure desired-state policy: testable without Android.
write("app/src/main/java/com/gree1d/reappzuku/core/AutomationDesiredState.java", '''package com.gree1d.reappzuku.core;

/** Pure desired-state rules for ReAppzuku background automation. */
public final class AutomationDesiredState {
    private AutomationDesiredState() {}

    public static boolean shouldRunForegroundService(boolean autoKillEnabled) {
        return autoKillEnabled;
    }

    public static boolean requiresBackgroundContinuity(
            boolean autoKillEnabled,
            boolean smartLifecycleEnabled,
            boolean sleepModeEnabled,
            boolean presetActive,
            boolean restrictionScheduleEnabled) {
        return autoKillEnabled || smartLifecycleEnabled || sleepModeEnabled
                || presetActive || restrictionScheduleEnabled;
    }
}
''')

# Centralize BackgroundWorkPolicy decisions.
rw("app/src/main/java/com/gree1d/reappzuku/core/BackgroundWorkPolicy.java",
'''    public static boolean requiresBackgroundContinuity(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

        // The foreground/background management service and everything attached
        // to it (periodic kill, screen-off actions, RAM threshold, HW triggers,
        // sleep mode, etc.) is represented by the master service switch.
        if (prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false)) return true;

        // Smart Lifecycle is intentionally independent from the legacy service
        // and uses WorkManager, so it must be considered separately.
        if (prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false)) return true;

        // Defensive: do not allow process-killing behavior while a sleep mode or
        // preset remains active even if preferences temporarily become inconsistent.
        if (prefs.getBoolean(KEY_SLEEP_MODE_ENABLED, false)) return true;
        if (prefs.getInt(KEY_ACTIVE_PRESET, 0) != 0) return true;

        // Restriction schedules are AlarmManager-based and can be enabled without
        // keeping the Auto-Kill screen open. Treat any enabled entry as automation.
        if (hasEnabledRestrictionSchedule(prefs)) return true;

        return false;
    }
''',
'''    public static boolean shouldRunForegroundService(boolean autoKillEnabled) {
        return AutomationDesiredState.shouldRunForegroundService(autoKillEnabled);
    }

    public static boolean shouldRunForegroundService(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return shouldRunForegroundService(prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false));
    }

    public static boolean requiresBackgroundContinuity(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return AutomationDesiredState.requiresBackgroundContinuity(
                prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false),
                prefs.getBoolean(KEY_SMART_LIFECYCLE_ENABLED, false),
                prefs.getBoolean(KEY_SLEEP_MODE_ENABLED, false),
                prefs.getInt(KEY_ACTIVE_PRESET, 0) != 0,
                hasEnabledRestrictionSchedule(prefs));
    }
''')

# Service self-recovery must respect explicit user disable.
rw("app/src/main/java/com/gree1d/reappzuku/service/ShappkyService.java",
'import com.gree1d.reappzuku.core.AppDebugManager.Category;\n',
'import com.gree1d.reappzuku.core.AppDebugManager.Category;\nimport com.gree1d.reappzuku.core.BackgroundWorkPolicy;\n')

rw("app/src/main/java/com/gree1d/reappzuku/service/ShappkyService.java",
'''    private void scheduleServiceRestart() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(this, RestartReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                RESTART_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3000, pi);
    }
''',
'''    private void scheduleServiceRestart() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(this, RestartReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                RESTART_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3000, pi);
    }

    private void cancelServiceRestart() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(this, RestartReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                RESTART_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }
''')

rw("app/src/main/java/com/gree1d/reappzuku/service/ShappkyService.java",
'''        isRunning = false;
        scheduleServiceRestart();
        AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": Service restart scheduled via AlarmManager");
''',
'''        isRunning = false;
        if (BackgroundWorkPolicy.shouldRunForegroundService(this)) {
            scheduleServiceRestart();
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": Service restart scheduled because Auto-Kill is still enabled");
        } else {
            cancelServiceRestart();
            AppDebugManager.d(Category.FOREGROUND_SERVICE, FILE_NAME + ": Service restart suppressed because Auto-Kill is disabled");
        }
''')

rw("app/src/main/java/com/gree1d/reappzuku/service/ShappkyService.java",
'''        public void onReceive(Context context, Intent intent) {
            if (!ShappkyService.isRunning()) {
''',
'''        public void onReceive(Context context, Intent intent) {
            if (!BackgroundWorkPolicy.shouldRunForegroundService(context)) {
                AppDebugManager.d(Category.FOREGROUND_SERVICE,
                        "ShappkyService.RestartReceiver: ignoring stale restart alarm because Auto-Kill is disabled");
                return;
            }
            if (!ShappkyService.isRunning()) {
''')

# Strict, fork-owned update provenance.
write("app/src/main/java/com/gree1d/reappzuku/core/ReleaseVersion.java", '''package com.gree1d.reappzuku.core;

/** Strict numeric release comparison. Non-version test tags are ignored. */
public final class ReleaseVersion {
    private ReleaseVersion() {}

    public static boolean isNewer(String remote, String local) {
        int[] r = parse(remote);
        int[] l = parse(local);
        if (r == null || l == null) return false;
        int length = Math.max(r.length, l.length);
        for (int i = 0; i < length; i++) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv > lv) return true;
            if (rv < lv) return false;
        }
        return false;
    }

    static int[] parse(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        if (v.isEmpty() || !v.matches("\\\\d+(\\\\.\\\\d+)*")) return null;
        String[] parts = v.split("\\\\.");
        int[] result = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) result[i] = Integer.parseInt(parts[i]);
            return result;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
''')

rw("app/src/main/java/com/gree1d/reappzuku/manager/UpdateChecker.java",
'import com.gree1d.reappzuku.core.AppDebugManager.Category;\n',
'import com.gree1d.reappzuku.core.AppDebugManager.Category;\nimport com.gree1d.reappzuku.core.ReleaseVersion;\n')

p = Path("app/src/main/java/com/gree1d/reappzuku/manager/UpdateChecker.java")
s = p.read_text(encoding="utf-8")
s = s.replace("https://api.github.com/repos/gree1d/ReAppzuku/releases/latest", "https://api.github.com/repos/HyperCriSiS/ReAppzuku/releases/latest")
s = s.replace("https://github.com/gree1d/ReAppzuku/releases", "https://github.com/HyperCriSiS/ReAppzuku/releases")
start = s.find("    public static boolean isNewer(String remote, String local) {")
end = s.find("    public static String getAppVersion", start)
if start < 0 or end < 0:
    raise RuntimeError("UpdateChecker version helper block not found")
s = s[:start] + '''    public static boolean isNewer(String remote, String local) {
        return ReleaseVersion.isNewer(remote, local);
    }

''' + s[end:]
p.write_text(s, encoding="utf-8")

# Platform/security cleanup.
p = Path("app/src/main/AndroidManifest.xml")
s = p.read_text(encoding="utf-8")
s = s.replace('    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />\n', '')
s = s.replace('                <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />\n', '')
p.write_text(s, encoding="utf-8")

p = Path("app/src/main/res/xml/accessibility_service_config.xml")
s = p.read_text(encoding="utf-8")
s = s.replace('android:settingsActivity="com.gree1d.reappzuku.SettingsActivity"', 'android:settingsActivity="com.gree1d.reappzuku.ui.SettingsActivity"')
s = s.replace('    android:accessibilityFlags="flagIncludeNotImportantViews"\n', '')
p.write_text(s, encoding="utf-8")

p = Path("app/src/main/java/com/gree1d/reappzuku/db/AppDatabase.java")
s = p.read_text(encoding="utf-8")
s = s.replace('                    .fallbackToDestructiveMigration()\n', '')
p.write_text(s, encoding="utf-8")

p = Path("app/build.gradle")
s = p.read_text(encoding="utf-8")
if 'testImplementation "junit:junit:4.13.2"' not in s:
    s = s.replace('    implementation "androidx.glance:glance:1.1.0"\n}', '    implementation "androidx.glance:glance:1.1.0"\n\n    testImplementation "junit:junit:4.13.2"\n}')
p.write_text(s, encoding="utf-8")

write("app/src/test/java/com/gree1d/reappzuku/core/AutomationDesiredStateTest.java", '''package com.gree1d.reappzuku.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class AutomationDesiredStateTest {
    @Test public void foregroundServiceFollowsMasterSwitch() {
        assertTrue(AutomationDesiredState.shouldRunForegroundService(true));
        assertFalse(AutomationDesiredState.shouldRunForegroundService(false));
    }

    @Test public void noAutomationAllowsOnDemandBehavior() {
        assertFalse(AutomationDesiredState.requiresBackgroundContinuity(false, false, false, false, false));
    }

    @Test public void everyAutomationSourceBlocksOnDemandBehavior() {
        assertTrue(AutomationDesiredState.requiresBackgroundContinuity(true, false, false, false, false));
        assertTrue(AutomationDesiredState.requiresBackgroundContinuity(false, true, false, false, false));
        assertTrue(AutomationDesiredState.requiresBackgroundContinuity(false, false, true, false, false));
        assertTrue(AutomationDesiredState.requiresBackgroundContinuity(false, false, false, true, false));
        assertTrue(AutomationDesiredState.requiresBackgroundContinuity(false, false, false, false, true));
    }
}
''')

write("app/src/test/java/com/gree1d/reappzuku/core/ReleaseVersionTest.java", '''package com.gree1d.reappzuku.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReleaseVersionTest {
    @Test public void comparesNumericReleases() {
        assertTrue(ReleaseVersion.isNewer("1.8.8", "1.8.7"));
        assertTrue(ReleaseVersion.isNewer("v2.0.0", "1.9.9"));
        assertFalse(ReleaseVersion.isNewer("1.8.7", "1.8.7"));
        assertFalse(ReleaseVersion.isNewer("1.8.6", "1.8.7"));
    }

    @Test public void ignoresNonProductionTags() {
        assertFalse(ReleaseVersion.isNewer("ondemand-test", "1.8.7"));
        assertFalse(ReleaseVersion.isNewer("1.8.8-beta", "1.8.7"));
    }
}
''')

print("assurance-phase1-core applied")
