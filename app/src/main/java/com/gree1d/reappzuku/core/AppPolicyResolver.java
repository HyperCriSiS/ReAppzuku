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

    public static boolean shouldExecuteImmediate(@Nullable AppPolicy explicitPolicy,
                                                 LegacyState legacy,
                                                 long trigger) {
        if (resolveStrategy(explicitPolicy, legacy) != AppPolicy.STRATEGY_IMMEDIATE) {
            return false;
        }
        if (explicitPolicy == null || trigger == 0L) {
            return true;
        }
        return (explicitPolicy.triggerMask & trigger) != 0L;
    }

    public static int resolveImmediateKillMethod(@Nullable AppPolicy explicitPolicy,
                                                 int legacyAutoKillType) {
        if (explicitPolicy != null) {
            return explicitPolicy.killMethod == AppPolicy.KILL_METHOD_AM_KILL
                    ? AppPolicy.KILL_METHOD_AM_KILL
                    : AppPolicy.KILL_METHOD_FORCE_STOP;
        }
        return legacyAutoKillType == AppPolicy.KILL_METHOD_AM_KILL
                ? AppPolicy.KILL_METHOD_AM_KILL
                : AppPolicy.KILL_METHOD_FORCE_STOP;
    }

    public static boolean shouldExecuteSmart(@Nullable AppPolicy explicitPolicy,
                                             LegacyState legacy,
                                             boolean bootPass) {
        if (resolveStrategy(explicitPolicy, legacy) != AppPolicy.STRATEGY_SMART) {
            return false;
        }
        if (!bootPass || explicitPolicy == null) {
            return true;
        }
        return explicitPolicy.bootCleanup
                && (explicitPolicy.triggerMask & AppPolicy.TRIGGER_BOOT_CLEANUP) != 0L;
    }

    public static long resolveSmartStandbyDelayMs(@Nullable AppPolicy explicitPolicy,
                                                   long legacyDelayMs) {
        if (explicitPolicy != null
                && explicitPolicy.strategy == AppPolicy.STRATEGY_SMART
                && explicitPolicy.standbyDelayMs > 0L) {
            return explicitPolicy.standbyDelayMs;
        }
        return legacyDelayMs > 0L
                ? legacyDelayMs
                : AppPolicy.DEFAULT_SMART_STANDBY_DELAY_MS;
    }

    public static long resolveSmartForceStopDelayMs(@Nullable AppPolicy explicitPolicy,
                                                     long legacyDelayMs,
                                                     long resolvedStandbyDelayMs) {
        long standbyDelayMs = resolvedStandbyDelayMs > 0L
                ? resolvedStandbyDelayMs
                : AppPolicy.DEFAULT_SMART_STANDBY_DELAY_MS;
        long forceStopDelayMs;
        if (explicitPolicy != null
                && explicitPolicy.strategy == AppPolicy.STRATEGY_SMART
                && explicitPolicy.forceStopDelayMs > 0L) {
            forceStopDelayMs = explicitPolicy.forceStopDelayMs;
        } else {
            forceStopDelayMs = legacyDelayMs > 0L
                    ? legacyDelayMs
                    : AppPolicy.DEFAULT_SMART_FORCE_STOP_DELAY_MS;
        }
        return Math.max(standbyDelayMs, forceStopDelayMs);
    }

    public static boolean isManagedStrategy(int strategy) {
        return strategy == AppPolicy.STRATEGY_SMART
                || strategy == AppPolicy.STRATEGY_IMMEDIATE;
    }
}
