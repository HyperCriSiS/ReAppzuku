package com.gree1d.reappzuku.core;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * Pure release-metadata selection, independent of Android and network access.
 * Untrusted API URLs are never used to construct a release page, and APK links must exactly
 * match the fork's signed-release tag/name/path contract.
 */
public final class ReleaseMetadataPolicy {
    public static final int MAX_JSON_CHARS = 1_048_576;
    public static final int MAX_CHANGELOG_CHARS = 16_384;
    private static final String RELEASES_PAGE =
            "https://github.com/HyperCriSiS/ReAppzuku/releases";

    private ReleaseMetadataPolicy() {}

    /** Rejects oversized metadata before allocating or parsing its entire JSON envelope. */
    public static String readBoundedJson(Reader reader) throws IOException {
        StringBuilder out = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) {
            if (n > MAX_JSON_CHARS - out.length()) {
                throw new IOException("GitHub release metadata exceeded the size bound");
            }
            out.append(buf, 0, n);
        }
        return out.toString();
    }

    public static final class Asset {
        public final String name;
        public final String browserDownloadUrl;

        public Asset(String name, String browserDownloadUrl) {
            this.name = name;
            this.browserDownloadUrl = browserDownloadUrl;
        }
    }

    public static final class Release {
        public final String rawTag;
        public final String changelog;
        public final boolean draft;
        public final boolean prerelease;
        public final List<Asset> assets;

        public Release(String rawTag, String changelog, boolean draft, boolean prerelease,
                       List<Asset> assets) {
            this.rawTag = rawTag;
            this.changelog = changelog;
            this.draft = draft;
            this.prerelease = prerelease;
            this.assets = assets;
        }
    }

    public static final class Selection {
        public final String tagName;
        public final String changelog;
        public final String downloadUrl;
        public final String releasePageUrl;

        private Selection(String tagName, String changelog, String downloadUrl,
                          String releasePageUrl) {
            this.tagName = tagName;
            this.changelog = changelog;
            this.downloadUrl = downloadUrl;
            this.releasePageUrl = releasePageUrl;
        }
    }

    public static Selection selectNewest(List<Release> releases) {
        Release best = null;
        if (releases != null) {
            for (Release release : releases) {
                if (release == null || release.draft || release.prerelease ||
                        !ReleaseVersion.isReleaseVersion(release.rawTag)) {
                    continue;
                }
                if (best == null || ReleaseVersion.isNewer(release.rawTag, best.rawTag)) {
                    best = release;
                }
            }
        }

        if (best == null) {
            // Rolling-only repositories have no stable update, not a transport failure.
            return new Selection("0.0.0", "", RELEASES_PAGE, RELEASES_PAGE);
        }

        String page = ReleaseAssetPolicy.trustedReleasePageUrl(best.rawTag);
        String download = page;
        if (best.assets != null) {
            for (Asset asset : best.assets) {
                if (asset != null && ReleaseAssetPolicy.isTrustedApkAsset(
                        best.rawTag, asset.name, asset.browserDownloadUrl)) {
                    download = asset.browserDownloadUrl;
                    break;
                }
            }
        }
        String name = best.rawTag.replaceFirst("^[vV]", "");
        String body = best.changelog == null ? "" : best.changelog.trim();
        if (body.length() > MAX_CHANGELOG_CHARS) {
            body = body.substring(0, MAX_CHANGELOG_CHARS);
        }
        return new Selection(name, body, download, page);
    }
}
