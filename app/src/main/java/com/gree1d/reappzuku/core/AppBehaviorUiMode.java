package com.gree1d.reappzuku.core;

/** On-demand is a convenience preset over two independently stored preferences. */
public final class AppBehaviorUiMode {
    public enum Mode { ON_DEMAND, STANDARD, CUSTOM }
    private AppBehaviorUiMode() {}

    public static Mode fromRequested(boolean preventShizukuAutoStart, boolean exitOnBack) {
        if (preventShizukuAutoStart && exitOnBack) return Mode.ON_DEMAND;
        if (!preventShizukuAutoStart && !exitOnBack) return Mode.STANDARD;
        return Mode.CUSTOM;
    }
}
