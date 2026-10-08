package com.gree1d.reappzuku.core;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReleaseMetadataPolicyTest {
    private static final String PAGE =
            "https://github.com/HyperCriSiS/ReAppzuku/releases";

    private static ReleaseMetadataPolicy.Asset asset(String tag, String url) {
        return new ReleaseMetadataPolicy.Asset("ReAppzuku-" + tag + ".apk", url);
    }

    private static String trustedApk(String tag) {
        return PAGE + "/download/" + tag + "/ReAppzuku-" + tag + ".apk";
    }

    private static ReleaseMetadataPolicy.Release release(String tag, boolean draft,
            boolean prerelease, ReleaseMetadataPolicy.Asset... assets) {
        return new ReleaseMetadataPolicy.Release(tag, "Changes", draft, prerelease,
                Arrays.asList(assets));
    }

    @Test public void selectsHighestNumericStableReleaseNotFirstApiEntry() {
        List<ReleaseMetadataPolicy.Release> shuffled = Arrays.asList(
                release("v1.9.0", false, false),
                release("v2.3.1", true, false, asset("v2.3.1", trustedApk("v2.3.1"))),
                release("v2.10.0", false, false, asset("v2.10.0", trustedApk("v2.10.0"))),
                release("v2.9.99", false, false),
                release("v999.0.0", false, true));
        ReleaseMetadataPolicy.Selection result = ReleaseMetadataPolicy.selectNewest(shuffled);
        assertEquals("2.10.0", result.tagName);
        assertEquals(PAGE + "/tag/v2.10.0", result.releasePageUrl);
        assertEquals(trustedApk("v2.10.0"), result.downloadUrl);
    }

    @Test public void stableReleaseWithoutTrustedApkOnlyLinksToTrustedReleasePage() {
        ReleaseMetadataPolicy.Selection result = ReleaseMetadataPolicy.selectNewest(
                Collections.singletonList(release("v3.0.0", false, false,
                        asset("v3.0.0", "https://evil.example/foreign.apk"),
                        new ReleaseMetadataPolicy.Asset("ReAppzuku-v3.0.0-copy.apk",
                                trustedApk("v3.0.0")),
                        asset("v3.0.0", PAGE + "/download/v2.0.0/ReAppzuku-v3.0.0.apk"))));
        assertEquals(PAGE + "/tag/v3.0.0", result.releasePageUrl);
        assertEquals(result.releasePageUrl, result.downloadUrl);
    }

    @Test public void rejectsMalformedAndUnstableTagsEvenIfTheyLookNewer() {
        ReleaseMetadataPolicy.Selection result = ReleaseMetadataPolicy.selectNewest(Arrays.asList(
                release("v100.0-beta", false, false),
                release(" v200.0", false, false),
                release("v99.0 ", false, false),
                release("ondemand-test", false, false),
                release("v2147483648.0", false, false),
                release("v4.0", false, true),
                release("v3.0", true, false),
                release("v2.0", false, false)));
        assertEquals("2.0", result.tagName);
    }

    @Test public void emptyOrRollingOnlyMetadataIsNotAnUpdate() {
        assertEquals("0.0.0", ReleaseMetadataPolicy.selectNewest(null).tagName);
        assertEquals(PAGE, ReleaseMetadataPolicy.selectNewest(
                Collections.emptyList()).downloadUrl);
        assertEquals("0.0.0", ReleaseMetadataPolicy.selectNewest(
                Arrays.asList(null, release("ondemand-test", false, false),
                        release("v2.0", true, false))).tagName);
    }

    @Test public void acceptsOnlyExactSameForkSignedArtifactPath() {
        ReleaseMetadataPolicy.Selection result = ReleaseMetadataPolicy.selectNewest(
                Collections.singletonList(release("v4.1.0", false, false,
                        asset("v4.1.0", "https://github.com/gree1d/ReAppzuku/releases/download/v4.1.0/ReAppzuku-v4.1.0.apk"),
                        asset("v4.1.0", trustedApk("v4.1.0") + "?redirect=evil"),
                        asset("v4.1.0", "http://github.com/HyperCriSiS/ReAppzuku/releases/download/v4.1.0/ReAppzuku-v4.1.0.apk"),
                        asset("v4.1.0", trustedApk("v4.1.0")))));
        assertEquals(trustedApk("v4.1.0"), result.downloadUrl);
    }

    @Test public void overlyLongChangelogIsClippedNotDisplayedInFull() {
        ReleaseMetadataPolicy.Selection result = ReleaseMetadataPolicy.selectNewest(
                Collections.singletonList(new ReleaseMetadataPolicy.Release(
                        "v2.1.0", "A".repeat(ReleaseMetadataPolicy.MAX_CHANGELOG_CHARS + 8),
                        false, false, Collections.emptyList())));
        assertEquals(ReleaseMetadataPolicy.MAX_CHANGELOG_CHARS, result.changelog.length());
    }

    @Test public void boundedNetworkReaderRejectsOversizedPayloadAndAcceptsExactLimit()
            throws Exception {
        String exact = "x".repeat(ReleaseMetadataPolicy.MAX_JSON_CHARS);
        assertEquals(exact.length(), ReleaseMetadataPolicy.readBoundedJson(
                new StringReader(exact)).length());
        try {
            ReleaseMetadataPolicy.readBoundedJson(new StringReader(exact + "X"));
            fail("Metadata parser must reject oversized response bodies");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("size bound"));
        }
    }

    @Test public void malformedUntrustedAssetObjectsNeverCreateDownloadLinks() {
        ReleaseMetadataPolicy.Selection result = ReleaseMetadataPolicy.selectNewest(
                Collections.singletonList(new ReleaseMetadataPolicy.Release("v2.0", null,
                        false, false, Arrays.asList(null,
                        new ReleaseMetadataPolicy.Asset(null, trustedApk("v2.0")),
                        new ReleaseMetadataPolicy.Asset("ReAppzuku-v2.0.apk", null)))));
        assertEquals(result.releasePageUrl, result.downloadUrl);
        assertEquals("", result.changelog);
        assertFalse(result.downloadUrl.endsWith(".apk"));
    }
}
