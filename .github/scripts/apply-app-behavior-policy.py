from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if new in text:
        return False
    if old not in text:
        raise RuntimeError(f"Expected text not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))
    return True

changed = False

# 1) Preference key
changed |= replace_once(
    "app/src/main/java/com/gree1d/reappzuku/core/PreferenceKeys.java",
    '    public static final String KEY_EXIT_ON_BACK = "exit_on_back";\n',
    '    public static final String KEY_EXIT_ON_BACK = "exit_on_back";\n'
    '    public static final String KEY_PREVENT_SHIZUKU_AUTOSTART = "prevent_shizuku_autostart";\n',
)

# 2) Central policy for features that must not be interrupted by full exit / on-demand startup.
policy = Path("app/src/main/java/com/gree1d/reappzuku/core/BackgroundWorkPolicy.java")
policy_text = r'''package com.gree1d.reappzuku.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;

/**
 * Single source of truth for App Behavior options that can terminate or avoid
 * starting ReAppzuku's main process. Those options are incompatible with
 * active background automation because they would interrupt the work.
 */
public final class BackgroundWorkPolicy {
    private static final String KEY_RESTRICTIONS_SCHEDULES = "restrictions_schedules";

    private BackgroundWorkPolicy() {}

    public static boolean requiresBackgroundContinuity(Context context) {
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

    private static boolean hasEnabledRestrictionSchedule(SharedPreferences prefs) {
        String json = prefs.getString(KEY_RESTRICTIONS_SCHEDULES, null);
        if (json == null || json.trim().isEmpty()) return false;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.optJSONObject(i);
                if (entry != null && entry.optBoolean("enabled", true)) return true;
            }
        } catch (Exception ignored) {
            // If stored scheduler state is unreadable, fail safe: do not permit
            // behavior that could terminate background work unexpectedly.
            return true;
        }
        return false;
    }

    public static boolean isOnDemandBehaviorAllowed(Context context) {
        return !requiresBackgroundContinuity(context);
    }

    public static boolean shouldPreventShizukuAutoStart(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return isOnDemandBehaviorAllowed(context)
                && prefs.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, true);
    }

    /**
     * Keep the cross-process Shizuku provider independent from SharedPreferences.
     * Android does not provide reliable live SharedPreferences coherence across
     * processes, so the normal process persists the policy in the enabled state
     * of a non-exported receiver. The :shizuku process only sends an explicit
     * broadcast; Android delivers it only when auto-start is allowed.
     */
    public static void syncShizukuWakeComponent(Context context) {
        boolean enableWakeReceiver = !shouldPreventShizukuAutoStart(context);
        ComponentName component = new ComponentName(context, ShizukuWakeReceiver.class);
        int desired = enableWakeReceiver
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        PackageManager pm = context.getPackageManager();
        if (pm.getComponentEnabledSetting(component) != desired) {
            pm.setComponentEnabledSetting(
                    component, desired, PackageManager.DONT_KILL_APP);
        }
    }

    /**
     * Force conflicting options off when background automation becomes active.
     * Returns true when the App Behavior options must be disabled in the UI.
     */
    public static boolean enforceCompatibleBehavior(Context context) {
        boolean blocked = requiresBackgroundContinuity(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (blocked) {
            boolean needsWrite = prefs.getBoolean(KEY_EXIT_ON_BACK, false)
                    || prefs.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, true);
            if (needsWrite) {
                prefs.edit()
                        .putBoolean(KEY_EXIT_ON_BACK, false)
                        .putBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, false)
                        .apply();
            }
        }
        syncShizukuWakeComponent(context);
        return blocked;
    }
}
'''
if not policy.exists() or policy.read_text() != policy_text:
    policy.write_text(policy_text)
    changed = True

# 3) Minimal receiver whose only purpose is to start the normal app process when
# auto-start is allowed. The receiver is non-exported.
receiver = Path("app/src/main/java/com/gree1d/reappzuku/core/ShizukuWakeReceiver.java")
receiver_text = r'''package com.gree1d.reappzuku.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Intentionally empty. Delivery of this explicit in-app broadcast starts the
 * normal ReAppzuku process, whose Application then attaches to Shizuku.
 */
public final class ShizukuWakeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // No work here: starting the normal application process is the work.
    }
}
'''
if not receiver.exists() or receiver.read_text() != receiver_text:
    receiver.write_text(receiver_text)
    changed = True

# 4) Provider process stays minimal, but can optionally wake the normal process.
app = Path("app/src/main/java/com/gree1d/reappzuku/core/App.java")
text = app.read_text()
if "ShizukuWakeReceiver.class" not in text:
    text = text.replace(
        "import android.content.Context;\n",
        "import android.content.Context;\nimport android.content.Intent;\n",
        1,
    )
    old = '''        if (shizukuProviderProcess) {
            return;
        }
'''
    new = '''        if (shizukuProviderProcess) {
            // Keep the Shizuku provider process tiny. The normal process stores
            // the auto-start policy in the enabled state of this non-exported
            // receiver, avoiding unreliable cross-process SharedPreferences.
            Shizuku.addBinderReceivedListenerSticky(() ->
                    sendBroadcast(new Intent(this, ShizukuWakeReceiver.class)));
            return;
        }

        // Reconcile App Behavior whenever the normal process starts (including
        // upgrades from builds that predate the configurable option).
        BackgroundWorkPolicy.enforceCompatibleBehavior(this);
'''
    if old not in text:
        raise RuntimeError("App.java provider-process block not found")
    app.write_text(text.replace(old, new, 1))
    changed = True

# 5) Non-exported receiver in manifest.
manifest = Path("app/src/main/AndroidManifest.xml")
text = manifest.read_text()
if "ShizukuWakeReceiver" not in text:
    marker = '        <receiver android:name="com.gree1d.reappzuku.core.BootReceiver" android:exported="true">\n'
    if marker not in text:
        raise RuntimeError("BootReceiver manifest marker not found")
    text = text.replace(
        marker,
        '        <receiver\n'
        '            android:name="com.gree1d.reappzuku.core.ShizukuWakeReceiver"\n'
        '            android:exported="false" />\n\n' + marker,
        1,
    )
    manifest.write_text(text)
    changed = True

# 6) BootReceiver must not start the foreground service when its master feature
# is disabled. Other schedulers keep their own lightweight scheduling paths.
boot = Path("app/src/main/java/com/gree1d/reappzuku/core/BootReceiver.java")
text = boot.read_text()
old_boot = '''            Intent serviceIntent = new Intent(context, ShappkyService.class);
            ContextCompat.startForegroundService(context, serviceIntent);

            RestrictionsScheduler.scheduleNextStatic(context);

            SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
            boolean autoKillEnabled = prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false);
            if (autoKillEnabled) {
                AutoKillWorker.schedule(context, "Periodic Kill");
                AppDebugManager.d(Category.CORE, "BootReceiver: Boot complete (" + action + "): service started, worker scheduled");
            } else {
                AutoKillWorker.cancel(context);
                AppDebugManager.d(Category.CORE, "BootReceiver: Boot complete (" + action + "): service started, worker skipped");
            }
'''
new_boot = '''            RestrictionsScheduler.scheduleNextStatic(context);

            SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
            boolean autoKillEnabled = prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false);
            if (autoKillEnabled) {
                Intent serviceIntent = new Intent(context, ShappkyService.class);
                ContextCompat.startForegroundService(context, serviceIntent);
                AutoKillWorker.schedule(context, "Periodic Kill");
                AppDebugManager.d(Category.CORE, "BootReceiver: Boot complete (" + action + "): background service started, worker scheduled");
            } else {
                AutoKillWorker.cancel(context);
                AppDebugManager.d(Category.CORE, "BootReceiver: Boot complete (" + action + "): no background service requested");
            }
'''
if new_boot not in text:
    if old_boot not in text:
        raise RuntimeError("BootReceiver startup block not found")
    boot.write_text(text.replace(old_boot, new_boot, 1))
    changed = True

# 7) MainActivity: stale preferences must never kill the process while automation is active.
main = Path("app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java")
text = main.read_text()
if "BackgroundWorkPolicy.isOnDemandBehaviorAllowed" not in text:
    text = text.replace(
        "import com.gree1d.reappzuku.core.ShellManager;\n",
        "import com.gree1d.reappzuku.core.ShellManager;\nimport com.gree1d.reappzuku.core.BackgroundWorkPolicy;\n",
        1,
    )
    text = text.replace(
        '                if (sharedPreferences.getBoolean(KEY_EXIT_ON_BACK, false)) {\n',
        '                if (sharedPreferences.getBoolean(KEY_EXIT_ON_BACK, false)\n'
        '                        && BackgroundWorkPolicy.isOnDemandBehaviorAllowed(MainActivity.this)) {\n',
        1,
    )
    main.write_text(text)
    changed = True

# 8) Settings UI logic.
settings = Path("app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivity.java")
text = settings.read_text()
if "BackgroundWorkPolicy" not in text:
    text = text.replace(
        "import com.gree1d.reappzuku.core.BackupManager;\n",
        "import com.gree1d.reappzuku.core.BackupManager;\nimport com.gree1d.reappzuku.core.BackgroundWorkPolicy;\n",
        1,
    )

if "switchPreventShizukuAutostart" not in text:
    # accent tint list
    text = text.replace(
        "            R.id.switch_sleep_mode,\n            R.id.switch_exit_on_back\n",
        "            R.id.switch_sleep_mode,\n            R.id.switch_exit_on_back,\n            R.id.switch_prevent_shizuku_autostart\n",
        1,
    )

    # load state (policy first so conflicting stale prefs are corrected before rendering)
    text = text.replace(
        "        binding.switchExitOnBack.setChecked(sharedPreferences.getBoolean(KEY_EXIT_ON_BACK, false));\n\n",
        "        BackgroundWorkPolicy.enforceCompatibleBehavior(this);\n"
        "        binding.switchPreventShizukuAutostart.setChecked(\n"
        "                sharedPreferences.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, true));\n"
        "        binding.switchExitOnBack.setChecked(sharedPreferences.getBoolean(KEY_EXIT_ON_BACK, false));\n"
        "        updateAppBehaviorAvailability();\n\n",
        1,
    )

    # listeners
    old_listeners = '''        binding.switchExitOnBack.setOnCheckedChangeListener((buttonView, isChecked) ->
                sharedPreferences.edit().putBoolean(KEY_EXIT_ON_BACK, isChecked).apply());
        binding.layoutExitOnBack.setOnClickListener(v -> binding.switchExitOnBack.toggle());

'''
    new_listeners = '''        binding.switchPreventShizukuAutostart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !BackgroundWorkPolicy.isOnDemandBehaviorAllowed(this)) {
                buttonView.setChecked(false);
                Toast.makeText(this, R.string.settings_app_behavior_blocked, Toast.LENGTH_LONG).show();
                return;
            }
            sharedPreferences.edit().putBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, isChecked).apply();
        });
        binding.layoutPreventShizukuAutostart.setOnClickListener(v -> {
            if (binding.switchPreventShizukuAutostart.isEnabled()) {
                binding.switchPreventShizukuAutostart.toggle();
            }
        });

        binding.switchExitOnBack.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !BackgroundWorkPolicy.isOnDemandBehaviorAllowed(this)) {
                buttonView.setChecked(false);
                Toast.makeText(this, R.string.settings_app_behavior_blocked, Toast.LENGTH_LONG).show();
                return;
            }
            sharedPreferences.edit().putBoolean(KEY_EXIT_ON_BACK, isChecked).apply();
        });
        binding.layoutExitOnBack.setOnClickListener(v -> {
            if (binding.switchExitOnBack.isEnabled()) binding.switchExitOnBack.toggle();
        });

'''
    if old_listeners not in text:
        raise RuntimeError("Settings App Behavior listener block not found")
    text = text.replace(old_listeners, new_listeners, 1)

    # onResume update after background state is refreshed
    text = text.replace(
        "        updateRamThresholdLimitVisibility(ramEnabled && autoKill);\n        updateShellModeText();\n",
        "        updateRamThresholdLimitVisibility(ramEnabled && autoKill);\n"
        "        updateAppBehaviorAvailability();\n"
        "        updateShellModeText();\n",
        1,
    )

    # always re-evaluate after any shared preference change (scheduler included)
    text = text.replace(
        "        }\n    }\n\n    private void setupToolbar() {\n",
        "        }\n        updateAppBehaviorAvailability();\n    }\n\n    private void setupToolbar() {\n",
        1,
    )

    # central UI method before smart lifecycle helper
    marker = "    private void updateSmartLifecycleOptionsVisibility(boolean enabled) {\n"
    method = '''    private void updateAppBehaviorAvailability() {
        boolean blocked = BackgroundWorkPolicy.enforceCompatibleBehavior(this);
        boolean enabled = !blocked;
        float alpha = enabled ? 1.0f : 0.5f;

        boolean preventAutoStart = sharedPreferences.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, true);
        boolean exitOnBack = sharedPreferences.getBoolean(KEY_EXIT_ON_BACK, false);

        binding.switchPreventShizukuAutostart.setChecked(preventAutoStart);
        binding.switchExitOnBack.setChecked(exitOnBack);
        binding.switchPreventShizukuAutostart.setEnabled(enabled);
        binding.switchExitOnBack.setEnabled(enabled);
        binding.layoutPreventShizukuAutostart.setEnabled(enabled);
        binding.layoutExitOnBack.setEnabled(enabled);
        binding.layoutPreventShizukuAutostart.setAlpha(alpha);
        binding.layoutExitOnBack.setAlpha(alpha);
    }

'''
    if marker not in text:
        raise RuntimeError("Smart Lifecycle helper marker not found")
    text = text.replace(marker, method + marker, 1)

    settings.write_text(text)
    changed = True

# 9) Add the new option to the existing App Behavior card before exit-on-Back.
layout = Path("app/src/main/res/layout/activity_settings.xml")
text = layout.read_text()
if 'android:id="@+id/switch_prevent_shizuku_autostart"' not in text:
    marker = '''                    <LinearLayout
                        android:id="@+id/layout_exit_on_back"'''
    if marker not in text:
        raise RuntimeError("App Behavior exit row marker not found")
    row = '''                    <LinearLayout
                        android:id="@+id/layout_prevent_shizuku_autostart"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:paddingHorizontal="8dp"
                        android:paddingVertical="12dp"
                        android:background="?attr/selectableItemBackground">

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:orientation="vertical">

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="@string/settings_prevent_shizuku_autostart_title"
                                android:textColor="@color/text_primary"
                                android:textSize="16sp" />

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:layout_marginTop="2dp"
                                android:text="@string/settings_prevent_shizuku_autostart_subtitle"
                                android:textColor="@color/text_secondary"
                                android:textSize="12sp" />
                        </LinearLayout>

                        <com.google.android.material.materialswitch.MaterialSwitch
                            android:id="@+id/switch_prevent_shizuku_autostart"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:clickable="true"
                            android:focusable="true" />
                    </LinearLayout>

'''
    layout.write_text(text.replace(marker, row + marker, 1))
    changed = True

# 10) Strings. Default locale fallback is sufficient for test branch.
strings = Path("app/src/main/res/values/strings.xml")
text = strings.read_text()
if 'name="settings_prevent_shizuku_autostart_title"' not in text:
    marker = '    <string name="settings_section_behavior">App behavior</string>\n'
    block = (
        marker +
        '    <string name="settings_prevent_shizuku_autostart_title">Prevent automatic start from Shizuku</string>\n'
        '    <string name="settings_prevent_shizuku_autostart_subtitle">Keep ReAppzuku on demand when Shizuku starts. Disabled while background automation is active.</string>\n'
        '    <string name="settings_app_behavior_blocked">Disable background automation before using this App Behavior option.</string>\n'
    )
    if marker not in text:
        raise RuntimeError("App Behavior strings marker not found")
    text = text.replace(marker, block, 1)
    # Also make the existing exit description explicit about the interlock.
    text = text.replace(
        '<string name="settings_exit_on_back_subtitle">On the main screen, Back stops the ReAppzuku main process; background features in that process stop too</string>',
        '<string name="settings_exit_on_back_subtitle">Stop the ReAppzuku main process from the main screen. Disabled while background automation is active.</string>'
    )
    strings.write_text(text)
    changed = True

print("app behavior policy patch applied" if changed else "app behavior policy patch already present")
