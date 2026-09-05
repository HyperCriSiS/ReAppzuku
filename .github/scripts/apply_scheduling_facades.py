from pathlib import Path


def one(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)


def replace_region(text, start_marker, end_marker, replacement):
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    return text[:start] + replacement + text[end:]


# PresetManager: inject Clock/AlarmScheduler while keeping the public constructor stable.
p = Path("app/src/main/java/com/gree1d/reappzuku/manager/PresetManager.java")
text = p.read_text()
text = one(text, "import android.app.AlarmManager;\n", "", "preset AlarmManager import")
text = one(text, "import com.gree1d.reappzuku.core.ExactAlarmCapability;\n",
           "import com.gree1d.reappzuku.core.AlarmScheduler;\n"
           "import com.gree1d.reappzuku.core.Clock;\n"
           "import com.gree1d.reappzuku.core.ScheduleTime;\n",
           "preset facade imports")
text = one(text,
'''    private final Context context;
    private final SharedPreferences mainPrefs;

    public PresetManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainPrefs = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
''',
'''    private final Context context;
    private final SharedPreferences mainPrefs;
    private final Clock clock;
    private final AlarmScheduler alarmScheduler;

    public PresetManager(Context context) {
        this(context, Clock.SYSTEM, new AlarmScheduler(context));
    }

    PresetManager(Context context, Clock clock, AlarmScheduler alarmScheduler) {
        if (clock == null) throw new IllegalArgumentException("clock == null");
        if (alarmScheduler == null) throw new IllegalArgumentException("alarmScheduler == null");
        this.context = context.getApplicationContext();
        this.mainPrefs = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        this.clock = clock;
        this.alarmScheduler = alarmScheduler;
    }
''', "preset constructor")
text = replace_region(text,
    "    public void scheduleAlarms(PresetModel model) {",
    "    public void restoreAfterBoot()",
'''    public void scheduleAlarms(PresetModel model) {
        cancelAlarms(model.presetNumber);
        if (!alarmScheduler.isAvailable()) {
            AppDebugManager.e(Category.AUTO_KILL_PRESETS,
                    "PresetManager: scheduleAlarms #" + model.presetNumber + " — AlarmManager is unavailable");
            return;
        }
        long activateTime = nextAlarmTime(model.startHour, model.startMinute);
        long deactivateTime = nextAlarmTime(model.endHour, model.endMinute);
        AlarmScheduler.ScheduleResult activateResult = alarmScheduler.scheduleRtcWakeup(
                activateTime, buildPendingIntent(model.presetNumber, ACTION_PRESET_ACTIVATE), true);
        AlarmScheduler.ScheduleResult deactivateResult = alarmScheduler.scheduleRtcWakeup(
                deactivateTime, buildPendingIntent(model.presetNumber, ACTION_PRESET_DEACTIVATE), true);
        if (activateResult != AlarmScheduler.ScheduleResult.EXACT
                || deactivateResult != AlarmScheduler.ScheduleResult.EXACT) {
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
        if (model == null || !alarmScheduler.isAvailable()) return;
        boolean isActivate = ACTION_PRESET_ACTIVATE.equals(action);
        int hour = isActivate ? model.startHour : model.endHour;
        int minute = isActivate ? model.startMinute : model.endMinute;
        long next = ScheduleTime.nextDailyOccurrence(clock, hour, minute);
        AlarmScheduler.ScheduleResult result = alarmScheduler.scheduleRtcWakeup(
                next, buildPendingIntent(presetNumber, action), true);
        if (result != AlarmScheduler.ScheduleResult.EXACT) {
            AppDebugManager.w(Category.AUTO_KILL_PRESETS,
                    "PresetManager: rescheduleNextAlarm using best-effort timing for preset #" + presetNumber + " action=" + action);
        }
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: rescheduleNextAlarm #" + presetNumber + " action=" + action
                + " nextAt=" + hour + ":" + String.format("%02d", minute)
                + " ms=" + next);
    }

    public void cancelAlarms(int presetNumber) {
        if (!alarmScheduler.isAvailable()) {
            AppDebugManager.e(Category.AUTO_KILL_PRESETS,
                    "PresetManager: cancelAlarms #" + presetNumber + " — AlarmManager is unavailable");
            return;
        }
        alarmScheduler.cancel(buildPendingIntent(presetNumber, ACTION_PRESET_ACTIVATE));
        alarmScheduler.cancel(buildPendingIntent(presetNumber, ACTION_PRESET_DEACTIVATE));
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
        return ScheduleTime.nextDailyOccurrence(clock, hour, minute);
    }

    public boolean isCurrentlyActive(PresetModel model) {
        int nowMinutes = ScheduleTime.currentMinutesOfDay(clock);
        int startMinutes = model.getStartTotalMinutes();
        int endMinutes = model.getEndTotalMinutes();
        boolean active;
        if (endMinutes <= startMinutes) {
            active = nowMinutes >= startMinutes || nowMinutes < endMinutes;
        } else {
            active = nowMinutes >= startMinutes && nowMinutes < endMinutes;
        }
        AppDebugManager.d(Category.AUTO_KILL_PRESETS, "PresetManager: isCurrentlyActive #" + model.presetNumber
                + " | now=" + (nowMinutes / 60) + ":" + String.format("%02d", nowMinutes % 60)
                + " range=" + model.startHour + ":" + String.format("%02d", model.startMinute)
                + "–" + model.endHour + ":" + String.format("%02d", model.endMinute)
                + " crossesMidnight=" + (endMinutes <= startMinutes)
                + " → active=" + active);
        return active;
    }

''')
text = text.replace("import java.util.Calendar;\n", "")
p.write_text(text)


