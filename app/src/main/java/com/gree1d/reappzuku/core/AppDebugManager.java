package com.gree1d.reappzuku.core;

import android.content.Context;

/**
 * Compatibility facade for historical debug call sites.
 * Production debug logging and persistent debug switches are disabled.
 */
public final class AppDebugManager {
    private AppDebugManager() {}

    public enum Category {
        MAIN_PAGE("Main Page"), SETTINGS_PAGE("Settings Page"), STATISTICS_PAGE("Statistics Page"),
        CORE("App Core"), FOREGROUND_SERVICE("Foreground Service"), TRIGGERS("Triggers"),
        ADVANCED_CONDITIONS("Advanced Conditions"), SCAN("Scan"), AUTO_KILL_BASE("Auto-Kill Base"),
        AUTO_KILL_PRESETS("Auto-Kill Presets"), SHORTCUTS_WIDGETS("Shortcuts & Widget"),
        BACKGROUND_RESTRICTIONS("Background Restrictions"), RESTRICTIONS_SCHEDULER("Restrictions Scheduler"),
        SLEEP_MODE("SleepMode"), BACKUP_RESTORE("Backup & Restore"), UTILS("Other utils");

        public final String displayName;
        Category(String displayName) { this.displayName = displayName; }
    }

    public static void init(Context context) {}
    public static void setEnabled(boolean enabled) {}
    public static boolean isEnabled() { return false; }
    public static void setCategory(Category category, boolean enabled) {}
    public static boolean isCategoryEnabled(Category category) { return false; }
    public static void enableCategories(Category... categories) {}
    public static void disableAllCategories() {}
    public static void v(Category category, String message) {}
    public static void d(Category category, String message) {}
    public static void i(Category category, String message) {}
    public static void w(Category category, String message) {}
    public static void e(Category category, String message) {}
    public static void e(Category category, String message, Throwable throwable) {}
    public static void w(Category category, String message, Throwable throwable) {}
}
