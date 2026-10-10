package com.gree1d.reappzuku.manager;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;

import com.gree1d.reappzuku.core.AlarmScheduler;
import com.gree1d.reappzuku.core.Clock;
import com.gree1d.reappzuku.core.ScheduleTime;
import com.gree1d.reappzuku.core.PrivilegedShell;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.SchedulerLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.service.ShappkyService;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;


public class RestrictionsScheduler {


    public static final String ACTION_SCHEDULER_TICK = "SCHEDULER_TICK";
    /** Internal service action; never starts a service on its own. */
    public static final String ACTION_SCHEDULER_CLOCK_RECONCILE =
            "com.gree1d.reappzuku.SCHEDULER_CLOCK_RECONCILE";


    private static final int SCHEDULER_ALARM_REQUEST_CODE = 2001;


    public static final int MAX_SCHEDULES = 15;


    private static final String KEY_SCHEDULES      = "restrictions_schedules";
    private static final String KEY_TEMP_PROTECTED = "temp_protected_packages";


    public static final int PROTECT_AUTO_KILL       = 1;
    public static final int PROTECT_BG_RESTRICTIONS = 1 << 1;
    public static final int PROTECT_SLEEP_MODE      = 1 << 2;
    public static final int PROTECT_ALL             = PROTECT_AUTO_KILL | PROTECT_BG_RESTRICTIONS | PROTECT_SLEEP_MODE;


    public static final int ON_ACTIVATE_NOTHING  = 0;
    public static final int ON_ACTIVATE_ACTIVITY = 1;
    public static final int ON_ACTIVATE_SERVICE  = 2;
    public static final int ON_ACTIVATE_RECEIVER = 3;


    public static class ScheduleEntry {

        public long id;

        public String packageName;

        public int startHour;

        public int startMinute;

        public int endHour;

        public int endMinute;

        public int protectFlags;

        public int onActivateAction;

        public String componentName;

        public boolean enabled;

        public boolean setBucketActive;

        public ScheduleEntry() {
            this.id               = Clock.SYSTEM.currentTimeMillis();
            this.protectFlags     = PROTECT_ALL;
            this.onActivateAction = ON_ACTIVATE_NOTHING;
            this.enabled          = true;
            this.setBucketActive  = false;
        }


        public boolean isActiveNow(int currentHour, int currentMinute) {
            if (!enabled) return false;
            int now  = currentHour  * 60 + currentMinute;
            int from = startHour    * 60 + startMinute;
            int to   = endHour      * 60 + endMinute;
            if (from == to) return false;
            if (from < to)  return now >= from && now < to;
            return now >= from || now < to;
        }


        public long nextStartMillis() {
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

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("id",          id);
            obj.put("packageName", packageName);
            obj.put("startHour",   startHour);
            obj.put("startMin",    startMinute);
            obj.put("endHour",     endHour);
            obj.put("endMin",      endMinute);
            obj.put("flags",       protectFlags);
            obj.put("onActivate",     onActivateAction);
            obj.put("componentName",  componentName != null ? componentName : "");
            obj.put("enabled",        enabled);
            obj.put("setBucketActive", setBucketActive);
            return obj;
        }

        public static ScheduleEntry fromJson(JSONObject obj) throws JSONException {
            ScheduleEntry e = new ScheduleEntry();
            e.id               = obj.getLong("id");
            e.packageName      = obj.getString("packageName");
            e.startHour        = obj.getInt("startHour");
            e.startMinute      = obj.getInt("startMin");
            e.endHour          = obj.getInt("endHour");
            e.endMinute        = obj.getInt("endMin");
            e.protectFlags     = obj.getInt("flags");
            e.onActivateAction = obj.getInt("onActivate");
            String comp        = obj.optString("componentName", "");
            e.componentName    = comp.isEmpty() ? null : comp;
            e.enabled          = obj.optBoolean("enabled", true);
            e.setBucketActive  = obj.optBoolean("setBucketActive", false);
            return e;
        }
    }


    public static final class SchedulerLog {

        private static final int MAX_ENTRIES    = 200;
        private static final int MAX_DETAIL_LEN = 180;

        private SchedulerLog() {}