# RestrictionsScheduler: use the same Clock and AlarmScheduler boundaries for instance/static paths.
r = Path("app/src/main/java/com/gree1d/reappzuku/manager/RestrictionsScheduler.java")
text = r.read_text()
text = one(text, "import android.app.AlarmManager;\n", "", "restrictions AlarmManager import")
text = one(text, "import com.gree1d.reappzuku.core.ExactAlarmCapability;\n",
           "import com.gree1d.reappzuku.core.AlarmScheduler;\n"
           "import com.gree1d.reappzuku.core.Clock;\n"
           "import com.gree1d.reappzuku.core.ScheduleTime;\n",
           "restrictions facade imports")
text = one(text, "            this.id               = System.currentTimeMillis();",
           "            this.id               = Clock.SYSTEM.currentTimeMillis();", "schedule id clock")
text = one(text,
'''        public long nextStartMillis() {
            return nextEventMillis(startHour, startMinute);
        }


        public long nextEndMillis() {
            return nextEventMillis(endHour, endMinute);
        }

        private long nextEventMillis(int hour, int minute) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE,      minute);
            cal.set(Calendar.SECOND,      0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            return cal.getTimeInMillis();
        }
''',
'''        public long nextStartMillis() {
            return nextStartMillis(Clock.SYSTEM);
        }

        long nextStartMillis(Clock clock) {
            return ScheduleTime.nextDailyOccurrence(clock, startHour, startMinute);
        }


        public long nextEndMillis() {
            return nextEndMillis(Clock.SYSTEM);
        }

        long nextEndMillis(Clock clock) {
            return ScheduleTime.nextDailyOccurrence(clock, endHour, endMinute);
        }
''', "schedule entry time")
text = text.replace("            entry.timestamp   = System.currentTimeMillis();",
                    "            entry.timestamp   = Clock.SYSTEM.currentTimeMillis();")
