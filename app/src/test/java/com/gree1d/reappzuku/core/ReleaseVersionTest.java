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

    @Test public void rejectsNonCanonicalUnboundedOrAmbiguousMetadataVersions() {
        assertFalse(ReleaseVersion.isReleaseVersion(" v2.0.0"));
        assertFalse(ReleaseVersion.isReleaseVersion("v2.0.0 "));
        assertFalse(ReleaseVersion.isReleaseVersion("v2.0.0\\n"));
        assertFalse(ReleaseVersion.isReleaseVersion("2.0.0+foreign"));
        assertFalse(ReleaseVersion.isReleaseVersion("1.2147483648"));
        assertFalse(ReleaseVersion.isReleaseVersion("1.".repeat(17) + "0"));
        assertFalse(ReleaseVersion.isReleaseVersion("1" + "0".repeat(65)));
        assertFalse(ReleaseVersion.isNewer(" v9.0", "1.0"));
        assertFalse(ReleaseVersion.isNewer("1.0", " 0.0"));
    }

    @Test public void comparesNumericVersionsWithoutTrustingReleaseOrdering() {
        assertTrue(ReleaseVersion.isNewer("v2.10.0", "2.9.99"));
        assertFalse(ReleaseVersion.isNewer("2.1", "2.1.0.0"));
        assertFalse(ReleaseVersion.isNewer("V1.0", "v1.0.0"));
    }

    @Test public void ignoresNonProductionTagsForComparison() {
        assertFalse(ReleaseVersion.isNewer("ondemand-test", "1.8.7"));
        assertFalse(ReleaseVersion.isNewer("1.8.8-beta", "1.8.7"));
    }
}