package com.gree1d.reappzuku.core;

import androidx.annotation.Nullable;

import com.gree1d.reappzuku.db.AppPolicy;

/**
 * Pure presentation/filter policy for the main app list.
 *
 * <p>Status is derived from the same effective-policy and legacy-fallback rules used by
 * execution. Needs-setup is a queue state layered on top of lifecycle ownership; fail-safe
 * protection always wins.</p>
 */
public final class AppPolicyListState {
    public static final int STATUS_UNMANAGED = 0;
    public static final int STATUS_PROTECTED = 1;
    public static final int STATUS_SMART = 2;
    public static final int STATUS_IMMEDIATE = 3;
    public static final int STATUS_NEEDS_SETUP = 4;

    public static final int FILTER_NONE = 0;
    public static final int FILTER_MANAGED = 1;
    public static final int FILTER_SMART = 1 << 1;
    public static final int FILTER_IMMEDIATE = 1 << 2;
    public static final int FILTER_PROTECTED = 1 << 3;
    public static final int FILTER_NEEDS_SETUP = 1 << 4;
    private static final int FILTER_ALL = FILTER_MANAGED
            | FILTER_SMART
            | FILTER_IMMEDIATE
            | FILTER_PROTECTED
            | FILTER_NEEDS_SETUP;

    private AppPolicyListState() {}

    public static int resolveStatus(
            @Nullable AppPolicy storedPolicy,
            boolean migrationSnapshotCurrent,
            @Nullable AppPolicyResolver.LegacyState legacyState,
            boolean needsSetup,
            boolean failSafeProtected) {
        if (failSafeProtected) return STATUS_PROTECTED;
        if (needsSetup) return STATUS_NEEDS_SETUP;

        AppPolicy effective = AppPolicyLegacyMigrator.resolveEffectivePolicy(
                storedPolicy, migrationSnapshotCurrent);
        int strategy = AppPolicyResolver.resolveStrategy(
                effective, migrationSnapshotCurrent ? null : legacyState);
        switch (strategy) {
            case AppPolicy.STRATEGY_PROTECTED:
                return STATUS_PROTECTED;
            case AppPolicy.STRATEGY_SMART:
                return STATUS_SMART;
            case AppPolicy.STRATEGY_IMMEDIATE:
                return STATUS_IMMEDIATE;
            case AppPolicy.STRATEGY_UNMANAGED:
            default:
                return STATUS_UNMANAGED;
        }
    }

    public static int sanitizeStatus(int status) {
        return status >= STATUS_UNMANAGED && status <= STATUS_NEEDS_SETUP
                ? status : STATUS_UNMANAGED;
    }

    public static int sanitizeFilterMask(int mask) {
        return mask & FILTER_ALL;
    }

    public static boolean isManagedStatus(int status) {
        return status == STATUS_SMART || status == STATUS_IMMEDIATE;
    }

    public static boolean matchesFilter(int filterMask, int status) {
        int mask = sanitizeFilterMask(filterMask);
        int safeStatus = sanitizeStatus(status);
        if (mask == FILTER_NONE) return true;

        if ((mask & FILTER_MANAGED) != 0 && isManagedStatus(safeStatus)) return true;
        if ((mask & FILTER_SMART) != 0 && safeStatus == STATUS_SMART) return true;
        if ((mask & FILTER_IMMEDIATE) != 0 && safeStatus == STATUS_IMMEDIATE) return true;
        if ((mask & FILTER_PROTECTED) != 0 && safeStatus == STATUS_PROTECTED) return true;
        return (mask & FILTER_NEEDS_SETUP) != 0 && safeStatus == STATUS_NEEDS_SETUP;
    }
}
