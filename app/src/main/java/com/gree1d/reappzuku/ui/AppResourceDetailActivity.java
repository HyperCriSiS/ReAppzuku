package com.gree1d.reappzuku.ui;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.core.App;
import com.gree1d.reappzuku.core.BaseActivity;
import com.gree1d.reappzuku.manager.CollectStatsManager;
import com.gree1d.reappzuku.manager.BackgroundAppManager;
import com.gree1d.reappzuku.manager.AutoKillManager;
import com.gree1d.reappzuku.core.PreferenceKeys;
import com.gree1d.reappzuku.core.AppConstants;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.utils.AppModel;
import com.gree1d.reappzuku.R;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.tabs.TabLayout;
import com.gree1d.reappzuku.databinding.ActivityAppResourceDetailBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public class AppResourceDetailActivity extends BaseActivity {

    private static final String TAG = "AppResourceDetailActivity";

    public static final String EXTRA_PACKAGE_NAME  = "extra_package_name";
    public static final String EXTRA_APP_NAME      = "extra_app_name";
    public static final String EXTRA_TOTAL_CPU_PCT = "extra_total_cpu_pct";
    public static final String EXTRA_TOTAL_RAM_MB  = "extra_total_ram_mb";
    public static final String EXTRA_PERIOD_IDX    = "extra_period_idx";

    private static final int[]    PERIODS_HOURS  = { 2, 6, 12, 24 };

    private static final float Y_NONE   = 0f;
    private static final float Y_LOW    = 1f;
    private static final float Y_MEDIUM = 2f;
    private static final float Y_HIGH   = 3f;

    private ActivityAppResourceDetailBinding binding;
    private CollectStatsManager collectStatsManager;
    private ShellManager shellManager;
    private BackgroundAppManager appManager;
    private AutoKillManager autoKillManager;
    private Handler handler;
    private ExecutorService executor;

    private String packageName;
    private String appName;
    private double totalAllAppsCpuPct;
    private double totalAllAppsRamMb;
    private int selectedPeriodIdx = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAppResourceDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        packageName        = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        appName            = getIntent().getStringExtra(EXTRA_APP_NAME);
        totalAllAppsCpuPct = getIntent().getDoubleExtra(EXTRA_TOTAL_CPU_PCT, 0);
        totalAllAppsRamMb  = getIntent().getDoubleExtra(EXTRA_TOTAL_RAM_MB, 0);
        selectedPeriodIdx  = getIntent().getIntExtra(EXTRA_PERIOD_IDX, 0);
        AppDebugManager.d(Category.STATISTICS_PAGE, TAG + ": onCreate package=" + packageName
                + " appName=" + appName + " periodIdx=" + selectedPeriodIdx);
        if (packageName == null) {
            AppDebugManager.w(Category.STATISTICS_PAGE, TAG + ": onCreate called without EXTRA_PACKAGE_NAME, finishing activity");
            finish();
            return;
        }

        App app              = (App) getApplication();
        handler              = app.getSharedHandler();
        executor             = app.getSharedExecutor();
        shellManager        = app.getShellManager();
        collectStatsManager = new CollectStatsManager(getApplicationContext(), handler, executor, shellManager);
        appManager           = new BackgroundAppManager(this, handler, executor, app.getShellExecutor(), shellManager);
        autoKillManager      = new AutoKillManager(this, handler, executor, shellManager, appManager.getCurrentAppsList());

        setupToolbar();
        setupAppCard();
        setupPeriodTabs();
        setupAddToListButton();
        loadData(PERIODS_HOURS[selectedPeriodIdx]);
    }

    private void setupAddToListButton() {
        AppModel app = findAppModel(packageName);
        
        if (app == null) {
            Drawable icon;
            boolean isSystem = false;
            
            try {
                icon = getPackageManager().getApplicationIcon(packageName);
                int flags = getPackageManager().getApplicationInfo(packageName, 0).flags;
                isSystem = (flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
            } catch (PackageManager.NameNotFoundException e) {
                icon = ContextCompat.getDrawable(this, android.R.drawable.sym_def_app_icon);
            }
    
            boolean isWhitelisted = appManager.getWhitelistedApps().contains(packageName);
            boolean isProtected = com.gree1d.reappzuku.core.ProtectedApps.isProtected(this, packageName);
            String finalAppName = appName != null ? appName : packageName;
    
            app = new AppModel(
                finalAppName, 
                packageName, 
                "", 
                0L, 
                icon, 
                isSystem,
                false, 
                isProtected  
            );
            
            app.setWhitelisted(isWhitelisted);
            
            if (appManager.getCurrentAppsList() != null) {
                appManager.getCurrentAppsList().add(app);
            }
        }
    
        if (app.isProtected()) {
            AppDebugManager.d(Category.STATISTICS_PAGE, TAG + ": setupAddToListButton hidden, protected app pkg=" + packageName);
            binding.sheetAddToTitle.setVisibility(View.GONE);
            return;
        }
    
        binding.sheetAddToTitle.setVisibility(View.VISIBLE);
    
        int accent = sharedPreferences.getInt(PreferenceKeys.KEY_ACCENT, AppConstants.ACCENT_SYSTEM);
        int accentColor;
        if (accent == AppConstants.ACCENT_CUSTOM) {
            accentColor = sharedPreferences.getInt(PreferenceKeys.KEY_ACCENT_CUSTOM_COLOR, AppConstants.ACCENT_CUSTOM_DEFAULT_COLOR);
        } else {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
            accentColor = typedValue.data;
        }
        binding.sheetAddToTitle.setTextColor(accentColor);
        
        final AppModel finalApp = app;
        binding.sheetAddToTitle.setOnClickListener(v -> showStatsAppOptionsSheet(finalApp));
    }

    
    private AppModel findAppModel(String pkg) {
        if (pkg == null) return null;
        List<AppModel> currentApps = appManager.getCurrentAppsList();
        if (currentApps == null || currentApps.isEmpty()) return null;
        
        for (AppModel app : currentApps) {
            if (pkg.equals(app.getPackageName())) {
                return app;
            }
        }
        return null;
    }


    private void showStatsAppOptionsSheet(AppModel app) {
        String pkg = app.getPackageName(); 
    
        int accent = sharedPreferences.getInt(PreferenceKeys.KEY_ACCENT, AppConstants.ACCENT_SYSTEM); 
        int accentColor;
    
        if (accent == AppConstants.ACCENT_CUSTOM) { 
            accentColor = sharedPreferences.getInt(PreferenceKeys.KEY_ACCENT_CUSTOM_COLOR, AppConstants.ACCENT_CUSTOM_DEFAULT_COLOR); 
        } else {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
            accentColor = typedValue.data;
        }
    
        StatsAppOptionsBottomSheet sheet = StatsAppOptionsBottomSheet.newInstance(
                app, 
                appManager.getWhitelistedApps().contains(pkg), 
                autoKillManager.getBlacklistedApps().contains(pkg), 
                appManager.supportsBackgroundRestriction(), 
                getBackgroundRestrictionMenuTitle(app), 
                accentColor 
        );
    
        try { 
            android.graphics.drawable.Drawable icon = getPackageManager().getApplicationIcon(pkg); 
            sheet.setAppIcon(icon); 
        } catch (PackageManager.NameNotFoundException e) { 
            AppDebugManager.w(Category.STATISTICS_PAGE, TAG + ": showStatsAppOptionsSheet failed to load icon for " + pkg, e); 
        }
    
        sheet.setListener(new StatsAppOptionsBottomSheet.Listener() { 
            @Override 
            public void onToggleWhitelist(boolean nowChecked) {
                toggleListMembership(app, "whitelist"); 
            }
    
            @Override 
            public void onToggleBlacklist(boolean nowChecked) { 
                toggleListMembership(app, "blacklist"); 
            }
    
            @Override
            public void onToggleBackgroundRestriction(boolean nowChecked) { 
                toggleBackgroundRestriction(app);
            }
        });
    
        sheet.show(getSupportFragmentManager(), "stats_app_options"); 
    }


    private void toggleListMembership(AppModel app, String listType) {
        if (app.isProtected()) {
            AppDebugManager.w(Category.STATISTICS_PAGE, TAG
                    + ": toggleListMembership blocked, protected app pkg=" + app.getPackageName() + " listType=" + listType);
            return;
        }

        String pkg = app.getPackageName();
        java.util.Set<String> currentSet;
        String addedMsg, removedMsg;

        switch (listType) {
            case "whitelist":
                currentSet = appManager.getWhitelistedApps();
                addedMsg = getString(R.string.main_added_to_whitelist);
                removedMsg = getString(R.string.main_removed_from_whitelist);
                break;
            case "blacklist":
                currentSet = autoKillManager.getBlacklistedApps();
                addedMsg = getString(R.string.main_added_to_blacklist);
                removedMsg = getString(R.string.main_removed_from_blacklist);
                break;
            default:
                AppDebugManager.w(Category.STATISTICS_PAGE, TAG + ": toggleListMembership unknown listType=" + listType);
                return;
        }

        boolean wasInList = currentSet.contains(pkg);
        if (wasInList) {
            currentSet.remove(pkg);
        } else {
            currentSet.add(pkg);
        }
        AppDebugManager.d(Category.STATISTICS_PAGE, TAG + ": toggleListMembership " + listType + " for " + pkg
                + " nowInList=" + !wasInList);

        switch (listType) {
            case "whitelist":
                appManager.saveWhitelistedApps(currentSet);
                app.setWhitelisted(!wasInList);
                break;
            case "blacklist":
                autoKillManager.saveBlacklistedApps(currentSet);
                break;
        }

        Toast.makeText(this, wasInList ? removedMsg : addedMsg, Toast.LENGTH_SHORT).show();
    }

    private void toggleBackgroundRestriction(AppModel app) {
        if (app.isProtected()) {
            AppDebugManager.w(Category.STATISTICS_PAGE, TAG
                    + ": toggleBackgroundRestriction blocked, protected app pkg=" + app.getPackageName());
            return;
        }
        boolean enableRestriction = !app.isBackgroundRestrictionDesired();
        AppDebugManager.i(Category.STATISTICS_PAGE, TAG + ": toggleBackgroundRestriction pkg=" + app.getPackageName()
                + " enable=" + enableRestriction);
        appManager.setBackgroundRestricted(app.getPackageName(), enableRestriction, null);
        app.setBackgroundRestrictionDesired(enableRestriction);
    }

    private String getBackgroundRestrictionMenuTitle(AppModel app) {
        if (app.needsBackgroundRestrictionReapply()) {
            return getString(R.string.main_restriction_menu_out_of_sync);
        }
        if (app.isBackgroundRestrictionExternal()) {
            return getString(R.string.main_restriction_menu_external);
        }
        if (app.isBackgroundRestrictionDesired() && !app.isBackgroundRestrictionActualKnown()) {
            return getString(R.string.main_restriction_menu_saved);
        }
        return getString(R.string.main_restriction_menu_default);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(appName != null ? appName : packageName);
        }
        int accent = sharedPreferences.getInt(PreferenceKeys.KEY_ACCENT, AppConstants.ACCENT_SYSTEM);
        if (accent == AppConstants.ACCENT_CUSTOM) {
            int customColor = sharedPreferences.getInt(PreferenceKeys.KEY_ACCENT_CUSTOM_COLOR, AppConstants.ACCENT_CUSTOM_DEFAULT_COLOR);
            int onColor = sharedPreferences.getInt(PreferenceKeys.KEY_ACCENT_ON_COLOR, AppConstants.ACCENT_ON_WHITE) == AppConstants.ACCENT_ON_BLACK
                    ? Color.BLACK : Color.WHITE;
            binding.toolbar.setBackgroundColor(customColor);
            binding.toolbar.setTitleTextColor(onColor);
            if (binding.toolbar.getNavigationIcon() != null)
                androidx.core.graphics.drawable.DrawableCompat.setTint(
                        binding.toolbar.getNavigationIcon(), onColor);
        } else if (accent == AppConstants.ACCENT_SYSTEM) {
            binding.toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.toolbar_navy));
            binding.toolbar.setTitleTextColor(Color.WHITE);
        } else {
            boolean isLightAccent = (accent == AppConstants.ACCENT_APRICOT || accent == AppConstants.ACCENT_SKY ||
                    accent == AppConstants.ACCENT_PAPAYA   || accent == AppConstants.ACCENT_LAVENDER ||
                    accent == AppConstants.ACCENT_MINT     || accent == AppConstants.ACCENT_PEACH ||
                    accent == AppConstants.ACCENT_POWDER   || accent == AppConstants.ACCENT_FOG);
            binding.toolbar.setTitleTextColor(isLightAccent ? Color.BLACK : Color.WHITE);
        }
    }

    private void setupAppCard() {
        binding.tvAppName.setText(appName != null ? appName : packageName);
        binding.tvPackageName.setText(packageName);
        try {
            Drawable icon = getPackageManager().getApplicationIcon(packageName);
            binding.ivAppIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            AppDebugManager.e(Category.STATISTICS_PAGE, TAG + ": failed to load app icon for " + packageName, e);
            binding.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    private void setupPeriodTabs() {
        String[] labels = getResources().getStringArray(R.array.chart_period_labels);
        for (String label : labels) {
            binding.tabDetailPeriod.addTab(binding.tabDetailPeriod.newTab().setText(label));
        }
        binding.tabDetailPeriod.selectTab(binding.tabDetailPeriod.getTabAt(selectedPeriodIdx));

        int accent = sharedPreferences.getInt(PreferenceKeys.KEY_ACCENT, AppConstants.ACCENT_SYSTEM);
        if (accent == AppConstants.ACCENT_CUSTOM) {
            int color = sharedPreferences.getInt(PreferenceKeys.KEY_ACCENT_CUSTOM_COLOR, AppConstants.ACCENT_CUSTOM_DEFAULT_COLOR);
            binding.tabDetailPeriod.setSelectedTabIndicatorColor(color);
            binding.tabDetailPeriod.setTabTextColors(
                    ContextCompat.getColor(this, R.color.text_secondary), color);
        }

        binding.tabDetailPeriod.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                selectedPeriodIdx = tab.getPosition();
                AppDebugManager.d(Category.STATISTICS_PAGE, TAG + ": period tab selected idx="
                        + selectedPeriodIdx + " hours=" + PERIODS_HOURS[selectedPeriodIdx]);
                loadData(PERIODS_HOURS[selectedPeriodIdx]);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadData(int hours) {
        AppDebugManager.d(Category.STATISTICS_PAGE, TAG + ": loadData started for package=" + packageName + " hours=" + hours);
        binding.layoutDetailLoading.setVisibility(View.VISIBLE);
        binding.cardDetailStats.setVisibility(View.GONE);
        binding.cardDetailActivity.setVisibility(View.GONE);

        collectStatsManager.getHourlyStatsAsync(
                packageName, hours, totalAllAppsCpuPct, totalAllAppsRamMb, result -> {

            binding.layoutDetailLoading.setVisibility(View.GONE);

            if (result.isPartialData) {
                AppDebugManager.w(Category.STATISTICS_PAGE, TAG + ": loadData received partial data for package=" + packageName);
                binding.tvDetailPartialWarning.setText(getString(R.string.stats_partial_data_warning));
                binding.tvDetailPartialWarning.setVisibility(View.VISIBLE);
            } else {
                binding.tvDetailPartialWarning.setVisibility(View.GONE);
            }

            if (result.stats == null || result.slices.isEmpty()) {
                AppDebugManager.w(Category.STATISTICS_PAGE, TAG + ": loadData has no stats/slices for package="
                        + packageName + " (stats null=" + (result.stats == null) + ", slices empty="
                        + (result.slices == null || result.slices.isEmpty()) + ")");
                return;
            }

            binding.cardDetailStats.setVisibility(View.VISIBLE);
            binding.cardDetailActivity.setVisibility(View.VISIBLE);

            CollectStatsManager.HourlyPeriodStats s = result.stats;

            binding.tvBatteryMin.setText(String.format(Locale.US, "%.1f mAh", s.minBatteryMah));
            binding.tvBatteryAvg.setText(String.format(Locale.US, "%.1f mAh", s.avgBatteryMah));
            binding.tvBatteryMax.setText(String.format(Locale.US, "%.1f mAh", s.maxBatteryMah));

            binding.tvCpuMin.setText(String.format(Locale.US, "%.1f%%", s.minCpuPct));
            binding.tvCpuAvg.setText(String.format(Locale.US, "%.1f%%", s.avgCpuPct));
            binding.tvCpuMax.setText(String.format(Locale.US, "%.1f%%", s.maxCpuPct));

            binding.tvRamMin.setText(getString(R.string.unit_mbb, s.minRamMb));
            binding.tvRamAvg.setText(getString(R.string.unit_mbb, s.avgRamMb));
            binding.tvRamMax.setText(getString(R.string.unit_mbb, s.maxRamMb));

            AppDebugManager.d(Category.STATISTICS_PAGE, TAG + ": loadData stats applied for package=" + packageName
                    + " slices=" + result.slices.size());

            buildActivityChart(result.slices);
        });
    }


    private void buildActivityChart(List<CollectStatsManager.ActivitySlice> slices) {
        AppDebugManager.d(Category.STATISTICS_PAGE, TAG + ": buildActivityChart building chart with "
                + slices.size() + " slices for hours=" + hours());
        int h = hours();
        boolean sparseLabels = h >= 6;

        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);

        List<Entry> entries = new ArrayList<>();
        String[] labels = new String[slices.size()];

        for (int i = 0; i < slices.size(); i++) {
            CollectStatsManager.ActivitySlice slice = slices.get(i);
            boolean active = slice.level != CollectStatsManager.ActivityLevel.NONE;
            float y;
            switch (slice.level) {
                case LOW:    y = Y_LOW;    break;
                case MEDIUM: y = Y_MEDIUM; break;
                case HIGH:   y = Y_HIGH;   break;
                default:
                    if (slice.level != CollectStatsManager.ActivityLevel.NONE) {
                        AppDebugManager.w(Category.STATISTICS_PAGE, TAG
                                + ": buildActivityChart encountered unexpected ActivityLevel=" + slice.level);
                    }
                    y = Y_NONE;
                    break;
            }
            entries.add(new Entry(i, y));

            String timeLabel = timeFormat.format(new java.util.Date(slice.slotTimestamp));

            if (!sparseLabels) {
                labels[i] = timeLabel;
            } else {
                boolean isHalfHour = (i % 2 == 1);
                boolean showLabel;
                if (!active) {
                    showLabel = false;
                } else if (!isHalfHour) {
                    showLabel = true;
                } else {
                    boolean prevActive = i > 0
                            && slices.get(i - 1).level != CollectStatsManager.ActivityLevel.NONE;
                    boolean nextActive = i < slices.size() - 1
                            && slices.get(i + 1).level != CollectStatsManager.ActivityLevel.NONE;
                    showLabel = !(prevActive && nextActive);
                }
                labels[i] = showLabel ? timeLabel : "";
            }
        }

        int color = ContextCompat.getColor(this, R.color.stats_ram);

        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setColor(color);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleColor(color);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawFilled(true);
        dataSet.setFillAlpha(40);
        dataSet.setFillColor(color);
        dataSet.setHighLightColor(color);
        dataSet.setHighlightLineWidth(1f);
        dataSet.enableDashedHighlightLine(6f, 3f, 0f);

        XAxis xAxis = binding.chartDetailActivity.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(slices.size(), false);
        xAxis.setTextSize(9f);
        xAxis.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setLabelRotationAngle(-30f);

        YAxis leftAxis = binding.chartDetailActivity.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(3.5f);
        leftAxis.setGranularity(1f);
        leftAxis.setLabelCount(4, true);
        leftAxis.setTextSize(10f);
        leftAxis.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(0x1A808080);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value < 0.5f) return getString(R.string.detail_activity_none);
                if (value < 1.5f) return getString(R.string.detail_activity_low);
                if (value < 2.5f) return getString(R.string.detail_activity_medium);
                return getString(R.string.detail_activity_high);
            }
        });

        boolean isScrollable = h >= 12;
        binding.chartDetailActivity.getAxisRight().setEnabled(false);
        binding.chartDetailActivity.setData(new LineData(dataSet));
        binding.chartDetailActivity.getDescription().setEnabled(false);
        binding.chartDetailActivity.getLegend().setEnabled(false);
        binding.chartDetailActivity.setTouchEnabled(true);
        binding.chartDetailActivity.setDragEnabled(isScrollable);
        binding.chartDetailActivity.setScaleXEnabled(isScrollable);
        binding.chartDetailActivity.setScaleYEnabled(false);
        binding.chartDetailActivity.setPinchZoom(false);
        if (isScrollable) {
            binding.chartDetailActivity.setVisibleXRangeMaximum(12f);
            binding.chartDetailActivity.moveViewToX(entries.size());
        } else {
            binding.chartDetailActivity.fitScreen();
        }
        if (isScrollable) {
            binding.chartDetailActivity.setOnChartGestureListener(
                    new com.github.mikephil.charting.listener.OnChartGestureListener() {
                @Override public void onChartGestureStart(android.view.MotionEvent me,
                        com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture lg) {
                    binding.chartDetailActivity.getParent().requestDisallowInterceptTouchEvent(true);
                }
                @Override public void onChartGestureEnd(android.view.MotionEvent me,
                        com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture lg) {
                    binding.chartDetailActivity.getParent().requestDisallowInterceptTouchEvent(false);
                }
                @Override public void onChartLongPressed(android.view.MotionEvent me) {}
                @Override public void onChartDoubleTapped(android.view.MotionEvent me) {}
                @Override public void onChartSingleTapped(android.view.MotionEvent me) {}
                @Override public void onChartFling(android.view.MotionEvent me1, android.view.MotionEvent me2, float vx, float vy) {}
                @Override public void onChartScale(android.view.MotionEvent me, float sx, float sy) {}
                @Override public void onChartTranslate(android.view.MotionEvent me, float dx, float dy) {}
            });
            android.widget.Toast.makeText(this, getString(R.string.hint_12_24_scroll),
                    android.widget.Toast.LENGTH_SHORT).show();
        } else {
            binding.chartDetailActivity.setOnChartGestureListener(null);
        }
        binding.chartDetailActivity.setExtraBottomOffset(10f);
        binding.chartDetailActivity.setExtraLeftOffset(4f);
        binding.chartDetailActivity.animateX(600);
        binding.chartDetailActivity.invalidate();
    }

    private int hours() {
        return PERIODS_HOURS[selectedPeriodIdx];
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppDebugManager.d(Category.STATISTICS_PAGE, TAG + ": onDestroy package=" + packageName);
        binding = null;
    }
}
