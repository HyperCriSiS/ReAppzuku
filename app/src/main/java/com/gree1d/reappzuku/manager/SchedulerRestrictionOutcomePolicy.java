package com.gree1d.reappzuku.manager;

/** A successful AppOps call cannot mask failed bucket or whitelist changes. */
public final class SchedulerRestrictionOutcomePolicy {
    private SchedulerRestrictionOutcomePolicy() {}

    public static String fromCounts(int[] counts, boolean bucketSucceeded,
                                    boolean whitelistSucceeded) {
        if (counts == null || counts.length < 2 || counts[0] <= 0 || counts[1] < 0) {
            return "error";
        }
        return counts[1] > 0 || !bucketSucceeded || !whitelistSucceeded ? "partial" : "ok";
    }
}
