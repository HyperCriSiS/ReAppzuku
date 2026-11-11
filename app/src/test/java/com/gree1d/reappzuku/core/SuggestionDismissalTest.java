package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

public class SuggestionDismissalTest {
    private static final long NOW = 100L * 24 * 60 * 60 * 1000;

    @Test
    public void aDismissedRecommendationComesBackAfterThirtyDays() {
        Map<String, Long> active = SuggestionLoader.activeDismissals(new HashSet<>(Arrays.asList(
                "a|1|" + (NOW - SuggestionLoader.DISMISS_MS + 1),
                "b|1|" + (NOW - SuggestionLoader.DISMISS_MS))), NOW);
        assertTrue(active.containsKey("a|1"));
        assertEquals(1, active.size());
    }

    @Test
    public void anEntryStoredWithoutATimeStartsItsThirtyDaysNow() {
        Map<String, Long> active = SuggestionLoader.activeDismissals(
                new HashSet<>(Arrays.asList("a|2", "broken", "c|1|x")), NOW);
        assertEquals(Long.valueOf(NOW), active.get("a|2"));
        assertEquals(1, active.size());
    }
}
