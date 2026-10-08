package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class AppBehaviorUiModeTest {
    @Test public void onDemandIsBothRequestedTrue() {
        assertEquals(AppBehaviorUiMode.Mode.ON_DEMAND,
                AppBehaviorUiMode.fromRequested(true, true));
    }

    @Test public void standardIsBothRequestedFalse() {
        assertEquals(AppBehaviorUiMode.Mode.STANDARD,
                AppBehaviorUiMode.fromRequested(false, false));
    }

    @Test public void bothMixedCombinationsAreCustom() {
        assertEquals(AppBehaviorUiMode.Mode.CUSTOM,
                AppBehaviorUiMode.fromRequested(true, false));
        assertEquals(AppBehaviorUiMode.Mode.CUSTOM,
                AppBehaviorUiMode.fromRequested(false, true));
    }
}