        public static void logLift(Context context, String packageName,
                                   String outcome, String componentName, boolean use24h) {
            String detail = componentName != null
                    ? "action=" + shortName(componentName)
                    : "action=none";
            append(context, "lift", packageName, outcome, detail);
        }


        private static String shortName(String componentName) {
            int dot = componentName.lastIndexOf('.');
            return dot >= 0 ? componentName.substring(dot + 1) : componentName;
        }


        public static void logRestore(Context context, String packageName,
                                      String outcome, boolean forceStop, boolean use24h) {
            String detail = "stop=" + (forceStop ? "force-stop" : "am-kill");
            append(context, "restore", packageName, outcome, detail);
        }

        public static String readDisplayText(Context context) {
            if (context == null) return context.getString(R.string.log_scheduler_empty);
            List<SchedulerLog.Entry> entries = readEntries(context);
            if (entries.isEmpty()) return context.getString(R.string.log_scheduler_empty);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(entries.get(i).toDisplayLine());
            }
            return sb.toString();
        }


        public static List<SchedulerLog.Entry> readEntries(Context context) {
            List<com.gree1d.reappzuku.db.SchedulerLog> rows =
                    AppDatabase.getInstance(context).schedulerLogDao().getRecent(MAX_ENTRIES);
            List<SchedulerLog.Entry> result = new ArrayList<>(rows.size());
            for (com.gree1d.reappzuku.db.SchedulerLog row : rows) {
                result.add(new Entry(
                        formatTimestamp(row.timestamp),
                        row.action      != null ? row.action      : "event",
                        row.packageName != null ? row.packageName : "-",
                        row.outcome     != null ? row.outcome     : "unknown",
                        row.detail      != null ? row.detail      : ""
                ));
            }
            return result;
        }

        public static void clear(Context context) {
            if (context == null) return;
            AppDatabase.getInstance(context).schedulerLogDao().clearAll();
        }

        private static void append(Context context, String action, String packageName,
                                   String outcome, String detail) {
            if (context == null) return;

            com.gree1d.reappzuku.db.SchedulerLog entry = new com.gree1d.reappzuku.db.SchedulerLog();
            entry.timestamp   = Clock.SYSTEM.currentTimeMillis();
            entry.action      = sanitize(action);
            entry.packageName = sanitize(packageName != null ? packageName : "-");
            entry.outcome     = sanitize(outcome     != null ? outcome     : "unknown");
            entry.detail      = sanitize(detail);

            com.gree1d.reappzuku.db.SchedulerLog.Dao dao =
                    AppDatabase.getInstance(context).schedulerLogDao();
            dao.insert(entry);


            int count = dao.getCount();
            if (count > MAX_ENTRIES) {
                dao.deleteOldest(count - MAX_ENTRIES);
            }
        }

