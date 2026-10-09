package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class NewAppSetupReviewPolicyTest {
    @Test public void sortsPendingDeterministicallyWithCaseSensitiveTieBreak() {
        List<String> sorted = NewAppSetupReviewPolicy.sortedSnapshot(
                Arrays.asList("com.zeta", "com.alpha", "com.Alpha", "com.beta"));
        assertEquals(Arrays.asList("com.Alpha", "com.alpha", "com.beta", "com.zeta"), sorted);
    }

    @Test public void capsPreviewWithoutChangingQueue() {
        String[] packages = new String[12];
        for (int i = 0; i < packages.length; i++) packages[i] = "com.example.app" + i;
        List<String> snapshot = NewAppSetupReviewPolicy.sortedSnapshot(Arrays.asList(packages));
        assertEquals(8, NewAppSetupReviewPolicy.preview(snapshot).size());
        assertEquals(12, snapshot.size());
        assertEquals(snapshot.get(0), NewAppSetupReviewPolicy.preview(snapshot).get(0));
    }

    @Test public void onlyActualQueuedChangesRequireReplayConfirmation() {
        assertFalse(NewAppSetupReviewPolicy.confirmModeChange(0, 0, 5));
        assertFalse(NewAppSetupReviewPolicy.confirmModeChange(0, 1, 0));
        assertTrue(NewAppSetupReviewPolicy.confirmModeChange(0, 1, 3));
        assertTrue(NewAppSetupReviewPolicy.confirmModeChange(1, 2, 3));
        assertFalse(NewAppSetupReviewPolicy.confirmPresetChange(0, 4, 5, 3));
        assertFalse(NewAppSetupReviewPolicy.confirmPresetChange(1, 4, 4, 3));
        assertFalse(NewAppSetupReviewPolicy.confirmPresetChange(1, 4, 5, 0));
        assertTrue(NewAppSetupReviewPolicy.confirmPresetChange(1, 4, 5, 3));
    }
}
