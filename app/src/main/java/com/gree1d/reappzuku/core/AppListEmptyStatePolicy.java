package com.gree1d.reappzuku.core;

/** Distinguishes an unfinished scan from an actual empty list or an empty filter result. */
public final class AppListEmptyStatePolicy {
    public enum State {
        HIDDEN,
        NO_RUNNING_APPS,
        NO_FILTER_RESULTS
    }

    private AppListEmptyStatePolicy() {}

    public static State resolve(boolean scanCompleted, int totalApps, int visibleApps) {
        if (!scanCompleted || visibleApps > 0) return State.HIDDEN;
        return totalApps == 0 ? State.NO_RUNNING_APPS : State.NO_FILTER_RESULTS;
    }
}