        private static String formatTimestamp(long millis) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
        }

        private static String sanitize(String value) {
            if (value == null) return "";
            String s = value.replace('\r', ' ').replace('\n', ' ')
                            .replace('|', '/').replaceAll("\\s+", " ").trim();
            return s.length() > MAX_DETAIL_LEN ? s.substring(0, MAX_DETAIL_LEN - 3) + "..." : s;
        }


        public static final class Entry {
            public final String timestamp;
            public final String action;
            public final String packageName;
            public final String outcome;
            public final String detail;

            private Entry(String timestamp, String action, String packageName,
                          String outcome, String detail) {
                this.timestamp   = timestamp;
                this.action      = action;
                this.packageName = packageName;
                this.outcome     = outcome;
                this.detail      = detail;
            }

            public String toDisplayLine() {
                StringBuilder line = new StringBuilder()
                        .append(timestamp).append(" | ")
                        .append(action).append(" | ")
                        .append(packageName).append(" | ")
                        .append(outcome);
                if (!detail.isEmpty()) line.append(" | ").append(detail);
                return line.toString();
            }
        }
    }


    public static final class SchedulerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent serviceIntent = new Intent(context, ShappkyService.class);
            serviceIntent.setAction(ACTION_SCHEDULER_TICK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }


    private final Context              context;
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

    }


    public List<ScheduleEntry> getSchedules() {
        List<ScheduleEntry> list = new ArrayList<>();
        String json = prefs.getString(KEY_SCHEDULES, null);
        if (json == null || json.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(ScheduleEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {

        }
        return list;
    }

    public void saveSchedules(List<ScheduleEntry> schedules) {
        JSONArray arr = new JSONArray();
        for (ScheduleEntry entry : schedules) {
            try { arr.put(entry.toJson()); }
            catch (JSONException ignored) { }
        }
        prefs.edit().putString(KEY_SCHEDULES, arr.toString()).apply();
    }


    public boolean addSchedule(ScheduleEntry entry) {
        List<ScheduleEntry> list = getSchedules();
        if (list.size() >= MAX_SCHEDULES) {

            return false;
        }
        list.add(entry);
        saveSchedules(list);
        scheduleNext();
        return true;
    }


    public boolean updateSchedule(ScheduleEntry updated) {
        List<ScheduleEntry> list = getSchedules();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == updated.id) {
                list.set(i, updated);
                saveSchedules(list);
                scheduleNext();

                return true;
            }
        }

        return false;
    }


    public void removeSchedule(long id) {
        List<ScheduleEntry> list = getSchedules();
        list.removeIf(e -> e.id == id);
        saveSchedules(list);
        scheduleNext();

    }


    public Set<String> getTempProtectedPackages() {
        return new HashSet<>(prefs.getStringSet(KEY_TEMP_PROTECTED, new HashSet<>()));
    }


    public boolean isProtected(String packageName, int flag) {
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


    public void scheduleNext() {
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

            return;
        }

    }


    /**
     * Read-only clock-change detection. The exported system receiver must never
     * mutate a protection marker or trigger privileged actions before a ready
     * ShappkyService handles a verified transition.
     */
    public static boolean needsClockReconciliation(Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        String json = preferences.getString(KEY_SCHEDULES, null);
        List<ScheduleEntry> schedules = new ArrayList<>();
        try {
            if (json != null && !json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                if (array.length() > MAX_SCHEDULES) return false;
                for (int i = 0; i < array.length(); i++) {
                    schedules.add(ScheduleEntry.fromJson(array.getJSONObject(i)));                }
            }
            Set<String> previous = new HashSet<>(
                    preferences.getStringSet(KEY_TEMP_PROTECTED, new HashSet<>()));
            return RestrictionsClockReconciliationPolicy.needsReconciliation(
                    schedules, previous, ScheduleTime.currentMinutesOfDay(Clock.SYSTEM));
        } catch (RuntimeException | JSONException invalid) {
            // Malformed stored schedules are not a basis for privileged mutation.
            return false;
        }
    }

    public static void scheduleNextStatic(Context context) {
        scheduleNextStatic(context, Clock.SYSTEM, new AlarmScheduler(context));
    }

    static void scheduleNextStatic(Context context, Clock clock, AlarmScheduler alarmScheduler) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_SCHEDULES, null);
        if (json == null || json.isEmpty()) {
            alarmScheduler.cancel(getAlarmIntent(context));
            return;
        }

        List<ScheduleEntry> schedules = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() > MAX_SCHEDULES) {
                alarmScheduler.cancel(getAlarmIntent(context));
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                schedules.add(ScheduleEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            alarmScheduler.cancel(getAlarmIntent(context));
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
            alarmScheduler.cancel(getAlarmIntent(context));
            return;
        }

        AlarmScheduler.ScheduleResult result =
                alarmScheduler.scheduleRtcWakeup(nearest, getAlarmIntent(context), true);
        if (result == AlarmScheduler.ScheduleResult.UNAVAILABLE) {

            return;
        }
    }

    private void cancelAlarm() {
        alarmScheduler.cancel(getAlarmIntent(context));
    }

    private PendingIntent getAlarmIntent() {
        return getAlarmIntent(context);
    }

    private static PendingIntent getAlarmIntent(Context context) {
        Intent intent = new Intent(context, SchedulerReceiver.class);
        intent.setAction(ACTION_SCHEDULER_TICK);
        return PendingIntent.getBroadcast(
                context,
                SCHEDULER_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }


    /**
     * A time/timezone jump may skip a normal schedule boundary. Recover only the
     * configured protection state through a shell-ready, already-running service.
     * Deliberately do not launch user components or force-stop apps merely because
     * the system clock changed. Unsuccessful privileged operations leave their
     * previous durable marker untouched for later retry.
     */
    public void reconcileAfterClockChange() {
        executor.execute(() -> {
            try {
                int minute = ScheduleTime.currentMinutesOfDay(clock);
                int hour = minute / 60;
                int minuteOfHour = minute % 60;
                List<ScheduleEntry> schedules = getSchedules();
                Set<String> previous = getTempProtectedPackages();
                boolean hadPreviousMarker = prefs.contains(KEY_TEMP_PROTECTED);
                Set<String> desired =
                        RestrictionsClockReconciliationPolicy.expectedPackages(schedules, minute);
                if (desired.equals(previous)) return;

                Set<String> completed = new HashSet<>(previous);
                boolean use24h = android.text.format.DateFormat.is24HourFormat(context);
                for (String pkg : diff(desired, previous)) {
                    ScheduleEntry entry = findActiveEntry(schedules, pkg, hour, minuteOfHour);
                    if (entry == null) continue;
                    boolean successful = true;
                    try {
                        String liftOutcome = null;
                        if ((entry.protectFlags & PROTECT_BG_RESTRICTIONS) != 0) {
                            liftOutcome = backgroundAppManager.liftRestrictionsForScheduler(pkg);
                            successful = "ok".equals(liftOutcome) || "skipped".equals(liftOutcome);
                        }
                        if (successful && (entry.protectFlags & PROTECT_SLEEP_MODE) != 0
                                && sleepModeManager.getFreezeType(pkg)
                                == SleepModeManager.FreezeType.TIMER) {
                            SleepModeManager.FreezeMethod method =
                                    sleepModeManager.getFreezeMethod(pkg);
                            PrivilegedShell.PackageStateAction action =
                                    method == SleepModeManager.FreezeMethod.SUSPEND
                                            ? PrivilegedShell.PackageStateAction.UNSUSPEND
                                            : PrivilegedShell.PackageStateAction.ENABLE;
                            successful = privilegedShell.applyPackageStateBlocking(pkg, action);
                        }
                        if (successful && entry.setBucketActive) {
                            successful = setAppBucketActive(pkg);
                        }
                        if (liftOutcome != null) {
                            SchedulerLog.logLift(context, pkg,
                                    successful ? liftOutcome : "partial", null, use24h);
                        }
                    } catch (RuntimeException failed) {
                        successful = false;
                    }
                    if (successful) completed.add(pkg);
                }

                for (String pkg : diff(previous, desired)) {
                    ScheduleEntry entry = findEntryForPackage(schedules, pkg);
                    // If the old schedule has been removed, there is no reliable
                    // restriction strength to restore; keep the recovery marker.
                    if (entry == null) continue;
                    boolean successful = true;
                    try {
                        if ((entry.protectFlags & PROTECT_BG_RESTRICTIONS) != 0) {
                            String outcome = backgroundAppManager.restoreRestrictionsForScheduler(pkg);
                            successful = "ok".equals(outcome) || "skipped".equals(outcome);
                            if (successful && !entry.setBucketActive) {
                                successful = restoreRestrictionBucket(pkg);
                            }
                            SchedulerLog.logRestore(context, pkg,
                                    successful ? outcome : "partial", false, use24h);
                        }
                        // Never call stopApp(pkg) on a manual clock jump.
                    } catch (RuntimeException failed) {
                        successful = false;
                    }
                    if (successful) completed.remove(pkg);
                }

                if (!completed.equals(previous)) {
                    // Persist only after the requested operations succeed.
                    // Android may change the in-memory map even on commit(false).
                    if (!prefs.edit().putStringSet(KEY_TEMP_PROTECTED, completed).commit()) {
                        SharedPreferences.Editor rollback = prefs.edit();
                        if (hadPreviousMarker) {
                            rollback.putStringSet(KEY_TEMP_PROTECTED, previous);
                        } else {
                            rollback.remove(KEY_TEMP_PROTECTED);
                        }
                        // Best effort: retain the previous visible state for a
                        // future reconciliation rather than claiming success.
                        rollback.commit();
                    }
                }
            } finally {
                scheduleNext();
            }
        });
    }

    /**
     * Normal RTC-boundary execution. Never claim an app protection transition
     * before all corresponding privileged operations have succeeded. A failed
     * package is left at its previous durable marker and can be retried by a
     * later reconciliation; one failure must not abort other packages.
     */
    public void tick() {
        executor.execute(() -> {
            try {
                int nowMinutes = ScheduleTime.currentMinutesOfDay(clock);
                int hour = nowMinutes / 60;
                int minute = nowMinutes % 60;
                List<ScheduleEntry> schedules = getSchedules();
                Set<String> wasProtected = getTempProtectedPackages();
                boolean hadPreviousMarker = prefs.contains(KEY_TEMP_PROTECTED);
                Set<String> shouldBeProtected =
                        RestrictionsClockReconciliationPolicy.expectedPackages(schedules, nowMinutes);
                Set<String> succeededActivations = new HashSet<>();
                Set<String> succeededDeactivations = new HashSet<>();
                boolean use24h = android.text.format.DateFormat.is24HourFormat(context);

                for (String pkg : diff(shouldBeProtected, wasProtected)) {
                    ScheduleEntry entry = findActiveEntry(schedules, pkg, hour, minute);
                    if (entry == null) continue;

                    boolean successful = true;
                    String outcome = null;
                    try {
                        if ((entry.protectFlags & PROTECT_BG_RESTRICTIONS) != 0) {
                            outcome = backgroundAppManager.liftRestrictionsForScheduler(pkg);
                            successful = "ok".equals(outcome) || "skipped".equals(outcome);
                        }
                        if (successful && (entry.protectFlags & PROTECT_SLEEP_MODE) != 0
                                && sleepModeManager.getFreezeType(pkg)
                                == SleepModeManager.FreezeType.TIMER) {
                            SleepModeManager.FreezeMethod method =
                                    sleepModeManager.getFreezeMethod(pkg);
                            PrivilegedShell.PackageStateAction action =
                                    method == SleepModeManager.FreezeMethod.SUSPEND
                                            ? PrivilegedShell.PackageStateAction.UNSUSPEND
                                            : PrivilegedShell.PackageStateAction.ENABLE;
                            successful = privilegedShell.applyPackageStateBlocking(pkg, action);
                        }
                        if (successful && entry.setBucketActive) {
                            successful = setAppBucketActive(pkg);
                        }
                        if (successful && entry.onActivateAction != ON_ACTIVATE_NOTHING
                                && entry.componentName != null) {
                            final String component = entry.componentName;
                            final int action = entry.onActivateAction;
                            successful = handler.postDelayed(
                                    () -> launchComponent(component, action), 500);
                        }
                    } catch (RuntimeException failure) {
                        successful = false;
                    }
                    if (outcome != null) {
                        try {
                            SchedulerLog.logLift(context, pkg,
                                    successful ? outcome : "partial", entry.componentName, use24h);
                        } catch (RuntimeException ignored) {
                            // Logging must not change the outcome of a completed action.
                        }
                    }
                    if (successful) succeededActivations.add(pkg);
                }

                for (String pkg : diff(wasProtected, shouldBeProtected)) {
                    ScheduleEntry entry = findEntryForPackage(schedules, pkg);
                    // The former policy for a removed schedule is unavailable.
                    // Do not guess its restriction strength or force-stop the app.
                    if (entry == null) continue;

                    boolean successful = true;
                    String outcome = null;
                    boolean forceStop = isForceStopMode();
                    try {
                        if ((entry.protectFlags & PROTECT_BG_RESTRICTIONS) != 0) {
                            outcome = backgroundAppManager.restoreRestrictionsForScheduler(pkg);
                            successful = "ok".equals(outcome) || "skipped".equals(outcome);
                            if (successful && !entry.setBucketActive) {
                                successful = restoreRestrictionBucket(pkg);
                            }
                        } else if ((entry.protectFlags & PROTECT_AUTO_KILL) != 0) {
                            successful = stopApp(pkg, forceStop);
                            outcome = successful ? "ok" : "error";
                        }
                    } catch (RuntimeException failure) {
                        successful = false;
                    }
                    if (outcome != null) {
                        try {
                            SchedulerLog.logRestore(context, pkg,
                                    successful ? outcome : "partial", forceStop, use24h);
                        } catch (RuntimeException ignored) {
                            // Logging must not misreport the real transition state.
                        }
                    }
                    if (successful) succeededDeactivations.add(pkg);
                }

                Set<String> completed = RestrictionsTickMarkerPolicy.afterCompletedTransitions(
                        wasProtected, succeededActivations, succeededDeactivations);
                if (!completed.equals(wasProtected)) {
                    if (!prefs.edit().putStringSet(KEY_TEMP_PROTECTED, completed).commit()) {
                        SharedPreferences.Editor rollback = prefs.edit();
                        if (hadPreviousMarker) {
                            rollback.putStringSet(KEY_TEMP_PROTECTED, wasProtected);
                        } else {
                            rollback.remove(KEY_TEMP_PROTECTED);
                        }
                        rollback.commit();
                    }
                }
            } finally {
                // Keep future boundaries armed even if a package operation fails.
                scheduleNext();
            }
        });
    }


    private boolean setAppBucketActive(String packageName) {
        try {
            return privilegedShell.setStandbyBucket(
                    packageName, PrivilegedShell.StandbyBucket.ACTIVE).succeeded();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean restoreRestrictionBucket(String packageName) {
        BackgroundAppManager.RestrictionType type = backgroundAppManager.getRestrictionType(packageName);
        int bucket;
        switch (type) {
            case HARD:
                bucket = 45;
                break;
            case MEDIUM:
                bucket = 40;
                break;
            case MANUAL:
                bucket = backgroundAppManager.getManualBucket(packageName);
                break;
            default:
                return true;
        }
        if (bucket == 0) return true;
        return privilegedShell.setStandbyBucket(
                packageName, PrivilegedShell.StandbyBucket.fromLegacyValue(bucket)).succeeded();
    }

    private boolean stopApp(String packageName, boolean forceStop) {
        PrivilegedShell.KillMode mode = forceStop
                ? PrivilegedShell.KillMode.FORCE_STOP
                : PrivilegedShell.KillMode.KILL;
        return privilegedShell.stopPackage(packageName, mode).succeeded();
    }


    private boolean isForceStopMode() {
        return prefs.getInt(KEY_AUTO_KILL_TYPE, 0) == 0;
    }


    private void launchComponent(String componentName, int type) {
        PrivilegedShell.ComponentAction action;
        switch (type) {
            case ON_ACTIVATE_ACTIVITY:
                action = PrivilegedShell.ComponentAction.START_ACTIVITY;
                break;
            case ON_ACTIVATE_SERVICE:
                action = PrivilegedShell.ComponentAction.START_FOREGROUND_SERVICE;
                break;
            case ON_ACTIVATE_RECEIVER:
                action = PrivilegedShell.ComponentAction.BROADCAST;
                break;
            default:

                return;
        }
        try {
            privilegedShell.launchComponent(componentName, action);
        } catch (IllegalArgumentException ignored) {
        }
    }


    public String getActivationLabel(Context context, ScheduleEntry entry) {
        if (entry.onActivateAction == ON_ACTIVATE_NOTHING || entry.componentName == null) {
            return context.getString(R.string.scheduler_action_none);
        }
        int dot = entry.componentName.lastIndexOf('.');
        String shortName = dot >= 0
                ? entry.componentName.substring(dot + 1)
                : entry.componentName;
        return context.getString(R.string.scheduler_action_launch_main, shortName);
    }


    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.removeAll(b);
        return result;
    }

    private ScheduleEntry findActiveEntry(List<ScheduleEntry> list, String pkg,
                                          int hour, int minute) {
        for (ScheduleEntry e : list) {
            if (e.packageName.equals(pkg) && e.isActiveNow(hour, minute)) return e;
        }
        return null;
    }

    private ScheduleEntry findEntryForPackage(List<ScheduleEntry> list, String pkg) {
        for (ScheduleEntry e : list) {
            if (e.packageName.equals(pkg)) return e;
        }
        return null;
    }

}
