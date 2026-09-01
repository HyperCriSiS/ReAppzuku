package com.gree1d.reappzuku.manager;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.ProtectedApps;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.service.ShappkyService;
import com.gree1d.reappzuku.utils.KillShortcutActivity;
import com.gree1d.reappzuku.utils.ShortcutAuth;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RamKillShortcutManager {
    private static final String TAG = "RamKillShortcutManager";
    private static final String SHORTCUT_ID = "ram_kill_shortcut";
    private static final int ICON_SIZE = 108;
    private static final int CORNER_RADIUS = 24;

    private final Context context;
    private final ShellManager shellManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public RamKillShortcutManager(Context context, ShellManager shellManager) {
        this.context = context.getApplicationContext();
        this.shellManager = shellManager;
    }

    public void requestPinShortcut() {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            AppDebugManager.w(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: Launcher does not support pinned shortcuts");
            return;
        }
        executor.execute(() -> {
            RamInfo info = readRamInfo();
            ShortcutInfoCompat shortcut = buildShortcut(info);
            Intent callbackIntent = new Intent(context, ShappkyService.class);
            callbackIntent.setAction("SHORTCUT_PINNED");
            PendingIntent callback = PendingIntent.getService(
                    context, 0, callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            mainHandler.post(() ->
                    ShortcutManagerCompat.requestPinShortcut(context, shortcut, callback.getIntentSender())
            );
        });
    }

    public void updateShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;
        executor.execute(() -> {
            ShortcutManager sm = context.getSystemService(ShortcutManager.class);
            if (sm == null) return;
            boolean isPinned = false;
            for (ShortcutInfo s : sm.getPinnedShortcuts()) {
                if (SHORTCUT_ID.equals(s.getId())) { isPinned = true; break; }
            }
            if (!isPinned) return;
            RamInfo info = readRamInfo();
            ShortcutInfoCompat shortcut = buildShortcut(info);
            ShortcutManagerCompat.updateShortcuts(context, Collections.singletonList(shortcut));
        });
    }

    public void performKillAndUpdate(AutoKillManager autoKillManager) {
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: performKillAndUpdate started, thread=" + Thread.currentThread().getName());
        executor.execute(() -> {
            long ramBefore = readAvailableRamKb();
            AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: performKillAndUpdate ramBefore=" + ramBefore + " KB");
            new Thread(() -> trimMemoryForActivePackages(autoKillManager)).start();
            autoKillManager.performAutoKillWithResult(null, null, (killCount, ignored) -> {
                AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: performKillAndUpdate kill callback received, killCount=" + killCount + ", scheduling toast in 2000ms");
                mainHandler.postDelayed(() -> {
                    long ramAfter = readAvailableRamKb();
                    long freedKb = Math.max(0, ramAfter - ramBefore);
                    AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: performKillAndUpdate ramAfter=" + ramAfter + " KB, freedKb=" + freedKb + " KB");
                    showKillToast(killCount, freedKb);
                    updateShortcut();
                }, 5000);
            }, "RAM Shortcut");
        });
    }

    private void showKillToast(int killCount, long freedKb) {
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: showKillToast killCount=" + killCount + ", freedKb=" + freedKb + ", thread=" + Thread.currentThread().getName());
        String ram;
        if (freedKb <= 0) {
            ram = null;
        } else if (freedKb < 1024) {
            ram = freedKb + " KB";
        } else {
            ram = String.format(Locale.getDefault(), "%.1f MB", freedKb / 1024f);
        }
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: showKillToast ram=" + ram);
        String msg;
        if (ram == null) {
            msg = context.getResources().getQuantityString(
                    R.plurals.toast_killed_apps_no_ram, killCount, killCount);
        } else {
            msg = context.getResources().getQuantityString(
                    R.plurals.toast_killed_apps, killCount, killCount, ram);
        }
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: showKillToast msg=\"" + msg + "\", showing toast");
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: showKillToast toast shown");
    }

    private void trimMemoryForActivePackages(AutoKillManager autoKillManager) {
        String psOutput = shellManager.runShellCommandAndGetFullOutput("ps -A -o pid,name");
        if (psOutput == null || psOutput.trim().isEmpty()) {
            AppDebugManager.w(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: trimMemoryForActivePackages ps output is null or empty, skipping");
            return;
        }
        android.content.pm.PackageManager pm = context.getPackageManager();
        java.util.Set<String> whitelist = autoKillManager.getWhitelistedApps();
        for (String line : psOutput.split("\n")) {
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length < 2) continue;
            String pid = parts[0].trim();
            String fullName = parts[1].trim();
            String basePkg = fullName.contains(":") ? fullName.substring(0, fullName.indexOf(":")) : fullName;
            if (basePkg.isEmpty() || !basePkg.contains(".")) continue;
            if (whitelist.contains(basePkg)) continue;
            if (ProtectedApps.isProtected(context, basePkg)) continue;
            try {
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(basePkg, 0);
                if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_PERSISTENT) != 0) continue;
                AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: trimMemoryForActivePackages sending COMPLETE trim to pid=" + pid + " pkg=" + basePkg);
                shellManager.runShellCommandBlocking("am send-trim-memory " + pid + " COMPLETE");
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        }
    }

    private ShortcutInfoCompat buildShortcut(RamInfo info) {
        Bitmap icon = buildIcon(info);
        Intent killIntent = new Intent(context, KillShortcutActivity.class);
        killIntent.setAction(ShortcutAuth.ACTION_RAM_KILL_SECURE);
        ShortcutAuth.authorize(context, killIntent);
        return new ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                .setShortLabel(formatLabel(info))
                .setLongLabel(formatLabel(info))
                .setIcon(IconCompat.createWithBitmap(icon))
                .setIntent(killIntent)
                .build();
    }

    private Bitmap buildIcon(RamInfo info) {
        Bitmap bmp = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(resolveBackgroundColor());
        canvas.drawRoundRect(new RectF(0, 0, ICON_SIZE, ICON_SIZE), CORNER_RADIUS, CORNER_RADIUS, bgPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(resolveTextColor());
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(40f);
        textPaint.setFakeBoldText(true);

        String percentText = info != null ? info.percent + "%" : "—";
        float cx = ICON_SIZE / 2f;
        float cy = ICON_SIZE / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(percentText, cx, cy, textPaint);

        return bmp;
    }

    private int resolveBackgroundColor() {
        return ContextCompat.getColor(context, R.color.primary);
    }

    private int resolveTextColor() {
        return Color.WHITE;
    }

    private String formatLabel(RamInfo info) {
        if (info == null) return context.getString(R.string.settings_add_ram_shortcut_title);
        return info.percent + "% RAM";
    }

    private long readAvailableRamKb() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemAvailable:")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) return Long.parseLong(parts[1]);
                }
            }
        } catch (Exception e) {
            AppDebugManager.e(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: readAvailableRamKb failed to read /proc/meminfo", e);
        }
        return 0;
    }

    private RamInfo readRamInfo() {
        long totalKb = 0;
        long availableKb = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) totalKb = Long.parseLong(parts[1]);
                } else if (line.startsWith("MemAvailable:")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) availableKb = Long.parseLong(parts[1]);
                }
                if (totalKb > 0 && availableKb > 0) break;
            }
        } catch (IOException | NumberFormatException e) {
            AppDebugManager.e(Category.SHORTCUTS_WIDGETS, "RamKillShortcutManager: readRamInfo failed to read /proc/meminfo", e);
            return null;
        }
        if (totalKb <= 0) return null;
        int percent = (int) Math.round((totalKb - availableKb) * 100.0 / totalKb);
        return new RamInfo(percent);
    }

    private static final class RamInfo {
        final int percent;
        RamInfo(int percent) { this.percent = Math.max(0, Math.min(100, percent)); }
    }
}
