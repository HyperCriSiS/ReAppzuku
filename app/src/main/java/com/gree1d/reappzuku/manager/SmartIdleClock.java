package com.gree1d.reappzuku.manager;

/**
 * Pure timing rules of Smart Lifecycle. The idle clock of an app starts when
 * it was last used. Without usage data it starts when a pass first sees the
 * app in the background.
 */
final class SmartIdleClock {

    private SmartIdleClock() {
    }

    /**
     * @param stored   the persisted clock start, or 0
     * @param lastUsed the last use reported by usage statistics, or 0
     * @return the clock start; {@code now} when nothing is known yet
     */
    static long resolveSince(long stored, long lastUsed, long now) {
        // A value in the future comes from a clock change and carries no information.
        long since = Math.max(stored > now ? 0L : stored, lastUsed > now ? 0L : lastUsed);
        return since <= 0L ? now : since;
    }

    /** The app was used after its clock started, so the system raised its standby bucket again. */
    static boolean usedSinceClockStart(long stored, long lastUsed) {
        return stored > 0L && lastUsed > stored;
    }

    /**
     * Whether a pass has anything to do for the app. An app nothing is known
     * about is due once, so a full pass can start its clock.
     */
    static boolean isDue(long stored, long lastUsed, long now,
                         long standbyDelay, long forceStopDelay, boolean standbyApplied) {
        if (stored <= 0L && lastUsed <= 0L) return true;
        long idle = now - resolveSince(stored, lastUsed, now);
        return idle >= forceStopDelay
                || (idle >= standbyDelay && (!standbyApplied || usedSinceClockStart(stored, lastUsed)));
    }
}