text = one(text,
'''    private final Context              context;
    private final Handler              handler;
    private final ExecutorService      executor;
    private final ShellManager         shellManager;
    private final PrivilegedShell       privilegedShell;
    private final BackgroundAppManager backgroundAppManager;
    private final SleepModeManager     sleepModeManager;
    private final SharedPreferences    prefs;

    public RestrictionsScheduler(Context context,
                                 Handler handler,
                                 ExecutorService executor,
                                 ShellManager shellManager,
                                 BackgroundAppManager backgroundAppManager,
                                 SleepModeManager sleepModeManager) {
        this.context              = context;
        this.handler              = handler;
        this.executor             = executor;
        this.shellManager         = shellManager;
        this.privilegedShell       = new PrivilegedShell(shellManager);
        this.backgroundAppManager = backgroundAppManager;
        this.sleepModeManager     = sleepModeManager;
        this.prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        AppDebugManager.d(Category.RESTRICTIONS_SCHEDULER, "RestrictionsScheduler: initialized");
    }
''',
'''    private final Context              context;
    private final Handler              handler;
    private final ExecutorService      executor;
    private final ShellManager         shellManager;
    private final PrivilegedShell      privilegedShell;
    private final BackgroundAppManager backgroundAppManager;
    private final SleepModeManager     sleepModeManager;
    private final SharedPreferences    prefs;
    private final Clock                clock;
    private final AlarmScheduler       alarmScheduler;

    public RestrictionsScheduler(Context context,
                                 Handler handler,
                                 ExecutorService executor,
                                 ShellManager shellManager,
                                 BackgroundAppManager backgroundAppManager,
                                 SleepModeManager sleepModeManager) {
        this(context, handler, executor, shellManager, backgroundAppManager, sleepModeManager,
                Clock.SYSTEM, new AlarmScheduler(context));
    }

    RestrictionsScheduler(Context context,
                          Handler handler,
                          ExecutorService executor,
                          ShellManager shellManager,
                          BackgroundAppManager backgroundAppManager,
                          SleepModeManager sleepModeManager,
                          Clock clock,
                          AlarmScheduler alarmScheduler) {
        if (clock == null) throw new IllegalArgumentException("clock == null");
        if (alarmScheduler == null) throw new IllegalArgumentException("alarmScheduler == null");
        this.context              = context;
        this.handler              = handler;
        this.executor             = executor;
        this.shellManager         = shellManager;
        this.privilegedShell      = new PrivilegedShell(shellManager);
        this.backgroundAppManager = backgroundAppManager;
        this.sleepModeManager     = sleepModeManager;
        this.prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        this.clock = clock;
        this.alarmScheduler = alarmScheduler;
        AppDebugManager.d(Category.RESTRICTIONS_SCHEDULER, "RestrictionsScheduler: initialized");
    }
''', "restrictions constructor")
start = text.index("    public boolean isProtected(String packageName, int flag) {")
end = text.index("    public void scheduleNext()", start)
text = text[:start] + '''    public boolean isProtected(String packageName, int flag) {
        if (!getTempProtectedPackages().contains(packageName)) return false;
        int nowMinutes = ScheduleTime.currentMinutesOfDay(clock);
        int h = nowMinutes / 60;
        int m = nowMinutes % 60;
        for (ScheduleEntry e : getSchedules()) {
            if (e.packageName.equals(packageName) && e.isActiveNow(h, m)) {
                return (e.protectFlags & flag) != 0;
            }
        }
        return false;
    }


''' + text[end:]
start = text.index("    public void scheduleNext()")
end = text.index("    private PendingIntent getAlarmIntent()", start)
text = text[:start] + '''    public void scheduleNext() {
        List<ScheduleEntry> schedules = getSchedules();
        if (schedules.isEmpty()) {
            cancelAlarm();
            return;
        }

        long now = clock.currentTimeMillis();
        long nearest = Long.MAX_VALUE;
        int nowMinutes = ScheduleTime.currentMinutesOfDay(clock);
        int h = nowMinutes / 60;
        int m = nowMinutes % 60;

        for (ScheduleEntry e : schedules) {
            if (!e.enabled) continue;
            long candidate = e.isActiveNow(h, m) ? e.nextEndMillis(clock) : e.nextStartMillis(clock);
            if (candidate < nearest) nearest = candidate;
        }

        if (nearest == Long.MAX_VALUE || nearest <= now) {
            cancelAlarm();
            return;
        }

        AlarmScheduler.ScheduleResult result = alarmScheduler.scheduleRtcWakeup(nearest, getAlarmIntent(), true);
        if (result == AlarmScheduler.ScheduleResult.UNAVAILABLE) {
            AppDebugManager.w(Category.RESTRICTIONS_SCHEDULER,
                    "RestrictionsScheduler: AlarmManager unavailable");
            return;
        }
        if (result != AlarmScheduler.ScheduleResult.EXACT) {
            AppDebugManager.w(Category.RESTRICTIONS_SCHEDULER,
                    "RestrictionsScheduler: exact alarm permission unavailable; using best-effort timing");
        }

        AppDebugManager.d(Category.RESTRICTIONS_SCHEDULER,
                "RestrictionsScheduler: scheduleNext: alarm in " + ((nearest - now) / 1000 / 60) + " min");
    }


    public static void scheduleNextStatic(Context context) {
        scheduleNextStatic(context, Clock.SYSTEM, new AlarmScheduler(context));
    }

    static void scheduleNextStatic(Context context, Clock clock, AlarmScheduler alarmScheduler) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_SCHEDULES, null);
        if (json == null || json.isEmpty()) {
            AppDebugManager.d(Category.RESTRICTIONS_SCHEDULER, "RestrictionsScheduler: scheduleNextStatic: no schedules stored");
            return;
        }

        List<ScheduleEntry> schedules = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                schedules.add(ScheduleEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            AppDebugManager.e(Category.RESTRICTIONS_SCHEDULER, "RestrictionsScheduler: scheduleNextStatic: parse error", e);
            return;
        }

        long now = clock.currentTimeMillis();
        long nearest = Long.MAX_VALUE;
        int nowMinutes = ScheduleTime.currentMinutesOfDay(clock);
        int h = nowMinutes / 60;
        int m = nowMinutes % 60;

        for (ScheduleEntry e : schedules) {
            if (!e.enabled) continue;
            long candidate = e.isActiveNow(h, m) ? e.nextEndMillis(clock) : e.nextStartMillis(clock);
            if (candidate < nearest) nearest = candidate;
        }

        if (nearest == Long.MAX_VALUE || nearest <= now) return;

        AlarmScheduler.ScheduleResult result =
                alarmScheduler.scheduleRtcWakeup(nearest, getAlarmIntent(context), true);
        if (result == AlarmScheduler.ScheduleResult.UNAVAILABLE) {
            AppDebugManager.w(Category.RESTRICTIONS_SCHEDULER,
                    "RestrictionsScheduler: scheduleNextStatic: AlarmManager unavailable");
            return;
        }
        if (result != AlarmScheduler.ScheduleResult.EXACT) {
            AppDebugManager.w(Category.RESTRICTIONS_SCHEDULER,
                    "RestrictionsScheduler: scheduleNextStatic using best-effort timing");
        }
        AppDebugManager.d(Category.RESTRICTIONS_SCHEDULER,
                "RestrictionsScheduler: scheduleNextStatic: alarm in " + ((nearest - now) / 1000 / 60) + " min");
    }

    private void cancelAlarm() {
        alarmScheduler.cancel(getAlarmIntent(context));
    }

''' + text[end:]
start = text.index("    public void tick()")
tail = text[start:]
tick_old = '''            Calendar cal = Calendar.getInstance();
            int hour   = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);
'''
if tail.count(tick_old) != 1:
    raise SystemExit(f"tick clock block: expected 1, got {tail.count(tick_old)}")
