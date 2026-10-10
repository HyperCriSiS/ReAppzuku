package com.gree1d.reappzuku.manager;

import java.util.HashSet;
import java.util.Set;

/**
 * Durably visible protections reflect only completed transitions. Failures,
 * missing schedule definitions and pending operations are intentionally absent
 * from the completion sets and keep their prior markers for future recovery.
 */
public final class RestrictionsTickMarkerPolicy {
    private RestrictionsTickMarkerPolicy() {}

    public static Set<String> afterCompletedTransitions(
            Set<String> previous,
            Set<String> completedActivations,
            Set<String> completedDeactivations) {
        if (previous == null || completedActivations == null || completedDeactivations == null) {
            throw new IllegalArgumentException("marker sets must not be null");
        }
        Set<String> next = new HashSet<>(previous);
        next.addAll(completedActivations);
        next.removeAll(completedDeactivations);
        return next;
    }
}
