package com.gree1d.reappzuku.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReleaseVersionTest {
    @Test public void comparesNumericReleases() {
        assertTrue(ReleaseVersion.isNewer("1.8.8", "1.8.7"));
        assertTrue(ReleaseVersion.isNewer("v2.0.0", "1.9.9"));
        assertFalse(ReleaseVersion.isNewer("1.8.7", "1.8.7"));
        assertFalse(ReleaseVersion.isNewer("1.8.6", "1.8.7"));
    }

    @Test public void ignoresNonProductionTags() {
        assertFalse(ReleaseVersion.isNewer("ondemand-test", "1.8.7"));
        assertFalse(ReleaseVersion.isNewer("1.8.8-beta", "1.8.7"));
    }
}
