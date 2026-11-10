package com.gree1d.reappzuku.manager;

/**
 * Pure timing rules of Smart Lifecycle. The idle clock of an app starts when a
 * pass first sees it idle in the background. A later use moves the clock
 * forward. Usage data can only delay an action, never bring it closer.
 */
final class SmartIdleClock {

    private SmartIdleClock() {
    }

    /** A stored start in the future comes from a clock change and carries no information. */
    static boolean isStarted(long stored, long now) {
        return stored > 0L && stored <= now;
    }

    /**
     * @param stored   the persisted clock start, or 0
     * @param lastUsed the last known use of the app, or 0
     * @return the clock start; {@code now} when the clock has not started yet
     */
    static long resolveSince(long stored, long lastUsed, long now) {
        if (!isStarted(stored, now)) return now;
        return Math.max(stored, Math.min(lastUsed, now));
    }

    /** The app was used after its clock started, so the system raised its standby bucket again. */
    static boolean usedSinceClockStart(long stored, long lastUsed) {
        return stored > 0L && lastUsed > stored;
    }

    /**
     * Whether a pass has anything to do for the app. An app whose clock has
     * not started is always due, so a full pass can observe it.
     */
    static boolean isDue(long stored, long lastUsed, long now,
                         long standbyDelay, long forceStopDelay, boolean standbyApplied) {
        if (!isStarted(stored, now)) return true;
        long idle = now - resolveSince(stored, lastUsed, now);
        return idle >= forceStopDelay
                || (idle >= standbyDelay && (!standbyApplied || usedSinceClockStart(stored, lastUsed)));
    }
}
