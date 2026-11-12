package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.gree1d.reappzuku.R;
import static com.gree1d.reappzuku.core.AppConstants.RAM_MONITOR_UPDATE_INTERVAL_MS;
import static com.gree1d.reappzuku.core.PreferenceKeys.*;
import static com.gree1d.reappzuku.core.AppConstants.*;

public class RamMonitor {

    private final Context context;
    private final Handler handler;
    private final LinearProgressIndicator ramUsageBar;
    private final TextView ramUsageText;
    private final ExecutorService executor;
    private volatile boolean isMonitoring = false;
    private Runnable monitorRunnable;
    /** What the bar shows; -1 before the first value and after the accent color was set from outside. */
    private long shownUsedMb = -1;
    private int shownBand = -1;

    /**
     * The bar is only moved for a change of half a percent or more. Its
     * animation renders dozens of frames, and a smaller step is not visible.
     */
    static boolean barNeedsUpdate(long shownUsedMb, long usedMb, long totalMb) {
        return shownUsedMb < 0 || Math.abs(usedMb - shownUsedMb) * 200 >= totalMb;
    }

    /** 0 normal, 1 from 60 percent, 2 from 85 percent: the three colors of the bar. */
    static int colorBand(long usedMb, long totalMb) {
        float fraction = (float) usedMb / totalMb;
        return fraction >= 0.85f ? 2 : fraction >= 0.6f ? 1 : 0;
    }

    public RamMonitor(Context context, Handler handler,
                      LinearProgressIndicator ramUsageBar, TextView ramUsageText) {
        this.context = context;
        this.handler = handler;
        this.ramUsageBar = ramUsageBar;
        this.ramUsageText = ramUsageText;
        this.executor = Executors.newSingleThreadExecutor();

        applyAccentColor();
    }

    public void applyAccentColor() {
        SharedPreferences prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE);
        int accent = prefs.getInt(KEY_ACCENT, ACCENT_SYSTEM);

        int baseColor;
        if (accent == ACCENT_CUSTOM) {
            baseColor = prefs.getInt(KEY_ACCENT_CUSTOM_COLOR, ACCENT_CUSTOM_DEFAULT_COLOR);
        } else {
            baseColor = com.google.android.material.color.MaterialColors.getColor(
                    ramUsageBar, androidx.appcompat.R.attr.colorPrimary);
        }
        ramUsageBar.setIndicatorColor(baseColor);
        shownBand = -1;
    }

    public void startMonitoring() {
        if (isMonitoring) {
            return;
        }


        isMonitoring = true;
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isMonitoring) {
                    return;
                }

                executor.execute(() -> {
                    final RamInfo ramInfo = readRamUsage();

                    handler.post(() -> {
                        if (!isMonitoring)
                            return;

                        if (ramInfo != null && ramInfo.totalMb > 0) {
                            if (barNeedsUpdate(shownUsedMb, ramInfo.usedMb, ramInfo.totalMb)) {
                                ramUsageBar.setMax((int) ramInfo.totalMb);
                                // Animated when the bar first fills. A later step of a few
                                // pixels rendered some 70 frames for a move nobody can follow.
                                ramUsageBar.setProgress((int) ramInfo.usedMb, shownUsedMb < 0);
                                shownUsedMb = ramInfo.usedMb;
                            }
                            ramUsageText.setText(context.getString(R.string.ram_usage,
                                    ramInfo.usedMb, ramInfo.totalMb));

                            int band = colorBand(ramInfo.usedMb, ramInfo.totalMb);
                            if (band != shownBand) {
                                if (band == 2) {
                                    ramUsageBar.setIndicatorColor(
                                            resolveAttrColor(androidx.appcompat.R.attr.colorError));
                                } else if (band == 1) {
                                    ramUsageBar.setIndicatorColor(0xFFFF9800);
                                } else {
                                    applyAccentColor();
                                }
                                shownBand = band;
                            }
                        } else {

                            ramUsageText.setText(context.getString(R.string.ram_usage_unavailable));
                        }
                    });
                });

                if (isMonitoring) {
                    handler.postDelayed(this, RAM_MONITOR_UPDATE_INTERVAL_MS);
                }
            }
        };

        handler.post(monitorRunnable);
    }

    private int resolveAttrColor(int attrRes) {
        android.util.TypedValue tv = new android.util.TypedValue();
        context.getTheme().resolveAttribute(attrRes, tv, true);
        return tv.data;
    }

    private RamInfo readRamUsage() {
        try (RandomAccessFile reader = new RandomAccessFile("/proc/meminfo", "r")) {
            String line;
            long memTotal = 0;
            long memAvailable = 0;

            for (int i = 0; i < 3 && (line = reader.readLine()) != null; i++) {
                if (line.startsWith("MemTotal")) {
                    memTotal = parseMemValue(line);
                } else if (line.startsWith("MemAvailable")) {
                    memAvailable = parseMemValue(line);
                }
            }

            if (memTotal > 0) {
                long memUsed = memTotal - memAvailable;
                return new RamInfo(memUsed / 1024, memTotal / 1024);
            }

        } catch (IOException | NumberFormatException e) {

        }
        return null;
    }

    private long parseMemValue(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 2) {
            return Long.parseLong(parts[1]);
        }
        return 0;
    }

    public void stopMonitoring() {

        isMonitoring = false;
        if (monitorRunnable != null) {
            handler.removeCallbacks(monitorRunnable);
            monitorRunnable = null;
        }
        executor.shutdownNow();
    }

    private static class RamInfo {
        final long usedMb;
        final long totalMb;

        RamInfo(long usedMb, long totalMb) {
            this.usedMb = usedMb;
            this.totalMb = totalMb;
        }
    }
}
