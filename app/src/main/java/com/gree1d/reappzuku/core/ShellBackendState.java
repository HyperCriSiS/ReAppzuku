package com.gree1d.reappzuku.core;

/**
 * Explicit privilege-backend readiness state. Permission alone is deliberately
 * not treated as readiness: Shizuku also needs a connected UserService.
 */
public enum ShellBackendState {
    ROOT_READY(true, false, false),
    SHIZUKU_READY(true, false, false),
    SHIZUKU_BINDING(false, true, false),
    SHIZUKU_GRANTED(false, true, false),
    SHIZUKU_PERMISSION_PENDING(false, true, false),
    SHIZUKU_PERMISSION_REQUIRED(false, false, true),
    SHIZUKU_UNAVAILABLE(false, false, false),
    SHIZUKU_LOST(false, false, false);

    private final boolean ready;
    private final boolean waiting;
    private final boolean permissionRequestNeeded;

    ShellBackendState(boolean ready, boolean waiting, boolean permissionRequestNeeded) {
        this.ready = ready;
        this.waiting = waiting;
        this.permissionRequestNeeded = permissionRequestNeeded;
    }

    public boolean isReady() { return ready; }
    public boolean isWaiting() { return waiting; }
    public boolean needsPermissionRequest() { return permissionRequestNeeded; }

    static ShellBackendState resolve(
            boolean rootReady,
            boolean binderAvailable,
            boolean binderEverSeen,
            boolean permissionGranted,
            boolean permissionRequestPending,
            boolean serviceBinding,
            boolean serviceReady) {
        if (rootReady) return ROOT_READY;
        if (!binderAvailable) return binderEverSeen ? SHIZUKU_LOST : SHIZUKU_UNAVAILABLE;
        if (!permissionGranted) {
            return permissionRequestPending ? SHIZUKU_PERMISSION_PENDING : SHIZUKU_PERMISSION_REQUIRED;
        }
        if (serviceReady) return SHIZUKU_READY;
        if (serviceBinding) return SHIZUKU_BINDING;
        return SHIZUKU_GRANTED;
    }
}
