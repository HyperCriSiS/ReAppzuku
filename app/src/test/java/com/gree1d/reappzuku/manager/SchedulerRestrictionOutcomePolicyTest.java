package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class SchedulerRestrictionOutcomePolicyTest {
    @Test public void allStepsSuccess() {
        assertEquals("ok", SchedulerRestrictionOutcomePolicy.fromCounts(new int[]{2, 0}, true, true));
    }

    @Test public void bucketFailureIsPartial() {
        assertEquals("partial", SchedulerRestrictionOutcomePolicy.fromCounts(new int[]{2, 0}, false, true));
    }

    @Test public void whitelistFailureIsPartial() {
        assertEquals("partial", SchedulerRestrictionOutcomePolicy.fromCounts(new int[]{2, 0}, true, false));
    }

    @Test public void appOpsPartialIsPartial() {
        assertEquals("partial", SchedulerRestrictionOutcomePolicy.fromCounts(new int[]{1, 1}, true, true));
    }

    @Test public void noSucceededOpsIsError() {
        assertEquals("error", SchedulerRestrictionOutcomePolicy.fromCounts(new int[]{0, 1}, true, true));
    }

    @Test public void malformedCountsAreError() {
        assertEquals("error", SchedulerRestrictionOutcomePolicy.fromCounts(null, true, true));
        assertEquals("error", SchedulerRestrictionOutcomePolicy.fromCounts(new int[]{1}, true, true));
        assertEquals("error", SchedulerRestrictionOutcomePolicy.fromCounts(new int[]{2, -1}, true, true));
    }
}
