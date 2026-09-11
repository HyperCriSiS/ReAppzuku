package com.gree1d.reappzuku.core;

/**
 * Binds update downloads to the exact APK naming and origin produced by the signed release workflow.
 * A valid release may still be shown without a trusted APK asset, but it must not become a direct
 * executable download link.
 */
public final class ReleaseAssetPolicy {
    private static final String RELEASES_PAGE =
            "https://github.com/HyperCriSiS/ReAppzuku/releases";
    private static final String RELEASE_DOWNLOAD_PREFIX = RELEASES_PAGE + "/download/";

    private ReleaseAssetPolicy() {}

    public static String trustedReleasePageUrl(String rawTag) {
        if (rawTag == null) return RELEASES_PAGE;
        String tag = rawTag.trim();
        if (!tag.equals(rawTag) || !ReleaseVersion.isReleaseVersion(tag)) return RELEASES_PAGE;
        return RELEASES_PAGE + "/tag/" + tag;
    }

    public static boolean isTrustedApkAsset(
            String rawTag,
            String assetName,
            String browserDownloadUrl) {
        if (rawTag == null || assetName == null || browserDownloadUrl == null) return false;
        String tag = rawTag.trim();
        if (!tag.equals(rawTag) || !ReleaseVersion.isReleaseVersion(tag)) return false;

        String expectedName = "ReAppzuku-" + tag + ".apk";
        if (!expectedName.equals(assetName)) return false;

        String expectedUrl = RELEASE_DOWNLOAD_PREFIX + tag + "/" + expectedName;
        return expectedUrl.equals(browserDownloadUrl);
    }
}
