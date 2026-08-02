package com.gree1d.reappzuku.ui;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.BaseActivity;
import com.gree1d.reappzuku.core.App;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.manager.RestrictionsScheduler;
import com.gree1d.reappzuku.utils.BackgroundRestrictionLog;
import com.gree1d.reappzuku.utils.SleepModeLogManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import static com.gree1d.reappzuku.core.AppConstants.*;
import static com.gree1d.reappzuku.core.PreferenceKeys.*;

public class LogDetailActivity extends BaseActivity {

    private static final String FILE = "LogDetailActivity";

    public static final String EXTRA_LOG_TYPE = "extra_log_type";

    public enum LogType {
        AUTO_KILL,
        TOP_OFFENDERS,
        BACKGROUND_RESTRICTIONS,
        SLEEP_MODE,
        SCHEDULER
    }

    private static final int TOP_OFFENDERS_LIMIT = 50;
    private static final long[] TOP_OFFENDER_FILTER_WINDOWS_MS = {
            STATS_HISTORY_DURATION_MS,
            24 * 60 * 60 * 1000L,
            7 * 24 * 60 * 60 * 1000L,
            -1L
    };

    Handler handler;
    ExecutorService executor;

    private ShellManager shellManager;
    private BackgroundAppManager appManager;

    private LogType logType;
    private String[] topOffenderFilterLabels;

    private Toolbar toolbar;
    private View filterLayout;
    private AutoCompleteTextView filterSpinner;
    private TextView summaryText;
    private ProgressBar loading;
    private ListView listView;
    private TextView emptyView;

    private SettingsSurfaceAdapter adapter;
    private MenuItem clearMenuItem;