tail = tail.replace(tick_old,
'''            int nowMinutes = ScheduleTime.currentMinutesOfDay(clock);
            int hour = nowMinutes / 60;
            int minute = nowMinutes % 60;
''', 1)
text = text[:start] + tail
text = text.replace("import java.util.Calendar;\n", "")
r.write_text(text)


# Roadmap: close source-level hardening/UX and record maintainability progress without overstating runtime proof.
road = Path("docs/ROADMAP.md")
text = road.read_text()
text = one(text,
    "- [ ] Introduce typed/validated privileged operations instead of ad-hoc shell command strings where practical.",
    "- [x] Route mutating package/component/PID operations through typed, validated `PrivilegedShell` commands and reject invalid persisted/input values before shell execution.",
    "roadmap privileged status")
parser_anchor = "- This is repeatable parser/source evidence, not a substitute for the still-open Android/OEM runtime probes.\n"
if "### Privileged shell evidence — 2026-09-05" not in text:
    text = one(text, parser_anchor, parser_anchor + '''

### Privileged shell evidence — 2026-09-05

- `PrivilegedShell` owns validated kill/force-stop, uninstall, AppOps, standby bucket, DeviceIdle whitelist, suspend/unsuspend, enable/disable, PID kill and explicit component-launch commands.
- Runs `33940964413` and `33941247796` passed unit tests, lint, AndroidTest compilation and debug APK build before integrating the major manager paths.
- Final strict run `33946348081` passed the same gates and required a repository-wide mutating-shell audit to return `NONE` outside `PrivilegedShell`, including the Quick Tile and Restrictions Watchdog paths.
- Normal read-only validation run `33946501074` then passed on the cleaned permanent source state.
- These are source/JVM/CI invariants; live Shizuku/root execution probes remain separate Android evidence.
''', "roadmap privileged evidence")
text = one(text,
    "- [ ] Explain blocking automation directly beside disabled App Behavior controls.",
    "- [x] Explain blocking automation directly beside disabled App Behavior controls, listing the active AutoKill/Smart Lifecycle/Sleep/Preset/Scheduler blockers from the central policy.",
    "roadmap blocker UX")
