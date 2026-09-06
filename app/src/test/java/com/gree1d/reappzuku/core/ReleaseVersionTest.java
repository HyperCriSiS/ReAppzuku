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

    @Test public void identifiesOnlyProductionReleaseVersions() {
        assertTrue(ReleaseVersion.isReleaseVersion("1.8.8"));
        assertTrue(ReleaseVersion.isReleaseVersion("v2.0.0"));
        assertTrue(ReleaseVersion.isReleaseVersion("V2.0"));
        assertFalse(ReleaseVersion.isReleaseVersion("ondemand-test"));
        assertFalse(ReleaseVersion.isReleaseVersion("1.8.8-beta"));
        assertFalse(ReleaseVersion.isReleaseVersion(""));
        assertFalse(ReleaseVersion.isReleaseVersion(null));
    }

    @Test public void ignoresNonProductionTagsForComparison() {
        assertFalse(ReleaseVersion.isNewer("ondemand-test", "1.8.7"));
        assertFalse(ReleaseVersion.isNewer("1.8.8-beta", "1.8.7"));
    }
}