    private final int[] currentTopOffenderFilterIndex = {0};
    private final List<KillHistoryEntry> killHistoryEntries = new ArrayList<>();
    private final List<TopOffender> topOffenderEntries = new ArrayList<>();
    private final List<BackgroundRestrictionLog.LogEntry> restrictionLogEntries = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_detail);
        AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": onCreate");

        logType = (LogType) getIntent().getSerializableExtra(EXTRA_LOG_TYPE);
        if (logType == null) logType = LogType.AUTO_KILL;

        topOffenderFilterLabels = getResources().getStringArray(R.array.settings_top_offender_filter_labels);

        App app = (App) getApplication();
        handler  = app.getSharedHandler();
        executor = app.getSharedExecutor();
        shellManager = app.getShellManager();
        appManager   = new BackgroundAppManager(getApplicationContext(), handler, executor, app.getShellExecutor(), shellManager);

        toolbar       = findViewById(R.id.log_detail_toolbar);
        filterLayout  = findViewById(R.id.log_detail_filter_layout);
        filterSpinner = findViewById(R.id.log_detail_filter);
        summaryText   = findViewById(R.id.log_detail_summary);
        loading       = findViewById(R.id.log_detail_loading);
        listView      = findViewById(R.id.log_detail_list);
        emptyView     = findViewById(R.id.log_detail_empty);

        adapter = new SettingsSurfaceAdapter();
        listView.setAdapter(adapter);
        listView.setEmptyView(emptyView);

        setupToolbar();

        switch (logType) {
            case AUTO_KILL:               setupAutoKill();               break;
            case TOP_OFFENDERS:           setupTopOffenders();           break;
            case BACKGROUND_RESTRICTIONS: setupBackgroundRestrictions(); break;
            case SLEEP_MODE:              setupSleepMode();              break;
            case SCHEDULER:               setupScheduler();              break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": onDestroy");
    }

    android.content.SharedPreferences prefs() {
        return sharedPreferences;
    }


    private void setupToolbar() {
        toolbar.setTitle(titleForLogType(logType));
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_log_detail);
        clearMenuItem = toolbar.getMenu().findItem(R.id.action_log_detail_clear);
        if (clearMenuItem != null && logType == LogType.TOP_OFFENDERS) {
            clearMenuItem.setTitle(R.string.stats_top_offenders_reset);
        }
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_log_detail_clear) {
                onClearClicked();
                return true;
            }
            return false;
        });

        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent == ACCENT_CUSTOM) {
            int customColor = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
            int onColor = sharedPreferences.getInt(KEY_ACCENT_ON_COLOR, ACCENT_ON_WHITE) == ACCENT_ON_BLACK
                    ? Color.BLACK : Color.WHITE;
            toolbar.setBackgroundColor(customColor);
            toolbar.setTitleTextColor(onColor);
            if (toolbar.getNavigationIcon() != null)
                androidx.core.graphics.drawable.DrawableCompat.setTint(toolbar.getNavigationIcon(), onColor);
        } else if (accent == ACCENT_SYSTEM) {
            toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.toolbar_navy));
            toolbar.setTitleTextColor(Color.WHITE);
        } else {
            boolean isLightAccent = (accent == ACCENT_APRICOT || accent == ACCENT_SKY ||
                    accent == ACCENT_PAPAYA || accent == ACCENT_LAVENDER ||
                    accent == ACCENT_MINT || accent == ACCENT_PEACH ||
                    accent == ACCENT_POWDER || accent == ACCENT_FOG);
            toolbar.setTitleTextColor(isLightAccent ? Color.BLACK : Color.WHITE);
        }
    }

    private String titleForLogType(LogType type) {
        switch (type) {
            case AUTO_KILL:               return getString(R.string.settings_kill_history_title);
            case TOP_OFFENDERS:           return getString(R.string.settings_top_offenders_title);
            case BACKGROUND_RESTRICTIONS: return getString(R.string.settings_restriction_log_title);
            case SLEEP_MODE:              return getString(R.string.log_sleep_mode_title);
            case SCHEDULER:               return getString(R.string.log_scheduler_title);
            default:                      return "";
        }
    }

    private void onClearClicked() {
        switch (logType) {
            case AUTO_KILL:               clearAutoKill();               break;
            case TOP_OFFENDERS:           clearTopOffenders();           break;
            case BACKGROUND_RESTRICTIONS: clearBackgroundRestrictions(); break;
            case SLEEP_MODE:              clearSleepMode();              break;
            case SCHEDULER:               clearScheduler();              break;
        }
    }


    // ---------- Auto-Kill (kill history) ----------

    private void setupAutoKill() {
        emptyView.setText(getString(R.string.stats_no_activity_12h));
        loading.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        summaryText.setText(getString(R.string.stats_loading));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < killHistoryEntries.size()) {
                KillHistoryEntry entry = killHistoryEntries.get(position);
                showLogAppOptions(entry.appName, entry.packageName, STATS_HISTORY_DURATION_MS);
            }
        });

        loadAutoKill();
    }

    private void loadAutoKill() {
        executor.execute(() -> {
            long twelveHoursAgo = System.currentTimeMillis() - STATS_HISTORY_DURATION_MS;
            com.gree1d.reappzuku.db.AppStatsDao appStatsDao = com.gree1d.reappzuku.db.AppDatabase
                    .getInstance(this).appStatsDao();
            List<com.gree1d.reappzuku.db.AppStatsAggregate> statsList =
                    appStatsDao.getAllStatsSince(twelveHoursAgo);
            AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": loadAutoKill loaded " + statsList.size()
                    + " stats rows since 12h ago");

            List<KillHistoryEntry> historyEntries = new ArrayList<>();
            java.util.Map<String, String> pendingNameUpdates = new java.util.HashMap<>();
            int totalKills = 0;
            int totalRelaunches = 0;
            long totalRecoveredKb = 0;
            java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);

            for (com.gree1d.reappzuku.db.AppStatsAggregate stats : statsList) {
                if (stats == null || stats.packageName == null) continue;
                if (stats.killCount <= 0 && stats.relaunchCount <= 0) continue;

                List<String> detailParts = new ArrayList<>();
                if (stats.killCount > 0) {
                    detailParts.add(getString(R.string.stats_kill_detail, stats.killCount));
                }
                if (stats.relaunchCount > 0) {
                    String relaunchDetail = getString(R.string.stats_relaunch_detail, stats.relaunchCount);
                    if (stats.lastRelaunchTime > 0) {
                        relaunchDetail += getString(R.string.stats_last_relaunch_time,
                                timeFormat.format(new java.util.Date(stats.lastRelaunchTime)));
                    }
                    detailParts.add(relaunchDetail);
                }
                if (stats.totalRecoveredKb > 0) {
                    detailParts.add(getString(R.string.stats_recovered_ram,
                            formatRecoveredSize(stats.totalRecoveredKb)));
                }

                long lastEventTime = Math.max(stats.lastKillTime, stats.lastRelaunchTime);
                String badge = lastEventTime > 0 ? timeFormat.format(new java.util.Date(lastEventTime)) : "";
                historyEntries.add(new KillHistoryEntry(
                        resolveAggregateAppName(stats, pendingNameUpdates),
                        stats.packageName,
                        String.join(" | ", detailParts),
                        badge,
                        lastEventTime,
                        stats.lastKillSource));
                totalKills += stats.killCount;
                totalRelaunches += stats.relaunchCount;
                totalRecoveredKb += stats.totalRecoveredKb;
            }

            if (!pendingNameUpdates.isEmpty()) {
                appStatsDao.updateAppNames(pendingNameUpdates);
            }

            Collections.sort(historyEntries, (a, b) -> Long.compare(b.lastEventTime, a.lastEventTime));
            List<SettingsSurfaceRow> rows = buildKillHistoryRows(historyEntries);
            String summary = getString(R.string.stats_summary_12h,
                    rows.size(), totalKills, totalRelaunches, formatRecoveredSize(totalRecoveredKb));

            handler.post(() -> {
                if (isFinishingOrDestroyed()) return;
                killHistoryEntries.clear();
                killHistoryEntries.addAll(historyEntries);
                adapter.setItems(rows);
                summaryText.setText(summary);
                loading.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void clearAutoKill() {
        AppDebugManager.i(Category.STATISTICS_PAGE, FILE + ": kill history stats cleared by user");
        long sinceTime = System.currentTimeMillis() - STATS_HISTORY_DURATION_MS;
        executor.execute(() -> {
            com.gree1d.reappzuku.db.AppDatabase.getInstance(this).appStatsDao().deleteStatsSince(sinceTime);
            handler.post(this::loadAutoKill);
        });
    }


    // ---------- Top Offenders ----------

    private void setupTopOffenders() {
        emptyView.setText(getString(R.string.stats_top_offenders_empty));
        filterLayout.setVisibility(View.VISIBLE);

        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, topOffenderFilterLabels);
        filterSpinner.setAdapter(filterAdapter);
        filterSpinner.setText(topOffenderFilterLabels[0], false);
        filterSpinner.setOnItemClickListener((parent, view, position, id) -> {
            currentTopOffenderFilterIndex[0] = position;
            filterSpinner.setText(topOffenderFilterLabels[position], false);
            loadTopOffenders(position);
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < topOffenderEntries.size()) {
                TopOffender o = topOffenderEntries.get(position);
                showLogAppOptions(o.appName, o.packageName,
                        TOP_OFFENDER_FILTER_WINDOWS_MS[currentTopOffenderFilterIndex[0]]);
            }
        });

        loadTopOffenders(0);
    }

    private void loadTopOffenders(int filterIndex) {
        if (filterIndex < 0 || filterIndex >= TOP_OFFENDER_FILTER_WINDOWS_MS.length) filterIndex = 0;
        final int selectedFilterIndex = filterIndex;

        loading.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        summaryText.setText(getString(R.string.stats_loading));

        executor.execute(() -> {
            long windowMs = TOP_OFFENDER_FILTER_WINDOWS_MS[selectedFilterIndex];
            com.gree1d.reappzuku.db.AppStatsDao appStatsDao =
                    com.gree1d.reappzuku.db.AppDatabase.getInstance(this).appStatsDao();
            List<com.gree1d.reappzuku.db.AppStatsAggregate> stats;
            if (windowMs > 0) {
                stats = appStatsDao.getAllStatsSince(System.currentTimeMillis() - windowMs);
            } else {
                stats = appStatsDao.getAllStats();
            }

            List<TopOffender> offenders = buildTopOffenders(stats, appStatsDao);
            AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": loadTopOffenders filter=" + selectedFilterIndex
                    + " loaded " + offenders.size() + " offenders");

            int totalKills = 0, totalRelaunches = 0;
            long totalRecoveredKb = 0;
            for (TopOffender o : offenders) {
                totalKills      += o.killCount;
                totalRelaunches += o.relaunchCount;
                totalRecoveredKb += o.recoveredKb;
            }

            String summary = getString(R.string.stats_top_offenders_summary,
                    topOffenderFilterLabels[selectedFilterIndex],
                    offenders.size(), totalKills, totalRelaunches,
                    formatRecoveredSize(totalRecoveredKb));

            handler.post(() -> {
                if (isFinishingOrDestroyed()) return;
                topOffenderEntries.clear();
                topOffenderEntries.addAll(offenders);
                adapter.setItems(buildTopOffenderRows(offenders));
                summaryText.setText(summary);
                loading.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(offenders.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void clearTopOffenders() {
        AppDebugManager.i(Category.STATISTICS_PAGE, FILE + ": top offenders stats cleared by user");
        int filterIndex = currentTopOffenderFilterIndex[0];
        long windowMs = TOP_OFFENDER_FILTER_WINDOWS_MS[filterIndex];
        long sinceTime = windowMs > 0 ? System.currentTimeMillis() - windowMs : 0L;
        executor.execute(() -> {
            com.gree1d.reappzuku.db.AppStatsDao dao =
                    com.gree1d.reappzuku.db.AppDatabase.getInstance(this).appStatsDao();
            if (sinceTime > 0) dao.deleteStatsSince(sinceTime);
            else               dao.deleteAll();
            handler.post(() -> loadTopOffenders(filterIndex));
        });
    }

    private List<TopOffender> buildTopOffenders(List<com.gree1d.reappzuku.db.AppStatsAggregate> statsList,
                                                 com.gree1d.reappzuku.db.AppStatsDao appStatsDao) {
        List<TopOffender> offenders = new ArrayList<>();
        java.util.Map<String, String> pendingNameUpdates = new java.util.HashMap<>();
        for (com.gree1d.reappzuku.db.AppStatsAggregate stats : statsList) {
            if (stats == null || stats.packageName == null) continue;
            if (stats.killCount <= 0 && stats.relaunchCount <= 0 && stats.totalRecoveredKb <= 0) continue;
            String appName = resolveAggregateAppName(stats, pendingNameUpdates);
            double score = (stats.killCount * 1.0) + (stats.relaunchCount * 2.0) + (stats.totalRecoveredKb / 102400.0);
            offenders.add(new TopOffender(appName, stats.packageName, stats.killCount,
                    stats.relaunchCount, stats.totalRecoveredKb, score));
        }
        if (!pendingNameUpdates.isEmpty()) {
            appStatsDao.updateAppNames(pendingNameUpdates);
        }
        Collections.sort(offenders, (a, b) -> {
            int c = Double.compare(b.score, a.score);
            if (c != 0) return c;
            c = Integer.compare(b.killCount, a.killCount);
            if (c != 0) return c;
            c = Integer.compare(b.relaunchCount, a.relaunchCount);
            if (c != 0) return c;
            return Long.compare(b.recoveredKb, a.recoveredKb);
        });
        return offenders.size() > TOP_OFFENDERS_LIMIT
                ? new ArrayList<>(offenders.subList(0, TOP_OFFENDERS_LIMIT))
                : offenders;
    }

    private List<SettingsSurfaceRow> buildTopOffenderRows(List<TopOffender> offenders) {
        List<SettingsSurfaceRow> rows = new ArrayList<>();
        for (int i = 0; i < offenders.size(); i++) {
            TopOffender o = offenders.get(i);
            rows.add(new SettingsSurfaceRow("#" + (i + 1), o.appName, o.packageName,
                    getString(R.string.stats_offender_metrics, o.killCount, o.relaunchCount,
                            formatRecoveredSize(o.recoveredKb)),
                    getString(R.string.stats_offender_score,
                            String.format(Locale.US, "%.1f", o.score)),
                    o.packageName));
        }
        return rows;
    }


    // ---------- Background Restrictions ----------

    private void setupBackgroundRestrictions() {
        emptyView.setText(getString(R.string.settings_restriction_log_empty));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < restrictionLogEntries.size()) {
                showRestrictionLogEntryDetails(restrictionLogEntries.get(position));
            }
        });
        loading.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        summaryText.setText(getString(R.string.stats_loading));
        loadBackgroundRestrictions();
    }

    private void loadBackgroundRestrictions() {
        executor.execute(() -> {
            List<BackgroundRestrictionLog.LogEntry> entries = BackgroundRestrictionLog.readEntries(this);
            AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": restriction log loaded " + entries.size() + " entries");
            List<SettingsSurfaceRow> rows = buildRestrictionLogRows(entries);
            String summary = getString(R.string.settings_restriction_log_summary, rows.size());
            handler.post(() -> {
                if (isFinishingOrDestroyed()) return;
                restrictionLogEntries.clear();
                restrictionLogEntries.addAll(entries);
                adapter.setItems(rows);
                summaryText.setText(summary);
                loading.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void clearBackgroundRestrictions() {
        executor.execute(() -> {
            AppDebugManager.i(Category.STATISTICS_PAGE, FILE + ": restriction log cleared by user");
            appManager.clearBackgroundRestrictionLog();
            handler.post(this::loadBackgroundRestrictions);
        });
    }

    private List<SettingsSurfaceRow> buildRestrictionLogRows(List<BackgroundRestrictionLog.LogEntry> logEntries) {
        List<SettingsSurfaceRow> rows = new ArrayList<>();
        for (int i = 0; i < logEntries.size(); i++) {
            BackgroundRestrictionLog.LogEntry entry = logEntries.get(i);
            String title = (entry.packageName == null || entry.packageName.equals("-"))
                    ? humanizeLogAction(entry.action) : entry.packageName;
            String subtitle = entry.timestamp;
            if (entry.action != null && !entry.action.trim().isEmpty()) {
                subtitle = subtitle.isEmpty() ? humanizeLogAction(entry.action)
                        : subtitle + " | " + humanizeLogAction(entry.action);
            }
            String detail = humanizeLogOutcome(entry.outcome);
            String extraDetail = buildRestrictionLogRowDetail(entry);
            if (!extraDetail.isEmpty()) {
                detail = detail.isEmpty() ? extraDetail : detail + "  |  " + extraDetail;
            }
            rows.add(new SettingsSurfaceRow("#" + (i + 1), title, subtitle, detail,
                    resolveRestrictionTypeBadge(entry.action), entry.packageName));
        }
        return rows;
    }

    private String buildRestrictionLogRowDetail(BackgroundRestrictionLog.LogEntry entry) {
        String raw    = entry.detail != null ? entry.detail : "";
        if (raw.isEmpty()) return "";
        String action = entry.action != null ? entry.action : "";
        if ("watchdog".equals(action)) {
            String repaired = extractDetailValue(raw, "repaired");
            return repaired != null ? "repaired=" + repaired : "";
        }
        if ("watchdog-bucket".equals(action)) {
            String was = extractDetailValue(raw, "was");
            String set = extractDetailValue(raw, "set");
            if (was != null && set != null) return "was=" + was + " → " + set;
            return "";
        }
        if (raw.contains("appops=")) {
            String forceStop = extractDetailValue(raw, "force-stop");
            return forceStop != null ? "force-stop=" + forceStop : "";
        }
        return raw;
    }

    private void showRestrictionLogEntryDetails(BackgroundRestrictionLog.LogEntry entry) {
        StringBuilder body = new StringBuilder();
        body.append(entry.timestamp).append("\n\n");

        String actionLabel  = humanizeLogAction(entry.action);
        String outcomeLabel = humanizeLogOutcome(entry.outcome);
        body.append(getString(R.string.log_detail_action)).append(": ").append(actionLabel).append("\n");
        body.append(getString(R.string.log_detail_outcome)).append(": ").append(outcomeLabel).append("\n");

        String detail = entry.detail != null ? entry.detail : "";

        boolean isWatchdogBucket = "watchdog-bucket".equals(entry.action);
        boolean isWatchdog       = "watchdog".equals(entry.action);

        if (isWatchdogBucket) {
            body.append("\n");
            String was = extractDetailValue(detail, "was");
            String set = extractDetailValue(detail, "set");
            if (was != null) body.append(getString(R.string.log_detail_bucket_was)).append(": ").append(bucketBucketName(was)).append("\n");
            if (set != null) body.append(getString(R.string.log_detail_bucket_set)).append(": ").append(bucketBucketName(set)).append("\n");
            body.append(getString(R.string.log_detail_bucket_restored)).append(": ")
                    .append("ok".equals(entry.outcome)
                            ? getString(R.string.log_outcome_ok)
                            : getString(R.string.log_outcome_failed))
                    .append("\n");
        } else if (isWatchdog) {
            body.append("\n");
            String missing  = extractDetailValue(detail, "missing");
            String repaired = extractDetailValue(detail, "repaired");
            if (missing  != null) body.append(getString(R.string.log_detail_ops_missing)).append(": ").append(missing).append("\n");
            if (repaired != null) body.append(getString(R.string.log_detail_ops_repaired)).append(": ").append(repaired).append("\n");
            List<String> repairedOps = extractOpsList(detail, "repairedOps");
            if (!repairedOps.isEmpty()) {
                body.append("\n");
                for (String op : repairedOps) body.append("  ✓ ").append(op).append("\n");
            }
            List<String> failedOps = extractOpsList(detail, "failedOps");
            if (!failedOps.isEmpty()) {
                if (repairedOps.isEmpty()) body.append("\n");
                body.append(getString(R.string.log_detail_ops_failed)).append(":\n");
                for (String op : failedOps) body.append("  ✗ ").append(op).append("\n");
            }
        } else {
            boolean isSoftAction = "reapply-soft".equals(entry.action) || "restrict-soft".equals(entry.action) || "restrict".equals(entry.action);
            boolean hasOpsInfo   = !isSoftAction && detail.contains("appops=");
            if (hasOpsInfo) {
                body.append("\n");
                List<String> failedOps = extractOpsList(detail, "failedOps");
                boolean allOk     = detail.contains("appops=ok(");
                boolean allFailed = detail.contains("appops=failed(0/");
                boolean isManual  = "reapply-manual".equals(entry.action) || "restrict-manual".equals(entry.action);
                body.append(getString(R.string.log_detail_appops)).append(":\n");
                if (isManual) {
                    int manualMask = appManager.getManualOpsMask(entry.packageName);
                    if (manualMask != 0) {
                        for (int i = 0; i < BackgroundAppManager.ALL_OPS.length; i++) {
                            if ((manualMask & (1 << i)) == 0) continue;
                            String op     = BackgroundAppManager.ALL_OPS[i];
                            boolean failed = failedOps.contains(op);
                            if (allOk)     body.append("  ✓ ").append(op).append("\n");
                            else if (allFailed) body.append("  ✗ ").append(op).append("\n");
                            else           body.append(failed ? "  ✗ " : "  ✓ ").append(op).append("\n");
                        }
                    } else {
                        for (String op : failedOps) body.append("  ✗ ").append(op).append("\n");
                        String appopsRaw = extractDetailValue(detail, "appops");
                        if (appopsRaw != null && !appopsRaw.isEmpty())
                            body.append("  ").append(appopsRaw).append("\n");
                    }
                } else {
                    String[] opsForAction;
                    switch (entry.action != null ? entry.action : "") {
                        case "reapply-medium":
                        case "restrict-medium": opsForAction = BackgroundAppManager.MEDIUM_OPS; break;
                        default:                opsForAction = BackgroundAppManager.ALL_OPS;    break;
                    }
                    for (String op : opsForAction) {
                        if (allOk)          body.append("  ✓ ").append(op).append("\n");
                        else if (allFailed) body.append("  ✗ ").append(op).append("\n");
                        else {
                            boolean failed = failedOps.contains(op);
                            body.append(failed ? "  ✗ " : "  ✓ ").append(op).append("\n");
                        }
                    }
                }
            }
            String forceStop = extractDetailValue(detail, "force-stop");
            String bucketVal = extractDetailValue(detail, "bucket");
            if (bucketVal  != null) body.append("\n").append(getString(R.string.log_detail_bucket_set)).append(": ").append(bucketBucketName(bucketVal)).append("\n");
            if (forceStop  != null) body.append(getString(R.string.log_detail_force_stop)).append(": ").append(forceStop).append("\n");
        }

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        TextView textView = new TextView(this);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        textView.setPadding(pad, pad, pad, pad);
        textView.setTextSize(13f);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        textView.setText(body.toString().trim());
        scrollView.addView(textView);

        String title = (entry.packageName == null || entry.packageName.equals("-"))
                ? humanizeLogAction(entry.action) : entry.packageName;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(scrollView)
                .setNegativeButton(getString(R.string.dialog_close), (d, w) -> d.dismiss());

        if (entry.packageName != null && entry.packageName.contains(".")) {
            builder.setPositiveButton(getString(R.string.settings_open_app_info),
                    (d, w) -> openAppInfo(entry.packageName));
        }

        applyCustomAccentToDialogButtons(builder.show());
    }


    // ---------- Sleep Mode ----------

    private void setupSleepMode() {
        emptyView.setText(getString(R.string.log_sleep_mode_empty));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            SettingsSurfaceRow row = adapter.getItem(position);
            if (row != null && row.packageName != null && row.packageName.contains(".")) {
                openAppInfo(row.packageName);
            }
        });
        loading.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        summaryText.setText(getString(R.string.stats_loading));
        loadSleepMode();
    }

    private void loadSleepMode() {
        executor.execute(() -> {
            List<SleepModeLogManager.LogEntry> entries = SleepModeLogManager.readEntries(this);
            AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": sleep mode log loaded " + entries.size() + " entries");
            List<SettingsSurfaceRow> rows = buildSleepModeLogRows(entries);
            String summary = getString(R.string.settings_restriction_log_summary, rows.size());
            handler.post(() -> {
                if (isFinishingOrDestroyed()) return;
                adapter.setItems(rows);
                summaryText.setText(summary);
                loading.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void clearSleepMode() {
        executor.execute(() -> {
            AppDebugManager.i(Category.STATISTICS_PAGE, FILE + ": sleep mode log cleared by user");
            SleepModeLogManager.clear(this);
            handler.post(this::loadSleepMode);
        });
    }

    private List<SettingsSurfaceRow> buildSleepModeLogRows(List<SleepModeLogManager.LogEntry> logEntries) {
        List<SettingsSurfaceRow> rows = new ArrayList<>();
        for (int i = 0; i < logEntries.size(); i++) {
            SleepModeLogManager.LogEntry entry = logEntries.get(i);
            String title = (entry.packageName == null || entry.packageName.equals("-"))
                    ? humanizeLogAction(entry.action) : entry.packageName;
            String typeTag = sleepModeTypeTag(entry);
            String subtitle = entry.timestamp;
            if (!typeTag.isEmpty()) {
                subtitle = subtitle.isEmpty() ? typeTag : subtitle + " | " + typeTag;
            }
            String detail = humanizeLogOutcome(entry.outcome);
            rows.add(new SettingsSurfaceRow("#" + (i + 1), title, subtitle, detail,
                    resolveSleepModeLogBadge(entry.action), entry.packageName));
        }
        return rows;
    }

    private String sleepModeTypeTag(SleepModeLogManager.LogEntry entry) {
        String type   = entry.freezeType;
        String method = entry.method;
        if (type == null || type.equals("-")) return "";
        String typeLabel = "permanent".equals(type) ? "perm" : "temp";
        if (method == null || method.equals("-")) return typeLabel;
        return typeLabel + " • " + method;
    }


    // ---------- Scheduler ----------

    private void setupScheduler() {
        emptyView.setText(getString(R.string.log_scheduler_empty));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            SettingsSurfaceRow row = adapter.getItem(position);
            if (row != null && row.packageName != null && row.packageName.contains(".")) {
                openAppInfo(row.packageName);
            }
        });
        loading.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        summaryText.setText(getString(R.string.stats_loading));
        loadScheduler();
    }

    private void loadScheduler() {
        executor.execute(() -> {
            List<RestrictionsScheduler.SchedulerLog.Entry> entries =
                    RestrictionsScheduler.SchedulerLog.readEntries(this);
            AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": scheduler log loaded " + entries.size() + " entries");
            List<SettingsSurfaceRow> rows = buildSchedulerLogRows(entries);
            String summary = getString(R.string.settings_restriction_log_summary, rows.size());
            handler.post(() -> {
                if (isFinishingOrDestroyed()) return;
                adapter.setItems(rows);
                summaryText.setText(summary);
                loading.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void clearScheduler() {
        executor.execute(() -> {
            AppDebugManager.i(Category.STATISTICS_PAGE, FILE + ": scheduler log cleared by user");
            RestrictionsScheduler.SchedulerLog.clear(this);
            handler.post(this::loadScheduler);
        });
    }

    private List<SettingsSurfaceRow> buildSchedulerLogRows(List<RestrictionsScheduler.SchedulerLog.Entry> logEntries) {
        List<SettingsSurfaceRow> rows = new ArrayList<>();
        for (int i = 0; i < logEntries.size(); i++) {
            RestrictionsScheduler.SchedulerLog.Entry entry = logEntries.get(i);
            String title = (entry.packageName == null || entry.packageName.equals("-"))
                    ? humanizeLogAction(entry.action) : entry.packageName;
            String subtitle = entry.timestamp;
            if (entry.action != null && !entry.action.trim().isEmpty()) {
                subtitle = subtitle.isEmpty() ? humanizeLogAction(entry.action)
                        : subtitle + " | " + humanizeLogAction(entry.action);
            }
            String detail = humanizeLogOutcome(entry.outcome);
            if (entry.detail != null && !entry.detail.trim().isEmpty()) {
                detail = detail.isEmpty() ? entry.detail : detail + "  |  " + entry.detail;
            }
            rows.add(new SettingsSurfaceRow("#" + (i + 1), title, subtitle, detail,
                    resolveSchedulerLogBadge(entry.action), entry.packageName));
        }
        return rows;
    }


    // ---------- Shared: log item bottom sheet ----------

    private void showLogAppOptions(String appName, String packageName, long windowMs) {
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        int accentColor = accent == ACCENT_CUSTOM
                ? sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR)
                : 0;
        LogAppOptionsBottomSheet sheet = LogAppOptionsBottomSheet.newInstance(
                appName, packageName, windowMs, appManager.supportsBackgroundRestriction(), accentColor);
        sheet.setAppManager(appManager);
        sheet.setListener(new LogAppOptionsBottomSheet.Listener() {
            @Override
            public void onShowKillDetail(String appName, String packageName, long windowMs) {
                showKillDetailDialog(appName, packageName, windowMs);
            }

            @Override
            public void onRestrictionTypeChanged(String packageName, BackgroundAppManager.RestrictionType type) {
                appManager.setRestrictionType(packageName, type);
                appManager.setBackgroundRestricted(packageName, true, null);
            }
        });
        sheet.show(getSupportFragmentManager(), "log_app_options");
    }


    // ---------- Shared: kill detail dialog ----------

    private void showKillDetailDialog(String appName, String packageName, long windowMs) {
        executor.execute(() -> {
            long since = windowMs > 0 ? System.currentTimeMillis() - windowMs : 0L;
            com.gree1d.reappzuku.db.AppStatsDao dao =
                    com.gree1d.reappzuku.db.AppDatabase.getInstance(this).appStatsDao();
            List<com.gree1d.reappzuku.db.AppStats> kills = dao.getKillsSince(packageName, since);

            java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
            java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);

            handler.post(() -> {
                if (isFinishingOrDestroyed()) return;
                float dp = getResources().getDisplayMetrics().density;

                android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
                android.widget.LinearLayout container = new android.widget.LinearLayout(this);
                container.setOrientation(android.widget.LinearLayout.VERTICAL);
                int padH = Math.round(16 * dp);
                int padV = Math.round(8 * dp);
                container.setPadding(padH, padV, padH, padV);
                scrollView.addView(container);

                List<com.gree1d.reappzuku.db.AppStats> validKills = new ArrayList<>();
                for (com.gree1d.reappzuku.db.AppStats kill : kills) {
                    if (kill.lastKillTime > 0) validKills.add(kill);
                }

                if (validKills.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText("—");
                    empty.setTextSize(14f);
                    empty.setPadding(0, Math.round(8 * dp), 0, Math.round(8 * dp));
                    empty.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                    container.addView(empty);
                } else {
                    int accentRaw = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
                    int badgeColor = accentRaw == ACCENT_CUSTOM
                            ? sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR)
                            : com.google.android.material.color.MaterialColors.getColor(
                                    container, com.google.android.material.R.attr.colorSecondary);

                    for (int i = 0; i < validKills.size(); i++) {
                        com.gree1d.reappzuku.db.AppStats kill = validKills.get(i);
                        java.util.Date d = new java.util.Date(kill.lastKillTime);
                        String source = kill.lastKillSource != null && !kill.lastKillSource.isEmpty()
                                ? kill.lastKillSource : "—";

                        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                        row.setOrientation(android.widget.LinearLayout.VERTICAL);
                        android.widget.LinearLayout.LayoutParams rowLp =
                                new android.widget.LinearLayout.LayoutParams(
                                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                        rowLp.topMargin = Math.round(10 * dp);
                        rowLp.bottomMargin = Math.round(10 * dp);
                        row.setLayoutParams(rowLp);

                        android.widget.LinearLayout badgesRow = new android.widget.LinearLayout(this);
                        badgesRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                        badgesRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        android.widget.LinearLayout.LayoutParams badgesLp =
                                new android.widget.LinearLayout.LayoutParams(
                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                        badgesLp.bottomMargin = Math.round(5 * dp);
                        badgesRow.setLayoutParams(badgesLp);

                        badgesRow.addView(makeBadge(dateFormat.format(d), badgeColor, dp));
                        android.widget.Space space = new android.widget.Space(this);
                        space.setLayoutParams(new android.widget.LinearLayout.LayoutParams(Math.round(6 * dp), 1));
                        badgesRow.addView(space);
                        badgesRow.addView(makeBadge(timeFormat.format(d), badgeColor, dp));

                        TextView sourceTv = new TextView(this);
                        sourceTv.setText(source);
                        sourceTv.setTextSize(13f);
                        sourceTv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

                        row.addView(badgesRow);
                        row.addView(sourceTv);
                        container.addView(row);

                        if (i < validKills.size() - 1) {
                            View divider = new View(this);
                            android.widget.LinearLayout.LayoutParams divLp =
                                    new android.widget.LinearLayout.LayoutParams(
                                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                            Math.round(1 * dp));
                            divider.setLayoutParams(divLp);
                            divider.setBackgroundColor(
                                    androidx.core.graphics.ColorUtils.setAlphaComponent(
                                            ContextCompat.getColor(this, R.color.text_primary), 30));
                            container.addView(divider);
                        }
                    }
                }

                applyCustomAccentToDialogButtons(new MaterialAlertDialogBuilder(this)
                        .setTitle(appName)
                        .setView(scrollView)
                        .setNegativeButton(getString(R.string.dialog_close), (d, w) -> d.dismiss())
                        .show());
            });
        });
    }


    // ---------- Shared helpers ----------

    private boolean isFinishingOrDestroyed() {
        return isFinishing() ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }

    private void openAppInfo(String packageName) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception e) {
            AppDebugManager.e(Category.STATISTICS_PAGE, FILE + ": openAppInfo failed for " + packageName, e);
            Toast.makeText(this, getString(R.string.settings_open_app_info_error), Toast.LENGTH_SHORT).show();
        }
    }

    private void applyCustomAccentToDialogButtons(AlertDialog dialog) {
        int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
        if (accent != ACCENT_CUSTOM) return;
        int nightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        int color = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                ? Color.WHITE : Color.BLACK;
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button btn = dialog.getButton(which);
            if (btn != null) btn.setTextColor(color);
        }
    }

    private TextView makeBadge(String text, int badgeColor, float dp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(badgeColor);
        int padH = Math.round(8 * dp);
        int padV = Math.round(3 * dp);
        tv.setPadding(padH, padV, padH, padV);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(6 * dp);
        bg.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(badgeColor, 30));
        bg.setStroke(Math.round(dp), androidx.core.graphics.ColorUtils.setAlphaComponent(badgeColor, 70));
        tv.setBackground(bg);
        return tv;
    }

    private String resolveAggregateAppName(com.gree1d.reappzuku.db.AppStatsAggregate stats,
                                            java.util.Map<String, String> pendingNameUpdates) {
        if (stats.appName != null && !stats.appName.trim().isEmpty()) return stats.appName;
        try {
            android.content.pm.ApplicationInfo appInfo =
                    getPackageManager().getApplicationInfo(stats.packageName, 0);
            CharSequence label = getPackageManager().getApplicationLabel(appInfo);
            if (label != null) {
                String name = label.toString();
                if (pendingNameUpdates != null) {
                    pendingNameUpdates.put(stats.packageName, name);
                }
                stats.appName = name;
                return name;
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            AppDebugManager.w(Category.STATISTICS_PAGE, FILE + ": resolveAggregateAppName package not found: "
                    + stats.packageName, e);
        }
        return stats.packageName;
    }

    private String resolveRestrictionTypeBadge(String action) {
        if (action == null) return "";
        switch (action.trim().toLowerCase()) {
            case "restrict-hard":   case "reapply-hard":   return getString(R.string.restriction_badge_hard);
            case "restrict-medium": case "reapply-medium": return getString(R.string.restriction_badge_medium);
            case "restrict-soft":   case "reapply-soft":   case "restrict": return getString(R.string.restriction_badge_soft);
            case "restrict-manual": case "reapply-manual": return getString(R.string.restriction_badge_manual);
            case "allow":                                  return getString(R.string.restriction_badge_removed);
            default:                                       return "";
        }
    }

    private String resolveSleepModeLogBadge(String action) {
        if (action == null) return "";
        switch (action.trim().toLowerCase()) {
            case "freeze":   return getString(R.string.log_badge_freeze);
            case "unfreeze": return getString(R.string.log_badge_unfreeze);
            default:         return "";
        }
    }

    private String resolveSchedulerLogBadge(String action) {
        if (action == null) return "";
        switch (action.trim().toLowerCase()) {
            case "lift":    return getString(R.string.log_badge_lift);
            case "restore": return getString(R.string.log_badge_restore);
            default:        return "";
        }
    }

    private String humanizeLogAction(String action) {
        if (action == null || action.trim().isEmpty()) return getString(R.string.log_action_event);
        String n = action.trim().replace('-', ' ').replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private String humanizeLogOutcome(String outcome) {
        if (outcome == null || outcome.trim().isEmpty()) return "";
        String trimmed = outcome.trim();
        String suffix = "";
        int parenIdx = trimmed.indexOf(" (");
        if (parenIdx > 0 && trimmed.endsWith(")")) {
            suffix  = trimmed.substring(parenIdx);
            trimmed = trimmed.substring(0, parenIdx);
        }
        String localized;
        switch (trimmed.toLowerCase()) {
            case "ok":                         localized = getString(R.string.log_outcome_ok);                        break;
            case "verified":                   localized = getString(R.string.log_outcome_verified);                  break;
            case "failed":                     localized = getString(R.string.log_outcome_failed);                    break;
            case "skipped":                    localized = getString(R.string.log_outcome_skipped);                   break;
            case "verify-failed":              localized = getString(R.string.log_outcome_verify_failed);             break;
            case "verify-unavailable":         localized = getString(R.string.log_outcome_verify_unavailable);        break;
            case "battery-whitelist-removed":  localized = getString(R.string.log_outcome_battery_whitelist_removed); break;
            case "battery-whitelist-restored": localized = getString(R.string.log_outcome_battery_whitelist_restored); break;
            default:
                String n = trimmed.replace('-', ' ').replace('_', ' ');
                localized = n.toUpperCase(Locale.US);
        }
        return localized + suffix;
    }

    private String extractDetailValue(String detail, String key) {
        if (detail == null || key == null) return null;
        String prefix = key + "=";
        int start = detail.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        int end = detail.indexOf(' ', start);
        return end < 0 ? detail.substring(start) : detail.substring(start, end);
    }

    private List<String> extractOpsList(String detail, String key) {
        List<String> result = new ArrayList<>();
        if (detail == null) return result;
        String prefix = key + "=[";
        int start = detail.indexOf(prefix);
        if (start < 0) return result;
        start += prefix.length();
        int end = detail.indexOf(']', start);
        if (end < 0) return result;
        String inner = detail.substring(start, end).trim();
        if (inner.isEmpty()) return result;
        for (String op : inner.split(",")) {
            String trimmed = op.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private String bucketBucketName(String value) {
        switch (value) {
            case "40": return "RARE (40)";
            case "45": return "RESTRICTED (45)";
            case "30": return "WORKING_SET (30)";
            case "20": return "FREQUENT (20)";
            case "10": return "ACTIVE (10)";
            default:   return value;
        }
    }

    private String formatRecoveredSize(long kb) {
        if (kb < 1024)          return getString(R.string.unit_kb, kb);
        if (kb < 1024 * 1024)   return getString(R.string.unit_mb_precise, kb / 1024f);
        return                         getString(R.string.unit_gb_precise, kb / (1024f * 1024f));
    }


    // ---------- Model / adapter ----------

    static class TopOffender {
        final String appName, packageName;
        final int killCount, relaunchCount;
        final long recoveredKb;
        final double score;

        TopOffender(String appName, String packageName, int killCount, int relaunchCount,
                    long recoveredKb, double score) {
            this.appName = appName; this.packageName = packageName;
            this.killCount = killCount; this.relaunchCount = relaunchCount;
            this.recoveredKb = recoveredKb; this.score = score;
        }
    }

    static class KillHistoryEntry {
        final String appName, packageName, detail, badge, lastKillSource;
        final long lastEventTime;

        KillHistoryEntry(String appName, String packageName, String detail, String badge,
                         long lastEventTime, String lastKillSource) {
            this.appName = appName; this.packageName = packageName;
            this.detail = detail; this.badge = badge;
            this.lastEventTime = lastEventTime; this.lastKillSource = lastKillSource;
        }
    }

    private List<SettingsSurfaceRow> buildKillHistoryRows(List<KillHistoryEntry> entries) {
        List<SettingsSurfaceRow> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            KillHistoryEntry e = entries.get(i);
            rows.add(new SettingsSurfaceRow("#" + (i + 1), e.appName, e.packageName, e.detail, e.badge, e.packageName));
        }
        return rows;
    }

    static class SettingsSurfaceRow {
        final String leadingText, title, subtitle, detail, badge, packageName;

        SettingsSurfaceRow(String leadingText, String title, String subtitle,
                           String detail, String badge, String packageName) {
            this.leadingText = leadingText; this.title = title; this.subtitle = subtitle;
            this.detail = detail; this.badge = badge; this.packageName = packageName;
        }
    }

    class SettingsSurfaceAdapter extends BaseAdapter {
        private final List<SettingsSurfaceRow> items = new ArrayList<>();
        private final LayoutInflater inflater = LayoutInflater.from(LogDetailActivity.this);

        void setItems(List<SettingsSurfaceRow> newItems) {
            items.clear();
            if (newItems != null) items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override public int getCount()                { return items.size(); }
        @Override public SettingsSurfaceRow getItem(int pos) {
            return (pos >= 0 && pos < items.size()) ? items.get(pos) : null;
        }
        @Override public long getItemId(int pos)       { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null
                    ? convertView
                    : inflater.inflate(R.layout.item_top_offender, parent, false);
            SettingsSurfaceRow item = getItem(position);
            if (item == null) return view;
            bindOptionalText((TextView) view.findViewById(R.id.offender_rank),    item.leadingText);
            ((TextView) view.findViewById(R.id.offender_name)).setText(item.title);
            bindOptionalText((TextView) view.findViewById(R.id.offender_package), item.subtitle);
            bindOptionalText((TextView) view.findViewById(R.id.offender_metrics), item.detail);
            bindOptionalText((TextView) view.findViewById(R.id.offender_score),   item.badge);

            int accent = sharedPreferences.getInt(KEY_ACCENT, ACCENT_SYSTEM);
            TextView rank  = view.findViewById(R.id.offender_rank);
            TextView score = view.findViewById(R.id.offender_score);
            if (accent == ACCENT_CUSTOM) {
                int color = sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
                if (rank  != null) rank.setTextColor(color);
                if (score != null) score.setTextColor(color);
            }
            if (score != null) {
                if (item.badge != null && !item.badge.trim().isEmpty()) {
                    int badgeColor = accent == ACCENT_CUSTOM
                            ? sharedPreferences.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR)
                            : com.google.android.material.color.MaterialColors.getColor(
                                    score, com.google.android.material.R.attr.colorSecondary);
                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                    bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(6 * score.getResources().getDisplayMetrics().density);
                    bg.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(badgeColor, 40));
                    bg.setStroke(Math.round(score.getResources().getDisplayMetrics().density),
                            androidx.core.graphics.ColorUtils.setAlphaComponent(badgeColor, 80));
                    score.setBackground(bg);
                } else {
                    score.setBackground(null);
                }
            }
            return view;
        }

        private void bindOptionalText(TextView view, String text) {
            if (text == null || text.trim().isEmpty()) {
                view.setVisibility(View.GONE);
                view.setText("");
                return;
            }
            view.setVisibility(View.VISIBLE);
            view.setText(text);
        }
    }
}