text = one(text,
    "- [ ] Split large managers behind testable facades (`PrivilegedShell`, `AlarmScheduler`, `ProtectionPolicy`, `BackupCodec`, `Clock`).",
    "- [~] Split large managers behind testable facades: `PrivilegedShell`, `AlarmScheduler`, `Clock`/`ScheduleTime` and central protection/background/parser policy boundaries are in place; `BackupCodec` remains to extract.",
    "roadmap maintainability status")
phase8_anchor = "- [~] Keep `CHECK_MATRIX.md` status/evidence current after every high-impact change (refreshed through 2026-09-05; ongoing discipline).\n"
if "### Maintainability evidence — 2026-09-05" not in text:
    text = one(text, phase8_anchor, phase8_anchor + '''

### Maintainability evidence — 2026-09-05

- App Behavior exposes the exact active continuity blockers from `BackgroundWorkPolicy` rather than duplicating feature-state logic in the UI.
- `Clock` plus pure `ScheduleTime` make daily scheduling deterministic under JVM tests, while `AlarmScheduler` centralizes AlarmManager availability and exact-vs-best-effort behavior.
- `PresetManager` and `RestrictionsScheduler` keep their existing public constructors but receive clock/alarm dependencies through internal injection points.
- `BackupCodec` is the remaining named facade before this roadmap item can be closed.
''', "roadmap maintainability evidence")
road.write_text(text)


# Matrix: refresh source evidence, deliberately leaving Android runtime RISK cells untouched.
matrix = Path("docs/CHECK_MATRIX.md")
text = matrix.read_text()
text = one(text,
'''- **CM-P2-02:** package identifiers imported from backups/presets and high-impact shell boundaries
  now pass a shared package-name validator; typed privileged operations remain a longer-term goal.''',
'''- **CM-P2-02:** mutating package/component/PID operations now route through `PrivilegedShell` with
  typed enums/validated identifiers. Strict run `33946348081` required the repository-wide raw
  mutating-shell audit to return `NONE` outside that boundary; Android execution evidence remains open.''',
    "matrix shell refresh")
text = one(text,
'''- **CM-P2-04:** fork-specific Smart Lifecycle, App Behavior, shortcut-security and accessibility
  strings are propagated to ES/RU/UK/ZH.''',
'''- **CM-P2-04:** fork-specific Smart Lifecycle, App Behavior, shortcut-security and accessibility
  strings are propagated to ES/RU/UK/ZH. Disabled App Behavior controls now also list the exact
  active continuity blockers from `BackgroundWorkPolicy`.''',
    "matrix UX refresh")
latest_anchor = "Latest assurance evidence:\n"
if "workflow run `33946348081`" not in text:
    text = one(text, latest_anchor, latest_anchor + '''- workflow run `33940964413`: typed `PrivilegedShell` integration for AutoKill/BackgroundAppManager passed unit, lint, AndroidTest compile and APK build;
- workflow run `33941247796`: lifecycle/scheduler `PrivilegedShell` integration passed the same gates;
- workflow run `33946348081`: App Behavior blocker policy/UI plus final strict raw mutating-shell audit (`NONE`) passed unit, lint, AndroidTest compile and APK build;
- workflow run `33946501074`: normal read-only validation passed on the cleaned permanent source state;
''', "matrix latest evidence")
# Add current maintainability status before historical finding section.
maint_anchor = "# High-priority findings\n"
if "**Maintainability status:**" not in text:
    text = one(text, maint_anchor,
'''**Maintainability status:** `PrivilegedShell` and parser/protection/background policy boundaries are now explicit. `Clock`/`ScheduleTime` and `AlarmScheduler` isolate scheduler time/alarm decisions; `BackupCodec` remains the named facade gap. This is source/testability progress, not Android runtime proof.

''' + maint_anchor, "matrix maintainability status")
matrix.write_text(text)
