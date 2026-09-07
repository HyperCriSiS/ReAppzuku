package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReleaseAssetPolicyTest {
    @Test
    public void acceptsExactSignedWorkflowAsset() {
        assertTrue(ReleaseAssetPolicy.isTrustedApkAsset(
                "v1.2.3",
                "ReAppzuku-v1.2.3.apk",
                "https://github.com/HyperCriSiS/ReAppzuku/releases/download/v1.2.3/ReAppzuku-v1.2.3.apk"));
        assertTrue(ReleaseAssetPolicy.isTrustedApkAsset(
                "1.2.3",
                "ReAppzuku-1.2.3.apk",
                "https://github.com/HyperCriSiS/ReAppzuku/releases/download/1.2.3/ReAppzuku-1.2.3.apk"));
    }

    @Test
    public void rejectsArbitraryApkFromSameRelease() {
        assertFalse(ReleaseAssetPolicy.isTrustedApkAsset(
                "v1.2.3",
                "other.apk",
                "https://github.com/HyperCriSiS/ReAppzuku/releases/download/v1.2.3/other.apk"));
    }

    @Test
    public void rejectsExpectedNameFromWrongOriginOrRepository() {
        assertFalse(ReleaseAssetPolicy.isTrustedApkAsset(
                "v1.2.3",
                "ReAppzuku-v1.2.3.apk",
                "https://example.com/ReAppzuku-v1.2.3.apk"));
        assertFalse(ReleaseAssetPolicy.isTrustedApkAsset(
                "v1.2.3",
                "ReAppzuku-v1.2.3.apk",
                "https://github.com/gree1d/ReAppzuku/releases/download/v1.2.3/ReAppzuku-v1.2.3.apk"));
    }

    @Test
    public void rejectsNonReleaseTagsAndWhitespaceVariants() {
        assertFalse(ReleaseAssetPolicy.isTrustedApkAsset(
                "ondemand-test",
                "ReAppzuku-ondemand-test.apk",
                "https://github.com/HyperCriSiS/ReAppzuku/releases/download/ondemand-test/ReAppzuku-ondemand-test.apk"));
        assertFalse(ReleaseAssetPolicy.isTrustedApkAsset(
                " v1.2.3",
                "ReAppzuku-v1.2.3.apk",
                "https://github.com/HyperCriSiS/ReAppzuku/releases/download/v1.2.3/ReAppzuku-v1.2.3.apk"));
    }
}
