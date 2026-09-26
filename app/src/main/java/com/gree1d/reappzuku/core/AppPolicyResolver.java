package com.gree1d.reappzuku.core;

import androidx.annotation.Nullable;

import com.gree1d.reappzuku.db.AppPolicy;

/**
 * Single decision boundary for per-app lifecycle ownership.
 *
 * During the migration window explicit Room-backed policies win. Apps without an
 * explicit policy are mapped from the legacy Auto-Kill / Smart Lifecycle state so
 * the two old engines can be moved behind this resolver incrementally.
 */
public final class AppPolicyResolver {
    private AppPolicyResolver() {}

    public static final class LegacyState {
        public final boolean autoKillEnabled;
        public final boolean smartLifecycleEnabled;
        public final boolean whitelistMode;
        public final boolean inWhitelist;
        public final boolean inBlacklist;

        public LegacyState(boolean autoKillEnabled,
                           boolean smartLifecycleEnabled,
                           boolean whitelistMode,
                           boolean inWhitelist,
                           boolean inBlacklist) {
            this.autoKillEnabled = autoKillEnabled;
            this.smartLifecycleEnabled = smartLifecycleEnabled;
            this.whitelistMode = whitelistMode;
            this.inWhitelist = inWhitelist;
            this.inBlacklist = inBlacklist;
        }
    }

    public static int resolveStrategy(@Nullable AppPolicy explicitPolicy, LegacyState legacy) {
        if (explicitPolicy != null) return explicitPolicy.strategy;
        if (legacy == null) return AppPolicy.STRATEGY_UNMANAGED;

        // Smart Lifecycle historically consumes the blacklist too. Give it ownership
        // first so the same package is never treated as SMART and IMMEDIATE at once.
        if (legacy.smartLifecycleEnabled && legacy.inBlacklist) {
            return AppPolicy.STRATEGY_SMART;
        }

        if (!legacy.autoKillEnabled) {
            return AppPolicy.STRATEGY_UNMANAGED;
        }

        if (legacy.whitelistMode) {
            return legacy.inWhitelist
                    ? AppPolicy.STRATEGY_PROTECTED
                    : AppPolicy.STRATEGY_IMMEDIATE;
        }

        return legacy.inBlacklist
                ? AppPolicy.STRATEGY_IMMEDIATE
                : AppPolicy.STRATEGY_UNMANAGED;
    }

    public static boolean isManagedStrategy(int strategy) {
        return strategy == AppPolicy.STRATEGY_SMART
                || strategy == AppPolicy.STRATEGY_IMMEDIATE;
    }
}
